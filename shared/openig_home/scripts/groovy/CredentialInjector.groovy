import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status

// ============================================================
// CredentialInjector.groovy
//
// Runs AFTER OAuth2ClientFilter (OIDC session verified)
// and AFTER FileAttributesFilter (WP credentials loaded).
//
// Flow:
//  1. Read WP credentials from attributes.wp_credentials
//  2. Reuse browser-held WP cookies when they match the current OIDC sub
//  3. Otherwise: POST to wp-login.php, capture fresh session cookies
//  4. Store only small markers in JwtSession (sub + cookie names)
//  5. Inject WP cookies into the outgoing request to WordPress
// ============================================================

def CANONICAL_ORIGIN = System.getenv('CANONICAL_ORIGIN_APP1') ?: 'http://wp-a.sso.local'
def CANONICAL_HOST = 'wp-a.sso.local'

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

def isWpCookieName = { String cookieName ->
    return cookieName != null && (
        cookieName.startsWith('wordpress_') ||
        cookieName.startsWith('wordpress_logged_in_') ||
        cookieName.startsWith('wp-settings-') ||
        cookieName.startsWith('wp_woocommerce_')
    )
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

def stripWpCookies = { String cookieHeader ->
    def remaining = splitCookieHeader(cookieHeader)
        .findAll { cookie -> !isWpCookieName(cookie.name) }
        .collect { cookie -> cookie.raw }
    return remaining.join('; ')
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

// --- Step 1: Get WP credentials from FileAttributesFilter result ---
def wpCreds = attributes.wp_credentials
if (wpCreds == null) {
    // Use explicit check instead of ?[] to avoid Groovy ternary ambiguity
    def oidcInfo = attributes.openid
    def username = 'unknown'
    if (oidcInfo != null) {
        def userInfo = oidcInfo['user_info']
        if (userInfo != null) {
            username = userInfo['preferred_username'] ?: 'unknown'
        }
    }
    logger.error("[CredentialInjector] No WP credentials found for Keycloak user: " + username)
    def response = new Response(Status.INTERNAL_SERVER_ERROR)
    response.entity.setString("<html><body><h2>No mapped credentials for your account. Contact admin.</h2></body></html>")
    response.headers.add("Content-Type", "text/html")
    return response
}

def wpUsername = wpCreds['wp_username'] as String
def wpPassword = wpCreds['wp_password'] as String

def oidcInfo = attributes.openid
def currentUserSub = null
if (oidcInfo != null) {
    def userInfo = oidcInfo['user_info']
    if (userInfo != null) {
        currentUserSub = userInfo['sub'] as String
    }
}

if (currentUserSub == null || currentUserSub.trim().isEmpty()) {
    logger.error("[CredentialInjector] Missing OIDC user_info.sub for '" + wpUsername + "'")
    def response = new Response(Status.INTERNAL_SERVER_ERROR)
    response.entity.setString("<html><body><h2>SSO session is missing the user identity. Please retry.</h2></body></html>")
    response.headers.add("Content-Type", "text/html")
    return response
}

// --- Step 2: Remove legacy large session field and inspect browser cookies ---
if (session['wp_session_cookies'] != null) {
    session.remove('wp_session_cookies')
    logger.info("[CredentialInjector] Removed legacy wp_session_cookies from JwtSession")
}

def sessionWpUserSub = session['wp_user_sub']
if (sessionWpUserSub != null) {
    sessionWpUserSub = sessionWpUserSub as String
}
def sessionWpCookieNames = session['wp_cookie_names']
if (sessionWpCookieNames != null) {
    sessionWpCookieNames = sessionWpCookieNames as String
}

def incomingCookieHeader = request.headers.getFirst('Cookie')
def browserCookies = splitCookieHeader(incomingCookieHeader)
def browserWpCookies = browserCookies
    .findAll { cookie -> isWpCookieName(cookie.name) }
    .collect { cookie -> cookie.raw }
def browserHasLoggedInCookie = browserCookies.any { cookie -> cookie.name.startsWith('wordpress_logged_in_') }
boolean sameUser = (sessionWpUserSub != null && sessionWpUserSub == currentUserSub)

def wpCookiesForUpstream = []
def pendingBrowserSetCookies = []
def cookieNamesForSession = []

if (browserHasLoggedInCookie && sameUser) {
    wpCookiesForUpstream.addAll(browserWpCookies)
    logger.debug("[CredentialInjector] Reusing browser WP cookies for '" + wpUsername + "'")
} else {
    if (browserHasLoggedInCookie && !sameUser) {
        logger.info("[CredentialInjector] Browser WP cookies belong to a different user; refreshing session for '" + wpUsername + "'")
    } else {
        logger.info("[CredentialInjector] No reusable browser WP cookies for '" + wpUsername + "' — logging into WordPress")
    }

    // --- Step 3: POST to wp-login.php using HttpURLConnection ---
    // Using HttpURLConnection directly (not ClientHandler) to:
    //  a) Avoid DNS resolution issues in some OpenIG versions
    //  b) Control redirect behavior (we want 302, not to follow it)
    try {
        def loginUrl = new URL("http://shared-wordpress/wp-login.php")
        def conn = (HttpURLConnection) loginUrl.openConnection()
        conn.requestMethod = 'POST'
        conn.doOutput = true
        conn.instanceFollowRedirects = false
        conn.setRequestProperty('Content-Type', 'application/x-www-form-urlencoded')
        conn.setRequestProperty('Accept', 'text/html,application/xhtml+xml')
        // WP requires this test cookie to be set before login POST
        conn.setRequestProperty('Cookie', 'wordpress_test_cookie=WP+Cookie+check')
        conn.connectTimeout = 5000
        conn.readTimeout    = 5000

        def encodedUser = URLEncoder.encode(wpUsername, 'UTF-8')
        def encodedPass = URLEncoder.encode(wpPassword, 'UTF-8')
        def formBody = "log=" + encodedUser +
                       "&pwd=" + encodedPass +
                       "&wp-submit=Log+In" +
                       "&redirect_to=%2F" +
                       "&testcookie=1"

        def writer = new OutputStreamWriter(conn.outputStream, 'UTF-8')
        writer.write(formBody)
        writer.flush()
        writer.close()

        def statusCode = conn.responseCode
        logger.debug("[CredentialInjector] WP login HTTP status: " + statusCode)

        // WordPress returns 302 on successful login with Set-Cookie headers
        if (statusCode == 302 || statusCode == 301) {
            def setCookieHeaders = conn.headerFields.get('Set-Cookie')
            if (setCookieHeaders != null && !setCookieHeaders.isEmpty()) {
                def cookiePairs = []
                for (def cookieHeader : setCookieHeaders) {
                    if (cookieHeader == null || cookieHeader.trim().isEmpty()) {
                        continue
                    }
                    def nameValue = cookieHeader.split(';', 2)[0].trim()
                    if (nameValue && !nameValue.isEmpty()) {
                        cookiePairs.add(nameValue)
                        def eqIndex = nameValue.indexOf('=')
                        if (eqIndex > 0) {
                            cookieNamesForSession.add(nameValue.substring(0, eqIndex).trim())
                        }
                        pendingBrowserSetCookies.add(rewriteSetCookieHeader(cookieHeader))
                    }
                }
                if (!cookiePairs.isEmpty()) {
                    wpCookiesForUpstream.addAll(cookiePairs)
                    session['wp_user_sub'] = currentUserSub
                    session['wp_cookie_names'] = cookieNamesForSession.unique().join(',')
                    logger.info("[CredentialInjector] WP login OK for '" + wpUsername + "', issued " + cookiePairs.size() + " browser cookies")
                } else {
                    logger.error("[CredentialInjector] WP login 302 but empty Set-Cookie headers — fail-closed")
                    conn.disconnect()
                    def failResp = new Response(Status.BAD_GATEWAY)
                    failResp.headers.put('Content-Type', ['text/html'])
                    failResp.entity.setString("<html><body><h2>WordPress login succeeded but returned no session cookies. Please retry.</h2></body></html>")
                    return failResp
                }
            } else {
                logger.error("[CredentialInjector] WP login 302 but no Set-Cookie headers — fail-closed")
                conn.disconnect()
                def failResp = new Response(Status.BAD_GATEWAY)
                failResp.headers.put('Content-Type', ['text/html'])
                failResp.entity.setString("<html><body><h2>WordPress login succeeded but returned no session cookies. Please retry.</h2></body></html>")
                return failResp
            }
        } else {
            // FIX-15: fail-closed — do not proxy unauthenticated if WP login fails
            logger.error("[CredentialInjector] WP login failed — HTTP " + statusCode + " for user '" + wpUsername + "'")
            conn.disconnect()
            def failResp = new Response(Status.BAD_GATEWAY)
            failResp.entity.setString("<html><body><h2>WordPress login failed (HTTP " + statusCode + "). Please retry or contact support.</h2></body></html>")
            failResp.headers.put('Content-Type', ['text/html'])
            return failResp
        }

        conn.disconnect()

    } catch (Exception e) {
        logger.error("[CredentialInjector] Exception during WP login for '" + wpUsername + "'", e)
        def errResp = new Response(Status.BAD_GATEWAY)
        errResp.entity.setString("<html><body><h2>SSO login failed. Please retry or contact support.</h2></body></html>")
        errResp.headers.put('Content-Type', ['text/html'])
        return errResp
    }
}

// --- Step 5: Inject WP session cookies into the outgoing request ---
if (!wpCookiesForUpstream.isEmpty()) {
    def baseCookieHeader = stripWpCookies(request.headers.getFirst('Cookie'))
    def combined = mergeCookieHeader(baseCookieHeader, wpCookiesForUpstream)
    if (combined != null && !combined.isEmpty()) {
        request.headers.put('Cookie', [combined])
    } else {
        request.headers.remove('Cookie')
    }
    logger.debug("[CredentialInjector] Injected WP cookies for '" + wpUsername + "'")
} else {
    def filteredCookieHeader = stripWpCookies(request.headers.getFirst('Cookie'))
    if (filteredCookieHeader != null && !filteredCookieHeader.isEmpty()) {
        request.headers.put('Cookie', [filteredCookieHeader])
    } else {
        request.headers.remove('Cookie')
    }
    logger.debug("[CredentialInjector] No WP cookies to inject for '" + wpUsername + "'")
}

// --- Step 6: Send request, detect WP session expiry ---
return next.handle(context, request).then({ response ->
    def location = response.headers.getFirst('Location')
    def status = response.status.code
    if ((status == 301 || status == 302) && location != null && location.contains('wp-login.php') && !location.contains('action=logout')) {
        logger.warn("[CredentialInjector] WP redirected to wp-login.php — browser WP session expired, clearing browser cookies")
        def retryPath = request.uri.path ?: '/'
        def retryQuery = request.uri.query ? '?' + request.uri.query : ''
        def retryResp = new Response(response.status)
        retryResp.headers['Location'] = CANONICAL_ORIGIN + retryPath + retryQuery
        def cookieNamesToClear = []
        if (sessionWpCookieNames != null && !sessionWpCookieNames.trim().isEmpty()) {
            cookieNamesToClear = sessionWpCookieNames
                .split(',')
                .collect { it.trim() }
                .findAll { it != null && !it.isEmpty() }
        } else if (session['wp_cookie_names'] != null) {
            cookieNamesToClear = (session['wp_cookie_names'] as String)
                .split(',')
                .collect { it.trim() }
                .findAll { it != null && !it.isEmpty() }
        }
        for (def cookieName : cookieNamesToClear.unique()) {
            retryResp.headers.add('Set-Cookie', expireCookieHeader(cookieName))
        }
        return retryResp
    }

    for (def setCookieHeader : pendingBrowserSetCookies) {
        response.headers.add('Set-Cookie', setCookieHeader)
    }
    return response
})
