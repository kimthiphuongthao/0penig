# Task 4: Security Final Audit — Post-Fix Phase

**Agent:** security-reviewer (Opus, READ-ONLY)
**Date:** 2026-03-16
**Scope:** All OpenIG gateway code, nginx, docker-compose, Vault across 3 stacks
**Risk Level:** MEDIUM

> Update 2026-03-17: Pattern Consolidation Steps 1-6 are complete. HIGH finding S-6 was resolved in Step 4 (`3b8a6d8`), Step 5 resolved S-4 (`5ae657e`), S-5 (`aaf66d5`), S-15 (`aaf66d5`), and S-16 (`aaf66d5`), STEP-02 resolved S-9 (`37672ed`), and STEP-03 resolved S-3 (`b738577`). Follow-up 2026-03-18: APP5 was re-rotated to a strong alphanumeric-only secret after confirming OpenIG `OAuth2ClientFilter` does not URL-encode `client_secret`. Historical severity counts below remain the original audit snapshot.

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
**Status:** RESOLVED 2026-03-17 in STEP-03 (`b738577`).

### S-4: vault/keys/ Not in .gitignore
**Category:** A02
**Risk:** One `git add .` commits unseal keys + admin tokens.
**Fix:** Add `**/vault/keys/` to .gitignore.
**Status:** RESOLVED 2026-03-17 in Step 5 (`5ae657e`).

### S-5: Redmine Port 3000 Exposed to Host (Stack B)
**Category:** A01
**File:** `stack-b/docker-compose.yml:163-164`
**Blast radius:** Direct Redmine access bypasses all SSO/SLO controls.
**Fix:** Remove `ports: - "3000:3000"`.
**Status:** RESOLVED 2026-03-17 in Step 5 (`aaf66d5`).

### S-6: SloHandler Missing try-catch (Stacks A + C)
**Category:** A04 + A07
**Files:** SloHandler.groovy (A), SloHandlerGrafana.groovy (C), SloHandlerPhpMyAdmin.groovy (C)
**Blast radius:** Session cleared but Keycloak logout incomplete → SSO session remains active.
**Fix:** Wrap in try-catch (follow SloHandlerRedmine pattern).
**Status:** RESOLVED 2026-03-17 in Pattern Consolidation Step 4 (`3b8a6d8`).

---

## MEDIUM (7 items)

| # | Finding | Category |
|---|---------|----------|
| S-7 | No security response headers on nginx (X-Frame-Options, CSP, etc.) | A05 |
| S-8 | JwtSession cookies lack Secure/SameSite flags | A07 |
| S-9 | Weak OIDC client secrets Stack C ("secret-c") — RESOLVED in STEP-02 (`37672ed`); APP5 re-validated 2026-03-18 with an OpenIG-compatible alphanumeric-only secret | A07 |
| S-10 | OpenIG containers run as root (Stacks A/B) | A05 |
| S-11 | Vault TLS disabled + UI enabled | A02 |
| S-12 | Vault UI enabled (accessible from Docker network) | A05 |
| S-13 | Hardcoded passwords in vault-bootstrap.sh | A02 |

---

## LOW (4 items)

| # | Finding |
|---|---------|
| S-14 | Stack C nginx missing proxy timeout configuration |
| S-15 | Stack C nginx missing proxy buffer configuration - RESOLVED in Step 5 (`aaf66d5`) |
| S-16 | Empty dead code file (App1ResponseRewriter.groovy) - RESOLVED in Step 5 (`aaf66d5`) |
| S-17 | Inconsistent Keycloak URL configuration (Stack C hardcoded vs B env var) |

---

## OWASP Top 10 Coverage

| Category | Status | Notes |
|----------|--------|-------|
| A01 - Broken Access Control | 1 historical finding | Redmine port 3000 exposure is now resolved in Step 5 |
| A02 - Cryptographic Failures | 4 historical findings | Secrets in git, Vault TLS, session tokens remain; `vault/keys/` gitignore is resolved |
| A03 - Injection | **PASS** | RESP length-prefixed, XSS escaped |
| A04 - Insecure Design | 2 FINDINGS | Session tokens in cookie, SloHandler try-catch |
| A05 - Security Misconfiguration | 5 historical findings | Headers, root, Vault UI remain; Stack C proxy buffers and dead code item are resolved |
| A06 - Vulnerable Components | NOT AUDITED | No package manager |
| A07 - Auth Failures | 2 FINDINGS | Weak secrets, cookie flags |
| A08 - Integrity Failures | **PASS** | Idempotent bootstraps |
| A09 - Logging Failures | **PASS** | Vault audit, Groovy logging, id_token_hint redacted |
| A10 - SSRF | **PASS** | No user-supplied URLs for outbound |

---

## Security Checklist

- [x] No hardcoded secrets in Groovy scripts or route files (FIX-06)
- [x] Secrets in docker-compose.yml moved to gitignored `.env` files via STEP-03 (`b738577`)
- [x] `vault/keys/` gitignored via Step 5 (`5ae657e`)
- [x] Backchannel logout JWT fully validated (RS256, iss, aud, exp, iat, events, sid)
- [x] Redis RESP commands use parameterized key sizes
- [x] Fail-closed on Redis errors (FIX-03/04)
- [x] OAuth2ClientFilter on all routes
- [x] Redmine port 3000 removed in Step 5 (`aaf66d5`)
- [x] /openig admin endpoint returns 403
- [x] Untrusted headers stripped at nginx
- [x] Vault audit logging enabled
- [x] admin.json set to PRODUCTION mode
- [ ] Redis has no authentication
- [ ] No security response headers
- [ ] JwtSession cookies lack Secure/SameSite
- [x] SloHandler try-catch added via Pattern Consolidation Step 4 (`3b8a6d8`)
- [x] phpMyAdmin/Grafana sensitive material removed from JwtSession (FIX-09)
- [ ] WP/Jellyfin/Redmine session tokens still in JwtSession
