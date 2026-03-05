import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

import java.net.Socket
import java.net.URLDecoder
import java.util.Base64

def readRespLine = { InputStream input ->
    ByteArrayOutputStream buffer = new ByteArrayOutputStream()
    int previous = -1
    while (true) {
        int current = input.read()
        if (current == -1) {
            break
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

try {
    String body = request.entity?.getString() ?: ''
    String logoutTokenParam = body.split('&').find { it.startsWith('logout_token=') }
    if (!logoutTokenParam) {
        throw new IllegalArgumentException('logout_token is missing')
    }

    String encodedLogoutToken = logoutTokenParam.substring('logout_token='.length())
    String logoutToken = URLDecoder.decode(encodedLogoutToken, 'UTF-8')

    String[] tokenParts = logoutToken.split('\\.')
    if (tokenParts.length < 2) {
        throw new IllegalArgumentException('logout_token is not a JWT')
    }

    String payloadJson = new String(Base64.getUrlDecoder().decode(tokenParts[1]), 'UTF-8')
    def payload = new JsonSlurper().parseText(payloadJson)
    String sid = (payload?.sid ?: payload?.sub) as String
    if (!sid?.trim()) {
        throw new IllegalArgumentException('sid/sub missing in logout_token')
    }

    String redisHost = System.getenv('REDIS_HOST') ?: 'redis-b'
    int redisPort = 6379
    String key = "blacklist:${sid}"
    int keySize = key.getBytes('UTF-8').length
    String command = "*5\r\n\$3\r\nSET\r\n\$${keySize}\r\n${key}\r\n\$1\r\n1\r\n\$2\r\nEX\r\n\$4\r\n3600\r\n"

    new Socket(redisHost, redisPort).withCloseable { socket ->
        socket.outputStream.write(command.getBytes('UTF-8'))
        socket.outputStream.flush()
        String reply = readRespLine(socket.inputStream)
        if (!reply?.startsWith('+OK')) {
            throw new IllegalStateException("Redis SET failed: ${reply}")
        }
    }

    return newResultPromise(new Response(Status.OK))
} catch (Exception e) {
    logger.error('[BackchannelLogoutHandler] Failed', e)
    return newResultPromise(new Response(Status.BAD_REQUEST))
}
