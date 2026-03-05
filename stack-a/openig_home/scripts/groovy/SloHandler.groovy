import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import groovy.json.JsonSlurper
import java.net.Socket
import java.net.URLEncoder
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
        throw new IllegalArgumentException('id_token is not a valid JWT')
    }
    def payload = parts[1]
    def normalized = payload.replace('-', '+').replace('_', '/')
    while (normalized.length() % 4 != 0) {
        normalized += '='
    }
    def json = new String(Base64.decoder.decode(normalized), 'UTF-8')
    new JsonSlurper().parseText(json)
}

def addSidToRedisBlacklist = { String sid ->
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
}

def publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openiga.sso.local:80'
def idToken = session['oauth2:' + publicUrl + '/openid/app1']?.get('atr')?.get('id_token')

if (idToken) {
    try {
        def claims = decodeJwtClaims(idToken as String)
        def sid = claims?.sid as String
        if (sid != null && !sid.trim().isEmpty()) {
            addSidToRedisBlacklist(sid)
        } else {
            logger.warn('SloHandler: id_token missing sid claim, skipping Redis blacklist write')
        }
    } catch (Exception e) {
        logger.error('SloHandler: failed to add sid to Redis blacklist', e)
    }
}

session.clear()

def baseLogoutUrl = 'http://auth.sso.local:8080/realms/sso-realm/protocol/openid-connect/logout'
def params = 'client_id=openig-client&post_logout_redirect_uri=' + URLEncoder.encode('http://openiga.sso.local/app1/', 'UTF-8')
if (idToken) {
    params += '&id_token_hint=' + URLEncoder.encode(idToken as String, 'UTF-8')
}
def logoutUrl = baseLogoutUrl + '?' + params
logger.warn('SloHandler: logout URL = ' + logoutUrl)

def response = new Response(Status.FOUND)
response.headers['Location'] = logoutUrl
return newResultPromise(response)
