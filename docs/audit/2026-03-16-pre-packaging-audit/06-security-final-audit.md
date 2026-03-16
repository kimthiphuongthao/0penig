# Task 4: Security Final Audit — Post-Fix Phase

**Agent:** security-reviewer (Opus, READ-ONLY)
**Date:** 2026-03-16
**Scope:** All OpenIG gateway code, nginx, docker-compose, Vault across 3 stacks
**Risk Level:** MEDIUM

---

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 1 |
| HIGH | 5 |
| MEDIUM | 7 |
| LOW | 4 |

---

## CRITICAL

### S-1: App Session Tokens in JwtSession Cookie Over HTTP
**Files:** `CredentialInjector.groovy:96`, `JellyfinTokenInjector.groovy:95-97`, `RedmineCredentialInjector.groovy:186`
**Category:** A02 + A04
**Issue:** FIX-09 removed Vault tokens and phpMyAdmin creds, but WordPress cookies (`wp_session_cookies`), Jellyfin token (`jellyfin_token`), and Redmine cookies (`redmine_session_cookies`) remain in JwtSession. Over HTTP, the encrypted cookie transits in cleartext. If intercepted + decrypted with sharedSecret → full app impersonation.
**Remediation:** Move to Redis server-side storage, or document as lab limitation requiring TLS.

---

## HIGH

### S-2: Redis Without Authentication (All Stacks)
**Category:** A05
**Files:** `stack-{a,b,c}/docker-compose.yml` — redis-server without `--requirepass`
**Blast radius:** Any Docker network process can read/write blacklist keys.
**Fix:** Add `--requirepass` + AUTH in Groovy scripts.

### S-3: Secrets in docker-compose.yml Committed to Git
**Category:** A02
**Files:** All 3 docker-compose.yml — JWT_SHARED_SECRET, OIDC_CLIENT_SECRET, KEYSTORE_PASSWORD
**Fix:** Use `.env` file (gitignored).

### S-4: vault/keys/ Not in .gitignore
**Category:** A02
**Risk:** One `git add .` commits unseal keys + admin tokens.
**Fix:** Add `**/vault/keys/` to .gitignore.

### S-5: Redmine Port 3000 Exposed to Host (Stack B)
**Category:** A01
**File:** `stack-b/docker-compose.yml:163-164`
**Blast radius:** Direct Redmine access bypasses all SSO/SLO controls.
**Fix:** Remove `ports: - "3000:3000"`.

### S-6: SloHandler Missing try-catch (Stacks A + C)
**Category:** A04 + A07
**Files:** SloHandler.groovy (A), SloHandlerGrafana.groovy (C), SloHandlerPhpMyAdmin.groovy (C)
**Blast radius:** Session cleared but Keycloak logout incomplete → SSO session remains active.
**Fix:** Wrap in try-catch (follow SloHandlerRedmine pattern).

---

## MEDIUM (7 items)

| # | Finding | Category |
|---|---------|----------|
| S-7 | No security response headers on nginx (X-Frame-Options, CSP, etc.) | A05 |
| S-8 | JwtSession cookies lack Secure/SameSite flags | A07 |
| S-9 | Weak OIDC client secrets Stack C ("secret-c") | A07 |
| S-10 | OpenIG containers run as root (Stacks A/B) | A05 |
| S-11 | Vault TLS disabled + UI enabled | A02 |
| S-12 | Vault UI enabled (accessible from Docker network) | A05 |
| S-13 | Hardcoded passwords in vault-bootstrap.sh | A02 |

---

## LOW (4 items)

| # | Finding |
|---|---------|
| S-14 | Stack C nginx missing proxy timeout configuration |
| S-15 | Stack C nginx missing proxy buffer configuration |
| S-16 | Empty dead code file (App1ResponseRewriter.groovy) |
| S-17 | Inconsistent Keycloak URL configuration (Stack C hardcoded vs B env var) |

---

## OWASP Top 10 Coverage

| Category | Status | Notes |
|----------|--------|-------|
| A01 - Broken Access Control | 1 FINDING | Redmine port 3000 exposed |
| A02 - Cryptographic Failures | 4 FINDINGS | Secrets in git, Vault TLS, vault/keys, session tokens |
| A03 - Injection | **PASS** | RESP length-prefixed, XSS escaped |
| A04 - Insecure Design | 2 FINDINGS | Session tokens in cookie, SloHandler try-catch |
| A05 - Security Misconfiguration | 5 FINDINGS | Headers, root, Vault UI, timeouts, dead code |
| A06 - Vulnerable Components | NOT AUDITED | No package manager |
| A07 - Auth Failures | 2 FINDINGS | Weak secrets, cookie flags |
| A08 - Integrity Failures | **PASS** | Idempotent bootstraps |
| A09 - Logging Failures | **PASS** | Vault audit, Groovy logging, id_token_hint redacted |
| A10 - SSRF | **PASS** | No user-supplied URLs for outbound |

---

## Security Checklist

- [x] No hardcoded secrets in Groovy scripts or route files (FIX-06)
- [ ] Secrets in docker-compose.yml committed to git
- [ ] vault/keys/ not in .gitignore
- [x] Backchannel logout JWT fully validated (RS256, iss, aud, exp, iat, events, sid)
- [x] Redis RESP commands use parameterized key sizes
- [x] Fail-closed on Redis errors (FIX-03/04)
- [x] OAuth2ClientFilter on all routes
- [ ] Redmine port 3000 bypasses SSO
- [x] /openig admin endpoint returns 403
- [x] Untrusted headers stripped at nginx
- [x] Vault audit logging enabled
- [x] admin.json set to PRODUCTION mode
- [ ] Redis has no authentication
- [ ] No security response headers
- [ ] JwtSession cookies lack Secure/SameSite
- [ ] SloHandler missing try-catch (A, C)
- [x] phpMyAdmin/Grafana sensitive material removed from JwtSession (FIX-09)
- [ ] WP/Jellyfin/Redmine session tokens still in JwtSession
