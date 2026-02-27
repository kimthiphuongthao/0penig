# 🕵️ Adversarial Review Report: SSO Lab

**Reviewed by:** BMAD Cynical Reviewer  
**Target:** SSO Lab Architecture & Configuration (`01-wordpress.json`, `CredentialInjector.groovy`, HA Setup)

## TL;DR
The system "works" for a happy-path lab environment, but it's a house of cards. The architecture leaks credentials, relies on brittle assumptions about WordPress login mechanics, violates basic OpenIG best practices, and completely ignores edge cases. If this goes to production, it will break immediately.

---

## 🛑 Critical Security & Architecture Flaws

1. **Credential Replay via Plaintext HTTP GET/POST**
   `CredentialInjector.groovy` fetches credentials from a local CSV and POSTs them to `http://wordpress/wp-login.php`. It transmits cleartext passwords over the internal network without HTTPS. Worse, if the request fails, it silently eats the exception and logs it, leaving the user confused.
2. **Missing Rate Limiting / Brute Force Amplification**
   Keycloak is acting as the front door, but `CredentialInjector.groovy` performs an automated login storm against WordPress for every new session. If Keycloak users are compromised or test scripts run amok, OpenIG will DDoS the legacy WordPress instance with `wp-login.php` POSTs.
3. **Insecure Storage of `wp_session_cookies` in `JwtSession`**
   The groovy script grabs *all* `Set-Cookie` headers from the WordPress login response and blindly stuffs them into the OpenIG session (`session['wp_session_cookies']`). This expands the JwtSession size massively (which has a hard 4KB browser limit). If WP returns too many cookies, the JWT will truncate or fail to encrypt, breaking the SSO entirely.
4. **Ignored WordPress Nonce/CSRF Protections**
   The script assumes `wp-login.php` only needs `log` and `pwd`. Modern WordPress installations often require login nonces or CSRF tokens set securely by the server on initial page load. This script bypasses form loading and POSTs directly, which will break if WP security plugins (like Wordfence) are installed.
5. **Session Desync (Keycloak Logout != WP Logout)**
   There is zero logic to handle Single Logout (SLO). If a user logs out of Keycloak, their OpenIG session might die, but the `wordpress_logged_in` cookie injected into the browser/proxy remains valid until it expires. The user is still logged into WordPress.

---

## ⚠️ Configuration & Logic Weaknesses

6. **Brittle FileAttributesFilter Mapping**
   `WpCredentialLookup` searches `credentials.csv` using the mapped key. However, the JSON config omits the `fields` array definition. It's relying entirely on implicit column ordering. If someone adds a column to the CSV, the entire SSO mapping collapses instantly.
7. **Dangerous Terminal Handler `ClientHandler`**
   The target route uses `ClientHandler` instead of `ReverseProxyHandler`. `ClientHandler` does not automatically rewrite `Host`, `X-Forwarded-For`, or `X-Forwarded-Proto` headers. WordPress will think all traffic is coming from the OpenIG internal IP, breaking URL generation and audit logs within WordPress.
8. **Hardcoded Infrastructure Credentials**
   The `01-wordpress.json` uses `"clientSecret": "openig-client-secret"` in plaintext. 
9. **Misconfigured HA Logging & Auditability**
   OpenIG by default doesn't log standard HTTP access logs for proxied requests unless a `DispatchHandler` or explicit log filter is added. The `TEST_GUIDE.md` relies on `grep "GET /wp-admin"` in OpenIG logs, which failed during automated testing because it simply doesn't exist.
10. **Exception Eating in Groovy**
    In `CredentialInjector.groovy`, Step 3's `try/catch` block catches `Exception e` and just logs it. It explicitly notes: `// Non-fatal: continue, WordPress will show its login page`. This is a terrible UX. If the SSO binding fails, the user is suddenly presented with a legacy login screen they don't have the password for, completely defeating the purpose of SSO. It should throw an HTTP 500 or redirect to a custom error page.

---
*End of cynical review. Fix these before you even think about calling this "done".*
