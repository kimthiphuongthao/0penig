import org.forgerock.http.protocol.Response

def host = (request.headers.getFirst('Host') ?: '').toLowerCase()
def hostWithoutDefaultPort = host.replaceFirst(/:80$/, '')
if (hostWithoutDefaultPort == 'wp-a.sso.local') {
    return next.handle(context, request).then({ response ->
        def location = response.headers.getFirst('Location')
        if (location != null && location.contains('http://wordpress/')) {
            def newLocation = location.replace('http://wordpress/', 'http://wp-a.sso.local/')
            logger.debug('[App1ResponseRewriter] Location: ' + location + ' → ' + newLocation)
            response.headers.put('Location', [newLocation])
        }
        return response
    })
}

return next.handle(context, request).then({ response ->
    // --- Location header rewrite ---
    def location = response.headers.getFirst('Location')
    if (location != null && !location.isEmpty()) {
        def newLocation = null
        // Absolute URL: http://openiga.sso.local/wp-admin/... → .../app1/wp-admin/...
        if (location =~ /^https?:\/\/openiga\.sso\.local(\/wp-)/) {
            newLocation = location.replaceFirst(/(^https?:\/\/openiga\.sso\.local)(\/wp-)/, '$1/app1$2')
        }
        // Absolute path: /wp-admin/... → /app1/wp-admin/...
        else if (location.startsWith('/wp-admin') || location.startsWith('/wp-login.php') ||
                 location.startsWith('/wp-content/') || location.startsWith('/wp-includes/') ||
                 location.startsWith('/wp-json/')) {
            newLocation = '/app1' + location
        }
        if (newLocation != null) {
            logger.debug('[App1ResponseRewriter] Location: ' + location + ' → ' + newLocation)
            response.headers.put('Location', [newLocation])
        }
    }

    // --- Response body rewrite for HTML/JS ---
    def contentType = response.headers.getFirst('Content-Type') ?: ''
    if (contentType.contains('text/html') || contentType.contains('javascript')) {
        def body = response.entity.getString()
        if (body != null && !body.isEmpty()) {
            def rewritten = body
                .replaceAll('(href|src|action)="(/wp-)', '$1="/app1$2')
                .replaceAll('(href|src|action)=\'(/wp-)', '$1=\'/app1$2')
                .replaceAll('(http://openiga\\.sso\\.local)(/wp-)', '$1/app1$2')
            if (rewritten != body) {
                logger.debug('[App1ResponseRewriter] Rewrote body URLs')
                response.entity = rewritten
            }
            // entity.getString() always decompresses — remove encoding headers
            response.headers.remove('Content-Encoding')
            response.headers.remove('Content-Length')
        }
    }

    return response
})
