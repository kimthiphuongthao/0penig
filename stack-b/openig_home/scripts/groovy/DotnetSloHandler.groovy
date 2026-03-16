// DEAD CODE — dotnet routes removed (00-dotnet-logout.json deleted).
// This file is NOT wired into any active route. Kept for reference only.

import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import java.net.URLEncoder

def idToken = session['oauth2:/openid/app3']?.get('atr')?.get('id_token')
session.clear()

def keycloakBrowserUrl = System.getenv('KEYCLOAK_BROWSER_URL')
def clientId           = System.getenv('OIDC_CLIENT_ID')
def appPublicBaseUrl   = System.getenv('APP_PUBLIC_BASE_URL')

def baseLogoutUrl = keycloakBrowserUrl + '/realms/sso-realm/protocol/openid-connect/logout'
def params = 'client_id=' + clientId + '&post_logout_redirect_uri=' + URLEncoder.encode(appPublicBaseUrl + '/', 'UTF-8')
if (idToken) {
    params += '&id_token_hint=' + URLEncoder.encode(idToken as String, 'UTF-8')
    logger.info('DotnetSloHandler: redirecting with id_token_hint')
} else {
    logger.warn('DotnetSloHandler: no id_token found in session')
}
def logoutUrl = baseLogoutUrl + '?' + params

def response = new Response(Status.FOUND)
response.headers['Location'] = logoutUrl
return newResultPromise(response)
