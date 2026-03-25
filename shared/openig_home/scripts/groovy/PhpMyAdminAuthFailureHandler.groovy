import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

import java.net.URI
import java.net.URLEncoder

String clientEndpoint = binding.hasVariable('clientEndpoint') ? String.valueOf(binding.variables['clientEndpoint']) : '/openid/app6'
String clientId = binding.hasVariable('clientId') ? String.valueOf(binding.variables['clientId']) : 'openig-client-c-app6'
String canonicalOriginEnvVar = binding.hasVariable('canonicalOriginEnvVar') ? String.valueOf(binding.variables['canonicalOriginEnvVar'])?.trim() : null
String canonicalOrigin = null
if (canonicalOriginEnvVar) {
    canonicalOrigin = System.getenv(canonicalOriginEnvVar)?.trim()
    if (!canonicalOrigin) {
        throw new IllegalStateException("PhpMyAdminAuthFailureHandler requires env var ${canonicalOriginEnvVar}")
    }
} else if (binding.hasVariable('canonicalOrigin')) {
    canonicalOrigin = String.valueOf(binding.variables['canonicalOrigin'])?.trim()
}
if (!canonicalOrigin) {
    throw new IllegalStateException('PhpMyAdminAuthFailureHandler requires canonicalOrigin or canonicalOriginEnvVar')
}
String postLogoutPath = binding.hasVariable('postLogoutPath') ? String.valueOf(binding.variables['postLogoutPath']) : '/'
String redirectUrl = binding.hasVariable('redirectUrl') ? String.valueOf(binding.variables['redirectUrl']) : canonicalOrigin + '/'

def readStringListArg = { String pluralName, String singularName ->
    List<String> values = []
    if (binding.hasVariable(pluralName)) {
        def rawValues = binding.variables[pluralName]
        if (rawValues instanceof Collection) {
            values.addAll(rawValues.collect { String.valueOf(it).toLowerCase() })
        } else if (rawValues != null) {
            values.add(String.valueOf(rawValues).toLowerCase())
        }
    } else if (binding.hasVariable(singularName)) {
        def rawValue = binding.variables[singularName]
        if (rawValue != null) {
            values.add(String.valueOf(rawValue).toLowerCase())
        }
    }
    values
}

List<String> logoutPathNeedles = []
logoutPathNeedles.addAll(readStringListArg('logoutPathNeedles', 'logoutPathNeedle'))
List<String> logoutQueryNeedles = []
logoutQueryNeedles.addAll(readStringListArg('logoutQueryNeedles', 'logoutQueryNeedle'))
String retryQueryParam = binding.hasVariable('retryQueryParam') ? String.valueOf(binding.variables['retryQueryParam'])?.trim() : '_ig_pma_retry'
String retryQueryValue = binding.hasVariable('retryQueryValue') ? String.valueOf(binding.variables['retryQueryValue'])?.trim() : '1'
List<String> configuredSessionKeysToClear = binding.hasVariable('sessionKeysToClear') ?
    ((binding.variables['sessionKeysToClear'] as List)?.collect { String.valueOf(it) }) :
    []

String requestPath = request.uri.path ?: ''
String requestQuery = request.uri.query ?: ''
String requestMethod = request.method ?: 'GET'
String requestPathLower = requestPath.toLowerCase()
String requestQueryLower = requestQuery.toLowerCase()
boolean logoutRequest =
    logoutPathNeedles.any { requestPathLower.contains(it) } ||
    logoutQueryNeedles.any { requestQueryLower.contains(it) }
boolean retryRequest =
    retryQueryParam?.trim() &&
    requestQueryLower.contains((retryQueryParam + '=' + retryQueryValue).toLowerCase())
boolean canRedirectForRetry = ['GET', 'HEAD'].contains(requestMethod?.toUpperCase())

def removeAppSessionKeys = {
    Set<String> sessionKeysToClear = new LinkedHashSet<>(configuredSessionKeysToClear)
    try {
        session.keySet()
            .collect { String.valueOf(it) }
            .findAll { it.startsWith('oauth2:') && it.endsWith(clientEndpoint) }
            .each { sessionKeysToClear.add(it) }
    } catch (Exception e) {
        logger.warn('[PhpMyAdminAuthFailureHandler] Failed to enumerate app6 oauth2 session keys before clear', e)
    }

    sessionKeysToClear.each { key ->
        session.remove(key)
    }
}

def buildRetryUrl = { String baseUrl ->
    if (!retryQueryParam?.trim()) {
        return baseUrl
    }

    String separator = baseUrl.contains('?') ?
        ((baseUrl.endsWith('?') || baseUrl.endsWith('&')) ? '' : '&') :
        '?'
    baseUrl + separator +
        URLEncoder.encode(retryQueryParam, 'UTF-8') +
        '=' +
        URLEncoder.encode(retryQueryValue ?: '1', 'UTF-8')
}

