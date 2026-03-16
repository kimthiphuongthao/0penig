import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import java.net.URLEncoder

String hostHeader = request.headers.getFirst('Host') as String
String hostWithoutPort = hostHeader?.split(':')?.getAt(0)
String hostWithPort = hostHeader?.contains(':') ? hostHeader : (hostWithoutPort ? "${hostWithoutPort}:18080" : null)
String openigPublicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openig-c.sso.local:10080'

List<String> oauth2SessionKeys = []
if (hostWithPort) {
    oauth2SessionKeys.add("oauth2:http://${hostWithPort}/openid/app5")
}
if (hostWithoutPort) {
    oauth2SessionKeys.add("oauth2:http://${hostWithoutPort}/openid/app5")
}
oauth2SessionKeys.add("oauth2:${openigPublicUrl}/openid/app5")

String idToken = null
for (String key : oauth2SessionKeys.unique()) {
    idToken = session[key]?.get('atr')?.get('id_token') as String
    if (idToken?.trim()) {
        break
    }
}

session.clear()

String keycloakBrowserUrl = System.getenv('KEYCLOAK_BROWSER_URL') ?: 'http://auth.sso.local:8080'
if (!System.getenv('KEYCLOAK_BROWSER_URL')) {
    logger.warn('[SloHandlerGrafana] KEYCLOAK_BROWSER_URL is not set, falling back to http://auth.sso.local:8080')
}
logger.info("[SloHandlerGrafana] Handling ${request.method} ${request.uri.path}")
String logoutUrl = keycloakBrowserUrl +
    '/realms/sso-realm/protocol/openid-connect/logout?client_id=openig-client-c-app5' +
    '&post_logout_redirect_uri=' + URLEncoder.encode((System.getenv('CANONICAL_ORIGIN_APP5') ?: 'http://grafana-c.sso.local:18080') + '/', 'UTF-8')
if (idToken?.trim()) {
    logoutUrl += '&id_token_hint=' + URLEncoder.encode(idToken, 'UTF-8')
} else {
    logger.warn('[SloHandlerGrafana] No id_token found in session during logout')
}

Response response = new Response(Status.FOUND)
response.headers.put('Location', [logoutUrl])
return newResultPromise(response)
