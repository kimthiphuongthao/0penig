import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

String configuredClientEndpoint = binding.hasVariable('clientEndpoint') ? (clientEndpoint as String) : null
configuredClientEndpoint = configuredClientEndpoint?.trim()
String configuredTokenRefKey = binding.hasVariable('tokenRefKey') ? (tokenRefKey as String) : 'token_ref_id'

try {
    if (!configuredClientEndpoint) {
        throw new IllegalStateException('SpaAuthGuardFilter requires clientEndpoint arg')
    }

    List<String> oauth2SessionEntries
    try {
        oauth2SessionEntries = session.keySet()
            .collect { String.valueOf(it) }
            .findAll { it.startsWith('oauth2:') && it.endsWith(configuredClientEndpoint) }
    } catch (Exception ignored) {
        oauth2SessionEntries = ["oauth2:${configuredClientEndpoint}"]
    }

    if (!oauth2SessionEntries.isEmpty()) {
        return next.handle(context, request)
    }

    String acceptHeader = request.headers.getFirst('Accept') as String
    if (acceptHeader?.contains('application/json') && !acceptHeader.contains('text/html')) {
        logger.warn(
            '[SpaAuthGuardFilter] Blocking XHR request without active oauth2 session endpoint={} path={}',
            configuredClientEndpoint,
            request.uri.path
        )
        return newResultPromise(new Response(Status.UNAUTHORIZED))
    }

    logger.debug(
        '[SpaAuthGuardFilter] Allowing browser request without session endpoint={} path={}',
        configuredClientEndpoint,
        request.uri.path
    )
    return next.handle(context, request)
} catch (Exception e) {
    logger.error(
        '[SpaAuthGuardFilter] Failed for endpoint={} tokenRefKey={}',
        configuredClientEndpoint,
        configuredTokenRefKey,
        e
    )
    return next.handle(context, request)
}
