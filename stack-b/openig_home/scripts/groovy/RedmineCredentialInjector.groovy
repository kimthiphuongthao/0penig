import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import java.util.concurrent.ConcurrentHashMap
import groovy.transform.Field

import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Field static final ConcurrentHashMap<String, String> redmineSessionCache = new ConcurrentHashMap<>()

def logPrefix = '[RedmineCredentialInjector] '

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

    String redmineSessionCookies = redmineSessionCache.get(login)

    if (redmineSessionCookies == null || redmineSessionCookies.isEmpty()) {
        logger.info(logPrefix + 'No cached Redmine session for ' + login + ' - logging in')

        HttpURLConnection getConnection = (HttpURLConnection) new URL('http://redmine:3000/login').openConnection()
        getConnection.requestMethod = 'GET'
        getConnection.instanceFollowRedirects = false
        getConnection.connectTimeout = 5000
        getConnection.readTimeout = 5000

        int getStatus = getConnection.responseCode
        String loginHtml = ''
        InputStream getStream = null
        if (getStatus >= 200 && getStatus < 400) {
            getStream = getConnection.inputStream
        } else {
            getStream = getConnection.errorStream
        }
        if (getStream != null) {
            loginHtml = getStream.getText('UTF-8')
            getStream.close()
        }

        List<String> initCookiePairs = []
        def getHeaderFields = getConnection.headerFields
        if (getHeaderFields != null) {
            for (def headerEntry : getHeaderFields.entrySet()) {
                def headerName = headerEntry.key
                if (headerName != null && headerName.equalsIgnoreCase('Set-Cookie')) {
                    def headerValues = headerEntry.value
                    if (headerValues != null) {
                        for (def headerValue : headerValues) {
                            if (headerValue != null) {
                                String setCookieLine = headerValue.toString()
                                int semi = setCookieLine.indexOf(';')
                                String pair = semi >= 0 ? setCookieLine.substring(0, semi) : setCookieLine
                                pair = pair.trim()
                                if (!pair.isEmpty()) {
                                    initCookiePairs.add(pair)
                                }
                            }
                        }
                    }
                }
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

        String tokenMarker = '<meta name="csrf-token" content="'
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

        HttpURLConnection postConnection = (HttpURLConnection) new URL('http://redmine:3000/login').openConnection()
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
        String postBody = ''
        InputStream postStream = null
        if (postStatus >= 200 && postStatus < 400) {
            postStream = postConnection.inputStream
        } else {
            postStream = postConnection.errorStream
        }
        if (postStream != null) {
            postBody = postStream.getText('UTF-8')
            postStream.close()
        }

        List<String> postCookiePairs = []
        def postHeaderFields = postConnection.headerFields
        if (postHeaderFields != null) {
            for (def headerEntry : postHeaderFields.entrySet()) {
                def headerName = headerEntry.key
                if (headerName != null && headerName.equalsIgnoreCase('Set-Cookie')) {
                    def headerValues = headerEntry.value
                    if (headerValues != null) {
                        for (def headerValue : headerValues) {
                            if (headerValue != null) {
                                String setCookieLine = headerValue.toString()
                                int semi = setCookieLine.indexOf(';')
                                String pair = semi >= 0 ? setCookieLine.substring(0, semi) : setCookieLine
                                pair = pair.trim()
                                if (!pair.isEmpty()) {
                                    postCookiePairs.add(pair)
                                }
                            }
                        }
                    }
                }
            }
        }
        postConnection.disconnect()

        if (postStatus != 302) {
            logger.error(logPrefix + 'POST /login failed with HTTP ' + postStatus)
            logger.error(logPrefix + 'POST /login response body: ' + postBody)
            def response = new Response(Status.BAD_GATEWAY)
            response.entity.setString('<html><body><h2>Unable to authenticate to Redmine.</h2></body></html>')
            response.headers.put('Content-Type', ['text/html'])
            return response
        }

        String newCookieHeader = postCookiePairs.join('; ')
        if (newCookieHeader == null || newCookieHeader.isEmpty() || !newCookieHeader.contains('_redmine_session=')) {
            logger.error(logPrefix + 'POST /login did not return _redmine_session cookie')
            def response = new Response(Status.BAD_GATEWAY)
            response.entity.setString('<html><body><h2>Redmine session cookie was not issued.</h2></body></html>')
            response.headers.put('Content-Type', ['text/html'])
            return response
        }

        redmineSessionCache.put(login, newCookieHeader)
        redmineSessionCookies = newCookieHeader
        logger.info(logPrefix + 'Redmine login OK for ' + login)
    }

    request.headers.put('Cookie', [redmineSessionCookies])

    return next.handle(context, request).then({ response ->
        int statusCode = response.status.code
        String location = response.headers.getFirst('Location')

        if ((statusCode == 301 || statusCode == 302) && location != null && location.contains('/login')) {
            logger.info(logPrefix + 'Detected redirect to /login, clearing cached cookies')
            redmineSessionCache.remove(login)

            String path = request.uri?.rawPath
            if (path == null || path.isEmpty()) {
                path = request.uri?.path
            }
            if (path == null || path.isEmpty()) {
                path = '/'
            }

            String query = request.uri?.rawQuery
            if (query != null && !query.isEmpty()) {
                path = path + '?' + query
            }

            String CANONICAL_ORIGIN = System.getenv('CANONICAL_ORIGIN_APP3') ?: 'http://redmine-b.sso.local:9080'
            String retryLocation = CANONICAL_ORIGIN + path

            def retryResponse = new Response(Status.FOUND)
            retryResponse.headers.put('Location', [retryLocation])
            return retryResponse
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
