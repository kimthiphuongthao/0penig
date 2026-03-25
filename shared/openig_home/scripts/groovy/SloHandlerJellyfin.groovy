import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import java.net.InetSocketAddress
import java.net.Socket
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

String configuredClientEndpoint = binding.hasVariable('clientEndpoint') ? (clientEndpoint as String) : '/openid/app4'
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

def resolveTokenRefRedisKeyPrefix = { String tokenRefKeyName ->
    if (!tokenRefKeyName) {
        return configuredRedisKeyPrefix
    }
    if (tokenRefKeyName == configuredTokenRefKey || tokenRefKeyName == 'token_ref_id') {
        return configuredRedisKeyPrefix
    }
    String keyPrefixMarker = 'token_ref_id_'
    if (tokenRefKeyName.startsWith(keyPrefixMarker)) {
        return tokenRefKeyName.substring(keyPrefixMarker.length())
    }
    configuredRedisKeyPrefix
}

def CANONICAL_ORIGIN = System.getenv('CANONICAL_ORIGIN_APP4') ?: 'http://jellyfin-b.sso.local:9080'

def publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openigb.sso.local:9080'
def hostHeader = request.headers.getFirst('Host') as String
def hostWithoutPort = hostHeader?.split(':')?.getAt(0)
def hostWithPort = hostHeader?.contains(':') ? hostHeader : (hostWithoutPort ? hostWithoutPort + ':9080' : null)

try {
    def oauth2Keys = []
    if (hostWithPort) {
        oauth2Keys.add('oauth2:http://' + hostWithPort + configuredClientEndpoint)
    }
    if (hostWithoutPort) {
        oauth2Keys.add('oauth2:http://' + hostWithoutPort + configuredClientEndpoint)
    }
    oauth2Keys.add('oauth2:' + publicUrl + configuredClientEndpoint)

    String idToken = null
    String userSub = session['jellyfin_user_sub'] as String
    for (def oauth2Key : oauth2Keys.unique()) {
        def oauth2Entry = session[oauth2Key]
        if (!idToken?.trim()) {
            idToken = oauth2Entry?.get('atr')?.get('id_token') as String
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
            HttpURLConnection logoutConnection = (HttpURLConnection) new URL('http://shared-jellyfin:8096/Sessions/Logout').openConnection()
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

    // BUG-SSO2-AFTER-SLO fix: delete all token Redis entries to prevent zombie-restore
    if (configuredRedisHost) {
        try {
            session.keySet()
                .collect { String.valueOf(it) }
                .findAll { it.startsWith('token_ref_id') || it == configuredTokenRefKey }
                .each { tokenRefKeyName ->
                    String tokenRefIdValue = session[tokenRefKeyName] as String
                    if (tokenRefIdValue?.trim()) {
                        String redisKeyPrefixForTokenRef = resolveTokenRefRedisKeyPrefix(tokenRefKeyName)
                        String keyToDelete = (redisKeyPrefixForTokenRef ? redisKeyPrefixForTokenRef + ':' : '') + 'token_ref:' + tokenRefIdValue
                        int kSize = keyToDelete.getBytes('UTF-8').length
                        String delCmd = '*2\r\n$3\r\nDEL\r\n$' + kSize + '\r\n' + keyToDelete + '\r\n'
                        try {
                            withRedisSocket { socket ->
                                socket.outputStream.write(delCmd.getBytes('UTF-8'))
                                socket.outputStream.flush()
                                readRespLine(socket.inputStream)
                            }
                        } catch (Exception redisEx) {
                            logger.warn('[SloHandlerJellyfin] Redis DEL failed for key={} tokenRefId={}', tokenRefKeyName, tokenRefIdValue, redisEx)
                        }
                    }
                }
        } catch (Exception e) {
            logger.warn('[SloHandlerJellyfin] Failed to enumerate session for Redis cleanup endpoint={}', configuredClientEndpoint, e)
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
