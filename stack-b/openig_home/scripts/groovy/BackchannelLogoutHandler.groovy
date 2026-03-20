import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import static org.forgerock.util.promise.Promises.newResultPromise

import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

// --- JWT Validation Configuration ---
List expectedAudiences = binding.hasVariable('audiences') ? (audiences as List) : []
String configuredRedisHost = binding.hasVariable('redisHost') ? (redisHost as String) : (System.getenv('REDIS_HOST') ?: 'redis-b')
String configuredRedisPort = binding.hasVariable('redisPort') ? String.valueOf(redisPort) : (System.getenv('REDIS_PORT') ?: '6379')
configuredRedisPort = configuredRedisPort?.trim()
String configuredJwksUri = binding.hasVariable('jwksUri') ? (jwksUri as String) : null
String configuredIssuer = binding.hasVariable('issuer') ? (issuer as String) : null
int redisBlacklistTtlSeconds = binding.hasVariable('ttlSeconds') ? (ttlSeconds as int) : 28800
final long CLOCK_SKEW_SECONDS = 60
final long JWKS_CACHE_TTL_SECONDS = 3600

private static boolean validateClaims(Map payload, def expectedAudience) {
    def aud = payload.aud
    if (expectedAudience instanceof List) {
        def audList = (aud instanceof List) ? aud : [aud]
        return audList.any { expectedAudience.contains(it) }
    } else {
        return (aud instanceof List) ? aud.contains(expectedAudience) : aud == expectedAudience
    }
}

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
        if (current == -1) throw new IOException('Unexpected EOF from Redis')
        if (previous == '\r' as char && current == '\n' as char) break
        if (previous != -1) buffer.write(previous)
        previous = current
    }
    new String(buffer.toByteArray(), 'UTF-8')
}

// --- Helper: Decode base64url ---
def base64UrlDecode = { String input ->
    Base64.getUrlDecoder().decode(input)
}

// --- Helper: Fetch JWKS from Keycloak ---
def fetchJwksKeys = { String jwksUri ->
    logger.info('[BackchannelLogoutHandler] Fetching JWKS from Keycloak')
    try {
        URL url = new URL(jwksUri)
        HttpURLConnection conn = (HttpURLConnection) url.openConnection()
        conn.requestMethod = 'GET'
        conn.connectTimeout = 5000
        conn.readTimeout = 5000

        int httpStatus = conn.responseCode
        String responseBody = readResponseBody(conn)
        if (httpStatus < 200 || httpStatus >= 300) {
            logger.error('[BackchannelLogoutHandler] JWKS fetch returned HTTP {}', httpStatus)
            return null
        }
        if (!responseBody) {
            logger.error('[BackchannelLogoutHandler] JWKS response body is empty')
            return null
        }

        def jwks = new JsonSlurper().parseText(responseBody)
        if (!jwks?.keys) {
            logger.error('[BackchannelLogoutHandler] JWKS response missing keys array')
            return null
        }

        Map keysByKid = [:]
        jwks.keys.each { key ->
            if (key?.kid) {
                keysByKid[key.kid as String] = key
            }
        }
        if (keysByKid.isEmpty()) {
            logger.error('[BackchannelLogoutHandler] JWKS response contains no usable keys')
            return null
        }

        logger.info('[BackchannelLogoutHandler] JWKS fetched successfully, keys: {}', keysByKid.size())
        return keysByKid
    } catch (Exception e) {
        logger.error('[BackchannelLogoutHandler] Failed to fetch JWKS', e)
        return null
    }
}

// --- Helper: Load JWKS keys through global cache ---
def loadJwksKeys = {
    def cacheEntry = globals.compute('jwks_cache') { k, existing ->
        long now = System.currentTimeMillis() / 1000
        if (existing != null && (now - (existing.cachedAt as long)) < JWKS_CACHE_TTL_SECONDS) {
            logger.info('[BackchannelLogoutHandler] Using cached JWKS')
            return existing
        }
        def newKeys = fetchJwksKeys(configuredJwksUri)
        [keys: newKeys, cachedAt: now]
    }
    cacheEntry?.keys as Map
}

