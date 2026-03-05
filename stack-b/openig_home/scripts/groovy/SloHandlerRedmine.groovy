import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import java.net.URLEncoder

def publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openigb.sso.local:9080'
def hostHeader = request.headers.getFirst('Host') as String
def hostWithoutPort = hostHeader?.split(':')?.getAt(0)
def hostWithPort = hostHeader?.contains(':') ? hostHeader : (hostWithoutPort ? hostWithoutPort + ':9080' : null)

def oauth2Keys = []
if (hostWithPort) {
    oauth2Keys.add('oauth2:http://' + hostWithPort + '/openid/app4')
}
if (hostWithoutPort) {
    oauth2Keys.add('oauth2:http://' + hostWithoutPort + '/openid/app4')
}
oauth2Keys.add('oauth2:' + publicUrl + '/openid/app4')

def idToken = null
for (def key : oauth2Keys.unique()) {
    idToken = session[key]?.get('atr')?.get('id_token')
    if (idToken) {
        break
    }
}

session.clear()

def keycloakBrowserUrl = System.getenv('KEYCLOAK_BROWSER_URL')
def clientId = System.getenv('OIDC_CLIENT_ID')
if (!hostWithoutPort) {
    hostWithoutPort = 'openigb.sso.local'
}
def postLogoutUri = hostWithoutPort == 'openigb.sso.local' ? (publicUrl + '/app4/') : ('http://' + hostWithPort + '/')

def logoutUrl = keycloakBrowserUrl + '/realms/sso-realm/protocol/openid-connect/logout?client_id=' + clientId + '&post_logout_redirect_uri=' + URLEncoder.encode(postLogoutUri, 'UTF-8')
if (idToken) {
    logoutUrl = logoutUrl + '&id_token_hint=' + URLEncoder.encode(idToken as String, 'UTF-8')
    logger.info('[SloHandlerRedmine] Redirecting with id_token_hint')
} else {
    logger.warn('[SloHandlerRedmine] No id_token found in session')
}

def response = new Response(Status.FOUND)
response.headers.put('Location', [logoutUrl as String])
return newResultPromise(response)
