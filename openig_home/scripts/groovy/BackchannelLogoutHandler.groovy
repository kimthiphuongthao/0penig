import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

import java.net.Socket
import java.net.URLDecoder
import java.util.Base64

def readLine = { input ->
    def bytes = new ByteArrayOutputStream()
    while (true) {
        int next = input.read()
        if (next == -1) {
            throw new EOFException('Unexpected end of stream while reading Redis response line')
        }
        if (next == 13) {
            int lf = input.read()
            if (lf != 10) {
                throw new IOException('Invalid Redis response line ending')
            }
            break
        }
        bytes.write(next)
    }
    bytes.toString('UTF-8')
}

def decodeJwtClaims = { String token ->
    def parts = token.split('\\.')
    if (parts.length < 2) {
        throw new IllegalArgumentException('logout_token is not a valid JWT')
    }
    def payload = parts[1]
    def normalized = payload.replace('-', '+').replace('_', '/')
    while (normalized.length() % 4 != 0) {
        normalized += '='
    }
    def json = new String(Base64.decoder.decode(normalized), 'UTF-8')
    new JsonSlurper().parseText(json)
}

def response = null

try {
    def formBody = request.entity.getString()
    if (formBody == null || formBody.trim().isEmpty()) {
        throw new IllegalArgumentException('Missing request body')
    }

    def logoutToken = null
    formBody.split('&').each { pair ->
        if (pair != null && pair.startsWith('logout_token=')) {
            logoutToken = URLDecoder.decode(pair.substring('logout_token='.length()), 'UTF-8')
        }
    }

    if (logoutToken == null || logoutToken.isEmpty()) {
        throw new IllegalArgumentException('Missing logout_token form field')
    }

    def claims = decodeJwtClaims(logoutToken)
    def sid = (claims?.sid ?: claims?.sub) as String
    if (sid == null || sid.trim().isEmpty()) {
        throw new IllegalArgumentException('logout_token missing sid/sub claim')
    }

    def redisHost = System.getenv('REDIS_HOST') ?: 'redis-a'
    def redisPort = 6379
    def key = "blacklist:${sid}"
    def command = "*5\r\n" +
        "\$3\r\nSET\r\n" +
        "\$${key.getBytes('UTF-8').length}\r\n${key}\r\n" +
        "\$1\r\n1\r\n" +
        "\$2\r\nEX\r\n" +
        "\$4\r\n3600\r\n"

    Socket socket = null
    try {
        socket = new Socket(redisHost, redisPort)
        socket.soTimeout = 2000
        def output = socket.getOutputStream()
        def input = socket.getInputStream()

        output.write(command.getBytes('UTF-8'))
        output.flush()

        int marker = input.read()
        if (marker != ('+' as char)) {
            throw new IOException('Redis SET failed: unexpected response type')
        }
        def statusText = readLine(input)
        if (!'OK'.equals(statusText)) {
            throw new IOException("Redis SET failed: ${statusText}")
        }
    } finally {
        if (socket != null) {
            socket.close()
        }
    }

    response = new Response(Status.OK)
    response.entity.setString('OK')
} catch (Exception e) {
    logger.error('BackchannelLogoutHandler failed', e)
    response = new Response(Status.BAD_REQUEST)
    response.entity.setString('invalid logout request')
}

return newResultPromise(response)