// --- Helper: Get public key from JWKS by kid ---
def getPublicKeyFromJwks = { Map jwksKeys, String kid ->
    if (!jwksKeys) return null

    def keyEntry = jwksKeys[kid]
    if (!keyEntry) {
        logger.warn('[BackchannelLogoutHandler] Key with kid={} not found in JWKS', kid)
        return null
    }

    if (keyEntry.use != 'sig') {
        logger.warn('[BackchannelLogoutHandler] Key with kid={} is not a signing key', kid)
        return null
    }

    try {
        if (keyEntry.kty == 'RSA') {
            // Reconstruct RSA public key from JWK (n=modulus, e=exponent)
            byte[] nBytes = base64UrlDecode(keyEntry.n)
            byte[] eBytes = base64UrlDecode(keyEntry.e)
            BigInteger modulus = new BigInteger(1, nBytes)
            BigInteger exponent = new BigInteger(1, eBytes)
            RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent)
            KeyFactory keyFactory = KeyFactory.getInstance('RSA')
            def publicKey = keyFactory.generatePublic(keySpec)
            logger.info('[BackchannelLogoutHandler] Successfully reconstructed RSA public key for kid={}', kid)
            return publicKey
        }

        if (keyEntry.kty == 'EC') {
            if (keyEntry.crv != 'P-256') {
                logger.warn('[BackchannelLogoutHandler] Key with kid={} has unsupported EC curve={}', kid, keyEntry.crv)
                return null
            }

            byte[] xBytes = base64UrlDecode(keyEntry.x)
            byte[] yBytes = base64UrlDecode(keyEntry.y)
            BigInteger x = new BigInteger(1, xBytes)
            BigInteger y = new BigInteger(1, yBytes)

            AlgorithmParameters algorithmParameters = AlgorithmParameters.getInstance('EC')
            algorithmParameters.init(new ECGenParameterSpec('secp256r1'))
            def ecParameterSpec = algorithmParameters.getParameterSpec(java.security.spec.ECParameterSpec)
            ECPublicKeySpec keySpec = new ECPublicKeySpec(new ECPoint(x, y), ecParameterSpec)
            KeyFactory keyFactory = KeyFactory.getInstance('EC')
            def publicKey = keyFactory.generatePublic(keySpec)
            logger.info('[BackchannelLogoutHandler] Successfully reconstructed EC public key for kid={}', kid)
            return publicKey
        }

        logger.warn('[BackchannelLogoutHandler] Key with kid={} has unsupported kty={}', kid, keyEntry.kty)
        return null
    } catch (Exception e) {
        logger.error('[BackchannelLogoutHandler] Failed to reconstruct public key for kid={}', kid, e)
        return null
    }
}

