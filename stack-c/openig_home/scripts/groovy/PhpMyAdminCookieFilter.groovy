// NOT WIRED — phpMyAdmin CSRF token mismatch makes this filter unusable (FIX-10 WONT_FIX).
// Do NOT add to any route chain. User switch handled by cacheHeader:false + attributes (FIX-09).

import groovy.json.JsonSlurper

import java.util.Base64

String normalizeCookieName(String cookieName) {
    cookieName?.trim()?.toLowerCase(Locale.ROOT)
}

Map parseCookiePair(String rawCookie) {
    String cookieText = rawCookie?.trim()
    if (!cookieText) {
        return null
    }

    int separatorIndex = cookieText.indexOf('=')
    if (separatorIndex < 0) {
        return null
    }

    String cookieName = cookieText.substring(0, separatorIndex).trim()
    if (!cookieName) {
        return null
    }

    [
        raw  : cookieText,
        name : cookieName,
        value: cookieText.substring(separatorIndex + 1).trim()
    ]
}

List<String> headerValues(def headers, String headerName) {
    def values = headers?.get(headerName)
    if (values == null) {
        return []
    }
    if (values instanceof Collection) {
        return values.collect { it as String }.findAll { it != null }
    }
    return [values as String]
}

String requestCookieValue(def headers, String cookieName) {
    String normalizedTargetName = normalizeCookieName(cookieName)

    for (String cookieHeader : headerValues(headers, 'Cookie')) {
        for (String rawCookiePart : (cookieHeader?.split(';') ?: [])) {
            Map cookie = parseCookiePair(rawCookiePart)
            if (cookie != null && normalizeCookieName(cookie.name as String) == normalizedTargetName) {
                return cookie.value as String
            }
        }
    }

    return null
}

String responseSetCookieValue(def headers, String cookieName) {
    String normalizedTargetName = normalizeCookieName(cookieName)

    for (String setCookieHeader : headerValues(headers, 'Set-Cookie')) {
        String cookiePair = setCookieHeader?.split(';', 2)?.getAt(0)
        Map cookie = parseCookiePair(cookiePair as String)
        if (cookie != null && normalizeCookieName(cookie.name as String) == normalizedTargetName) {
            return cookie.value as String
        }
    }

    return null
}

void prependSetCookieHeader(def headers, String setCookieHeader) {
    List<String> setCookieHeaders = headerValues(headers, 'Set-Cookie')
    headers.put('Set-Cookie', [setCookieHeader] + setCookieHeaders)
}

def decodeJwtClaims = { String jwt ->
    String[] parts = jwt.split('\\.')
    if (parts.length < 2) return null
    String payload = new String(Base64.getUrlDecoder().decode(parts[1]), 'UTF-8')
    new JsonSlurper().parseText(payload)
}

String hostHeader = request.headers.getFirst('Host') as String
String hostWithPort = hostHeader
String hostWithoutPort = hostHeader?.split(':')?.getAt(0)
String openigPublicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openig-c.sso.local:10080'

List<String> oauth2SessionKeys = []
if (hostWithPort) oauth2SessionKeys.add("oauth2:http://${hostWithPort}/openid/app6")
if (hostWithoutPort) oauth2SessionKeys.add("oauth2:http://${hostWithoutPort}/openid/app6")
oauth2SessionKeys.add("oauth2:${openigPublicUrl}/openid/app6")

String idToken = null
for (String key : oauth2SessionKeys) {
    idToken = session[key]?.get('atr')?.get('id_token') as String
    if (idToken?.trim()) break
}

def idTokenClaims = idToken?.trim() ? decodeJwtClaims(idToken) : null
String currentSsoUser = (idTokenClaims?.preferred_username ?: idTokenClaims?.email ?: idTokenClaims?.sub)?.toString()?.trim()
String storedCookieOwner = session['pma_cookie_owner']?.toString()?.trim()
String storedCookieValue = session['pma_cookie_value']?.toString()
String browserCookieValue = requestCookieValue(request.headers, 'phpMyAdmin')

boolean shouldExpireBrowserCookie = currentSsoUser &&
        browserCookieValue != null &&
        (storedCookieOwner != currentSsoUser || storedCookieValue != browserCookieValue)

return next.handle(context, request).thenOnResult { response ->
    String capturedPhpMyAdminCookieValue = responseSetCookieValue(response.headers, 'phpMyAdmin')

    if (shouldExpireBrowserCookie) {
        prependSetCookieHeader(response.headers, 'phpMyAdmin=; Max-Age=0; Path=/')
    }

    if (currentSsoUser && capturedPhpMyAdminCookieValue != null && capturedPhpMyAdminCookieValue != '') {
        session['pma_cookie_owner'] = currentSsoUser
        session['pma_cookie_value'] = capturedPhpMyAdminCookieValue
    }
}
