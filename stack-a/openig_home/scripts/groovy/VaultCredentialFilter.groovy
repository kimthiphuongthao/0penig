import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status

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

try {
    def vaultAddr = System.getenv('VAULT_ADDR') ?: 'http://vault:8200'
    def vaultRoleIdFile = System.getenv('VAULT_ROLE_ID_FILE')
    def vaultSecretIdFile = System.getenv('VAULT_SECRET_ID_FILE')

    def username = attributes.openid['user_info']['preferred_username'] as String
    if (username == null || username.trim().isEmpty()) {
        throw new IllegalStateException('Missing preferred_username in OpenID user_info')
    }
    username = username.trim()

    // FIX-09: Vault token cached in globals (per-instance ConcurrentHashMap, NOT in JwtSession cookie)
    long nowEpochSeconds = (long)(System.currentTimeMillis() / 1000)
    def cachedToken = globals['vault_token']
    def vaultToken = null

    if (cachedToken == null || cachedToken.expiry <= nowEpochSeconds) {
        if (vaultRoleIdFile == null || vaultRoleIdFile.trim().isEmpty()) {
            throw new IllegalStateException('VAULT_ROLE_ID_FILE is not set')
        }
        if (vaultSecretIdFile == null || vaultSecretIdFile.trim().isEmpty()) {
            throw new IllegalStateException('VAULT_SECRET_ID_FILE is not set')
        }

        def roleId = new File(vaultRoleIdFile).text.trim()
        def secretId = new File(vaultSecretIdFile).text.trim()
        if (roleId.isEmpty() || secretId.isEmpty()) {
            throw new IllegalStateException('Role ID or Secret ID file is empty')
        }

        def loginConnection = (HttpURLConnection) new URL("${vaultAddr}/v1/auth/approle/login").openConnection()
        loginConnection.requestMethod = 'POST'
        loginConnection.doOutput = true
        loginConnection.setRequestProperty('Content-Type', 'application/json')
        loginConnection.setRequestProperty('Accept', 'application/json')
        loginConnection.connectTimeout = 5000
        loginConnection.readTimeout = 5000

        def loginPayload = JsonOutput.toJson([role_id: roleId, secret_id: secretId])
        def loginWriter = new OutputStreamWriter(loginConnection.outputStream, 'UTF-8')
        loginWriter.write(loginPayload)
        loginWriter.flush()
        loginWriter.close()

        def loginStatus = loginConnection.responseCode
        def loginBody = readResponseBody(loginConnection)
        if (loginStatus < 200 || loginStatus >= 300) {
            throw new IllegalStateException("Vault AppRole login failed with HTTP ${loginStatus}")
        }

        def loginJson = new JsonSlurper().parseText(loginBody)
        def newVaultToken = loginJson?.auth?.client_token as String
        def leaseDuration = (loginJson?.auth?.lease_duration ?: 0) as long
        if (newVaultToken == null || newVaultToken.isEmpty()) {
            throw new IllegalStateException('Vault auth.client_token is missing in response')
        }

        globals['vault_token'] = [token: newVaultToken, expiry: nowEpochSeconds + leaseDuration]
        vaultToken = newVaultToken

        loginConnection.disconnect()
    } else {
        vaultToken = cachedToken.token
    }

    def encodedUsername = URLEncoder.encode(username, 'UTF-8').replace('+', '%20')
    def credsConnection = (HttpURLConnection) new URL("${vaultAddr}/v1/secret/data/wp-creds/${encodedUsername}").openConnection()
    credsConnection.requestMethod = 'GET'
    credsConnection.setRequestProperty('Accept', 'application/json')
    credsConnection.setRequestProperty('X-Vault-Token', vaultToken)
    credsConnection.connectTimeout = 5000
    credsConnection.readTimeout = 5000

    def credsStatus = credsConnection.responseCode
    def credsBody = readResponseBody(credsConnection)
    credsConnection.disconnect()
    if (credsStatus == 403) {
        // Token rejected — invalidate globals cache, will re-login on next request
        globals.remove('vault_token')
        def r = new Response(Status.BAD_GATEWAY)
        r.headers.put('Content-Type', ['text/html'])
        r.entity.setString('<html><body><h2>Vault token expired. Please retry.</h2></body></html>')
        return r
    }
    if (credsStatus < 200 || credsStatus >= 300) {
        throw new IllegalStateException("Vault credential lookup failed with HTTP ${credsStatus}")
    }

    def credsJson = new JsonSlurper().parseText(credsBody)
    def wpUsername = credsJson?.data?.data?.username as String
    def wpPassword = credsJson?.data?.data?.password as String
    if (wpUsername == null || wpUsername.isEmpty() || wpPassword == null || wpPassword.isEmpty()) {
        throw new IllegalStateException('Vault response is missing username or password')
    }

    attributes.wp_credentials = [wp_username: wpUsername, wp_password: wpPassword]
    return next.handle(context, request)
} catch (Exception e) {
    logger.error('[VaultCredentialFilter] Failed to fetch WordPress credentials from Vault', e)
    def errorResponse = new Response(Status.BAD_GATEWAY)
    errorResponse.headers.put('Content-Type', ['text/html'])
    errorResponse.entity.setString('<html><body><h2>Unable to retrieve credentials from Vault. Please try again later.</h2></body></html>')
    return errorResponse
}
