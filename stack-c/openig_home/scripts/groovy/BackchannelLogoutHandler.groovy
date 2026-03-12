import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

// --- JWT Validation Constants ---
final String KEYCLOAK_ISSUER = 'http://auth.sso.local:8080/realms/sso-lab'
final String KEYCLOAK_JWKS_URI = 'http://auth.sso.local:8080/realms/sso-lab/protocol/openid-connect/certs'
final List<String> EXPECTED_AUDIENCES = ['openig-client-c-app5', 'openig-client-c-app6']
final long CLOCK_SKEW_SECONDS = 60

// --- Helper: Read HTTP response body ---
def readResponseBody = { HttpURLConnection connection ->
    InputStream is = (connection.responseCode >= 400) ? connection.errorStream : connection.inputStream
    if (is == null) return null
    new BufferedReader(new InputStreamReader(is, 'UTF-8')).withCloseable { reader ->
        reader.text
    }
}

// --- Helper: Read Redis response line ---
def readRespLine = { InputStream input ->
    ByteArrayOutputStream buffer = new ByteArrayOutputStream()
    int previous = -1
    while (true) {
        int current = input.read()
        if (current == -1) break
        if (previous == '\r' as char && current == '\n' as char) break
        if (previous != -1) buffer.write(previous)
        previous = current
    }
    new String(buffer.toByteArray(), 'UTF-8')
}

// --- Helper: Decode base64url ---
def base64UrlDecode = { String input ->
    // Add padding if missing
    int padding = 4 - (input.length() % 4)
    String padded = (padding < 4) ? input + ('=' * padding) : input
    Base64.getUrlDecoder().decode(padded)
}

// --- Helper: Fetch JWKS from Keycloak ---
def fetchJwks = { String jwksUri ->
    logger.info('[BackchannelLogoutHandler] Fetching JWKS from Keycloak')
    try {
        URL url = new URL(jwksUri)
        HttpURLConnection conn = (HttpURLConnection) url.openConnection()
        conn.requestMethod = 'GET'
        conn.connectTimeout = 5000
        conn.readTimeout = 5000

        String responseBody = readResponseBody(conn)
        if (!responseBody) {
            logger.error('[BackchannelLogoutHandler] JWKS response body is empty')
            return null
        }

        def jwks = new JsonSlurper().parseText(responseBody)
        logger.info('[BackchannelLogoutHandler] JWKS fetched successfully, keys: {}', jwks.keys?.size())
        return jwks
    } catch (Exception e) {
        logger.error('[BackchannelLogoutHandler] Failed to fetch JWKS', e)
        return null
    }
}

// --- Helper: Get public key from JWKS by kid ---
def getPublicKeyFromJwks = { def jwks, String kid ->
    if (!jwks?.keys) return null

    def keyEntry = jwks.keys.find { it.kid == kid }
    if (!keyEntry) {
        logger.warn('[BackchannelLogoutHandler] Key with kid={} not found in JWKS', kid)
        return null
    }

    if (keyEntry.kty != 'RSA' || keyEntry.use != 'sig') {
        logger.warn('[BackchannelLogoutHandler] Key with kid={} is not an RSA signing key', kid)
        return null
    }

    // Reconstruct RSA public key from JWK (n=modulus, e=exponent)
    try {
        byte[] nBytes = base64UrlDecode(keyEntry.n)
        byte[] eBytes = base64UrlDecode(keyEntry.e)

        // Build PKCS#1 RSAPublicKey structure: SEQUENCE { INTEGER n, INTEGER e }
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        // Exponent
        baos.write(0x02) // INTEGER tag
        baos.write(eBytes.length)
        baos.write(eBytes)
        // Modulus
        baos.write(0x02) // INTEGER tag
        baos.write(0x81) // Long form length (1 byte length field)
        baos.write(nBytes.length)
        baos.write(nBytes)

        // Wrap in SEQUENCE
        byte[] rsaKeyBytes = baos.toByteArray()
        ByteArrayOutputStream seqBaos = new ByteArrayOutputStream()
        seqBaos.write(0x30) // SEQUENCE tag
        seqBaos.write(0x81) // Long form length
        seqBaos.write(rsaKeyBytes.length)
        seqBaos.write(rsaKeyBytes)

        byte[] seqBytes = seqBaos.toByteArray()
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(seqBytes)
        KeyFactory keyFactory = KeyFactory.getInstance('RSA')
        def publicKey = keyFactory.generatePublic(keySpec)
        logger.info('[BackchannelLogoutHandler] Successfully reconstructed public key for kid={}', kid)
        return publicKey
    } catch (Exception e) {
        logger.error('[BackchannelLogoutHandler] Failed to reconstruct public key for kid={}', kid, e)
        return null
    }
}

