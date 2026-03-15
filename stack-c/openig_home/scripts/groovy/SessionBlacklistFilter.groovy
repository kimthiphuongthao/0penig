import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64

def decodeClaimsFromToken = { String jwt ->
    String[] tokenParts = jwt.split('\\.')
    if (tokenParts.length < 2) {
        return null
    }
    String payloadJson = new String(Base64.getUrlDecoder().decode(tokenParts[1]), 'UTF-8')
    new JsonSlurper().parseText(payloadJson)
}

def decodeSidFromToken = { String jwt ->
    def payload = decodeClaimsFromToken(jwt)
    (payload?.sid ?: payload?.sub) as String
}

def decodePreferredUsernameFromToken = { String jwt ->
    def payload = decodeClaimsFromToken(jwt)
    (payload?.preferred_username ?: payload?.preferredUsername ?: payload?.email ?: payload?.sub) as String
}

def readRespLine = { InputStream input ->
    ByteArrayOutputStream buffer = new ByteArrayOutputStream()
    int previous = -1
    while (true) {
        int current = input.read()
        if (current == -1) {
            break
        }
        if (previous == '\r' as char && current == '\n' as char) {
            break
        }
        if (previous != -1) {
            buffer.write(previous)
        }
        previous = current
    }
    new String(buffer.toByteArray(), 'UTF-8')
}

try {
    def cfg = (binding.hasVariable('args') && args instanceof Map) ? (args as Map) : [:]
    String configuredClientEndpoint = cfg.clientEndpoint as String
    String sessionCacheKey = (cfg.sessionCacheKey as String)?.trim()

    String hostHeader = request.headers.getFirst('Host') as String
    String hostWithoutPort = hostHeader?.split(':')?.getAt(0)
    String hostWithPort = hostHeader?.contains(':') ? hostHeader : (hostWithoutPort ? "${hostWithoutPort}:80" : null)
    String openigPublicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: (hostWithPort ? "http://${hostWithPort}" : (hostWithoutPort ? "http://${hostWithoutPort}" : 'http://openigc.sso.local'))

    List<String> endpoints = []
    if (configuredClientEndpoint?.trim()) {
        endpoints.add(configuredClientEndpoint.trim())
    }
    if (!endpoints.contains('/openid/app5')) {
        endpoints.add('/openid/app5')
    }
    if (!endpoints.contains('/openid/app6')) {
        endpoints.add('/openid/app6')
    }

    if (!sessionCacheKey) {
        if (configuredClientEndpoint == '/openid/app6' || (hostWithoutPort ?: '').contains('phpmyadmin')) {
            sessionCacheKey = 'oidc_sid_app6'
        } else {
            sessionCacheKey = 'oidc_sid_app5'
        }
    }

    List<String> oauth2SessionKeys = []
    for (String endpoint : endpoints.unique()) {
        if (hostWithPort) {
            oauth2SessionKeys.add("oauth2:http://${hostWithPort}${endpoint}")
        }
        if (hostWithoutPort) {
            oauth2SessionKeys.add("oauth2:http://${hostWithoutPort}${endpoint}")
        }
        oauth2SessionKeys.add("oauth2:${openigPublicUrl}${endpoint}")
    }

    String idToken = null
    for (String oauth2SessionKey : oauth2SessionKeys.unique()) {
        idToken = session[oauth2SessionKey]?.get('atr')?.get('id_token') as String
        if (idToken?.trim()) {
            break
        }
    }

    boolean shouldCacheGrafanaUsername = configuredClientEndpoint == '/openid/app5' ||
        sessionCacheKey == 'oidc_sid_app5' ||
        (hostWithoutPort ?: '').contains('grafana')
    if (shouldCacheGrafanaUsername && idToken?.trim()) {
        String preferredUsername = decodePreferredUsernameFromToken(idToken)
        if (preferredUsername?.trim()) {
            String normalizedUsername = preferredUsername.trim()
            session['grafana_username'] = normalizedUsername
            if (binding.hasVariable('attributes') && attributes != null) {
                attributes.grafana_username = normalizedUsername
            }
        }
    }

    String sid = session[sessionCacheKey] as String
    if (!sid?.trim()) {
        if (!idToken?.trim()) {
            return next.handle(context, request)
        }

        sid = decodeSidFromToken(idToken)
        if (!sid?.trim()) {
            return next.handle(context, request)
        }

        session[sessionCacheKey] = sid
    }

    String redisHost = System.getenv('REDIS_HOST') ?: 'redis-c'
    int redisPort = 6379
    String key = "blacklist:${sid}"
    int keySize = key.getBytes('UTF-8').length
    String command = "*2\r\n\$3\r\nGET\r\n\$${keySize}\r\n${key}\r\n"

    boolean blacklisted = false
    new Socket().withCloseable { socket ->
        socket.connect(new InetSocketAddress(redisHost, redisPort), 200)  // 200ms connect timeout
        socket.setSoTimeout(500)  // 500ms read timeout
        socket.outputStream.write(command.getBytes('UTF-8'))
        socket.outputStream.flush()

        String firstLine = readRespLine(socket.inputStream)
        blacklisted = firstLine != '$-1'
    }

    if (blacklisted) {
        session.clear()

        String originalPath = request.uri.path ?: '/'
        String originalQuery = request.uri.query ? '?' + request.uri.query : ''
        String redirectHost = hostHeader ?: hostWithoutPort ?: 'openigc.sso.local'
        String redirectUrl = "http://${redirectHost}${originalPath}${originalQuery}"

        Response response = new Response(Status.FOUND)
        response.headers.put('Location', [redirectUrl as String])
        return newResultPromise(response)
    }

    return next.handle(context, request)
} catch (Exception e) {
    logger.error('[SessionBlacklistFilter] Redis check failed, denying request (fail-closed)', e)
    return newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR))
}
