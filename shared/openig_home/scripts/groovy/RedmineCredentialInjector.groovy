import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status

import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

def logPrefix = '[RedmineCredentialInjector] '
def CANONICAL_ORIGIN = System.getenv('CANONICAL_ORIGIN_APP3') ?: 'http://redmine-b.sso.local:9080'
def CANONICAL_HOST = 'redmine-b.sso.local'
def REDMINE_LOGIN_URL = 'http://shared-redmine:3000/login'

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
        cookies.add([name: cookieName, value: cookieValue, raw: cookieName + '=' + cookieValue])
    }

    return cookies
}

def mergeCookieHeader = { String existingCookieHeader, Collection cookiePairs ->
    def mergedByName = new LinkedHashMap<String, String>()

    for (def cookie : splitCookieHeader(existingCookieHeader)) {
        mergedByName[cookie.name] = cookie.raw
    }

    if (cookiePairs != null) {
        for (def cookiePair : cookiePairs) {
            def trimmed = cookiePair?.toString()?.trim()
            if (trimmed == null || trimmed.isEmpty()) {
                continue
            }
            def eqIndex = trimmed.indexOf('=')
            if (eqIndex <= 0) {
                continue
            }
            def cookieName = trimmed.substring(0, eqIndex).trim()
            if (cookieName.isEmpty()) {
                continue
            }
            mergedByName[cookieName] = trimmed
        }
    }

    return mergedByName.values().join('; ')
}

def rewriteSetCookieHeader = { String setCookieHeader ->
    if (setCookieHeader == null || setCookieHeader.trim().isEmpty()) {
        return setCookieHeader
    }

    def segments = setCookieHeader.split(';')
    def rewritten = []
    boolean hasDomain = false
    boolean hasSameSite = false

    for (int i = 0; i < segments.length; i++) {
        def segment = segments[i]?.trim()
        if (segment == null || segment.isEmpty()) {
            continue
        }

        if (i == 0) {
            rewritten.add(segment)
            continue
        }

        def lower = segment.toLowerCase()
        if (lower.startsWith('domain=')) {
            rewritten.add('Domain=' + CANONICAL_HOST)
            hasDomain = true
        } else if (lower.startsWith('samesite=')) {
            rewritten.add('SameSite=Lax')
            hasSameSite = true
        } else {
            rewritten.add(segment)
        }
    }

    if (!hasDomain) {
        rewritten.add('Domain=' + CANONICAL_HOST)
    }
    if (!hasSameSite) {
        rewritten.add('SameSite=Lax')
    }

    return rewritten.join('; ')
}

def expireCookieHeader = { String cookieName ->
    return cookieName + '=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Domain=' + CANONICAL_HOST + '; SameSite=Lax'
}