def buildRetryFailureResponse = {
    removeAppSessionKeys()
    Response response = new Response(Status.BAD_GATEWAY)
    response.headers.put('Content-Type', ['text/html'])
    String retryHref = redirectUrl
    String logoutHref = canonicalOrigin + '/?logout=1'
    response.entity.setString(
        '<html><body><h2>phpMyAdmin rejected the downstream credentials after SSO re-authentication.</h2>' +
        '<p>The gateway stopped the automatic retry loop.</p>' +
        '<p><a href="' + retryHref + '">Retry login</a> | <a href="' + logoutHref + '">Sign out</a></p>' +
        '</body></html>'
    )
    response
}

if (!logoutRequest) {
    if (!canRedirectForRetry || retryRequest) {
        logger.warn(
            '[PhpMyAdminAuthFailureHandler] Non-logout 401 stopped retry loop method={} path={} query={} keys={}',
            requestMethod,
            requestPath,
            requestQuery,
            configuredSessionKeysToClear
        )
        return newResultPromise(buildRetryFailureResponse())
    }

    removeAppSessionKeys()
    String retryUrl = buildRetryUrl(redirectUrl)
    Response response = new Response(Status.FOUND)
    response.headers.put('Location', [retryUrl as String])
    logger.info(
        '[PhpMyAdminAuthFailureHandler] Non-logout 401 redirected to {} after clearing keys={} method={} path={} query={}',
        retryUrl,
        configuredSessionKeysToClear,
        requestMethod,
        requestPath,
        requestQuery
    )
    return newResultPromise(response)
}

def publicUrl = System.getenv('OPENIG_PUBLIC_URL')?.trim()
if (!publicUrl) {
    throw new IllegalStateException('PhpMyAdminAuthFailureHandler requires env var OPENIG_PUBLIC_URL')
}
def hostHeader = request.headers.getFirst('Host') as String
def hostWithoutPort = hostHeader?.split(':')?.getAt(0)
def publicUri = new URI(publicUrl)
def defaultPort = publicUri.port > 0 ? String.valueOf(publicUri.port) : ('https'.equalsIgnoreCase(publicUri.scheme) ? '443' : '80')
def hostWithPort = hostHeader?.contains(':') ? hostHeader : (hostWithoutPort ? hostWithoutPort + ':' + defaultPort : null)

def oauth2Keys = []
if (hostWithPort) {
    oauth2Keys.add('oauth2:http://' + hostWithPort + clientEndpoint)
}
if (hostWithoutPort) {
    oauth2Keys.add('oauth2:http://' + hostWithoutPort + clientEndpoint)
}
oauth2Keys.add('oauth2:' + publicUrl + clientEndpoint)

String idToken = null
for (def key : oauth2Keys.unique()) {
    idToken = session[key]?.get('atr')?.get('id_token') as String
    if (idToken?.trim()) {
        break
    }
}

session.clear()

try {
    def keycloakBrowserUrl = System.getenv('KEYCLOAK_BROWSER_URL') ?: 'http://auth.sso.local:8080'
    def postLogoutUri = canonicalOrigin + postLogoutPath
    def logoutUrl = keycloakBrowserUrl + '/realms/sso-realm/protocol/openid-connect/logout?client_id=' + clientId + '&post_logout_redirect_uri=' + URLEncoder.encode(postLogoutUri, 'UTF-8')
    if (idToken?.trim()) {
        logoutUrl += '&id_token_hint=' + URLEncoder.encode(idToken, 'UTF-8')
        logger.info('[PhpMyAdminAuthFailureHandler] Logout 401 redirected with id_token_hint=PRESENT clientId={} postLogoutUri={} path={} query={}', clientId, postLogoutUri, requestPath, requestQuery)
    } else {
        logger.warn('[PhpMyAdminAuthFailureHandler] Logout 401 had no id_token, proceeding without id_token_hint path={} query={}', requestPath, requestQuery)
    }

    def response = new Response(Status.FOUND)
    response.headers.put('Location', [logoutUrl as String])
    return newResultPromise(response)
} catch (Exception e) {
    logger.error('[PhpMyAdminAuthFailureHandler] Logout redirect failed', e)
    def errResp = new Response(Status.INTERNAL_SERVER_ERROR)
    errResp.headers.put('Content-Type', ['text/html'])
    errResp.entity.setString('<html><body><h2>Logout failed. Please close your browser.</h2></body></html>')
    return newResultPromise(errResp)
}
