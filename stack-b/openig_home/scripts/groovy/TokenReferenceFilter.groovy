import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

String configuredClientEndpoint = binding.hasVariable('clientEndpoint') ? (clientEndpoint as String) : null
configuredClientEndpoint = configuredClientEndpoint?.trim()
String configuredRedisHost = binding.hasVariable('redisHost') ? (redisHost as String) : null
configuredRedisHost = configuredRedisHost?.trim()
int configuredRedisTtl = binding.hasVariable('redisTtl') ? (redisTtl as Number).intValue() : 1800
String configuredRedisPassword = System.getenv('REDIS_PASSWORD') ?: ''

def readRespLine = { InputStream input ->
    ByteArrayOutputStream buffer = new ByteArrayOutputStream()
    int previous = -1
    while (true) {
        int current = input.read()
        if (current == -1) {
            throw new IOException('Unexpected EOF from Redis')
        }
        if (previous == '\r' as char && current == '\n' as char) {
            break
        }
        if (previous != -1) {
            buffer.write(previous)
        }
        previous = current
    }
    new String(buffer.toByteArray(), 'UTF-8')
}

def readExactBytes = { InputStream input, int size ->
    byte[] bytes = new byte[size]
    int offset = 0
    while (offset < size) {
        int read = input.read(bytes, offset, size - offset)
        if (read == -1) {
            throw new IOException('Unexpected EOF from Redis bulk string')
        }
        offset += read
    }
    int carriageReturn = input.read()
    int lineFeed = input.read()
    if (carriageReturn != '\r' as char || lineFeed != '\n' as char) {
        throw new IOException('Invalid Redis bulk string terminator')
    }
    bytes
}

def buildAuthCommand = {
    if (!configuredRedisPassword) {
        return null
    }
    int redisPasswordSize = configuredRedisPassword.getBytes('UTF-8').length
    "*2\r\n\$4\r\nAUTH\r\n\$${redisPasswordSize}\r\n${configuredRedisPassword}\r\n"
}

def withRedisSocket = { Closure action ->
    String authCommand = buildAuthCommand()
    new Socket().withCloseable { socket ->
        socket.connect(new InetSocketAddress(configuredRedisHost, 6379), 200)
        socket.setSoTimeout(500)
        if (authCommand != null) {
            socket.outputStream.write(authCommand.getBytes('UTF-8'))
            socket.outputStream.flush()
            String authReply = readRespLine(socket.inputStream)
            if (!authReply?.startsWith('+OK')) {
                throw new IOException("Redis AUTH failed: ${authReply}")
            }
        }
        action(socket)
    }
}

def getFromRedis = { String tokenRefId ->
    String key = "token_ref:${tokenRefId}"
    int keySize = key.getBytes('UTF-8').length
    String command = "*2\r\n\$3\r\nGET\r\n\$${keySize}\r\n${key}\r\n"

    withRedisSocket { Socket socket ->
        socket.outputStream.write(command.getBytes('UTF-8'))
        socket.outputStream.flush()
        String firstLine = readRespLine(socket.inputStream)
        if (firstLine == '$-1') {
            return null
        }
        if (!firstLine?.startsWith('$')) {
            throw new IOException("Unexpected Redis GET response: ${firstLine}")
        }
        int payloadSize = firstLine.substring(1) as int
        new String(readExactBytes(socket.inputStream, payloadSize), 'UTF-8')
    }
}

def setInRedis = { String tokenRefId, String payload ->
    String key = "token_ref:${tokenRefId}"
    int keySize = key.getBytes('UTF-8').length
    int valueSize = payload.getBytes('UTF-8').length
    String ttl = String.valueOf(configuredRedisTtl)
    String command = "*5\r\n\$3\r\nSET\r\n\$${keySize}\r\n${key}\r\n\$${valueSize}\r\n${payload}\r\n\$2\r\nEX\r\n\$${ttl.length()}\r\n${ttl}\r\n"

    withRedisSocket { Socket socket ->
        socket.outputStream.write(command.getBytes('UTF-8'))
        socket.outputStream.flush()
        String reply = readRespLine(socket.inputStream)
        if (!reply?.startsWith('+OK')) {
            throw new IOException("Redis SET failed: ${reply}")
        }
    }
}

String publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: ''
String hostHeader = request.headers.getFirst('Host') as String
String hostWithoutPort = hostHeader?.split(':')?.getAt(0)
String defaultPort = publicUrl.contains(':9080') ? '9080' : publicUrl.contains(':18080') ? '18080' : '80'
String hostWithPort = hostHeader?.contains(':') ? hostHeader : (hostWithoutPort ? hostWithoutPort + ':' + defaultPort : null)

def oauth2SessionKeys = [
    "oauth2:${configuredClientEndpoint}",
    hostWithPort ? "oauth2:http://${hostWithPort}${configuredClientEndpoint}" : null,
    hostWithoutPort ? "oauth2:http://${hostWithoutPort}${configuredClientEndpoint}" : null,
    publicUrl ? "oauth2:${publicUrl}${configuredClientEndpoint}" : null
].findAll { it != null && !it.trim().isEmpty() }.unique()

String primaryOauth2SessionKey = oauth2SessionKeys[0]

def findOauth2SessionValue = {
    for (def oauth2SessionKey : oauth2SessionKeys) {
        def oauth2Value = session[oauth2SessionKey]
        if (oauth2Value != null) {
            return oauth2Value
        }
    }
    null
}

def restoreOauth2SessionValue = { def oauth2Value ->
    for (def oauth2SessionKey : oauth2SessionKeys) {
        session[oauth2SessionKey] = oauth2Value
    }
}

def removeOauth2SessionValues = {
    for (def oauth2SessionKey : oauth2SessionKeys) {
        session.remove(oauth2SessionKey)
    }
}

try {
    if (!configuredClientEndpoint) {
        throw new IllegalStateException('TokenReferenceFilter requires clientEndpoint arg')
    }
    if (!configuredRedisHost) {
        throw new IllegalStateException('TokenReferenceFilter requires redisHost arg')
    }

    def oauth2SessionValue = findOauth2SessionValue()
    if (oauth2SessionValue != null && session[primaryOauth2SessionKey] == null) {
        session[primaryOauth2SessionKey] = oauth2SessionValue
    }

    String tokenRefId = session['token_ref_id'] as String
    if (tokenRefId?.trim() && session[primaryOauth2SessionKey] == null) {
        String redisPayload = getFromRedis(tokenRefId)
        if (!redisPayload) {
            logger.error('[TokenRef] Missing Redis payload for token_ref_id={} endpoint={}', tokenRefId, configuredClientEndpoint)
            return newResultPromise(new Response(Status.BAD_GATEWAY))
        }

        def restoredOauth2Value = new JsonSlurper().parseText(redisPayload)
        restoreOauth2SessionValue(restoredOauth2Value)
        logger.info('[TokenRef] Restored oauth2 session for endpoint={} token_ref_id={}', configuredClientEndpoint, tokenRefId)
    }

    return next.handle(context, request).then({ response ->
        def oauth2ValueForResponse = findOauth2SessionValue()
        if (oauth2ValueForResponse == null) {
            return response
        }

        try {
            String newTokenRefId = UUID.randomUUID().toString()
            String redisPayload = JsonOutput.toJson(oauth2ValueForResponse)
            setInRedis(newTokenRefId, redisPayload)
            removeOauth2SessionValues()
            session['token_ref_id'] = newTokenRefId
            logger.info('[TokenRef] Stored oauth2 session for endpoint={} token_ref_id={}', configuredClientEndpoint, newTokenRefId)
        } catch (Exception e) {
            logger.error('[TokenRef] Failed to offload oauth2 session for endpoint={}', configuredClientEndpoint, e)
        }

        response
    })
} catch (Exception e) {
    logger.error('[TokenRef] Failed to restore oauth2 session for endpoint={}', configuredClientEndpoint, e)
    newResultPromise(new Response(Status.BAD_GATEWAY))
}
