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

    def userInfo = attributes.openid['user_info']
    def email = userInfo['email'] as String
    def username = userInfo['preferred_username'] as String
    if (email == null || email.trim().isEmpty()) {
        throw new IllegalStateException('Missing email in OpenID user_info')
    }
    if (username == null || username.trim().isEmpty()) {
        throw new IllegalStateException('Missing preferred_username in OpenID user_info')
    }
    email = email.trim()
    username = username.trim()

    def nowEpochSeconds = (System.currentTimeMillis() / 1000L) as long
    def vaultToken = session['vault_token_jellyfin'] as String
    def tokenExpiryRaw = session['vault_token_expiry_jellyfin']
    def vaultTokenExpiry = tokenExpiryRaw != null ? (tokenExpiryRaw as Long) : 0L

    if (vaultToken == null || vaultToken.isEmpty() || vaultTokenExpiry <= nowEpochSeconds) {
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
        loginConnection.disconnect()
        if (loginStatus < 200 || loginStatus >= 300) {
            throw new IllegalStateException("Vault AppRole login failed with HTTP ${loginStatus}")
        }

        def loginJson = new JsonSlurper().parseText(loginBody)
        def newVaultToken = loginJson?.auth?.client_token as String
        def leaseDuration = (loginJson?.auth?.lease_duration ?: 0) as long
        if (newVaultToken == null || newVaultToken.isEmpty()) {
            throw new IllegalStateException('Vault auth.client_token is missing in response')
        }

        session['vault_token_jellyfin'] = newVaultToken
        session['vault_token_expiry_jellyfin'] = nowEpochSeconds + leaseDuration
        vaultToken = newVaultToken
    }

    def encodedEmail = URLEncoder.encode(email, 'UTF-8').replace('+', '%20')
    def credsConnection = (HttpURLConnection) new URL("${vaultAddr}/v1/secret/data/jellyfin-creds/${encodedEmail}").openConnection()
    credsConnection.requestMethod = 'GET'
    credsConnection.setRequestProperty('Accept', 'application/json')
    credsConnection.setRequestProperty('X-Vault-Token', vaultToken)
    credsConnection.connectTimeout = 5000
    credsConnection.readTimeout = 5000

    def credsStatus = credsConnection.responseCode
    def credsBody = readResponseBody(credsConnection)
    credsConnection.disconnect()
    if (credsStatus == 403) {
        session.remove('vault_token_jellyfin')
        session.remove('vault_token_expiry_jellyfin')
        throw new IllegalStateException('Vault token rejected for Jellyfin lookup (HTTP 403)')
    }
    if (credsStatus < 200 || credsStatus >= 300) {
        throw new IllegalStateException("Vault credential lookup failed with HTTP ${credsStatus}")
    }

    def credsJson = new JsonSlurper().parseText(credsBody)
    def jellyfinPassword = credsJson?.data?.data?.password as String
    if (jellyfinPassword == null || jellyfinPassword.isEmpty()) {
        throw new IllegalStateException('Vault response is missing password')
    }

    attributes.jellyfin_credentials = [username: username, password: jellyfinPassword]
    return next.handle(context, request)
} catch (Exception e) {
    logger.error('[VaultCredentialFilterJellyfin] Failed to fetch Jellyfin credentials from Vault', e)
    def errorResponse = new Response(Status.INTERNAL_SERVER_ERROR)
    errorResponse.headers.put('Content-Type', ['text/html'])
    errorResponse.entity.setString('<html><body><h2>Unable to retrieve Jellyfin credentials from Vault. Please try again later.</h2></body></html>')
    return errorResponse
}
