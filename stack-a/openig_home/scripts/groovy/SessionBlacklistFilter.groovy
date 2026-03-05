import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

import java.net.Socket
import java.util.Base64

def readLine = { input ->
    def bytes = new ByteArrayOutputStream()
    while (true) {
        int next = input.read()
        if (next == -1) {
            throw new EOFException('Unexpected end of stream while reading Redis response line')
        }
        if (next == 13) {
            int lf = input.read()
            if (lf != 10) {
                throw new IOException('Invalid Redis response line ending')
            }
            break
        }
        bytes.write(next)
    }
    bytes.toString('UTF-8')
}

def readFully = { input, byte[] target ->
    int offset = 0
    while (offset < target.length) {
        int count = input.read(target, offset, target.length - offset)
        if (count == -1) {
            throw new EOFException('Unexpected end of stream while reading Redis bulk string')
        }
        offset += count
    }
}

def decodeJwtClaims = { String token ->
    def parts = token.split('\\.')
    if (parts.length < 2) {
        throw new IllegalArgumentException('id_token is not a valid JWT')
    }
    def payload = parts[1]
    def normalized = payload.replace('-', '+').replace('_', '/')
    while (normalized.length() % 4 != 0) {
        normalized += '='
    }
    def json = new String(Base64.decoder.decode(normalized), 'UTF-8')
    new JsonSlurper().parseText(json)
}

def resolveSid = {
    def cachedSid = session['oidc_sid'] as String
    if (cachedSid != null && !cachedSid.trim().isEmpty()) {
        return cachedSid
    }

    def host = request.headers.getFirst('Host') ?: 'openiga.sso.local'
    def hostWithPort = host.contains(':') ? host : (host + ':80')
    def hostWithoutPort = hostWithPort.replaceFirst(/:80$/, '')
    def publicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openiga.sso.local:80'

    def sessionKeys = [
        'oauth2:http://' + hostWithPort + '/openid/app1',
        'oauth2:http://' + hostWithoutPort + '/openid/app1',
        'oauth2:' + publicUrl + '/openid/app1'
    ].unique()

    String idToken = null
    for (def sessionKey : sessionKeys) {
        idToken = session[sessionKey]?.get('atr')?.get('id_token') as String
        if (idToken != null && !idToken.trim().isEmpty()) {
            break
        }
    }

    if (idToken == null || idToken.trim().isEmpty()) {
        return null
    }

    def claims = decodeJwtClaims(idToken)
    def sid = (claims?.sid ?: claims?.sub) as String
    if (sid != null && !sid.trim().isEmpty()) {
        session['oidc_sid'] = sid
        return sid
    }
    return null
}

def isBlacklisted = { String sid ->
    def redisHost = System.getenv('REDIS_HOST') ?: 'redis-a'
    def redisPort = 6379
    def key = "blacklist:${sid}"
    def command = "*2\r\n" +
        "\$3\r\nGET\r\n" +
        "\$${key.getBytes('UTF-8').length}\r\n${key}\r\n"

    Socket socket = null
    try {
        socket = new Socket(redisHost, redisPort)
        socket.soTimeout = 2000
        def output = socket.getOutputStream()
        def input = socket.getInputStream()

        output.write(command.getBytes('UTF-8'))
        output.flush()

        int marker = input.read()
        if (marker == -1) {
            throw new EOFException('Empty Redis response')
        }

        if (marker == ('$' as char)) {
            def lengthText = readLine(input)
            if ('-1'.equals(lengthText)) {
                return false
            }

            int length = Integer.parseInt(lengthText)
            if (length < 0) {
                return false
            }

            byte[] payload = new byte[length]
            readFully(input, payload)

            int cr = input.read()
            int lf = input.read()
            if (cr != 13 || lf != 10) {
                throw new IOException('Invalid Redis bulk string termination')
            }
            return true
        }

        if (marker == ('-' as char)) {
            throw new IOException("Redis GET error: ${readLine(input)}")
        }

        throw new IOException('Unexpected Redis response type for GET')
    } finally {
        if (socket != null) {
            socket.close()
        }
    }
}

try {
    def sid = resolveSid()
    if (sid == null || sid.trim().isEmpty()) {
        return next.handle(context, request)
    }

    def blacklisted = isBlacklisted(sid)
    logger.warn('[SessionBlacklistFilter] blacklisted=' + blacklisted + ' for sid=' + sid)
    if (blacklisted) {
        session.clear()
        def host = request.headers.getFirst('Host') ?: 'openiga.sso.local'
        def path = request.uri.path ?: '/'
        def query = request.uri.query
        def publicUrl = 'http://' + host + path + (query ? '?' + query : '')
        def response = new Response(Status.FOUND)
        response.headers['Location'] = publicUrl
        return newResultPromise(response)
    }
} catch (Exception e) {
    logger.warn('SessionBlacklistFilter Redis check failed; allowing request', e)
}

return next.handle(context, request)