def readResponseBody = { HttpURLConnection connection ->
    InputStream stream = null
    try {
        if (connection.responseCode >= 200 && connection.responseCode < 400) {
            stream = connection.inputStream
        } else {
            stream = connection.errorStream
        }
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

def extractSetCookieHeaders = { HttpURLConnection connection ->
    def headers = []
    def headerFields = connection.headerFields
    if (headerFields == null) {
        return headers
    }

    for (def headerEntry : headerFields.entrySet()) {
        def headerName = headerEntry.key
        if (headerName == null || !headerName.equalsIgnoreCase('Set-Cookie')) {
            continue
        }

        def headerValues = headerEntry.value
        if (headerValues == null) {
            continue
        }

        for (def headerValue : headerValues) {
            def cookieHeader = headerValue?.toString()?.trim()
            if (cookieHeader != null && !cookieHeader.isEmpty()) {
                headers.add(cookieHeader)
            }
        }
    }

    return headers
}

def extractCookiePair = { String setCookieHeader ->
    if (setCookieHeader == null || setCookieHeader.trim().isEmpty()) {
        return null
    }

    int semi = setCookieHeader.indexOf(';')
    def pair = semi >= 0 ? setCookieHeader.substring(0, semi) : setCookieHeader
    pair = pair?.trim()
    return pair != null && !pair.isEmpty() ? pair : null
}

try {
    def redmineCredentials = attributes.redmine_credentials
    String login = redmineCredentials != null ? (redmineCredentials['login'] as String) : null
    String password = redmineCredentials != null ? (redmineCredentials['password'] as String) : null

    if (login == null || login.trim().isEmpty() || password == null || password.trim().isEmpty()) {
        logger.error(logPrefix + 'Missing attributes.redmine_credentials login/password')
        def response = new Response(Status.INTERNAL_SERVER_ERROR)
        response.entity.setString('<html><body><h2>Missing Redmine credentials in request context.</h2></body></html>')
        response.headers.put('Content-Type', ['text/html'])
        return response
    }

    def oidcInfo = attributes.openid
    def currentUserSub = null
    if (oidcInfo != null) {
        def userInfo = oidcInfo['user_info']
        if (userInfo != null) {
            currentUserSub = userInfo['sub'] as String
        }
    }

    if (currentUserSub == null || currentUserSub.trim().isEmpty()) {
        logger.error(logPrefix + 'Missing OIDC user_info.sub for ' + login)
        def response = new Response(Status.INTERNAL_SERVER_ERROR)
        response.entity.setString('<html><body><h2>SSO session is missing the user identity. Please retry.</h2></body></html>')
        response.headers.put('Content-Type', ['text/html'])
        return response
    }

    if (session['redmine_session_cookies'] != null) {
        session.remove('redmine_session_cookies')
        logger.info(logPrefix + 'Removed legacy redmine_session_cookies from JwtSession')
    }

    def sessionRedmineUserSub = session['redmine_user_sub']
    if (sessionRedmineUserSub != null) {
        sessionRedmineUserSub = sessionRedmineUserSub as String
    }
    def sessionRedmineCookieNames = session['redmine_cookie_names']
    if (sessionRedmineCookieNames != null) {
        sessionRedmineCookieNames = sessionRedmineCookieNames as String
    }

    def knownRedmineCookieNames = []
    if (sessionRedmineCookieNames != null && !sessionRedmineCookieNames.trim().isEmpty()) {
        knownRedmineCookieNames = sessionRedmineCookieNames
            .split(',')
            .collect { it.trim() }
            .findAll { it != null && !it.isEmpty() }
    }

    def isRedmineCookieName = { String cookieName ->
        if (cookieName == null) {
            return false
        }
        if (cookieName == '_redmine_session') {
            return true
        }
        return knownRedmineCookieNames.contains(cookieName)
    }

    def stripRedmineCookies = { String cookieHeader ->
        def remaining = splitCookieHeader(cookieHeader)
            .findAll { cookie -> !isRedmineCookieName(cookie.name) }
            .collect { cookie -> cookie.raw }
        return remaining.join('; ')
    }

    def incomingCookieHeader = request.headers.getFirst('Cookie')
    def browserCookies = splitCookieHeader(incomingCookieHeader)
    def browserRedmineCookies = browserCookies
        .findAll { cookie -> isRedmineCookieName(cookie.name) }
        .collect { cookie -> cookie.raw }
    def browserRedmineCookieNames = browserCookies
        .findAll { cookie -> isRedmineCookieName(cookie.name) }
        .collect { cookie -> cookie.name }
    boolean sameUser = (sessionRedmineUserSub != null && sessionRedmineUserSub == currentUserSub)

    def redmineCookiesForUpstream = []
    def pendingBrowserSetCookies = []

    if (!browserRedmineCookies.isEmpty() && sameUser) {
        redmineCookiesForUpstream.addAll(browserRedmineCookies)
        logger.debug(logPrefix + 'Reusing browser Redmine cookies for ' + login)
    } else {
        if (!browserRedmineCookies.isEmpty() && !sameUser) {
            logger.info(logPrefix + 'Browser Redmine cookies belong to a different user; refreshing session for ' + login)
        } else {
            logger.info(logPrefix + 'No reusable browser Redmine cookies for ' + login + ' - logging in')
        }

        HttpURLConnection getConnection = (HttpURLConnection) new URL(REDMINE_LOGIN_URL).openConnection()
        getConnection.requestMethod = 'GET'
        getConnection.instanceFollowRedirects = false
        getConnection.connectTimeout = 5000
        getConnection.readTimeout = 5000

        int getStatus = getConnection.responseCode
        String loginHtml = readResponseBody(getConnection)
        def initCookiePairs = []
        for (def cookieHeader : extractSetCookieHeaders(getConnection)) {
            def cookiePair = extractCookiePair(cookieHeader)
            if (cookiePair != null) {
                initCookiePairs.add(cookiePair)
            }
        }
        getConnection.disconnect()

        if (getStatus != 200) {
            logger.error(logPrefix + 'GET /login failed with HTTP ' + getStatus)
            def response = new Response(Status.BAD_GATEWAY)
            response.entity.setString('<html><body><h2>Unable to initialize Redmine login flow.</h2></body></html>')
            response.headers.put('Content-Type', ['text/html'])
            return response
        }

        // Extract from the form hidden input (different from meta csrf-token in Redmine)
        String tokenMarker = 'name="authenticity_token" value="'
        int markerIndex = loginHtml.indexOf(tokenMarker)
        String authenticityToken = null
        if (markerIndex >= 0) {
            int tokenStart = markerIndex + tokenMarker.length()
            int tokenEnd = loginHtml.indexOf('"', tokenStart)
            if (tokenEnd > tokenStart) {
                authenticityToken = loginHtml.substring(tokenStart, tokenEnd)
            }
        }

        if (authenticityToken == null || authenticityToken.isEmpty()) {
            logger.error(logPrefix + 'CSRF token not found in Redmine login page')
            def response = new Response(Status.BAD_GATEWAY)
            response.entity.setString('<html><body><h2>Unable to parse Redmine CSRF token.</h2></body></html>')
            response.headers.put('Content-Type', ['text/html'])
            return response
        }

        String initCookie = initCookiePairs.join('; ')
        if (initCookie == null || initCookie.isEmpty()) {
            logger.error(logPrefix + 'GET /login did not provide initial cookies')
            def response = new Response(Status.BAD_GATEWAY)
            response.entity.setString('<html><body><h2>Unable to initialize Redmine session cookies.</h2></body></html>')
            response.headers.put('Content-Type', ['text/html'])
            return response
        }

        HttpURLConnection postConnection = (HttpURLConnection) new URL(REDMINE_LOGIN_URL).openConnection()
        postConnection.requestMethod = 'POST'
        postConnection.doOutput = true
        postConnection.instanceFollowRedirects = false
        postConnection.connectTimeout = 5000
        postConnection.readTimeout = 5000
        postConnection.setRequestProperty('Content-Type', 'application/x-www-form-urlencoded')
        postConnection.setRequestProperty('Cookie', initCookie)

        String formBody = 'utf8=%E2%9C%93'
        formBody = formBody + '&authenticity_token=' + URLEncoder.encode(authenticityToken, 'UTF-8')
        formBody = formBody + '&username=' + URLEncoder.encode(login, 'UTF-8')
        formBody = formBody + '&password=' + URLEncoder.encode(password, 'UTF-8')
        formBody = formBody + '&login=Login'

        OutputStream output = postConnection.outputStream
        output.write(formBody.getBytes('UTF-8'))
        output.flush()
        output.close()

        int postStatus = postConnection.responseCode
        String postBody = readResponseBody(postConnection)
        def postSetCookieHeaders = extractSetCookieHeaders(postConnection)
        postConnection.disconnect()

        if (postStatus != 302) {
            logger.error(logPrefix + 'POST /login failed with HTTP ' + postStatus)
            logger.error(logPrefix + 'POST /login response body: ' + postBody)
            def response = new Response(Status.BAD_GATEWAY)
            response.entity.setString('<html><body><h2>Unable to authenticate to Redmine.</h2></body></html>')
            response.headers.put('Content-Type', ['text/html'])
            return response
        }

        def cookiePairs = []
        def cookieNamesForSession = []
        for (def cookieHeader : postSetCookieHeaders) {
            def cookiePair = extractCookiePair(cookieHeader)
            if (cookiePair == null) {
                continue
            }
            cookiePairs.add(cookiePair)
            int eqIndex = cookiePair.indexOf('=')
            if (eqIndex > 0) {
                cookieNamesForSession.add(cookiePair.substring(0, eqIndex).trim())
            }
            pendingBrowserSetCookies.add(rewriteSetCookieHeader(cookieHeader))
        }

        if (cookiePairs.isEmpty() || !cookiePairs.any { it.startsWith('_redmine_session=') }) {
            logger.error(logPrefix + 'POST /login did not return _redmine_session cookie')
            def response = new Response(Status.BAD_GATEWAY)
            response.entity.setString('<html><body><h2>Redmine session cookie was not issued.</h2></body></html>')
            response.headers.put('Content-Type', ['text/html'])
            return response
        }

        redmineCookiesForUpstream.addAll(cookiePairs)
        session['redmine_user_sub'] = currentUserSub
        session['redmine_cookie_names'] = cookieNamesForSession.unique().join(',')
        logger.info(logPrefix + 'Redmine login OK for ' + login + ', issued ' + cookiePairs.size() + ' browser cookies')
    }

    if (!redmineCookiesForUpstream.isEmpty()) {
        def baseCookieHeader = stripRedmineCookies(request.headers.getFirst('Cookie'))
        def combined = mergeCookieHeader(baseCookieHeader, redmineCookiesForUpstream)
        if (combined != null && !combined.isEmpty()) {
            request.headers.put('Cookie', [combined])
        } else {
            request.headers.remove('Cookie')
        }
        logger.debug(logPrefix + 'Injected Redmine cookies for ' + login)
    } else {
        def filteredCookieHeader = stripRedmineCookies(request.headers.getFirst('Cookie'))
        if (filteredCookieHeader != null && !filteredCookieHeader.isEmpty()) {
            request.headers.put('Cookie', [filteredCookieHeader])
        } else {
            request.headers.remove('Cookie')
        }
        logger.debug(logPrefix + 'No Redmine cookies to inject for ' + login)
    }

    return next.handle(context, request).then({ response ->
        int statusCode = response.status.code
        String location = response.headers.getFirst('Location')

        if ((statusCode == 301 || statusCode == 302) && location != null && location.contains('/login')) {
            logger.warn(logPrefix + 'Redmine redirected to /login - browser Redmine session expired, clearing browser cookies')
            def retryPath = request.uri.path ?: '/'
            def retryQuery = request.uri.query ? '?' + request.uri.query : ''
            def retryResponse = new Response(response.status)
            retryResponse.headers['Location'] = CANONICAL_ORIGIN + retryPath + retryQuery

            def cookieNamesToClear = []
            if (sessionRedmineCookieNames != null && !sessionRedmineCookieNames.trim().isEmpty()) {
                cookieNamesToClear = sessionRedmineCookieNames
                    .split(',')
                    .collect { it.trim() }
                    .findAll { it != null && !it.isEmpty() }
            } else if (session['redmine_cookie_names'] != null) {
                cookieNamesToClear = (session['redmine_cookie_names'] as String)
                    .split(',')
                    .collect { it.trim() }
                    .findAll { it != null && !it.isEmpty() }
            } else if (!browserRedmineCookieNames.isEmpty()) {
                cookieNamesToClear = browserRedmineCookieNames
            } else {
                cookieNamesToClear = ['_redmine_session']
            }

            for (def cookieName : cookieNamesToClear.unique()) {
                retryResponse.headers.add('Set-Cookie', expireCookieHeader(cookieName))
            }
            return retryResponse
        }

        for (def setCookieHeader : pendingBrowserSetCookies) {
            response.headers.add('Set-Cookie', setCookieHeader)
        }
        return response
    })
} catch (Exception e) {
    logger.error(logPrefix + 'Failed', e)
    def response = new Response(Status.BAD_GATEWAY)
    response.entity.setString('<html><body><h2>Redmine SSO credential injection failed.</h2></body></html>')
    response.headers.put('Content-Type', ['text/html'])
    return response
}
