import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import java.net.URLEncoder

def readResponseBody = { HttpURLConnection connection ->
    def stream = null
    try {
        stream = connection.inputStream
    } catch (Exception ignored) {
        stream = connection.errorStream
    }

    if (stream == null) {
        return ''
    }

    try {
        return stream.getText('UTF-8')
    } finally {
        stream.close()
    }
}

def buildAuthorization = { String deviceId, String accessToken ->
    "MediaBrowser Client='OpenIG', Device='SSO-Gateway', DeviceId='${deviceId}', Version='10.0.0', Token='${accessToken}'"
}

def publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openigb.sso.local:9080'
def hostHeader = request.headers.getFirst('Host') as String
def hostWithoutPort = hostHeader?.split(':')?.getAt(0)
def hostWithPort = hostHeader?.contains(':') ? hostHeader : (hostWithoutPort ? hostWithoutPort + ':9080' : null)

try {
    String jellyfinToken = session['jellyfin_token'] as String
    String jellyfinDeviceId = session['jellyfin_device_id'] as String

    if (jellyfinToken?.trim()) {
        if (!jellyfinDeviceId?.trim()) {
            int sessionHash = Math.abs((session?.hashCode() ?: System.currentTimeMillis().hashCode()) as int)
            jellyfinDeviceId = 'openig-' + sessionHash
        }

        HttpURLConnection logoutConnection = (HttpURLConnection) new URL('http://jellyfin:8096/Sessions/Logout').openConnection()
        logoutConnection.requestMethod = 'POST'
        logoutConnection.doOutput = true
        logoutConnection.setRequestProperty('Authorization', buildAuthorization(jellyfinDeviceId, jellyfinToken) as String)
        logoutConnection.setRequestProperty('Accept', 'application/json')
        logoutConnection.connectTimeout = 5000
        logoutConnection.readTimeout = 5000

        int logoutStatus = logoutConnection.responseCode
        readResponseBody(logoutConnection)
        logoutConnection.disconnect()
        if (logoutStatus < 200 || logoutStatus >= 300) {
            logger.warn('[SloHandlerJellyfin] Jellyfin /Sessions/Logout returned HTTP ' + logoutStatus)
        }
    }

    def oauth2Keys = []
    if (hostWithPort) {
        oauth2Keys.add('oauth2:http://' + hostWithPort + '/openid/app3')
    }
    if (hostWithoutPort) {
        oauth2Keys.add('oauth2:http://' + hostWithoutPort + '/openid/app3')
    }
    oauth2Keys.add('oauth2:' + publicUrl + '/openid/app3')

    String idToken = null
    for (def oauth2Key : oauth2Keys.unique()) {
        idToken = session[oauth2Key]?.get('atr')?.get('id_token') as String
        if (idToken?.trim()) {
            break
        }
    }

    session.clear()

    String keycloakBrowserUrl = System.getenv('KEYCLOAK_BROWSER_URL')
    String clientId = System.getenv('OIDC_CLIENT_ID')
    if (!keycloakBrowserUrl?.trim() || !clientId?.trim()) {
        throw new IllegalStateException('KEYCLOAK_BROWSER_URL or OIDC_CLIENT_ID is missing')
    }

    if (!hostWithoutPort) {
        hostWithoutPort = 'jellyfin-b.sso.local'
    }
    String fallbackHostWithPort = hostWithPort ?: hostWithoutPort
    String postLogoutUri = 'http://' + fallbackHostWithPort + '/'

    String logoutUrl = keycloakBrowserUrl + '/realms/sso-realm/protocol/openid-connect/logout?client_id=' + clientId + '&post_logout_redirect_uri=' + URLEncoder.encode(postLogoutUri, 'UTF-8')
    if (idToken?.trim()) {
        logoutUrl = logoutUrl + '&id_token_hint=' + URLEncoder.encode(idToken as String, 'UTF-8')
        logger.info('[SloHandlerJellyfin] Redirecting with id_token_hint')
    } else {
        logger.warn('[SloHandlerJellyfin] No id_token found in session')
    }

    def response = new Response(Status.FOUND)
    response.headers.put('Location', [logoutUrl as String])
    return newResultPromise(response)
} catch (Exception e) {
    logger.error('[SloHandlerJellyfin] Failed', e)
    def response = new Response(Status.INTERNAL_SERVER_ERROR)
    response.headers.put('Content-Type', ['text/html'])
    response.entity.setString('<html><body><h2>Unable to complete logout. Please try again later.</h2></body></html>')
    return newResultPromise(response)
}
