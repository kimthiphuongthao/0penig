import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import java.net.Socket
import java.net.URLEncoder
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

def publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openigb.sso.local:9080'
def idToken = session['oauth2:' + publicUrl + '/openid/app4']?.get('atr')?.get('id_token')

if (idToken) {
    try {
        String[] tokenParts = (idToken as String).split('\\.')
        if (tokenParts.length < 2) {
            throw new IllegalArgumentException('id_token is not a JWT')
        }

        String payloadJson = new String(Base64.getUrlDecoder().decode(tokenParts[1]), 'UTF-8')
        def payload = new JsonSlurper().parseText(payloadJson)
        String sid = payload?.sid as String
        if (!sid?.trim()) {
            throw new IllegalArgumentException('sid missing in id_token')
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
        logger.info('[SloHandlerRedmine] SID added to Redis blacklist')
    } catch (Exception e) {
        logger.error('[SloHandlerRedmine] Failed to blacklist sid from id_token', e)
    }
}

session.clear()

def keycloakBrowserUrl = System.getenv('KEYCLOAK_BROWSER_URL')
def clientId = System.getenv('OIDC_CLIENT_ID')
def postLogoutUri = publicUrl + '/app4/'

def logoutUrl = keycloakBrowserUrl + '/realms/sso-realm/protocol/openid-connect/logout?client_id=' + clientId + '&post_logout_redirect_uri=' + URLEncoder.encode(postLogoutUri, 'UTF-8')
if (idToken) {
    logoutUrl = logoutUrl + '&id_token_hint=' + URLEncoder.encode(idToken as String, 'UTF-8')
    logger.info('[SloHandlerRedmine] Redirecting with id_token_hint')
} else {
    logger.warn('[SloHandlerRedmine] No id_token found in session')
}

def response = new Response(Status.FOUND)
response.headers['Location'] = '' + logoutUrl
return newResultPromise(response)
