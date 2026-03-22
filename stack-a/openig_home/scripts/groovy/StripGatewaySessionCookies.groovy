def cookieNamesToStrip = ['IG_SSO', 'IG_SSO_B', 'IG_SSO_C'] as Set
def cookieHeader = request.headers.getFirst('Cookie')

if (cookieHeader != null && !cookieHeader.trim().isEmpty()) {
    def stripped = cookieHeader
        .split(';')
        .collect { it?.trim() }
        .findAll { cookie ->
            if (cookie == null || cookie.isEmpty()) {
                return false
            }

            def eqIndex = cookie.indexOf('=')
            if (eqIndex <= 0) {
                return true
            }

            def cookieName = cookie.substring(0, eqIndex).trim()
            return !cookieNamesToStrip.contains(cookieName)
        }
        .join('; ')

    if (stripped.isEmpty()) {
        request.headers.remove('Cookie')
    } else {
        request.headers.put('Cookie', [stripped])
    }
}

return next.handle(context, request)
