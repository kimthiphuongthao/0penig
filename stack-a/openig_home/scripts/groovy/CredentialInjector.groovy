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
//  2. Check if WP session cookies cached in OpenIG JwtSession
//  3. If not: POST to wp-login.php, capture session cookies
//  4. Store cookies in JwtSession (shared across HA nodes via cookie)
//  5. Inject WP cookies into outgoing request to WordPress
// ============================================================

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

// --- Step 2: Check if WP session cookies already in JwtSession ---
def wpSessionCookies = session['wp_session_cookies']
if (wpSessionCookies != null) {
    wpSessionCookies = wpSessionCookies as String
}

if (wpSessionCookies == null || wpSessionCookies.isEmpty()) {
    logger.info("[CredentialInjector] No WP session for '" + wpUsername + "' — logging into WordPress")

    // --- Step 3: POST to wp-login.php using HttpURLConnection ---
    // Using HttpURLConnection directly (not ClientHandler) to:
    //  a) Avoid DNS resolution issues in some OpenIG versions
    //  b) Control redirect behavior (we want 302, not to follow it)
    try {
        def loginUrl = new URL("http://wordpress/wp-login.php")
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
                    def nameValue = cookieHeader.split(';')[0].trim()
                    if (nameValue && !nameValue.isEmpty()) {
                        cookiePairs.add(nameValue)
                    }
                }
                if (!cookiePairs.isEmpty()) {
                    wpSessionCookies = cookiePairs.join('; ')
                    // --- Step 4: Store in JwtSession ---
                    session['wp_session_cookies'] = wpSessionCookies
                    logger.info("[CredentialInjector] WP login OK for '" + wpUsername + "', cached " + cookiePairs.size() + " cookies")
                } else {
                    logger.warn("[CredentialInjector] WP login 302 but empty Set-Cookie headers")
                }
            } else {
                logger.warn("[CredentialInjector] WP login 302 but no Set-Cookie headers returned")
            }
        } else {
            logger.error("[CredentialInjector] WP login failed — HTTP " + statusCode + " for user '" + wpUsername + "'")
        }

        conn.disconnect()

    } catch (Exception e) {
        logger.error("[CredentialInjector] Exception during WP login for '" + wpUsername + "'", e)
        session.remove('wp_session_cookies')
        def errResp = new Response(Status.BAD_GATEWAY)
        errResp.entity.setString("<html><body><h2>SSO login failed. Please retry or contact support.</h2></body></html>")
        errResp.headers.put('Content-Type', ['text/html'])
        return errResp
    }
}

// --- Step 5: Inject WP session cookies into the outgoing request ---
if (wpSessionCookies != null && !wpSessionCookies.isEmpty()) {
    def existingCookies = request.headers.getFirst('Cookie')
    def combined = (existingCookies != null && !existingCookies.isEmpty())
        ? existingCookies + '; ' + wpSessionCookies
        : wpSessionCookies
    request.headers.put('Cookie', [combined])
    logger.debug("[CredentialInjector] Injected WP cookies for '" + wpUsername + "'")
} else {
    def existingCookies = request.headers.getFirst('Cookie')
    if (existingCookies != null && !existingCookies.isEmpty()) {
        def filteredCookies = existingCookies
            .split(';')
            .collect { it.trim() }
            .findAll { cookiePart ->
                if (cookiePart == null || cookiePart.isEmpty()) {
                    return false
                }
                def eqIndex = cookiePart.indexOf('=')
                def cookieName = (eqIndex >= 0) ? cookiePart.substring(0, eqIndex).trim() : cookiePart
                return !(cookieName.startsWith('wordpress_') ||
                         cookieName.startsWith('wordpress_logged_in_') ||
                         cookieName.startsWith('wp-settings-') ||
                         cookieName.startsWith('wp_woocommerce_'))
            }

        if (!filteredCookies.isEmpty()) {
            request.headers.put('Cookie', [filteredCookies.join('; ')])
        } else {
            request.headers.remove('Cookie')
        }
        logger.debug("[CredentialInjector] No fresh WP cookies; stripped WP cookies from request for '" + wpUsername + "'")
    }
}

// --- Step 6: Send request, detect WP session expiry ---
return next.handle(context, request).then({ response ->
    def location = response.headers.getFirst('Location')
    def status = response.status.code
    if ((status == 301 || status == 302) && location != null && location.contains('wp-login.php') && !location.contains('action=logout')) {
        logger.warn("[CredentialInjector] WP redirected to wp-login.php — cached session expired, clearing cache")
        session.remove('wp_session_cookies')
        // Redirect browser back to same URL so next request gets fresh WP login
        def retryResp = new Response(response.status)
        retryResp.headers['Location'] = request.uri.toString()
        return retryResp
    }
    return response
})
