import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64

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

def buildDeviceId = { String sub ->
    byte[] digest = MessageDigest.getInstance('SHA-256').digest(("jellyfin-${sub}").getBytes('UTF-8'))
    return digest.encodeHex().toString().substring(0, 32)
}

def extractSubFromIdToken = { String jwt ->
    if (!jwt?.trim()) {
        return null
    }
    String[] tokenParts = jwt.split('\\.')
    if (tokenParts.length < 2) {
        return null
    }
    String payloadJson = new String(Base64.getUrlDecoder().decode(tokenParts[1]), 'UTF-8')
    def payload = new JsonSlurper().parseText(payloadJson)
    payload?.sub as String
}

def CANONICAL_ORIGIN = System.getenv('CANONICAL_ORIGIN_APP4') ?: 'http://jellyfin-b.sso.local:9080'

def publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openigb.sso.local:9080'
def hostHeader = request.headers.getFirst('Host') as String
def hostWithoutPort = hostHeader?.split(':')?.getAt(0)
def hostWithPort = hostHeader?.contains(':') ? hostHeader : (hostWithoutPort ? hostWithoutPort + ':9080' : null)

try {
    def oauth2Keys = []
    if (hostWithPort) {
        oauth2Keys.add('oauth2:http://' + hostWithPort + '/openid/app4')
    }
    if (hostWithoutPort) {
        oauth2Keys.add('oauth2:http://' + hostWithoutPort + '/openid/app4')
    }
    oauth2Keys.add('oauth2:' + publicUrl + '/openid/app4')

    String idToken = null
    String userSub = session['jellyfin_user_sub'] as String
    for (def oauth2Key : oauth2Keys.unique()) {
        def oauth2Entry = session[oauth2Key]
        if (!idToken?.trim()) {
            idToken = oauth2Entry?.get('atr')?.get('id_token') as String
        }
        if (!userSub?.trim()) {
            userSub = oauth2Entry?.get('user_info')?.get('sub') as String
        }
        if (idToken?.trim() && userSub?.trim()) {
            break
        }
    }
    if (!userSub?.trim() && idToken?.trim()) {
        userSub = extractSubFromIdToken(idToken)
    }
    userSub = userSub?.trim()

    String jellyfinToken = session['jellyfin_token'] as String
    String jellyfinDeviceId = session['jellyfin_device_id'] as String

    if (jellyfinToken?.trim()) {
        if (!jellyfinDeviceId?.trim() && userSub) {
            jellyfinDeviceId = buildDeviceId(userSub)
        }

        if (jellyfinDeviceId?.trim()) {
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
        } else {
            logger.warn('[SloHandlerJellyfin] Missing jellyfin_device_id and no user sub available to derive a stable fallback; skipping Jellyfin /Sessions/Logout')
        }
    }

    session.clear()

    String keycloakBrowserUrl = System.getenv('KEYCLOAK_BROWSER_URL')
    String clientId = System.getenv('OIDC_CLIENT_ID_APP4')
    if (!keycloakBrowserUrl?.trim() || !clientId?.trim()) {
        throw new IllegalStateException('KEYCLOAK_BROWSER_URL or OIDC_CLIENT_ID_APP4 is missing')
    }

    String postLogoutUri = CANONICAL_ORIGIN + '/web/index.html'
    String logoutUrl = keycloakBrowserUrl + '/realms/sso-realm/protocol/openid-connect/logout?client_id=' + clientId + '&post_logout_redirect_uri=' + URLEncoder.encode(postLogoutUri, 'UTF-8')
    if (idToken?.trim()) {
        logoutUrl += '&id_token_hint=' + URLEncoder.encode(idToken as String, 'UTF-8')
        logger.info('[SloHandlerJellyfin] Redirecting with id_token_hint and post_logout_redirect_uri=' + postLogoutUri)
    } else {
        logger.warn('[SloHandlerJellyfin] No id_token_hint available; proceeding with Keycloak logout without id_token_hint')
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
