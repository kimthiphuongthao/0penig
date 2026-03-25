import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise
import java.security.MessageDigest

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

def buildDeviceId = { String sub ->
    byte[] digest = MessageDigest.getInstance('SHA-256').digest(("jellyfin-${sub}").getBytes('UTF-8'))
    return digest.encodeHex().toString().substring(0, 32)
}

def splitCookieHeader = { String cookieHeader ->
    def cookies = []
    if (cookieHeader == null || cookieHeader.trim().isEmpty()) {
        return cookies
    }

    for (def cookiePart : cookieHeader.split(';')) {
        def trimmed = cookiePart?.trim()
        if (trimmed == null || trimmed.isEmpty()) {
            continue
        }
        def eqIndex = trimmed.indexOf('=')
        if (eqIndex <= 0) {
            continue
        }
        def cookieName = trimmed.substring(0, eqIndex).trim()
        def cookieValue = trimmed.substring(eqIndex + 1).trim()
        if (cookieName.isEmpty()) {
            continue
        }
        cookies.add([name: cookieName, raw: cookieName + '=' + cookieValue])
    }

    return cookies
}

try {
    request.headers.remove('Authorization')
    request.headers.remove('X-Emby-Authorization')
    request.headers.remove('X-MediaBrowser-Token')

    def jellyfinCredentials = attributes.jellyfin_credentials
    String username = jellyfinCredentials != null ? (jellyfinCredentials['username'] as String) : null
    String password = jellyfinCredentials != null ? (jellyfinCredentials['password'] as String) : null

    if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
        logger.error('[JellyfinTokenInjector] Missing attributes.jellyfin_credentials username/password')
        def response = new Response(Status.INTERNAL_SERVER_ERROR)
        response.entity.setString('<html><body><h2>Missing Jellyfin credentials in request context.</h2></body></html>')
        response.headers.put('Content-Type', ['text/html'])
        return response
    }

    String accessToken = session['jellyfin_token'] as String
    String userId = session['jellyfin_user_id'] as String
    String deviceId = session['jellyfin_device_id'] as String
    String requestUserSub = attributes.openid?.get('user_info')?.get('sub') as String
    if (requestUserSub?.trim()) {
        requestUserSub = requestUserSub.trim()
        session['jellyfin_user_sub'] = requestUserSub
    }

    def requireUserSub = {
        String sub = requestUserSub ?: (session['jellyfin_user_sub'] as String)
        if (!sub?.trim()) {
            throw new IllegalStateException('[JellyfinTokenInjector] No sub claim in OIDC user_info; cannot derive stable device ID')
        }
        sub = sub.trim()
        session['jellyfin_user_sub'] = sub
        sub
    }

    if (!accessToken?.trim() || !userId?.trim()) {
        String acceptHeader = request.headers.getFirst('Accept') as String
        boolean isHtmlRequest = acceptHeader?.contains('text/html')
        if (!isHtmlRequest) {
            request.headers.remove('Authorization')
            request.headers.remove('X-Emby-Authorization')
            request.headers.remove('X-MediaBrowser-Token')
            return next.handle(context, request)
        }

        deviceId = buildDeviceId(requireUserSub())

        HttpURLConnection authConnection = (HttpURLConnection) new URL('http://shared-jellyfin:8096/Users/AuthenticateByName').openConnection()
        authConnection.requestMethod = 'POST'
        authConnection.doOutput = true
        authConnection.setRequestProperty('Content-Type', 'application/json')
        authConnection.setRequestProperty('Accept', 'application/json')
        authConnection.setRequestProperty('X-Emby-Authorization', ("MediaBrowser Client='OpenIG', Device='SSO-Gateway', DeviceId='${deviceId}', Version='10.0.0'") as String)
        authConnection.connectTimeout = 5000
        authConnection.readTimeout = 5000

        String authPayload = JsonOutput.toJson([Username: username, Pw: password])
        OutputStreamWriter authWriter = new OutputStreamWriter(authConnection.outputStream, 'UTF-8')
        authWriter.write(authPayload)
        authWriter.flush()
        authWriter.close()

        int authStatus = authConnection.responseCode
        String authBody = readResponseBody(authConnection)
        authConnection.disconnect()

        if (authStatus == 401) {
            session.remove('jellyfin_token')
            session.remove('jellyfin_user_id')
            session.remove('jellyfin_device_id')
            session.remove('jellyfin_user_sub')
            Response redirectResponse = new Response(Status.FOUND)
            redirectResponse.headers.put('Location', ['/' as String])
            return newResultPromise(redirectResponse)
        }
        if (authStatus < 200 || authStatus >= 300) {
            throw new IllegalStateException("Jellyfin AuthenticateByName failed with HTTP ${authStatus}")
        }

        def authJson = new JsonSlurper().parseText(authBody ?: '{}')
        accessToken = authJson?.AccessToken as String
        userId = authJson?.User?.Id as String
        if (accessToken == null || accessToken.trim().isEmpty() || userId == null || userId.trim().isEmpty()) {
            throw new IllegalStateException('Jellyfin auth response is missing AccessToken or User.Id')
        }

        session['jellyfin_token'] = accessToken
        session['jellyfin_user_id'] = userId
        session['jellyfin_device_id'] = deviceId
    }

    if (deviceId == null || deviceId.trim().isEmpty()) {
        deviceId = buildDeviceId(requireUserSub())
        session['jellyfin_device_id'] = deviceId
    }

    String authorizationHeader = 'MediaBrowser Client="OpenIG", Device="SSO-Gateway", DeviceId="' + (session['jellyfin_device_id'] as String) + '", Version="10.0.0", Token="' + (session['jellyfin_token'] as String) + '"'
    request.headers.put('Authorization', [authorizationHeader as String])

    return next.handle(context, request).then({ response ->
        if (response.status.code == 401) {
            session.remove('jellyfin_token')
            session.remove('jellyfin_user_id')
            session.remove('jellyfin_device_id')
            session.remove('jellyfin_user_sub')
            Response redirectResponse = new Response(Status.FOUND)
            redirectResponse.headers.put('Location', ['/' as String])
            return redirectResponse
        }
        return response
    })
} catch (Exception e) {
    logger.error('[JellyfinTokenInjector] Failed to obtain/inject Jellyfin token', e)
    def response = new Response(Status.INTERNAL_SERVER_ERROR)
    response.entity.setString('<html><body><h2>Unable to create Jellyfin session token. Please try again later.</h2></body></html>')
    response.headers.put('Content-Type', ['text/html'])
    return response
}
