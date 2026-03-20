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
def tokenRefKey = binding.hasVariable('tokenRefKey') ? (tokenRefKey as String) : 'token_ref_id'

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

def fallbackOauth2SessionKeys = [
    "oauth2:${configuredClientEndpoint}",
    hostWithPort ? "oauth2:http://${hostWithPort}${configuredClientEndpoint}" : null,
    hostWithoutPort ? "oauth2:http://${hostWithoutPort}${configuredClientEndpoint}" : null,
    publicUrl ? "oauth2:${publicUrl}${configuredClientEndpoint}" : null
].findAll { it != null && !it.trim().isEmpty() }.unique()

String restoreOauth2SessionKey = hostWithPort ?
    "oauth2:http://${hostWithPort}${configuredClientEndpoint}" :
    fallbackOauth2SessionKeys[0]

def discoverOauth2SessionKeys = {
    try {
        return session.keySet()
            .collect { String.valueOf(it) }
            .findAll { it.startsWith('oauth2:') && it.endsWith(configuredClientEndpoint) }
            .sort()
    } catch (Exception e) {
        List<String> matchedKeys = fallbackOauth2SessionKeys.findAll { candidateKey ->
            session[candidateKey] != null
        }
        logger.warn(
            '[TokenReferenceFilter] session.keySet() unavailable, using fallback endpoint={} matchedKeys={}',
            configuredClientEndpoint,
            matchedKeys,
            e
        )
        return matchedKeys
    }
}

def collectOauth2SessionEntries = {
    Map<String, Object> oauth2Entries = [:]
    for (String oauth2SessionKey : discoverOauth2SessionKeys()) {
        def oauth2Value = session[oauth2SessionKey]
        if (oauth2Value != null) {
            oauth2Entries[oauth2SessionKey] = oauth2Value
        }
    }
    oauth2Entries
}

def restoreOauth2SessionEntries = { Object storedPayload ->
    Map<String, Object> restoredEntries = [:]
    if (storedPayload instanceof Map && storedPayload.containsKey('oauth2Entries')) {
        def serializedEntries = storedPayload['oauth2Entries']
        if (serializedEntries instanceof Map) {
            serializedEntries.each { key, value ->
                if (key != null && value != null) {
                    restoredEntries[String.valueOf(key)] = value
                }
            }
        }
    } else if (storedPayload != null) {
        restoredEntries[restoreOauth2SessionKey] = storedPayload
    }

    restoredEntries.each { key, value ->
        session[key] = value
    }

    restoredEntries
}

def stripOauth2EntriesFromSession = { String newTokenRefId ->
    Map<String, Object> preservedEntries = [:]
    try {
        session.keySet().collect { String.valueOf(it) }.sort().each { key ->
            if (!key.startsWith('oauth2:') && key != tokenRefKey) {
                def value = session[key]
                if (value != null) {
                    preservedEntries[key] = value
                }
            }
        }
    } catch (Exception e) {
        logger.warn('[TokenReferenceFilter] Failed to enumerate non-oauth2 session keys before clear endpoint={}', configuredClientEndpoint, e)
    }

    session.clear()
    preservedEntries.each { key, value ->
        session[key] = value
    }
    session[tokenRefKey] = newTokenRefId
}

try {
    if (!configuredClientEndpoint) {
        throw new IllegalStateException('TokenReferenceFilter requires clientEndpoint arg')
    }
    if (!configuredRedisHost) {
        throw new IllegalStateException('TokenReferenceFilter requires redisHost arg')
    }

    String tokenRefId = session[tokenRefKey] as String
    if (tokenRefId?.trim() && collectOauth2SessionEntries().isEmpty()) {
        String redisPayload = getFromRedis(tokenRefId)
        if (!redisPayload) {
            logger.error('[TokenReferenceFilter] Missing Redis payload for tokenRefKey={} tokenRefId={} endpoint={}', tokenRefKey, tokenRefId, configuredClientEndpoint)
            return newResultPromise(new Response(Status.BAD_GATEWAY))
        }

        def restoredOauth2Entries = restoreOauth2SessionEntries(new JsonSlurper().parseText(redisPayload))
        logger.info(
            '[TokenReferenceFilter] Restored oauth2 session keys={} endpoint={} tokenRefKey={} tokenRefId={}',
            restoredOauth2Entries.keySet(),
            configuredClientEndpoint,
            tokenRefKey,
            tokenRefId
        )
    }

    return next.handle(context, request).then({ response ->
        def oauth2EntriesForResponse = collectOauth2SessionEntries()
        if (oauth2EntriesForResponse.isEmpty()) {
            logger.warn('[TokenReferenceFilter] No oauth2 session value found during response phase endpoint={}', configuredClientEndpoint)
            return response
        }

        try {
            try {
                logger.warn("[TokenReferenceFilter] Session keys at .then(): " + session.keySet().toString())
            } catch (Exception e) {
                logger.warn('[TokenReferenceFilter] session.keySet() failed at .then() endpoint={}', configuredClientEndpoint, e)
                fallbackOauth2SessionKeys.each { candidateKey ->
                    logger.warn('[TokenReferenceFilter] Candidate key {} present={}', candidateKey, session[candidateKey] != null)
                }
            }

            String newTokenRefId = UUID.randomUUID().toString()
            String redisPayload = JsonOutput.toJson([oauth2Entries: oauth2EntriesForResponse])
            setInRedis(newTokenRefId, redisPayload)
            stripOauth2EntriesFromSession(newTokenRefId)
            logger.info(
                '[TokenReferenceFilter] Stored oauth2 session keys={} endpoint={} tokenRefKey={} tokenRefId={}',
                oauth2EntriesForResponse.keySet(),
                configuredClientEndpoint,
                tokenRefKey,
                newTokenRefId
            )
        } catch (Exception e) {
            logger.error('[TokenReferenceFilter] Failed to offload oauth2 session for endpoint={}', configuredClientEndpoint, e)
        }

        response
    })
} catch (Exception e) {
    logger.error('[TokenReferenceFilter] Failed to restore oauth2 session for endpoint={}', configuredClientEndpoint, e)
    newResultPromise(new Response(Status.BAD_GATEWAY))
}
