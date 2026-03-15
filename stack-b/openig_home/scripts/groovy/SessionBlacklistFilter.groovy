import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

import java.net.InetSocketAddress
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
    String sid = session['oidc_sid'] as String
    if (!sid?.trim()) {
        String publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openigb.sso.local:9080'
        String app3SessionKey = 'oauth2:' + publicUrl + '/openid/app3'
        String app4SessionKey = 'oauth2:' + publicUrl + '/openid/app4'

        String idToken = session[app3SessionKey]?.get('atr')?.get('id_token') as String
        if (!idToken?.trim()) {
            idToken = session[app4SessionKey]?.get('atr')?.get('id_token') as String
        }
        if (!idToken?.trim()) {
            return next.handle(context, request)
        }

        sid = decodeSidFromToken(idToken)
        if (!sid?.trim()) {
            return next.handle(context, request)
        }

        session['oidc_sid'] = sid
    }

    String redisHost = System.getenv('REDIS_HOST') ?: 'redis-b'
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
        Response response = new Response(Status.FOUND)
        String CANONICAL_ORIGIN_B = System.getenv('CANONICAL_ORIGIN_APP3') ?: 'http://redmine-b.sso.local:9080'
        String originalPath = request.uri.path ?: '/'
        response.headers['Location'] = CANONICAL_ORIGIN_B + originalPath
        return newResultPromise(response)
    }

    return next.handle(context, request)
} catch (Exception e) {
    logger.error('[SessionBlacklistFilter] Redis check failed, denying request (fail-closed)', e)
    return newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR))
}
