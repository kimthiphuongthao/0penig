import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

import java.net.InetSocketAddress
import java.net.Socket

String sessionCacheKey = binding.hasVariable('sessionCacheKey') ? (sessionCacheKey as String)?.trim() : null
String configuredRedisPort = binding.hasVariable('redisPort') ? String.valueOf(redisPort) : (System.getenv('REDIS_PORT') ?: '6379')
configuredRedisPort = configuredRedisPort?.trim()
String configuredRedisHost = binding.hasVariable('redisHost') ? (redisHost as String)?.trim() : (System.getenv('REDIS_HOST') ?: 'shared-redis')
String configuredRedisAuth = binding.hasVariable('redisAuth') ? (redisAuth as String)?.trim() : (System.getenv('REDIS_AUTH') ?: '')
String configuredRedisUser = binding.hasVariable('redisUser') ? (redisUser as String)?.trim() : null
String configuredRedisPassword = System.getenv('REDIS_PASSWORD') ?: ''
String configuredRedisPasswordEnvVar = binding.hasVariable('redisPasswordEnvVar') ? (redisPasswordEnvVar as String)?.trim() : 'REDIS_PASSWORD'
String configuredRedisKeyPrefix = binding.hasVariable('redisKeyPrefix') ? (redisKeyPrefix as String)?.trim() : ''
if (configuredRedisPasswordEnvVar && configuredRedisPasswordEnvVar != 'REDIS_PASSWORD') {
    String overridePassword = System.getenv(configuredRedisPasswordEnvVar)
    if (overridePassword) { configuredRedisPassword = overridePassword }
}
String effectiveRedisSecret = configuredRedisAuth ?: configuredRedisPassword

def readRespLine = { InputStream input ->
    ByteArrayOutputStream buffer = new ByteArrayOutputStream()
    while (true) {
        int current = input.read()
        if (current == -1) {
            throw new IOException('Unexpected end of stream while reading Redis response line')
        }
        if (current == '\r' as char) {
            int lineFeed = input.read()
            if (lineFeed == -1) {
                throw new IOException('Unexpected end of stream while reading Redis response line')
            }
            if (lineFeed != '\n' as char) {
                throw new IOException('Invalid Redis response line ending')
            }
            break
        }
        buffer.write(current)
    }
    new String(buffer.toByteArray(), 'UTF-8')
}

def buildAuthCommand = {
    if (!effectiveRedisSecret) {
        return null
    }
    int redisPasswordSize = effectiveRedisSecret.getBytes('UTF-8').length
    if (configuredRedisUser) {
        int redisUserSize = configuredRedisUser.getBytes('UTF-8').length
        return "*3\r\n\$4\r\nAUTH\r\n\$${redisUserSize}\r\n${configuredRedisUser}\r\n\$${redisPasswordSize}\r\n${effectiveRedisSecret}\r\n"
    }
    "*2\r\n\$4\r\nAUTH\r\n\$${redisPasswordSize}\r\n${effectiveRedisSecret}\r\n"
}

def isJsonXhr = { String acceptHeader ->
    acceptHeader?.contains('application/json') && !acceptHeader.contains('text/html')
}

def isBlacklistedSid = { String sid ->
    int redisPort = configuredRedisPort as int
    String key = (configuredRedisKeyPrefix ? configuredRedisKeyPrefix + ':' : '') + "blacklist:${sid}"
    int keySize = key.getBytes('UTF-8').length
    String command = "*2\r\n\$3\r\nGET\r\n\$${keySize}\r\n${key}\r\n"
    String authCommand = buildAuthCommand()

    Socket socket = null
    try {
        socket = new Socket()
        socket.connect(new InetSocketAddress(configuredRedisHost, redisPort), 200)
        socket.setSoTimeout(500)
        if (authCommand != null) {
            socket.outputStream.write(authCommand.getBytes('UTF-8'))
            socket.outputStream.flush()
            String authReply = readRespLine(socket.inputStream)
            if (!authReply?.startsWith('+OK')) {
                throw new IOException("Redis AUTH failed: ${authReply}")
            }
        }

        socket.outputStream.write(command.getBytes('UTF-8'))
        socket.outputStream.flush()

        String firstLine = readRespLine(socket.inputStream)
        if (firstLine.startsWith('$')) {
            return firstLine != '$-1'
        }
        if (firstLine.startsWith('-')) {
            throw new IOException("Redis error: ${firstLine}")
        }
        throw new IOException("Unexpected Redis response: ${firstLine}")
    } finally {
        if (socket != null) {
            socket.close()
        }
    }
}

try {
    if (!sessionCacheKey) {
        throw new IllegalStateException('SpaBlacklistGuardFilter requires sessionCacheKey arg')
    }

    String sid = session[sessionCacheKey] as String
    if (!sid?.trim()) {
        return next.handle(context, request)
    }

    if (!isBlacklistedSid(sid)) {
        return next.handle(context, request)
    }

    String acceptHeader = request.headers.getFirst('Accept') as String
    if (isJsonXhr(acceptHeader)) {
        logger.warn(
            '[SpaBlacklistGuard] Blocking blacklisted XHR sid={} sessionCacheKey={} path={}',
            sid,
            sessionCacheKey,
            request.uri.path
        )
        return newResultPromise(new Response(Status.UNAUTHORIZED))
    }

    logger.debug(
        '[SpaBlacklistGuard] Allowing browser request for downstream session clear sid={} sessionCacheKey={} path={}',
        sid,
        sessionCacheKey,
        request.uri.path
    )
    return next.handle(context, request)
} catch (Exception e) {
    logger.error('[SpaBlacklistGuard] Failed blacklist pre-check, falling back to downstream filters', e)
    return next.handle(context, request)
}
