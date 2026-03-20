import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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

def buildBadGatewayResponse = { String message ->
    Response response = new Response(Status.BAD_GATEWAY)
    response.headers.put('Content-Type', ['text/html'])
    response.entity.setString("<html><body><h2>${message}</h2></body></html>")
    response
}

def readRequiredUserInfoField = { Map userInfo, String fieldName ->
    String value = userInfo != null ? (userInfo[fieldName] as String) : null
    if (value == null || value.trim().isEmpty()) {
        throw new IllegalStateException("Missing ${fieldName} in OpenID user_info")
    }
    value.trim()
}

String resolvedAppDisplayName = 'application'

try {
    String configuredAppDisplayName = binding.hasVariable('appDisplayName') ? (appDisplayName as String) : 'application'
    String configuredSecretPathPrefix = binding.hasVariable('secretPathPrefix') ? (secretPathPrefix as String) : 'redmine-creds'
    String configuredLookupUserInfoField = binding.hasVariable('lookupUserInfoField') ? (lookupUserInfoField as String) : 'email'
    String configuredCredentialAttributeName = binding.hasVariable('credentialAttributeName') ? (credentialAttributeName as String) : 'app_credentials'
    String configuredCredentialUsernameSource = binding.hasVariable('credentialUsernameSource') ? (credentialUsernameSource as String) : 'secret'
    String configuredCredentialUsernameField = binding.hasVariable('credentialUsernameField') ? (credentialUsernameField as String) : 'username'
    String configuredCredentialPasswordField = binding.hasVariable('credentialPasswordField') ? (credentialPasswordField as String) : 'password'
    String configuredAttributeUsernameField = binding.hasVariable('attributeUsernameField') ? (attributeUsernameField as String) : 'username'
    String configuredAttributePasswordField = binding.hasVariable('attributePasswordField') ? (attributePasswordField as String) : 'password'
    String configuredUsernameUserInfoField = binding.hasVariable('usernameUserInfoField') ? (usernameUserInfoField as String) : 'preferred_username'

    resolvedAppDisplayName = configuredAppDisplayName.trim()
    String secretPathPrefix = configuredSecretPathPrefix.trim()
    String lookupUserInfoField = configuredLookupUserInfoField.trim()
    String credentialAttributeName = configuredCredentialAttributeName.trim()
    String credentialUsernameSource = configuredCredentialUsernameSource.trim().toLowerCase()
    String credentialUsernameField = configuredCredentialUsernameField.trim()
    String credentialPasswordField = configuredCredentialPasswordField.trim()
    String attributeUsernameField = configuredAttributeUsernameField.trim()
    String attributePasswordField = configuredAttributePasswordField.trim()
    String usernameUserInfoField = configuredUsernameUserInfoField.trim()

    if (!(credentialUsernameSource in ['secret', 'user_info'])) {
        throw new IllegalStateException("Unsupported credentialUsernameSource: ${credentialUsernameSource}")
    }

    String vaultAddr = System.getenv('VAULT_ADDR') ?: 'http://vault:8200'
    String vaultRoleIdFile = System.getenv('VAULT_ROLE_ID_FILE')
    String vaultSecretIdFile = System.getenv('VAULT_SECRET_ID_FILE')

    def userInfo = attributes.openid != null ? attributes.openid['user_info'] : null
    String secretLookupValue = readRequiredUserInfoField(userInfo, lookupUserInfoField)
    String resolvedUsername = credentialUsernameSource == 'user_info' ? readRequiredUserInfoField(userInfo, usernameUserInfoField) : null

    long nowEpochSeconds = (long)(System.currentTimeMillis() / 1000)
    def tokenEntry = globals.compute('vault_token') { key, existing ->
        if (existing != null && existing.expiry > nowEpochSeconds) {
            return existing
        }

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
        long leaseDuration = (loginJson?.auth?.lease_duration ?: 0) as long
        if (!newVaultToken?.trim()) {
            throw new IllegalStateException('Vault auth.client_token is missing in response')
        }

        return [token: newVaultToken, expiry: nowEpochSeconds + leaseDuration]
    }
    String vaultToken = tokenEntry.token as String

    String encodedLookupValue = URLEncoder.encode(secretLookupValue, 'UTF-8').replace('+', '%20')
    HttpURLConnection credsConnection = (HttpURLConnection) new URL("${vaultAddr}/v1/secret/data/${secretPathPrefix}/${encodedLookupValue}").openConnection()
    credsConnection.requestMethod = 'GET'
    credsConnection.setRequestProperty('Accept', 'application/json')
    credsConnection.setRequestProperty('X-Vault-Token', vaultToken)
    credsConnection.connectTimeout = 5000
    credsConnection.readTimeout = 5000

    int credsStatus = credsConnection.responseCode
    String credsBody = readResponseBody(credsConnection)
    credsConnection.disconnect()
    if (credsStatus == 403) {
        globals.remove('vault_token')
        return buildBadGatewayResponse('Vault token expired. Please retry.')
    }
    if (credsStatus < 200 || credsStatus >= 300) {
        throw new IllegalStateException("Vault credential lookup failed with HTTP ${credsStatus}")
    }

    def credsJson = new JsonSlurper().parseText(credsBody)
    def credsData = credsJson?.data?.data ?: [:]
    String resolvedPassword = credsData[credentialPasswordField] as String
    if (resolvedPassword == null || resolvedPassword.trim().isEmpty()) {
        throw new IllegalStateException("Vault response is missing ${credentialPasswordField}")
    }

    if (credentialUsernameSource == 'secret') {
        resolvedUsername = credsData[credentialUsernameField] as String
        if (resolvedUsername == null || resolvedUsername.trim().isEmpty()) {
            throw new IllegalStateException("Vault response is missing ${credentialUsernameField}")
        }
    }

    def credentialMap = [:]
    if (attributeUsernameField) {
        credentialMap[attributeUsernameField] = resolvedUsername.trim()
    }
    credentialMap[attributePasswordField] = resolvedPassword.trim()

    attributes[credentialAttributeName] = credentialMap
    return next.handle(context, request)
} catch (Exception e) {
    logger.error("[VaultCredentialFilter] Failed to fetch ${resolvedAppDisplayName} credentials from Vault", e)
    return buildBadGatewayResponse("Unable to retrieve ${resolvedAppDisplayName} credentials from Vault. Please try again later.")
}
