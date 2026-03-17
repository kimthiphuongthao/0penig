import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import java.net.URLEncoder

String clientEndpoint = binding.hasVariable('clientEndpoint') ? (clientEndpoint as String) : '/openid/app1'
String clientId = binding.hasVariable('clientId') ? (clientId as String) : 'openig-client'
String canonicalOrigin = binding.hasVariable('canonicalOrigin') ? (canonicalOrigin as String) : 'http://wp-a.sso.local'
String postLogoutPath = binding.hasVariable('postLogoutPath') ? (postLogoutPath as String) : '/'

def publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openiga.sso.local'
def hostHeader = request.headers.getFirst('Host') as String
def hostWithoutPort = hostHeader?.split(':')?.getAt(0)
def defaultPort = publicUrl.contains(':9080') ? '9080' : publicUrl.contains(':18080') ? '18080' : '80'
def hostWithPort = hostHeader?.contains(':') ? hostHeader : (hostWithoutPort ? hostWithoutPort + ':' + defaultPort : null)

def oauth2Keys = []
if (hostWithPort) {
    oauth2Keys.add('oauth2:http://' + hostWithPort + clientEndpoint)
}
if (hostWithoutPort) {
    oauth2Keys.add('oauth2:http://' + hostWithoutPort + clientEndpoint)
}
oauth2Keys.add('oauth2:' + publicUrl + clientEndpoint)

String idToken = null
for (def key : oauth2Keys.unique()) {
    idToken = session[key]?.get('atr')?.get('id_token') as String
    if (idToken?.trim()) {
        break
    }
}

session.clear()

try {
    def keycloakBrowserUrl = System.getenv('KEYCLOAK_BROWSER_URL') ?: 'http://auth.sso.local:8080'
    def postLogoutUri = canonicalOrigin + postLogoutPath
    def logoutUrl = keycloakBrowserUrl + '/realms/sso-realm/protocol/openid-connect/logout?client_id=' + clientId + '&post_logout_redirect_uri=' + URLEncoder.encode(postLogoutUri, 'UTF-8')
    if (idToken?.trim()) {
        logoutUrl += '&id_token_hint=' + URLEncoder.encode(idToken, 'UTF-8')
        logger.info('[SloHandler] Redirecting with id_token_hint=PRESENT, clientId={}, postLogoutUri={}', clientId, postLogoutUri)
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
