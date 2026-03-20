import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

import java.net.URLEncoder

String clientEndpoint = binding.hasVariable('clientEndpoint') ? (clientEndpoint as String) : '/openid/app6'
String clientId = binding.hasVariable('clientId') ? (clientId as String) : 'openig-client-c-app6'
String canonicalOrigin = binding.hasVariable('canonicalOrigin') ? (canonicalOrigin as String) : 'http://phpmyadmin-c.sso.local:18080'
String postLogoutPath = binding.hasVariable('postLogoutPath') ? (postLogoutPath as String) : '/'
String redirectUrl = binding.hasVariable('redirectUrl') ? (redirectUrl as String) : canonicalOrigin + '/'
String logoutPathNeedle = binding.hasVariable('logoutPathNeedle') ? (logoutPathNeedle as String)?.toLowerCase() : null
String logoutQueryNeedle = binding.hasVariable('logoutQueryNeedle') ? (logoutQueryNeedle as String)?.toLowerCase() : null
List<String> configuredSessionKeysToClear = binding.hasVariable('sessionKeysToClear') ? (sessionKeysToClear as List)?.collect { String.valueOf(it) } : []

String requestPath = request.uri.path ?: ''
String requestQuery = request.uri.query ?: ''
String requestPathLower = requestPath.toLowerCase()
String requestQueryLower = requestQuery.toLowerCase()
boolean logoutRequest =
    (logoutPathNeedle && requestPathLower.contains(logoutPathNeedle)) ||
    (logoutQueryNeedle && requestQueryLower.contains(logoutQueryNeedle))

def removeAppSessionKeys = {
    Set<String> sessionKeysToClear = new LinkedHashSet<>(configuredSessionKeysToClear)
    try {
        session.keySet()
            .collect { String.valueOf(it) }
            .findAll { it.startsWith('oauth2:') && it.endsWith(clientEndpoint) }
            .each { sessionKeysToClear.add(it) }
    } catch (Exception e) {
        logger.warn('[PhpMyAdminAuthFailureHandler] Failed to enumerate app6 oauth2 session keys before clear', e)
    }

    sessionKeysToClear.each { key ->
        session.remove(key)
    }
}

if (!logoutRequest) {
    removeAppSessionKeys()
    Response response = new Response(Status.FOUND)
    response.headers.put('Location', [redirectUrl as String])
    logger.info('[PhpMyAdminAuthFailureHandler] Non-logout 401 redirected to {} after clearing keys={}', redirectUrl, configuredSessionKeysToClear)
    return newResultPromise(response)
}

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
        logger.info('[PhpMyAdminAuthFailureHandler] Logout 401 redirected with id_token_hint=PRESENT clientId={} postLogoutUri={}', clientId, postLogoutUri)
    } else {
        logger.warn('[PhpMyAdminAuthFailureHandler] Logout 401 had no id_token, proceeding without id_token_hint')
    }

    def response = new Response(Status.FOUND)
    response.headers.put('Location', [logoutUrl as String])
    return newResultPromise(response)
} catch (Exception e) {
    logger.error('[PhpMyAdminAuthFailureHandler] Logout redirect failed', e)
    def errResp = new Response(Status.INTERNAL_SERVER_ERROR)
    errResp.headers.put('Content-Type', ['text/html'])
    errResp.entity.setString('<html><body><h2>Logout failed. Please close your browser.</h2></body></html>')
    return newResultPromise(errResp)
}
