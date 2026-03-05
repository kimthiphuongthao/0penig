import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import java.net.URLEncoder

def publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openigb.sso.local:9080'
def idToken = session['oauth2:' + publicUrl + '/openid/app4']?.get('atr')?.get('id_token')

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
