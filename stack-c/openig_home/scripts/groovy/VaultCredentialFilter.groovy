import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

def readResponseBody = { HttpURLConnection connection ->
    def stream = null
    try {
        stream = connection.inputStream
    } catch (Exception ignored) {
        stream = connection.errorStream
    }

    if (stream == null) {
        return ''
    }

    try {
        return stream.getText('UTF-8')
    } finally {
        stream.close()
    }
}

def decodeJwtClaims = { String jwt ->
    String[] parts = jwt.split('\\.')
    if (parts.length < 2) return null
    String payload = new String(Base64.getUrlDecoder().decode(parts[1]), 'UTF-8')
    new JsonSlurper().parseText(payload)
}

try {
    // Return early if credentials already cached in session
    if (session['phpmyadmin_username']?.toString()?.trim()) {
        return next.handle(context, request)
    }

    String vaultAddr = System.getenv('VAULT_ADDR') ?: 'http://vault:8200'
    String vaultRoleIdFile = System.getenv('VAULT_ROLE_ID_FILE')
    String vaultSecretIdFile = System.getenv('VAULT_SECRET_ID_FILE')
    String openigPublicUrl = System.getenv('OPENIG_PUBLIC_URL') ?: 'http://openig-c.sso.local:10080'

    // Build session key candidates (same pattern as SessionBlacklistFilter)
    String hostHeader = request.headers.getFirst('Host') as String
    String hostWithPort = hostHeader
    String hostWithoutPort = hostHeader?.split(':')?.getAt(0)

    List<String> oauth2SessionKeys = []
    if (hostWithPort) oauth2SessionKeys.add("oauth2:http://${hostWithPort}/openid/app6")
    if (hostWithoutPort) oauth2SessionKeys.add("oauth2:http://${hostWithoutPort}/openid/app6")
    oauth2SessionKeys.add("oauth2:${openigPublicUrl}/openid/app6")

    String idToken = null
    for (String key : oauth2SessionKeys) {
        idToken = session[key]?.get('atr')?.get('id_token') as String
        if (idToken?.trim()) break
    }

    String username = null
    if (idToken?.trim()) {
        def claims = decodeJwtClaims(idToken)
        username = (claims?.preferred_username ?: claims?.email ?: claims?.sub) as String
    }
    if (!username?.trim()) {
        throw new IllegalStateException('Missing preferred_username/email/sub in id_token')
    }
    username = username.trim()

    long nowEpochSeconds = (System.currentTimeMillis() / 1000L) as long
    String vaultToken = session['vault_token'] as String
    long vaultTokenExpiry = (session['vault_token_expiry'] ?: 0L) as long

    if (!vaultToken?.trim() || vaultTokenExpiry <= nowEpochSeconds) {
        if (!vaultRoleIdFile?.trim()) {
            throw new IllegalStateException('VAULT_ROLE_ID_FILE is not set')
        }
        if (!vaultSecretIdFile?.trim()) {
            throw new IllegalStateException('VAULT_SECRET_ID_FILE is not set')
        }

        String roleId = new File(vaultRoleIdFile).text.trim()
        String secretId = new File(vaultSecretIdFile).text.trim()
        if (!roleId || !secretId) {
            throw new IllegalStateException('Role ID or Secret ID file is empty')
        }

        HttpURLConnection loginConnection = (HttpURLConnection) new URL("${vaultAddr}/v1/auth/approle/login").openConnection()
        loginConnection.requestMethod = 'POST'
        loginConnection.doOutput = true
        loginConnection.setRequestProperty('Content-Type', 'application/json')
        loginConnection.setRequestProperty('Accept', 'application/json')
        loginConnection.connectTimeout = 5000
        loginConnection.readTimeout = 5000

        String loginPayload = JsonOutput.toJson([role_id: roleId, secret_id: secretId])
        loginConnection.outputStream.withCloseable { it.write(loginPayload.getBytes('UTF-8')) }

        int loginStatus = loginConnection.responseCode
        String loginBody = readResponseBody(loginConnection)
        loginConnection.disconnect()

        if (loginStatus < 200 || loginStatus >= 300) {
            throw new IllegalStateException("Vault AppRole login failed with HTTP ${loginStatus}")
        }

        def loginJson = new JsonSlurper().parseText(loginBody)
        String newVaultToken = loginJson?.auth?.client_token as String
        long leaseDuration = (loginJson?.auth?.lease_duration ?: 300L) as long
        if (!newVaultToken?.trim()) {
            throw new IllegalStateException('Vault auth.client_token is missing in response')
        }

        long adjustedLease = Math.max(leaseDuration - 30L, 30L)
        session['vault_token'] = newVaultToken
        session['vault_token_expiry'] = nowEpochSeconds + adjustedLease
        vaultToken = newVaultToken
    }

    String encodedUsername = URLEncoder.encode(username, 'UTF-8').replace('+', '%20')
    HttpURLConnection credsConnection = (HttpURLConnection) new URL("${vaultAddr}/v1/secret/data/phpmyadmin/${encodedUsername}").openConnection()
    credsConnection.requestMethod = 'GET'
    credsConnection.setRequestProperty('Accept', 'application/json')
    credsConnection.setRequestProperty('X-Vault-Token', vaultToken)
    credsConnection.connectTimeout = 5000
    credsConnection.readTimeout = 5000

    int credsStatus = credsConnection.responseCode
    String credsBody = readResponseBody(credsConnection)
    credsConnection.disconnect()

    if (credsStatus == 403) {
        session.remove('vault_token')
        session.remove('vault_token_expiry')
        throw new IllegalStateException('Vault token rejected for phpMyAdmin lookup (HTTP 403)')
    }
    if (credsStatus < 200 || credsStatus >= 300) {
        throw new IllegalStateException("Vault credential lookup failed with HTTP ${credsStatus}")
    }

    def credsJson = new JsonSlurper().parseText(credsBody)
    String pmaUsername = credsJson?.data?.data?.username as String
    String pmaPassword = credsJson?.data?.data?.password as String
    if (!pmaUsername?.trim() || !pmaPassword?.trim()) {
        throw new IllegalStateException('Vault response is missing username or password')
    }

    session['phpmyadmin_username'] = pmaUsername
    session['phpmyadmin_password'] = pmaPassword
    return next.handle(context, request)
} catch (Exception e) {
    logger.error('[VaultCredentialFilter] Failed to fetch phpMyAdmin credentials from Vault', e)
    Response errorResponse = new Response(Status.INTERNAL_SERVER_ERROR)
    errorResponse.headers.put('Content-Type', ['text/html'])
    errorResponse.entity.setString('<html><body><h2>Unable to retrieve phpMyAdmin credentials from Vault. Please try again later.</h2></body></html>')
    return errorResponse
}