// --- Helper: Verify JWT signature ---
def verifySignature = { String jwt, java.security.PublicKey publicKey ->
    try {
        String[] parts = jwt.split('\\.')
        if (parts.length != 3) return false

        byte[] headerAndPayload = (parts[0] + '.' + parts[1]).getBytes('UTF-8')
        byte[] signatureBytes = base64UrlDecode(parts[2])

        Signature sig = Signature.getInstance('SHA256withRSA')
        sig.initVerify(publicKey)
        sig.update(headerAndPayload)

        boolean isValid = sig.verify(signatureBytes)
        logger.info('[BackchannelLogoutHandler] Signature verification: {}', isValid ? 'VALID' : 'INVALID')
        return isValid
    } catch (Exception e) {
        logger.error('[BackchannelLogoutHandler] Signature verification failed', e)
        return false
    }
}

// --- Helper: Validate JWT claims ---
def validateClaims = { def payload, List<String> expectedAudiences ->
    long now = System.currentTimeMillis() / 1000

    // 1. Validate 'iss' (issuer)
    if (payload.iss != KEYCLOAK_ISSUER) {
        logger.error('[BackchannelLogoutHandler] Invalid iss: expected={}, actual={}', KEYCLOAK_ISSUER, payload.iss)
        return [valid: false, error: "Invalid issuer: ${payload.iss}"]
    }
    logger.info('[BackchannelLogoutHandler] iss validation: OK (iss={})', payload.iss)

    // 2. Validate 'aud' (audience) - must contain one of the expected client IDs
    def aud = payload.aud
    boolean audValid = false
    if (aud instanceof String) {
        audValid = expectedAudiences.contains(aud)
    } else if (aud instanceof List) {
        audValid = aud.any { expectedAudiences.contains(it) }
    }
    if (!audValid) {
        logger.error('[BackchannelLogoutHandler] Invalid aud: expected one of {}, actual={}', expectedAudiences, aud)
        return [valid: false, error: "Invalid audience: ${aud}"]
    }
    logger.info('[BackchannelLogoutHandler] aud validation: OK (aud={})', aud)

    // 3. Validate 'events' claim - must contain backchannel logout event
    def events = payload.events
    if (!events || !(events instanceof Map)) {
        logger.error('[BackchannelLogoutHandler] Missing or invalid events claim')
        return [valid: false, error: 'Missing events claim']
    }
    def logoutEvent = events['http://schemas.openid.net/event/backchannel-logout']
    if (!logoutEvent || !(logoutEvent instanceof Map) || !logoutEvent.isEmpty()) {
        logger.error('[BackchannelLogoutHandler] Invalid backchannel logout event in claims')
        return [valid: false, error: 'Invalid backchannel logout event']
    }
    logger.info('[BackchannelLogoutHandler] events validation: OK')

    // 4. Validate 'iat' (issued at) - must be present and not in the future (with clock skew)
    if (!payload.iat) {
        logger.error('[BackchannelLogoutHandler] Missing iat claim')
        return [valid: false, error: 'Missing iat claim']
    }
    long iat = payload.iat as long
    if (iat > now + CLOCK_SKEW_SECONDS) {
        logger.error('[BackchannelLogoutHandler] Token issued in the future: iat={}, now={}', iat, now)
        return [valid: false, error: "Token issued in the future: iat=${iat}"]
    }
    logger.info('[BackchannelLogoutHandler] iat validation: OK (iat={})', iat)

    // 5. Validate 'exp' (expiration) - must be present and not expired (with clock skew)
    if (!payload.exp) {
        logger.error('[BackchannelLogoutHandler] Missing exp claim')
        return [valid: false, error: 'Missing exp claim']
    }
    long exp = payload.exp as long
    if (exp < now - CLOCK_SKEW_SECONDS) {
        logger.error('[BackchannelLogoutHandler] Token expired: exp={}, now={}', exp, now)
        return [valid: false, error: "Token expired: exp=${exp}"]
    }
    logger.info('[BackchannelLogoutHandler] exp validation: OK (exp={})', exp)

    // 6. Extract 'sid' or 'sub' (at least one required)
    String sid = (payload?.sid ?: payload?.sub) as String
    if (!sid?.trim()) {
        logger.error('[BackchannelLogoutHandler] Both sid and sub are missing')
        return [valid: false, error: 'Both sid and sub are missing']
    }
    logger.info('[BackchannelLogoutHandler] sid/sub extraction: OK (sid={})', sid)

    return [valid: true, sid: sid.trim()]
}

