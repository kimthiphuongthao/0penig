import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder

String configuredClientEndpoint = binding.hasVariable('clientEndpoint') ? (clientEndpoint as String) : '/openid/app1'
String configuredClientId = binding.hasVariable('clientId') ? (clientId as String) : 'openig-client'
String configuredCanonicalOrigin = binding.hasVariable('canonicalOrigin') ? (canonicalOrigin as String) : 'http://wp-a.sso.local'
String configuredPostLogoutPath = binding.hasVariable('postLogoutPath') ? (postLogoutPath as String) : '/'
String configuredTokenRefKey = binding.hasVariable('tokenRefKey') ? (tokenRefKey as String)?.trim() : 'token_ref_id'
String configuredRedisHost = binding.hasVariable('redisHost') ? (redisHost as String)?.trim() : null
String configuredRedisUser = binding.hasVariable('redisUser') ? (redisUser as String)?.trim() : null
String configuredRedisPasswordEnvVar = binding.hasVariable('redisPasswordEnvVar') ? (redisPasswordEnvVar as String)?.trim() : 'REDIS_PASSWORD'
String configuredRedisKeyPrefix = binding.hasVariable('redisKeyPrefix') ? (redisKeyPrefix as String)?.trim() : ''
String configuredRedisPassword = System.getenv('REDIS_PASSWORD') ?: ''
if (configuredRedisPasswordEnvVar && configuredRedisPasswordEnvVar != 'REDIS_PASSWORD') {
    String overridePassword = System.getenv(configuredRedisPasswordEnvVar)
    if (overridePassword) { configuredRedisPassword = overridePassword }
}

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

def publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openiga.sso.local'
def hostHeader = request.headers.getFirst('Host') as String
def hostWithoutPort = hostHeader?.split(':')?.getAt(0)
def defaultPort = publicUrl.contains(':9080') ? '9080' : publicUrl.contains(':18080') ? '18080' : '80'
def hostWithPort = hostHeader?.contains(':') ? hostHeader : (hostWithoutPort ? hostWithoutPort + ':' + defaultPort : null)

def oauth2Keys = []
if (hostWithPort) {
    oauth2Keys.add('oauth2:http://' + hostWithPort + configuredClientEndpoint)
}
if (hostWithoutPort) {
    oauth2Keys.add('oauth2:http://' + hostWithoutPort + configuredClientEndpoint)
}
oauth2Keys.add('oauth2:' + publicUrl + configuredClientEndpoint)

String idToken = null
for (def key : oauth2Keys.unique()) {
    idToken = session[key]?.get('atr')?.get('id_token') as String
    if (idToken?.trim()) {
        break
    }
}

// BUG-SSO2-AFTER-SLO fix: delete all token Redis entries to prevent zombie-restore
if (configuredRedisHost) {
    try {
        session.keySet()
            .collect { String.valueOf(it) }
            .findAll { it.startsWith('token_ref_id') || it == configuredTokenRefKey }
            .each { tokenRefKeyName ->
                String tokenRefIdValue = session[tokenRefKeyName] as String
                if (tokenRefIdValue?.trim()) {
                    String keyToDelete = (configuredRedisKeyPrefix ? configuredRedisKeyPrefix + ':' : '') + 'token_ref:' + tokenRefIdValue
                    int kSize = keyToDelete.getBytes('UTF-8').length
                    String delCmd = '*2\r\n$3\r\nDEL\r\n$' + kSize + '\r\n' + keyToDelete + '\r\n'
                    try {
                        withRedisSocket { socket ->
                            socket.outputStream.write(delCmd.getBytes('UTF-8'))
                            socket.outputStream.flush()
                            readRespLine(socket.inputStream)
                        }
                    } catch (Exception redisEx) {
                        logger.warn('[SloHandler] Redis DEL failed for key={} tokenRefId={}', tokenRefKeyName, tokenRefIdValue, redisEx)
                    }
                }
            }
    } catch (Exception e) {
        logger.warn('[SloHandler] Failed to enumerate session for Redis cleanup endpoint={}', configuredClientEndpoint, e)
    }
}

session.clear()

try {
    def keycloakBrowserUrl = System.getenv('KEYCLOAK_BROWSER_URL') ?: 'http://auth.sso.local:8080'
    def postLogoutUri = configuredCanonicalOrigin + configuredPostLogoutPath
    def logoutUrl = keycloakBrowserUrl + '/realms/sso-realm/protocol/openid-connect/logout?client_id=' + configuredClientId + '&post_logout_redirect_uri=' + URLEncoder.encode(postLogoutUri, 'UTF-8')
    if (idToken?.trim()) {
        logoutUrl += '&id_token_hint=' + URLEncoder.encode(idToken, 'UTF-8')
        logger.info('[SloHandler] Redirecting with id_token_hint=PRESENT, clientId={}, postLogoutUri={}', configuredClientId, postLogoutUri)
    } else {
        logger.warn('[SloHandler] No id_token found in session, proceeding without id_token_hint')
    }
    def response = new Response(Status.FOUND)
    response.headers.put('Location', [logoutUrl as String])
    return newResultPromise(response)
} catch (Exception e) {
    logger.error('[SloHandler] Logout failed', e)
    def errResp = new Response(Status.INTERNAL_SERVER_ERROR)
    errResp.headers.put('Content-Type', ['text/html'])
    errResp.entity.setString('<html><body><h2>Logout failed. Please close your browser.</h2></body></html>')
    return newResultPromise(errResp)
}
