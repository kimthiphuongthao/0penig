# Stack B — OpenIG Code & Security Review
**Date:** 2026-03-14
**Scope:** stack-b/openig_home/scripts/groovy/*.groovy, stack-b/openig_home/config/config.json, stack-b/openig_home/config/routes/*.json
**Sources:** subagent security-reviewer, subagent code-reviewer, Codex security review, Codex code review

---

## Summary

Stack B (Redmine + Jellyfin, 2-node HA) shares all core issues found in Stack A, plus introduces a **critical new Stack-B-specific finding**: Jellyfin logout reads the wrong OIDC session namespace, causing logout to silently fail. Confirmed strengths: BackchannelLogoutHandler JWT validation is robust (RS256, JWKS, full claims checking).

**[UPDATED 2026-03-17]** Current repo state beyond this historical review:
- F1 resolved in STEP-03 (`b738577`) — compose secrets moved to `.env` / runtime injection.
- F2 resolved in live state (`9cbf71a`) — blacklist TTL is aligned with the current `JwtSession` timeout.
- F3 resolved in FIX-03 (`278a29c`) — blacklist checks now fail closed.
- F5 resolved in FIX-01 (`a3cb6c3`) and preserved through Step 4 (`3b8a6d8`).
- F7 resolved in FIX-08 (`7fc73ba`) plus Step 5 env rollout (`aaf66d5`) — redirects now use pinned origins.
- F9 resolved in FIX-03/04 (`278a29c`) — Redis socket connect/read timeouts added.
- F10 resolved in FIX-05 (`9b770cd`) — internal backchannel failures now return `500`.
- F11 was verified in FIX-12 (`9086aaf`) — write/read paths now use consistent `sid ?: sub` fallback.
- F6 is only partially mitigated: Vault tokens were removed from `JwtSession` in FIX-09, but Jellyfin token material remains a documented pattern limitation.

---

## Confirmed Strengths

| Strength | Evidence |
|---|---|
| BackchannelLogoutHandler JWT validation: RS256 + JWKS + iss/aud/events/iat/exp | BackchannelLogoutHandler.groovy — full validation chain |
| Route ordering correct: logout/backchannel routes registered before app routes | routes/01-jellyfin.json, routes/02-redmine.json |
| Redmine/Jellyfin credential injection self-heals on 401 | RedmineCredentialInjector.groovy, JellyfinCredentialInjector.groovy |

---

## Findings

### F1 — Hardcoded Secrets in config.json and Route Files
**Severity:** HIGH
**Confirmed by:** subagent security, subagent code, Codex security, Codex code (4/4)

**Description:** JwtSession sharedSecret and OIDC clientSecrets are hardcoded in plaintext in config.json and route files. Secrets exposed to anyone with filesystem access.

**Evidence:**
- stack-b/openig_home/config/config.json — sharedSecret hardcoded
- stack-b/openig_home/config/routes/01-jellyfin.json — clientSecret hardcoded
- stack-b/openig_home/config/routes/02-redmine.json — clientSecret hardcoded

**Recommendation:** Externalize all secrets via VaultCredentialFilter or environment variable injection. Do not store any secret in config files committed to VCS.

---

### F2 — Revocation TTL < JwtSession Timeout
**Severity:** HIGH
**Confirmed by:** subagent security, subagent code, Codex security, Codex code (4/4)

**Description:** BackchannelLogoutHandler stores revoked session SIDs in Redis with TTL=3600s, but JwtSession.sessionTimeout is 8h (28800s). A revoked session cookie remains valid long after the Redis revocation entry expires — revocation is bypassed after 1 hour.

**Evidence:**
- BackchannelLogoutHandler.groovy:317 — Redis SET with TTL 3600
- stack-b/openig_home/config/config.json:15 — sessionTimeout: 28800

**Recommendation:** Set Redis revocation TTL = sessionTimeout (28800s), or use absolute expiry aligned to JWT exp claim.

---

### F3 — Revocation Fail-Open on Redis Errors
**Severity:** HIGH
**Confirmed by:** subagent security, subagent code, Codex security, Codex code (4/4)

**Description:** SessionBlacklistFilter, SessionBlacklistFilterApp3, SessionBlacklistFilterApp4 all catch Redis exceptions and call next.handle(context, request) — allowing revoked sessions through on Redis failure. This is fail-open, not fail-closed.

**Evidence:**
- SessionBlacklistFilter.groovy — catch block calls next.handle()
- SessionBlacklistFilterApp3.groovy — same pattern
- SessionBlacklistFilterApp4.groovy — same pattern

**[UPDATED 2026-03-17]** Status as of Pattern Consolidation Steps 2-4: `SessionBlacklistFilterApp3.groovy` and `SessionBlacklistFilterApp4.groovy` are historical references only. Step 2 deleted both duplicate variants and consolidated Stack B revocation checks into the shared parameterized `SessionBlacklistFilter.groovy`. The fail-open finding itself remains open unless fixed separately.

**Recommendation:** On Redis error, fail-closed: return 503 or redirect to login. Log error with sufficient detail for alerting.

---

### F4 — HTTP Allowed / requireHttps: false
**Severity:** HIGH
**Confirmed by:** subagent security, subagent code, Codex security, Codex code (4/4)

**Description:** requireHttps is false across routes and config. JwtSession cookies and OIDC tokens can be transmitted over plaintext HTTP, enabling interception.

**Evidence:**
- stack-b/openig_home/config/config.json — requireHttps: false
- Route files — no HTTPS enforcement

**Recommendation:** Set requireHttps: true. Enforce HTTPS at nginx layer for all inbound traffic. Set cookies Secure flag.

---

### F5 — Jellyfin Logout Reads Wrong OIDC Namespace (Stack B-specific, Critical)
**Severity:** HIGH
**Confirmed by:** subagent security, Codex security, Codex code (3/4)

**Description:** SloHandlerJellyfin.groovy reads the OIDC session from namespace 'app3' (line 59-70), but Jellyfin's OAuth2ClientFilter is registered with clientId 'openig-client-b-app4' (routes/01-jellyfin.json:95). The id_token_hint will be null or from the wrong session, causing RP-initiated logout to silently fail — the user is not logged out from Keycloak.

**Evidence:**
- SloHandlerJellyfin.groovy:59-70 — reads contexts.attributes['openid-connect.app3']
- stack-b/openig_home/config/routes/01-jellyfin.json:95 — OAuth2ClientFilter clientId: 'openig-client-b-app4'

**Recommendation:** Change SloHandlerJellyfin to read namespace 'app4' (matching the registered clientId). Verify id_token_hint is non-null before building logout URL.

---

### F6 — Sensitive Tokens Stored in Browser-Bound JwtSession Cookie
**Severity:** HIGH
**Confirmed by:** subagent security (1/4 — retain)

**Description:** Vault tokens and downstream app session cookies are stored inside the JwtSession (browser-side cookie). Compromise of sharedSecret exposes all session material, including Vault tokens. Long-lived cookie is a significant attack surface.

**Evidence:**
- stack-b/openig_home/config/config.json — JwtSession stores full session map
- VaultCredentialFilter writes Vault token into session

**Recommendation:** Minimize session footprint. Consider server-side session storage for sensitive tokens. Rotate sharedSecret periodically.

---

### F7 — Inbound Host Header Trusted for Redirect Construction
**Severity:** MEDIUM
**Confirmed by:** subagent security, Codex security, Codex code (3/4)

**Description:** SloHandlerJellyfin, SessionBlacklistFilterApp3, SessionBlacklistFilterApp4, and RedmineCredentialInjector use request.headers['Host'] or request.uri.host to build redirect URIs without validation. An attacker with control of the Host header can redirect victims to arbitrary URLs (open redirect / SSRF risk).

**Evidence:**
- SloHandlerJellyfin.groovy — uses request URI host for logout redirect
- SessionBlacklistFilterApp3.groovy — constructs redirect from inbound host
- RedmineCredentialInjector.groovy — uses inbound host

**[UPDATED 2026-03-17]** Status as of Pattern Consolidation Steps 2-4: the `SessionBlacklistFilterApp3.groovy` and `SessionBlacklistFilterApp4.groovy` mentions in this finding are historical references to the pre-Step-2 layout. Stack B now uses the shared parameterized `SessionBlacklistFilter.groovy`; the Host-derived redirect concern remains open.

**Recommendation:** Pin redirect base URLs as static configuration constants. Never build redirect targets from inbound request headers.

---

### F8 — Jellyfin Access Token Injected into Browser localStorage
**Severity:** MEDIUM
**Confirmed by:** subagent code, Codex security (2/4)

**Description:** JellyfinResponseRewriter injects the Jellyfin access token directly into browser localStorage via JavaScript. localStorage is accessible to any same-origin JavaScript (XSS risk) and persists across browser sessions.

**Evidence:**
- JellyfinResponseRewriter.groovy — injects token into localStorage

**Recommendation:** Use httpOnly session cookies for token storage instead of localStorage. Avoid injecting tokens into JavaScript-accessible storage.

---

### F9 — Redis Sockets Without Connect/Read Timeouts
**Severity:** MEDIUM
**Confirmed by:** subagent code, Codex security, Codex code (3/4)

**Description:** Raw Redis socket connections in SessionBlacklistFilter, SessionBlacklistFilterApp3, SessionBlacklistFilterApp4, and BackchannelLogoutHandler have no explicit connect or read timeout. A slow/unresponsive Redis will block OpenIG threads indefinitely, causing cascading latency or thread pool exhaustion.

**Evidence:**
- All blacklist filter files — new Socket() with no timeout set
- BackchannelLogoutHandler.groovy — Redis connection without timeout

**[UPDATED 2026-03-17]** Status as of Pattern Consolidation Steps 2-4: `SessionBlacklistFilterApp3.groovy` and `SessionBlacklistFilterApp4.groovy` were deleted in Step 2 during the shared `SessionBlacklistFilter.groovy` consolidation. The timeout concern still applies to the consolidated implementation until fixed.

**Recommendation:** Set socket.connect(timeout) and socket.setSoTimeout(readTimeout) explicitly (e.g., 200ms connect, 500ms read). Add circuit-breaker logic if Redis is unavailable.

---

### F10 — BackchannelLogoutHandler Returns 400 for Internal Failures
**Severity:** MEDIUM
**Confirmed by:** subagent code, Codex security, Codex code (3/4)

**Description:** BackchannelLogoutHandler returns HTTP 400 for internal/runtime failures (Redis errors, parsing exceptions) that are not client errors. Keycloak interprets 400 as permanent failure and may stop retrying backchannel logout delivery.

**Evidence:**
- BackchannelLogoutHandler.groovy — catch blocks return 400 regardless of error type

**Recommendation:** Return 500 for internal/infrastructure errors, 400 only for malformed/invalid logout tokens. This aligns with RFC 9126 backchannel logout spec.

---

### F11 — sid vs sub Mismatch in Backchannel Enforcement
**Severity:** MEDIUM
**Confirmed by:** Codex code (1/4 — retain for investigation)

**Description:** Some blacklist filter code paths may use sub (user subject) for revocation lookup when the backchannel logout JWT encodes the session as sid. If sid and sub are used inconsistently between write (BackchannelLogoutHandler) and read (SessionBlacklistFilter*), valid sessions may not be revoked.

**Evidence:**
- BackchannelLogoutHandler.groovy — writes by sid
- SessionBlacklistFilter*.groovy — verify read key matches sid, not sub

**Recommendation:** Audit write/read key consistency across all blacklist filter variants. Standardize on sid throughout.

---

## Source Comparison

| Finding | Subagent Security | Subagent Code | Codex Security | Codex Code |
|---|---|---|---|---|
| F1 Hardcoded secrets | ✅ | ✅ | ✅ | ✅ |
| F2 TTL mismatch | ✅ | ✅ | ✅ | ✅ |
| F3 Fail-open Redis | ✅ | ✅ | ✅ | ✅ |
| F4 HTTP allowed | ✅ | ✅ | ✅ | ✅ |
| F5 Jellyfin wrong namespace | ✅ | ❌ | ✅ | ✅ |
| F6 Tokens in JwtSession | ✅ | ❌ | ❌ | ❌ |
| F7 Host header trust | ✅ | ❌ | ✅ | ✅ |
| F8 localStorage injection | ❌ | ✅ | ✅ | ❌ |
| F9 Redis no timeout | ❌ | ✅ | ✅ | ✅ |
| F10 400 for internal errors | ❌ | ✅ | ✅ | ✅ |
| F11 sid vs sub mismatch | ❌ | ❌ | ❌ | ✅ |

**Coverage: Codex > Subagent** — Codex found F8, F9, F10, F11 more reliably; Subagent found F6 uniquely (valid finding).

---

## Priority Fix Order

1. **F5** — Fix Jellyfin logout namespace mismatch immediately (logout is silently broken)
2. **F1** — Externalize secrets via Vault (pre-production requirement)
3. **F2** — Align revocation TTL with sessionTimeout
4. **F3** — Fail-closed on Redis errors
5. **F4** — Enable HTTPS enforcement
6. **F7** — Pin redirect base URLs
7. **F8** — Replace localStorage with httpOnly cookie
8. **F9** — Add Redis socket timeouts
9. **F10** — Fix error response codes (400 → 500 for internal errors)
10. **F6** — Minimize JwtSession footprint
11. **F11** — Audit and standardize sid/sub key usage