// --- Main Logic ---
try {
    logger.info('[BackchannelLogoutHandler] Starting backchannel logout processing')

    // 1. Parse logout_token from request body
    String body = request.entity?.getString() ?: ''
    String logoutTokenParam = body.split('&').find { it.startsWith('logout_token=') }
    if (!logoutTokenParam) {
        logger.error('[BackchannelLogoutHandler] logout_token parameter missing')
        return newResultPromise(new Response(Status.BAD_REQUEST))
    }

    String encodedLogoutToken = logoutTokenParam.substring('logout_token='.length())
    String logoutToken = URLDecoder.decode(encodedLogoutToken, 'UTF-8')
    logger.info('[BackchannelLogoutHandler] logout_token received, length={}', logoutToken.length())

    // 2. Basic JWT structure validation
    String[] tokenParts = logoutToken.split('\\.')
    if (tokenParts.length != 3) {
        logger.error('[BackchannelLogoutHandler] Invalid JWT structure: expected 3 parts, got {}', tokenParts.length)
        return newResultPromise(new Response(Status.BAD_REQUEST))
    }

    // 3. Decode header to get kid
    String headerJson = new String(base64UrlDecode(tokenParts[0]), 'UTF-8')
    def header = new JsonSlurper().parseText(headerJson)
    String kid = header.kid as String
    if (!kid) {
        logger.error('[BackchannelLogoutHandler] Missing kid in JWT header')
        return newResultPromise(new Response(Status.BAD_REQUEST))
    }
    logger.info('[BackchannelLogoutHandler] JWT header parsed, kid={}', kid)

    // Check algorithm - must be RS256
    String alg = header.alg as String
    if (alg != 'RS256') {
        logger.error('[BackchannelLogoutHandler] Invalid algorithm: expected RS256, got {}', alg)
        return newResultPromise(new Response(Status.BAD_REQUEST))
    }
    logger.info('[BackchannelLogoutHandler] Algorithm validation: OK (alg=RS256)')

    // 4. Fetch JWKS from Keycloak
    def jwks = fetchJwks(KEYCLOAK_JWKS_URI)
    if (!jwks) {
        logger.error('[BackchannelLogoutHandler] Failed to fetch JWKS')
        return newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR))
    }

    // 5. Get public key from JWKS
    def publicKey = getPublicKeyFromJwks(jwks, kid)
    if (!publicKey) {
        logger.error('[BackchannelLogoutHandler] Failed to get public key for kid={}', kid)
        return newResultPromise(new Response(Status.BAD_REQUEST))
    }

    // 6. Verify JWT signature
    boolean signatureValid = verifySignature(logoutToken, publicKey)
    if (!signatureValid) {
        logger.error('[BackchannelLogoutHandler] JWT signature verification FAILED')
        return newResultPromise(new Response(Status.BAD_REQUEST))
    }
    logger.info('[BackchannelLogoutHandler] JWT signature validation: VALID')

    // 7. Decode payload and validate claims
    String payloadJson = new String(base64UrlDecode(tokenParts[1]), 'UTF-8')
    def payload = new JsonSlurper().parseText(payloadJson)

    def validationResult = validateClaims(payload, EXPECTED_AUDIENCES)
    if (!validationResult.valid) {
        logger.error('[BackchannelLogoutHandler] Claims validation failed: {}', validationResult.error)
        return newResultPromise(new Response(Status.BAD_REQUEST))
    }

    String sid = validationResult.sid
    logger.info('[BackchannelLogoutHandler] All JWT validations passed, sid={}', sid)

    // 8. Write to Redis blacklist
    String redisHost = System.getenv('REDIS_HOST') ?: 'redis-c'
    int redisPort = 6379
    String key = "blacklist:${sid}"
    int keySize = key.getBytes('UTF-8').length
    String command = "*5\r\n\$3\r\nSET\r\n\$${keySize}\r\n${key}\r\n\$1\r\n1\r\n\$2\r\nEX\r\n\$4\r\n3600\r\n"

    new Socket(redisHost, redisPort).withCloseable { socket ->
        socket.outputStream.write(command.getBytes('UTF-8'))
        socket.outputStream.flush()
        String reply = readRespLine(socket.inputStream)
        if (!reply?.startsWith('+OK')) {
            throw new IllegalStateException("Redis SET failed: ${reply}")
        }
    }

    logger.info('[BackchannelLogoutHandler] Redis blacklist updated successfully, key={}', key)
    return newResultPromise(new Response(Status.OK))

} catch (IllegalArgumentException e) {
    logger.error('[BackchannelLogoutHandler] Validation error: {}', e.message)
    return newResultPromise(new Response(Status.BAD_REQUEST))
} catch (Exception e) {
    logger.error('[BackchannelLogoutHandler] Unexpected error', e)
    return newResultPromise(new Response(Status.BAD_REQUEST))
}
