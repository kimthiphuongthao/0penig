import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.forgerock.http.protocol.Response
import org.forgerock.http.protocol.Status
import java.util.concurrent.ConcurrentHashMap
import groovy.transform.Field

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Field static final ConcurrentHashMap<String, Object> vaultCacheDotnet = new ConcurrentHashMap<>()
@Field static final ConcurrentHashMap<String, List<String>> dotnetSessionCache = new ConcurrentHashMap<>()

def readBody = { HttpURLConnection conn ->
    InputStream stream
    try {
        stream = conn.inputStream
    } catch (IOException ignored) {
        stream = conn.errorStream
    }

    if (stream == null) {
        return ""
    }

    stream.withCloseable { it.getText("UTF-8") }
}

def getSetCookieHeaders = { HttpURLConnection conn ->
    conn.headerFields
        ?.findAll { key, _ -> key != null && key.equalsIgnoreCase("Set-Cookie") }
        ?.collectMany { _, values -> values ?: [] } ?: []
}

def extractCookiePair = { String setCookie ->
    setCookie?.split(";", 2)?.first()?.trim()
}

try {
    String username = attributes?.openid?.get("user_info")?.get("preferred_username") as String
    if (!username?.trim()) {
        throw new IllegalStateException("preferred_username is missing")
    }

    String vaultAddr = System.getenv("VAULT_ADDR")
    String roleIdFile = System.getenv("VAULT_ROLE_ID_FILE")
    String secretIdFile = System.getenv("VAULT_SECRET_ID_FILE")
    if (!vaultAddr?.trim() || !roleIdFile?.trim() || !secretIdFile?.trim()) {
        throw new IllegalStateException("Vault environment variables are missing")
    }

    String roleId = new File(roleIdFile).getText("UTF-8").trim()
    String secretId = new File(secretIdFile).getText("UTF-8").trim()
    if (!roleId || !secretId) {
        throw new IllegalStateException("Vault AppRole credentials are empty")
    }

    long nowEpoch = (System.currentTimeMillis() / 1000L) as long
    String vaultToken = vaultCacheDotnet.get("vault_b_token") as String
    long vaultTokenExpiry = (vaultCacheDotnet.get("vault_b_token_expiry") ?: 0L) as long

    if (!vaultToken || vaultTokenExpiry <= nowEpoch) {
        HttpURLConnection loginConn = (HttpURLConnection) new URL("${vaultAddr}/v1/auth/approle/login").openConnection()
        loginConn.requestMethod = "POST"
        loginConn.doOutput = true
        loginConn.setRequestProperty("Content-Type", "application/json")

        String loginPayload = JsonOutput.toJson([
            role_id  : roleId,
            secret_id: secretId
        ])

        loginConn.outputStream.withCloseable { it.write(loginPayload.getBytes("UTF-8")) }

        int loginCode = loginConn.responseCode
        String loginBody = readBody(loginConn)
        if (loginCode < 200 || loginCode >= 300) {
            throw new IllegalStateException("Vault login failed with status ${loginCode}: ${loginBody}")
        }

        def loginJson = new JsonSlurper().parseText(loginBody ?: "{}")
        vaultToken = loginJson?.auth?.client_token as String
        long leaseDuration = (loginJson?.auth?.lease_duration ?: 300L) as long
        if (!vaultToken) {
            throw new IllegalStateException("Vault login did not return client token")
        }

        long refreshedEpoch = (System.currentTimeMillis() / 1000L) as long
        long adjustedLease = Math.max(leaseDuration - 30L, 30L)
        vaultCacheDotnet.put("vault_b_token", vaultToken)
        vaultCacheDotnet.put("vault_b_token_expiry", refreshedEpoch + adjustedLease)
    }

    String encodedUser = URLEncoder.encode(username, "UTF-8")
    HttpURLConnection secretConn = (HttpURLConnection) new URL("${vaultAddr}/v1/secret/data/dotnet-creds/${encodedUser}").openConnection()
    secretConn.requestMethod = "GET"
    secretConn.setRequestProperty("X-Vault-Token", vaultToken)

    int secretCode = secretConn.responseCode
    String secretBody = readBody(secretConn)
    if (secretCode == 403) {
        vaultCacheDotnet.remove("vault_b_token")
        vaultCacheDotnet.remove("vault_b_token_expiry")
        Response response = new Response(Status.BAD_GATEWAY)
        response.headers["Content-Type"] = ["text/html"]
        response.entity = "<html><body>SSO credential injection failed.</body></html>"
        return response
    }
    if (secretCode < 200 || secretCode >= 300) {
        throw new IllegalStateException("Vault secret read failed with status ${secretCode}: ${secretBody}")
    }

    def secretJson = new JsonSlurper().parseText(secretBody ?: "{}")
    String dotnetUsername = secretJson?.data?.data?.username as String
    String dotnetPassword = secretJson?.data?.data?.password as String
    if (!dotnetUsername || !dotnetPassword) {
        throw new IllegalStateException("Vault secret is missing username/password")
    }

    List<String> dotnetSessionSetCookies = dotnetSessionCache.get(username) ?: []

    if (dotnetSessionSetCookies.isEmpty()) {
        String baseUrl = (System.getenv("DOTNET_APP_BASE_URL") ?: "http://dotnet-app:5000")

        HttpURLConnection loginPageConn = (HttpURLConnection) new URL("${baseUrl}/Account/Login").openConnection()
        loginPageConn.requestMethod = "GET"
        loginPageConn.instanceFollowRedirects = true

        int loginPageCode = loginPageConn.responseCode
        String loginPageBody = readBody(loginPageConn)
        if (loginPageCode < 200 || loginPageCode >= 300) {
            throw new IllegalStateException("Dotnet login page failed with status ${loginPageCode}: ${loginPageBody}")
        }

        String requestVerificationToken
        def tokenNameFirst = (loginPageBody =~ /(?is)<input[^>]*name=["']__RequestVerificationToken["'][^>]*value=["']([^"']+)["'][^>]*>/)
        if (tokenNameFirst.find()) {
            requestVerificationToken = tokenNameFirst.group(1)
        } else {
            def tokenValueFirst = (loginPageBody =~ /(?is)<input[^>]*value=["']([^"']+)["'][^>]*name=["']__RequestVerificationToken["'][^>]*>/)
            if (tokenValueFirst.find()) {
                requestVerificationToken = tokenValueFirst.group(1)
            }
        }
        if (!requestVerificationToken) {
            throw new IllegalStateException("Could not find __RequestVerificationToken on login page")
        }

        List<String> antiForgerySetCookies = getSetCookieHeaders(loginPageConn)
            .findAll { it?.toLowerCase()?.contains("antiforgery") }
        List<String> antiForgeryCookiePairs = antiForgerySetCookies
            .collect { extractCookiePair(it) }
            .findAll { it }
        if (antiForgeryCookiePairs.isEmpty()) {
            throw new IllegalStateException("Could not find antiforgery cookies on login page")
        }

        HttpURLConnection loginPostConn = (HttpURLConnection) new URL("${baseUrl}/Account/Login").openConnection()
        loginPostConn.requestMethod = "POST"
        loginPostConn.instanceFollowRedirects = false
        loginPostConn.doOutput = true
        loginPostConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        loginPostConn.setRequestProperty("Cookie", antiForgeryCookiePairs.join("; "))

        String formBody = [
            "Username=${URLEncoder.encode(dotnetUsername, "UTF-8")}",
            "Password=${URLEncoder.encode(dotnetPassword, "UTF-8")}",
            "__RequestVerificationToken=${URLEncoder.encode(requestVerificationToken, "UTF-8")}"
        ].join("&")
        loginPostConn.outputStream.withCloseable { it.write(formBody.getBytes("UTF-8")) }

        int loginPostCode = loginPostConn.responseCode
        String loginPostBody = readBody(loginPostConn)
        if (loginPostCode != 302) {
            throw new IllegalStateException("Dotnet login POST expected 302 but got ${loginPostCode}: ${loginPostBody}")
        }

        List<String> loginPostSetCookies = getSetCookieHeaders(loginPostConn)
        LinkedHashSet<String> combinedSetCookies = new LinkedHashSet<>()
        combinedSetCookies.addAll(antiForgerySetCookies)
        combinedSetCookies.addAll(loginPostSetCookies)

        dotnetSessionSetCookies = combinedSetCookies.findAll { it } as List<String>
        if (dotnetSessionSetCookies.isEmpty()) {
            throw new IllegalStateException("Dotnet login did not return session cookies")
        }

        dotnetSessionCache.put(username, dotnetSessionSetCookies)
    }

    List<String> cookiePairs = dotnetSessionSetCookies
        .collect { extractCookiePair(it) }
        .findAll { it }
    if (cookiePairs.isEmpty()) {
        throw new IllegalStateException("No valid cookies available for upstream request")
    }

    request.headers["Cookie"] = [cookiePairs.join("; ")]
    return next.handle(context, request)
} catch (Exception e) {
    logger.error("[DotnetCredentialInjector] Failed", e)
    Response response = new Response(Status.BAD_GATEWAY)
    response.headers["Content-Type"] = ["text/html"]
    response.entity = "<html><body>SSO credential injection failed.</body></html>"
    return response
}