// --- Helper: Verify JWT signature ---
def verifySignature = { String jwt, java.security.PublicKey publicKey, String alg ->
    try {
        String[] parts = jwt.split('\\.')
        if (parts.length != 3) return false

        byte[] headerAndPayload = (parts[0] + '.' + parts[1]).getBytes('UTF-8')
        byte[] signatureBytes = base64UrlDecode(parts[2])

        Signature sig
        if (alg == 'RS256') {
            sig = Signature.getInstance('SHA256withRSA')
        } else if (alg == 'ES256') {
            if (signatureBytes.length != 64) {
                logger.warn('[BackchannelLogoutHandler] Invalid ES256 signature length: {}', signatureBytes.length)
                return false
            }

            byte[] rBytes = new byte[32]
            byte[] sBytes = new byte[32]
            System.arraycopy(signatureBytes, 0, rBytes, 0, 32)
            System.arraycopy(signatureBytes, 32, sBytes, 0, 32)

            def toDerInteger = { byte[] value ->
                int offset = 0
                while (offset < value.length - 1 && value[offset] == 0) {
                    offset++
                }
                int length = value.length - offset
                ByteArrayOutputStream derInteger = new ByteArrayOutputStream()
                if ((value[offset] & 0x80) != 0) {
                    derInteger.write(0)
                }
                derInteger.write(value, offset, length)
                derInteger.toByteArray()
            }

            byte[] derR = toDerInteger(rBytes)
            byte[] derS = toDerInteger(sBytes)
            ByteArrayOutputStream derSignature = new ByteArrayOutputStream()
            derSignature.write(0x30)
            derSignature.write(2 + derR.length + 2 + derS.length)
            derSignature.write(0x02)
            derSignature.write(derR.length)
            derSignature.write(derR)
            derSignature.write(0x02)
            derSignature.write(derS.length)
            derSignature.write(derS)
            signatureBytes = derSignature.toByteArray()
            sig = Signature.getInstance('SHA256withECDSA')
        } else {
            logger.warn('[BackchannelLogoutHandler] Unsupported signature algorithm: {}', alg)
            return false
        }

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

// --- Helper: Validate logout token claims ---
def validateLogoutTokenClaims = { Map payload, def expectedAudience ->
    long now = System.currentTimeMillis() / 1000

    // 1. Validate 'iss' (issuer)
    if (payload.iss != configuredIssuer) {
        logger.error('[BackchannelLogoutHandler] Invalid iss: expected={}, actual={}', configuredIssuer, payload.iss)
        return [valid: false, error: "Invalid issuer: ${payload.iss}"]
    }
    logger.info('[BackchannelLogoutHandler] iss validation: OK (iss={})', payload.iss)

    // 2. Validate 'aud' (audience) - must contain expected client ID
    Map claims = payload as Map
    boolean audValid = validateClaims(claims, expectedAudience)
    if (!audValid) {
        logger.error('[BackchannelLogoutHandler] Invalid aud: expected={}, actual={}', expectedAudience, payload.aud)
        return [valid: false, error: "Invalid audience: ${payload.aud}"]
    }
    logger.info('[BackchannelLogoutHandler] aud validation: OK (aud={})', payload.aud)

    // 3. Validate 'events' claim - must contain backchannel logout event
    def events = payload.events
    if (!events || !(events instanceof Map)) {
        logger.error('[BackchannelLogoutHandler] Missing or invalid events claim')
        return [valid: false, error: 'Missing events claim']
    }
    def logoutEvent = events['http://schemas.openid.net/event/backchannel-logout']
    if (logoutEvent == null || !(logoutEvent instanceof Map)) {
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

    // Check algorithm - must be RS256 or ES256
    String alg = header.alg as String
    if (alg != 'RS256' && alg != 'ES256') {
        logger.error('[BackchannelLogoutHandler] Invalid algorithm: expected RS256 or ES256, got {}', alg)
        return newResultPromise(new Response(Status.BAD_REQUEST))
    }
    logger.info('[BackchannelLogoutHandler] Algorithm validation: OK (alg={})', alg)

    // 4. Fetch JWKS from Keycloak through atomic global cache
    Map jwksKeys = loadJwksKeys()
    if (!jwksKeys) {
        logger.error('[BackchannelLogoutHandler] Failed to fetch JWKS')
        return newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR))
    }

    // 5. Get public key from JWKS
    def publicKey = getPublicKeyFromJwks(jwksKeys, kid)
    if (!publicKey) {
        logger.warn('[BackchannelLogoutHandler] kid={} not in cached JWKS, forcing refetch', kid)
        globals.compute('jwks_cache') { k, v -> null }
        jwksKeys = loadJwksKeys()
        if (!jwksKeys) {
            logger.error('[BackchannelLogoutHandler] Failed to refetch JWKS')
            return newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR))
        }
        publicKey = getPublicKeyFromJwks(jwksKeys, kid)
        if (!publicKey) {
            logger.error('[BackchannelLogoutHandler] kid={} not found even after JWKS refetch', kid)
            return newResultPromise(new Response(Status.BAD_REQUEST))
        }
    }

    // 6. Verify JWT signature
    boolean signatureValid = verifySignature(logoutToken, publicKey, alg)
    if (!signatureValid) {
        logger.error('[BackchannelLogoutHandler] JWT signature verification FAILED')
        return newResultPromise(new Response(Status.BAD_REQUEST))
    }
    logger.info('[BackchannelLogoutHandler] JWT signature validation: VALID')

    // 7. Decode payload and validate claims
    String payloadJson = new String(base64UrlDecode(tokenParts[1]), 'UTF-8')
    def payload = new JsonSlurper().parseText(payloadJson)

    def validationResult = validateLogoutTokenClaims(payload as Map, expectedAudiences)
    if (!validationResult.valid) {
        logger.error('[BackchannelLogoutHandler] Claims validation failed: {}', validationResult.error)
        return newResultPromise(new Response(Status.BAD_REQUEST))
    }

    String sid = validationResult.sid
    logger.info('[BackchannelLogoutHandler] All JWT validations passed, sid={}', sid)

    // 8. Write to Redis blacklist
      int redisPort = configuredRedisPort as int
      String redisPassword = System.getenv('REDIS_PASSWORD') ?: ''
      String key = "blacklist:${sid}"
      int keySize = key.getBytes('UTF-8').length
    // TTL must be >= JwtSession.sessionTimeout (1800s = 30min)
    String ttl = String.valueOf(redisBlacklistTtlSeconds)
    String command = "*5\r\n\$3\r\nSET\r\n\$${keySize}\r\n${key}\r\n\$1\r\n1\r\n\$2\r\nEX\r\n\$${ttl.length()}\r\n${ttl}\r\n"

      new Socket().withCloseable { socket ->
          socket.connect(new InetSocketAddress(configuredRedisHost, redisPort), 200)  // 200ms connect timeout
          socket.setSoTimeout(500)  // 500ms read timeout
          if (redisPassword) {
              int pwLen = redisPassword.getBytes('UTF-8').length
              String authCmd = "*2\r\n\$4\r\nAUTH\r\n\$${pwLen}\r\n${redisPassword}\r\n"
              socket.outputStream.write(authCmd.getBytes('UTF-8'))
              socket.outputStream.flush()
              String authReply = readRespLine(socket.inputStream)
              if (!authReply?.startsWith('+OK')) {
                  throw new IOException("Redis AUTH failed: ${authReply}")
              }
          }
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
    // JWT validation/parsing error - client sent bad data
    logger.error('[BackchannelLogoutHandler] Validation error: {}', e.message)
    return newResultPromise(new Response(Status.BAD_REQUEST))
} catch (Exception e) {
    // Runtime/infra error (Redis, network, unexpected) - return 500 so Keycloak retries
    logger.error('[BackchannelLogoutHandler] Runtime error (Redis/infra): {}', e.message, e)
    return newResultPromise(new Response(Status.INTERNAL_SERVER_ERROR))
}
