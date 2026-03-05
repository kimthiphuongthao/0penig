import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

import java.net.Socket
import java.util.Base64

def decodeSidFromToken = { String jwt ->
    String[] tokenParts = jwt.split('\\.')
    if (tokenParts.length < 2) {
        return null
    }
    String payloadJson = new String(Base64.getUrlDecoder().decode(tokenParts[1]), 'UTF-8')
    def payload = new JsonSlurper().parseText(payloadJson)
    (payload?.sid ?: payload?.sub) as String
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
    String sid = session['oidc_sid_app4'] as String
    if (!sid?.trim()) {
        String openigPublicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openigb.sso.local:9080'
        String hostHeader = request.headers.getFirst('Host') as String
        String hostWithoutPort = hostHeader?.split(':')?.getAt(0)
        String hostWithPort = hostHeader?.contains(':') ? hostHeader : (hostWithoutPort ? "${hostWithoutPort}:9080" : null)

        List<String> oauth2SessionKeys = []
        if (hostWithPort) {
            oauth2SessionKeys.add("oauth2:http://${hostWithPort}/openid/app4")
        }
        if (hostWithoutPort) {
            oauth2SessionKeys.add("oauth2:http://${hostWithoutPort}/openid/app4")
        }
        oauth2SessionKeys.add("oauth2:${openigPublicUrl}/openid/app4")

        String idToken = null
        for (String oauth2SessionKey : oauth2SessionKeys.unique()) {
            idToken = session[oauth2SessionKey]?.get('atr')?.get('id_token') as String
            if (idToken?.trim()) {
                break
            }
        }
        if (!idToken?.trim()) {
            return next.handle(context, request)
        }

        sid = decodeSidFromToken(idToken)
        if (!sid?.trim()) {
            return next.handle(context, request)
        }

        session['oidc_sid_app4'] = sid
    }

    String redisHost = System.getenv('REDIS_HOST') ?: 'redis-b'
    int redisPort = 6379
    String key = "blacklist:${sid}"
    int keySize = key.getBytes('UTF-8').length
    String command = "*2\r\n\$3\r\nGET\r\n\$${keySize}\r\n${key}\r\n"

    boolean blacklisted = false
    new Socket(redisHost, redisPort).withCloseable { socket ->
        socket.outputStream.write(command.getBytes('UTF-8'))
        socket.outputStream.flush()

        String firstLine = readRespLine(socket.inputStream)
        blacklisted = firstLine != '$-1'
    }

    if (blacklisted) {
        session.clear()
        Response response = new Response(Status.FOUND)
        String redirectHost = request.headers.getFirst('Host') as String
        if (!redirectHost?.trim()) {
            redirectHost = 'openigb.sso.local:9080'
        }
        response.headers.put('Location', [("http://${redirectHost}" + (request.uri.path ?: '/')) as String])
        return newResultPromise(response)
    }

    return next.handle(context, request)
} catch (Exception e) {
    logger.warn('[SessionBlacklistFilterApp4] Redis check failed, continuing request', e)
    return next.handle(context, request)
}
