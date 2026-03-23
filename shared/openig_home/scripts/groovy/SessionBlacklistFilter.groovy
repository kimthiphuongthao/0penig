import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64

String configuredClientEndpoint = binding.hasVariable('clientEndpoint') ? (clientEndpoint as String) : null
configuredClientEndpoint = configuredClientEndpoint?.trim()
String sessionCacheKey = binding.hasVariable('sessionCacheKey') ? (sessionCacheKey as String)?.trim() : null
String origin = null
String configuredCanonicalOriginEnvVar = binding.hasVariable('canonicalOriginEnvVar') ? (canonicalOriginEnvVar as String)?.trim() : null
if (configuredCanonicalOriginEnvVar) {
    def envVal = System.getenv(configuredCanonicalOriginEnvVar)
    if (envVal) {
        origin = envVal
    }
}
if (!origin && binding.hasVariable('canonicalOrigin')) {
    origin = (canonicalOrigin as String)?.trim()
}
String configuredRedisPort = binding.hasVariable('redisPort') ? String.valueOf(redisPort) : (System.getenv('REDIS_PORT') ?: '6379')
configuredRedisPort = configuredRedisPort?.trim()
String configuredRedisHost = binding.hasVariable('redisHost') ? (redisHost as String)?.trim() : (System.getenv('REDIS_HOST') ?: 'shared-redis')
String configuredRedisPassword = System.getenv('REDIS_PASSWORD') ?: ''
String configuredRedisUser = binding.hasVariable('redisUser') ? (redisUser as String)?.trim() : null
String configuredRedisPasswordEnvVar = binding.hasVariable('redisPasswordEnvVar') ? (redisPasswordEnvVar as String)?.trim() : 'REDIS_PASSWORD'
String configuredRedisKeyPrefix = binding.hasVariable('redisKeyPrefix') ? (redisKeyPrefix as String)?.trim() : ''
if (configuredRedisPasswordEnvVar && configuredRedisPasswordEnvVar != 'REDIS_PASSWORD') {
    String overridePassword = System.getenv(configuredRedisPasswordEnvVar)
    if (overridePassword) { configuredRedisPassword = overridePassword }
}

def decodeSidFromToken = { String jwt ->
    String[] tokenParts = jwt.split('\\.')
    if (tokenParts.length < 2) {
        return null
    }
    String payloadJson = new String(Base64.getUrlDecoder().decode(tokenParts[1]), 'UTF-8')
    def payload = new JsonSlurper().parseText(payloadJson)
    (payload?.sid ?: payload?.sub) as String
}

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
    if (!configuredRedisPassword) {
        return null
    }
    int redisPasswordSize = configuredRedisPassword.getBytes('UTF-8').length
    if (configuredRedisUser) {
        int redisUserSize = configuredRedisUser.getBytes('UTF-8').length
        return "*3\r\n\$4\r\nAUTH\r\n\$${redisUserSize}\r\n${configuredRedisUser}\r\n\$${redisPasswordSize}\r\n${configuredRedisPassword}\r\n"
    }
    "*2\r\n\$4\r\nAUTH\r\n\$${redisPasswordSize}\r\n${configuredRedisPassword}\r\n"
}

try {
    if (!configuredClientEndpoint) {
        throw new IllegalStateException('SessionBlacklistFilter requires clientEndpoint arg')
    }
    if (!sessionCacheKey) {
        throw new IllegalStateException('SessionBlacklistFilter requires sessionCacheKey arg')
    }
    if (!origin) {
        throw new IllegalStateException('SessionBlacklistFilter requires canonicalOrigin or canonicalOriginEnvVar')
    }

    String sid = session[sessionCacheKey] as String
    if (!sid?.trim()) {
        String publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openiga.sso.local:80'
        String hostHeader = request.headers.getFirst('Host') as String
        String hostWithoutPort = hostHeader?.split(':')?.getAt(0)
        String hostWithPort = hostHeader?.contains(':') ? hostHeader : (hostWithoutPort ? hostWithoutPort + ':80' : null)

        def sessionKeys = [
            hostWithPort ? 'oauth2:http://' + hostWithPort + configuredClientEndpoint : null,
            hostWithoutPort ? 'oauth2:http://' + hostWithoutPort + configuredClientEndpoint : null,
            'oauth2:' + publicUrl + configuredClientEndpoint
        ].findAll { it != null }.unique()

        String idToken = null
        for (def key : sessionKeys) {
            idToken = session[key]?.get('atr')?.get('id_token') as String
            if (idToken?.trim()) {
                break
            }
        }
        if (!idToken?.trim()) {
            return next.handle(context, request)
        }

        sid = decodeSidFromToken(idToken)
        if (!sid?.trim()) {
            return next.handle(context, request)
        }

        session[sessionCacheKey] = sid
    }

    int redisPort = configuredRedisPort as int
    String key = (configuredRedisKeyPrefix ? configuredRedisKeyPrefix + ':' : '') + "blacklist:${sid}"
    int keySize = key.getBytes('UTF-8').length
    String command = "*2\r\n\$3\r\nGET\r\n\$${keySize}\r\n${key}\r\n"
    String authCommand = buildAuthCommand()

    boolean blacklisted = false
    new Socket().withCloseable { socket ->
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
            blacklisted = firstLine != '$-1'
        } else if (firstLine.startsWith('-')) {
            throw new IOException("Redis error: ${firstLine}")
        } else {
            throw new IOException("Unexpected Redis response: ${firstLine}")
        }
    }

    if (blacklisted) {
        session.clear()
        String originalPath = request.uri.path ?: '/'
        String originalQuery = request.uri.query ? '?' + request.uri.query : ''
        String redirectUrl = origin + originalPath + originalQuery
        Response response = new Response(Status.FOUND)
        response.headers.put('Location', [redirectUrl as String])
        return newResultPromise(response)
    }

    return next.handle(context, request)
} catch (Exception e) {
    logger.error('[SessionBlacklistFilter] Redis check failed, denying request (fail-closed)', e)
    return newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR))
}
