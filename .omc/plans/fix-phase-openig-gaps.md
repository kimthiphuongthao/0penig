# Fix Phase — OpenIG Gateway GAPs Implementation Plan

**Version:** 1.0
**Date:** 2026-03-14
**Source:** Cross-stack review summary + 3 stack reviews + Standard Gateway Pattern v1.0
**Scope:** All identified GAPs across Stack A, B, C
**Branch:** `feat/subdomain-test`

---

## How to Read This Plan

- **Cross-stack** = same logical fix applied independently to A, B, C (respect stack independence)
- **Stack-specific** = fix only applies to one stack
- **Confidence** = N/4 reviewers confirmed (low confidence items flagged for investigation first)
- **Design decision needed** = implementation choice required before Codex can start
- Each fix lists exact file paths, what changes, and acceptance criteria

---

## Priority 0 — Broken Functionality + Revocation Correctness

These fixes address active bugs and the most critical security gaps. Must be done first because all other fixes assume revocation works correctly.

---

### Batch 0 — Pre-Implementation Investigations (run ALL before committing to batch order)

These investigations must complete before implementation begins. Results may alter batch ordering.

| Investigation | Needed for | Owner |
|---|---|---|
| Decide fail-closed UX: 503 vs forced re-auth for revocation indeterminate state | FIX-03 | Design decision |
| Decide runtime secret source pattern + rollout order (HA desync risk) | FIX-06 | Design decision |
| Decide server-side adapter state store (Redis, OpenIG session, other) | FIX-09, FIX-11 | Design decision |
| Audit Stack B app3 vs app4 route/namespace ownership | FIX-01, FIX-12 | Codex read-only |
| Validate Stack A low-confidence adapter findings (reproduce before patching) | FIX-14, FIX-15 | Codex read-only |
| Verify PhpMyAdminCookieFilter wiring doesn't trigger known Token mismatch | FIX-10 | Codex read-only |

---

### FIX-01: Jellyfin Logout OIDC Namespace Mismatch ⚡ BROKEN NOW

| Attribute | Value |
|-----------|-------|
| Finding | B-F5 |
| Severity | HIGH |
| Confidence | 3/4 |
| Scope | Stack B only |
| Design decision | No |

**Problem:** `SloHandlerJellyfin.groovy` reads OIDC session from namespace `app3` (line 59-70), but `OAuth2ClientFilter` in `01-jellyfin.json:95` uses clientId `openig-client-b-app4`. RP-initiated logout silently fails — user is NOT logged out from Keycloak.

**Files to change:**
- `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy` — change namespace from `app3` → `app4` (or match whatever `openig-client-b-app4` resolves to)

**What to change:**
1. Find all references to `openid-connect.app3` in SloHandlerJellyfin.groovy
2. Replace with `openid-connect.app4` (matching the OAuth2ClientFilter clientId suffix)
3. Add null-check for `id_token_hint` before building end_session URL — if null, log warning and still invalidate local session

**Acceptance criteria:**
- [ ] Login as alice via Jellyfin SSO → click logout → Keycloak session is terminated (not just local)
- [ ] Cross-stack SLO works: logout from Jellyfin also logs out from Redmine
- [ ] If id_token is missing, local session is still invalidated (graceful degradation)

---

### FIX-02: Revocation TTL Alignment (Cross-Stack)

| Attribute | Value |
|-----------|-------|
| Finding | A-F3, B-F2, C-F2 |
| Severity | HIGH |
| Confidence | 3-4/4 |
| Scope | Cross-stack (A + B + C) |
| Design decision | Yes — see below |

**Problem:** `BackchannelLogoutHandler.groovy` writes Redis blacklist with `EX 3600` (1h), but `JwtSession.sessionTimeout` is `28800` (8h). After 1 hour, revoked sessions become valid again.

**Design decision needed:**
- Option A: Set Redis TTL = `sessionTimeout` (28800s) — simple, conservative
- Option B: Compute remaining session lifetime from JWT `exp` claim + clock skew — more precise but complex
- **Recommendation: Option A** for lab scope. Option B is a future hardening item.

**Files to change (per stack):**

| Stack | File | Current | Target |
|-------|------|---------|--------|
| A | `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy` (~line 305-310) | `EX 3600` | `EX 28800` |
| B | `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy` (~line 317) | `EX 3600` | `EX 28800` |
| C | `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy` (~line 304-318) | `EX 3600` | `EX 28800` |

**What to change:**
1. In each `BackchannelLogoutHandler.groovy`, find the Redis `SET ... EX 3600` command
2. Replace `3600` with `28800` (matching `sessionTimeout` in each stack's `config.json`)
3. Add a comment: `// TTL must be >= JwtSession.sessionTimeout (28800s = 8h)`

**Acceptance criteria:**
- [ ] Backchannel logout → Redis key TTL is 28800 (verify with `docker exec <redis> redis-cli TTL blacklist:<sid>`)
- [ ] Revoked session remains blocked for full 8h window (no premature expiry)
- [ ] All 3 stacks independently verified

---

### FIX-03: Revocation Fail-Closed on Redis Errors (Cross-Stack)

| Attribute | Value |
|-----------|-------|
| Finding | A-F2, B-F3, C-F3 |
| Severity | HIGH |
| Confidence | 4/4 |
| Scope | Cross-stack (A + B + C) |
| Design decision | Yes — see below |

**Problem:** All `SessionBlacklistFilter` variants catch Redis exceptions and call `next.handle()` — allowing revoked sessions through on Redis failure (fail-open).

**Design decision needed:**
- Option A: Return HTTP 503 (Service Unavailable) — cleanest, tells user "try again"
- Option B: Clear local session + redirect to login — forces re-authentication
- **Recommendation: Option B** — better UX, user re-authenticates transparently via Keycloak SSO

**Files to change:**

| Stack | Files |
|-------|-------|
| A | `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy` (~line 95-97) |
| A | `stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy` (~line 157-159) |
| B | `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy` (catch block) |
| B | `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp3.groovy` (catch block) |
| B | `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy` (catch block) |
| C | `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy` (~line 123-155) |

**What to change:**
1. In each file's catch block for Redis errors:
   - Instead of `return next.handle(context, request)` (fail-open)
   - Change to: invalidate local session (`session.clear()` or equivalent) + return redirect to login path
2. Log the Redis error at ERROR level (not just WARN) for alerting
3. Keep the try/catch structure — only change the catch behavior

**Acceptance criteria:**
- [ ] Stop Redis container → authenticated request → user is redirected to login (NOT proxied through)
- [ ] Start Redis back → normal operation resumes
- [ ] All 6 filter files across 3 stacks verified independently
- [ ] Error logs show Redis connection failure at ERROR level

---

### FIX-04: Redis Socket Timeouts (Cross-Stack)

| Attribute | Value |
|-----------|-------|
| Finding | A-§6, B-F9, C-F7 |
| Severity | MEDIUM |
| Confidence | 3/4 |
| Scope | Cross-stack (A + B + C) |
| Design decision | No — use recommended 200ms connect / 500ms read |

**Problem:** Raw `new Socket(host, port)` without timeouts. Slow/dead Redis pins OpenIG worker threads indefinitely.

**Files to change:** Same files as FIX-03 (all SessionBlacklistFilter variants + BackchannelLogoutHandler in each stack)

| Stack | Files (read path — SessionBlacklistFilter) | Files (write path — BackchannelLogoutHandler) |
|-------|---------------------------------------------|------------------------------------------------|
| A | `SessionBlacklistFilter.groovy`, `SessionBlacklistFilterApp2.groovy` | `BackchannelLogoutHandler.groovy` |
| B | `SessionBlacklistFilter.groovy`, `SessionBlacklistFilterApp3.groovy`, `SessionBlacklistFilterApp4.groovy` | `BackchannelLogoutHandler.groovy` |
| C | `SessionBlacklistFilter.groovy` | `BackchannelLogoutHandler.groovy` |

**What to change:**
1. Replace `new Socket(host, port)` with:
   ```groovy
   def socket = new Socket()
   socket.connect(new InetSocketAddress(host, port), 200)  // 200ms connect
   socket.setSoTimeout(500)  // 500ms read
   ```
2. Apply to BOTH read paths (SessionBlacklistFilter*) and write paths (BackchannelLogoutHandler)

**Acceptance criteria:**
- [ ] Simulate slow Redis (e.g., `iptables` drop) → OpenIG returns within 1s, not hanging
- [ ] Normal Redis operation unaffected by timeout settings
- [ ] All stacks verified

**Note:** This fix is naturally bundled with FIX-03 since they touch the same files.

---

### FIX-05: Backchannel Handler Error Code Fix (Cross-Stack B+C)

| Attribute | Value |
|-----------|-------|
| Finding | B-F10, C-F8 |
| Severity | MEDIUM |
| Confidence | 3/4 |
| Scope | Stack B + Stack C (Stack A needs verification) |
| Design decision | No |

**Problem:** `BackchannelLogoutHandler` returns HTTP 400 for internal/runtime failures (Redis errors, parsing exceptions). Keycloak may stop retrying backchannel logout delivery.

**Files to change:**

| Stack | File |
|-------|------|
| A | `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy` (verify — may have same pattern) |
| B | `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy` (catch blocks) |
| C | `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy` (~line 323-328) |

**What to change:**
1. Separate catch blocks by error type:
   - JWT validation errors (malformed token, bad signature, expired, wrong aud) → keep `400`
   - Redis errors, JWKS fetch errors, unexpected RuntimeException → change to `500`
2. Log the error class distinction for observability

**Acceptance criteria:**
- [ ] Send malformed logout JWT → returns 400
- [ ] Stop Redis → send valid logout JWT → returns 500 (not 400)
- [ ] Keycloak retries on 500 (verify in Keycloak logs)

---

## Priority 1 — Secret Externalization + Transport Security

These fixes address exposure risk. They don't break current functionality but are pre-production requirements.

---

### FIX-06: Externalize Gateway & OIDC Secrets to Environment Variables (Cross-Stack)

| Attribute | Value |
|-----------|-------|
| Finding | A-F1, B-F1, C-F1 |
| Severity | HIGH |
| Confidence | 4/4 |
| Scope | Cross-stack (A + B + C) |
| Design decision | Yes — see below |

**Problem:** `sharedSecret`, OIDC `clientSecret`, PKCS12 password all hardcoded in config.json and route files.

**Design decision needed:**
- Option A: Use Vault AppRole (already in place) to fetch secrets at OpenIG startup → complex, requires custom init script
- Option B: Use environment variables injected via docker-compose → simpler, standard for lab
- Option C: Use Docker secrets (file mount) → cleanest for production
- **Recommendation: Option B** for lab phase. Add env vars to docker-compose, reference via `${env['VAR_NAME']}` in OpenIG config (if OpenIG 6 supports) or via Groovy `System.getenv()`. **Codex must verify OpenIG 6 env var substitution support first.**

**Files to change (per stack):**

| Stack | Config files with hardcoded secrets |
|-------|-------------------------------------|
| A | `stack-a/openig_home/config/config.json` (sharedSecret, PKCS12 password) |
| B | `stack-b/openig_home/config/config.json` (sharedSecret) |
| B | `stack-b/openig_home/config/routes/01-jellyfin.json` (clientSecret) |
| B | `stack-b/openig_home/config/routes/02-redmine.json` (clientSecret) |
| C | `stack-c/openig_home/config/config.json` (sharedSecret, PKCS12 password) |
| C | `stack-c/openig_home/config/routes/10-grafana.json` (clientSecret) |
| C | `stack-c/openig_home/config/routes/11-phpmyadmin.json` (clientSecret) |

Also for each stack:
| Stack | docker-compose file |
|-------|---------------------|
| A | `stack-a/docker-compose.yml` |
| B | `stack-b/docker-compose.yml` |
| C | `stack-c/docker-compose.yml` |

**What to change:**
1. **Pre-work:** Codex investigates OpenIG 6.0.2 env var substitution (`${env['X']}` or `&{X}`) support
2. Replace literal secret values with env var references in config.json and route files
3. Add corresponding env vars to docker-compose.yml for each OpenIG container
4. Rotate all secrets (generate new random values) — old values in git history are considered exposed
5. After deploy: all existing `IG_SSO*` cookies become invalid (expected — users must re-login)

⚠️ Secret rotation invalidates ALL active IG_SSO* session cookies across every stack. All logged-in users will be kicked out at rollout. Plan for a maintenance window or staged rollout.

**Acceptance criteria:**
- [ ] `grep -r "sharedSecret\|clientSecret\|password" openig_home/config/` returns NO literal secret values
- [ ] Secrets only appear in docker-compose env vars (or .env file gitignored)
- [ ] SSO/SLO still works after rotation + restart
- [ ] All 3 stacks verified independently

---

### FIX-07: Transport Security — HTTPS Enforcement

| Attribute | Value |
|-----------|-------|
| Finding | B-F4, C-F4, A-§6 |
| Severity | HIGH |
| Confidence | 4/4 |
| Scope | Cross-stack (A + B + C) |
| Design decision | Yes — see below |

**Problem:** All traffic (OIDC, Vault, logout, session cookies) runs over plaintext HTTP. `requireHttps: false` in OAuth2ClientFilter.

**Design decision needed:**
- This is a **lab environment**. Full HTTPS requires:
  1. Self-signed CA + certs for all `*.sso.local` domains
  2. nginx TLS termination config
  3. Docker networking changes (Vault internal TLS, Keycloak TLS)
  4. Trusted CA distribution to all containers
- **Recommendation: Phase this.**
  - Phase 7a (now): Document as known lab limitation, do NOT change `requireHttps` (breaks lab)
  - Phase 7b (later — Vault Hardening): Implement full TLS with self-signed CA
  - If `requireHttps: true` is set without TLS infra, everything breaks immediately

**Files to change (Phase 7a — documentation only):**
- `docs/standard-gateway-pattern.md` — add "Lab Exception" note
- Each stack's relevant route files — add comment `// TODO: requireHttps: true when TLS infra ready`

**Files to change (Phase 7b — actual TLS, deferred):**
- All `config.json` (3 stacks): set `requireHttps: true` on JwtSession
- All route files with OAuth2ClientFilter: `requireHttps: true`
- All SloHandler*.groovy: change `http://` → `https://` in redirect URLs
- All VaultCredentialFilter*.groovy: Vault endpoint to HTTPS
- All BackchannelLogoutHandler.groovy: JWKS endpoint to HTTPS
- nginx configs (3 stacks): TLS termination
- docker-compose files: cert volume mounts

**Acceptance criteria (Phase 7a):**
- [ ] Documentation updated acknowledging HTTP-only lab limitation
- [ ] No functional changes made (lab still works)

---

## Priority 2 — Redirect Integrity, Session Storage, Adapter Contract

---

### FIX-08: Pin Redirect Origins in Config (Cross-Stack)

| Attribute | Value |
|-----------|-------|
| Finding | A-F5, B-F7, C-F9 |
| Severity | MEDIUM |
| Confidence | 2-3/4 (A-F5 = 2/4 — low confidence) |
| Scope | Cross-stack (A + B + C) |
| Design decision | Yes — see below |

**⚠️ Low confidence for Stack A (2/4).** Codex should investigate A first before implementing.

**Problem:** Redirect URLs and session namespace roots built from inbound `Host` header. Attacker-controlled Host → open redirect / domain confusion.

**Design decision needed:**
- Define canonical public origin per app (already known from architecture.md):
  - `http://wp-a.sso.local` / `http://whoami-a.sso.local`
  - `http://redmine-b.sso.local:9080` / `http://jellyfin-b.sso.local:9080`
  - `http://grafana-c.sso.local:18080` / `http://phpmyadmin-c.sso.local:18080`
- These become constants in each script, not derived from request

**Files to change:**

| Stack | Files |
|-------|-------|
| A | `SloHandler.groovy` (~line 7-10, 24-30), `SessionBlacklistFilter.groovy` (~line 85-90), `SessionBlacklistFilterApp2.groovy` (~line 150-155) |
| B | `SloHandlerJellyfin.groovy`, `SloHandlerRedmine.groovy`, `SessionBlacklistFilterApp3.groovy`, `SessionBlacklistFilterApp4.groovy`, `RedmineCredentialInjector.groovy` |
| C | `SessionBlacklistFilter.groovy` (~line 52-55, 76-89, 141-147), `SloHandlerGrafana.groovy`, `SloHandlerPhpMyAdmin.groovy` |

**What to change:**
1. Define canonical origin constants at top of each script (or via env var)
2. Replace all `request.headers['Host']` / `request.uri.host` redirect constructions with pinned constants
3. Determine canonical origin based on which route matched (route-level context), not request header

**Acceptance criteria:**
- [ ] Send request with spoofed `Host: evil.com` → redirect still goes to canonical origin, not evil.com
- [ ] Normal flow unaffected
- [ ] All logout redirect URLs use pinned origins

---

### FIX-09: Remove Sensitive Material from JwtSession (Stack B + C)

| Attribute | Value |
|-----------|-------|
| Finding | B-F6, C-F5 |
| Severity | HIGH |
| Confidence | 1/4 (B-F6), single-source (C-F5) |
| Scope | Stack B + Stack C |
| Design decision | Yes — major architectural change |

**⚠️ LOW CONFIDENCE — Investigation required before implementation.**

**Problem:** Vault tokens, downstream app credentials (phpMyAdmin user/pass), and downstream session material are serialized into the browser-bound `JwtSession` cookie.

**Design decision needed:**
- Option A: Use Redis as server-side session store (keyed by opaque reference in JwtSession) — production-grade but complex
- Option B: Re-fetch credentials from Vault on each request (no caching in session) — simpler but adds Vault latency
- Option C: Cache credentials in OpenIG in-memory (not in cookie) with TTL — good balance
- **Recommendation:** Codex investigates current session storage pattern first. Determine:
  1. What exactly is stored in JwtSession? (full audit)
  2. Can we use OpenIG's `AttributesContext` or heap objects for in-memory caching instead?
  3. Performance impact of re-fetching from Vault per-request?

**Files to change (once design decided):**

| Stack | Files |
|-------|-------|
| B | `stack-b/openig_home/scripts/groovy/VaultCredentialFilterRedmine.groovy`, `VaultCredentialFilterJellyfin.groovy` |
| C | `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy` (~line 78-80, 122-125, 156-157) |

**Pre-implementation investigation tasks:**
- [ ] Codex: audit all `session.put()` / `session[key] =` calls across B and C to list what's stored
- [ ] Codex: check OpenIG 6 heap object / server-side cache options
- [ ] Decide architecture approach

**Acceptance criteria (post-investigation):**
- [ ] `vault_token`, `vault_token_expiry`, app credentials NOT present in decoded JwtSession cookie
- [ ] SSO/SLO still works (credentials still injected correctly)
- [ ] No significant latency increase on normal requests

---

### FIX-10: Wire PhpMyAdminCookieFilter into Route Chain (Stack C)

| Attribute | Value |
|-----------|-------|
| Finding | C-F6 |
| Severity | HIGH |
| Confidence | Single-source (Stack C had only 1 review source) |
| Scope | Stack C only |
| Design decision | Yes — see below |

**⚠️ CAUTION:** MEMORY.md notes a prior decision that wiring this filter causes "Token mismatch." Investigation needed before wiring.

**Problem:** `PhpMyAdminCookieFilter.groovy` exists in code to detect stale phpMyAdmin cookies after SSO user switch, but it's NOT in the `11-phpmyadmin.json` filter chain.

**Design decision needed:**
- From MEMORY.md: "PhpMyAdminCookieFilter: inactive trong route chain (unwired — gây Token mismatch nếu wire vào)"
- This means **blindly wiring it in will break things**
- **Recommendation:** Codex investigates the Token mismatch issue first, then proposes a fix for the filter itself before wiring it in

**Files to change:**
- `stack-c/openig_home/scripts/groovy/PhpMyAdminCookieFilter.groovy` — potential bug fix
- `stack-c/openig_home/config/routes/11-phpmyadmin.json` (~line 107-115) — wire filter into chain

**Pre-implementation investigation tasks:**
- [ ] Codex: reproduce Token mismatch by wiring filter into chain
- [ ] Codex: identify root cause of mismatch (cookie timing? filter ordering?)
- [ ] Propose fix for filter before wiring

**Acceptance criteria (post-investigation):**
- [ ] Filter is wired into route chain
- [ ] SSO user switch: alice logs in → logout → bob logs in → phpMyAdmin shows bob's session (not alice's stale cookie)
- [ ] No Token mismatch errors in logs
- [ ] Normal single-user flow unaffected

---

### FIX-11: Jellyfin localStorage Token Injection (Stack B)

| Attribute | Value |
|-----------|-------|
| Finding | B-F8 |
| Severity | MEDIUM |
| Confidence | 2/4 |
| Scope | Stack B only |
| Design decision | Yes — major adapter change |

**Problem:** `JellyfinResponseRewriter.groovy` injects Jellyfin access token into browser `localStorage`. Any same-origin XSS can steal it.

**Design decision needed:**
- Option A: Replace with `httpOnly` + `Secure` cookie — requires Jellyfin to accept cookie-based auth
- Option B: Use short-lived tokens + refresh mechanism — complex
- Option C: Accept risk for lab (Jellyfin requires localStorage for its SPA client) — document as known limitation
- **Recommendation:** Codex investigates whether Jellyfin's web client can work without localStorage token. If not, this may be a fundamental Jellyfin integration constraint → document as limitation.

**Files to change (if feasible):**
- `stack-b/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy`

**Pre-implementation investigation:**
- [ ] Codex: check if Jellyfin web client can function with httpOnly cookie auth instead of localStorage
- [ ] If not feasible → document as known limitation in gotchas.md

---

### FIX-12: sid vs sub Consistency Audit (Stack B)

| Attribute | Value |
|-----------|-------|
| Finding | B-F11 |
| Severity | MEDIUM |
| Confidence | 1/4 — investigation required |
| Scope | Stack B (verify A + C as well) |
| Design decision | No (pending investigation result) |

**⚠️ LOW CONFIDENCE (1/4).** Must investigate before any code change.

**Problem:** Potential mismatch between `sid` (session ID from backchannel JWT) used in write path vs `sub` (user subject) used in read path. If mismatched, revocation silently fails.

**Files to investigate:**

| Stack | Write path | Read paths |
|-------|-----------|------------|
| A | `BackchannelLogoutHandler.groovy` | `SessionBlacklistFilter.groovy`, `SessionBlacklistFilterApp2.groovy` |
| B | `BackchannelLogoutHandler.groovy` | `SessionBlacklistFilter.groovy`, `SessionBlacklistFilterApp3.groovy`, `SessionBlacklistFilterApp4.groovy` |
| C | `BackchannelLogoutHandler.groovy` | `SessionBlacklistFilter.groovy` |

**Investigation task for Codex:**
- [ ] Trace the exact Redis key written by `BackchannelLogoutHandler` (is it `blacklist:<sid>` or `blacklist:<sub>`?)
- [ ] Trace the exact Redis key read by each `SessionBlacklistFilter*` variant
- [ ] Compare: do they match?
- [ ] If mismatch found → fix to standardize on `sid` throughout

**Acceptance criteria (if fix needed):**
- [ ] Write path and read path use identical key format
- [ ] Backchannel logout → subsequent request is correctly blocked

---

## Priority 3 — Observability, Safety, Polish

These are SHOULD-level controls from the standard pattern. Lower risk, good hygiene.

---

### FIX-13: Redact id_token_hint from Logout Logs (Stack A)

| Attribute | Value |
|-----------|-------|
| Finding | A-F4 |
| Severity | MEDIUM |
| Confidence | 4/4 |
| Scope | Stack A only (Stack C already does this correctly — use C as reference) |
| Design decision | No |

**Problem:** `SloHandler.groovy` logs the full logout URL containing `id_token_hint`.

**Files to change:**
- `stack-a/openig_home/scripts/groovy/SloHandler.groovy` (~line 27-32)

**What to change:**
1. Replace `logger.info("Logout URL: ${logoutUrl}")` with redacted version
2. Log only: timestamp, session id (opaque), logout type, target host — NOT the full URL with token

**Acceptance criteria:**
- [ ] Trigger RP-initiated logout → check OpenIG logs → no `id_token_hint` value in logs
- [ ] Logout still functions correctly

---

### FIX-14: Unsafe Method Reauth Safety (Stack A)

| Attribute | Value |
|-----------|-------|
| Finding | A-§6 Codex-only |
| Severity | MEDIUM |
| Confidence | Codex-only (needs validation) |
| Scope | Stack A only |
| Design decision | Yes |

**Problem:** `CredentialInjector.groovy` redirects on session expiry even for POST/PUT requests, losing the request body.

**Design decision needed:**
- Option A: Return 401 with `WWW-Authenticate` hint for non-GET methods
- Option B: Return 403 with message "Session expired, please retry"
- **Recommendation: Option A** — standard HTTP semantics

**Files to change:**
- `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy` (~line 155)

**Pre-implementation investigation:**
- [ ] Codex: verify this code path exists and is reachable (Codex-only finding, needs validation)
- [ ] If confirmed → implement method check before redirect

**Acceptance criteria:**
- [ ] POST request with expired session → returns 401 (not 302 redirect)
- [ ] GET request with expired session → still redirects (existing behavior preserved)

---

### FIX-15: WordPress Adapter Fail-Closed (Stack A)

| Attribute | Value |
|-----------|-------|
| Finding | A-§6 Subagent-only |
| Severity | MEDIUM |
| Confidence | Subagent-only (needs validation) |
| Scope | Stack A only |
| Design decision | No (standard: fail closed) |

**Problem:** If WordPress synthetic login fails (Vault unreachable, credential lookup fails), the request may be proxied unauthenticated.

Also validate whether gateway-injected `wordpress_*` cookies can collide with browser-native WordPress cookies — if confirmed, introduce a cookie namespace isolation strategy.

**Files to change:**
- `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy` (~line 82-105, 120-151)

**Pre-implementation investigation:**
- [ ] Codex: trace the failure path in CredentialInjector — does it actually proxy unauthenticated on failure?
- [ ] If confirmed → change failure path to return 503 instead of proxying

**Acceptance criteria:**
- [ ] Stop Vault → access WordPress via SSO → returns 503 (not unauthenticated WordPress page)
- [ ] Vault back up → normal SSO flow works

---

## Summary Matrix

| Fix | Priority | Scope | Confidence | Design Decision | Dependencies |
|-----|----------|-------|------------|-----------------|-------------|
| FIX-01 Jellyfin namespace | P0 | B only | 3/4 | No | None |
| FIX-02 TTL alignment | P0 | A+B+C | 3-4/4 | Yes (simple) | None |
| FIX-03 Fail-closed | P0 | A+B+C | 4/4 | Yes (simple) | None |
| FIX-04 Redis timeouts | P0 | A+B+C | 3/4 | No | Bundle with FIX-03 |
| FIX-05 Error codes | P0 | A+B+C | 3/4 | No | Bundle with FIX-02 |
| FIX-06 Secrets | P1 | A+B+C | 4/4 | Yes (needs research) | None |
| FIX-07 TLS | P1 | A+B+C | 4/4 | Yes (phased) | Phase 7b depends on FIX-06 |
| FIX-08 Pinned origins | P2 | A+B+C | 2-3/4 | Yes | None |
| FIX-09 Session storage | P2 | B+C | 1/4 | Yes (major) | Investigation first |
| FIX-10 CookieFilter wire | P2 | C only | Single | Yes | Investigation first |
| FIX-11 localStorage | P2 | B only | 2/4 | Yes | Investigation first |
| FIX-12 sid/sub audit | P2 | B (verify A+C) | 1/4 | No | Investigation first |
| FIX-13 Log redaction | P3 | A only | 4/4 | No | None |
| FIX-14 Unsafe method | P3 | A only | Codex-only | Yes | Validation first |
| FIX-15 Adapter fail-closed | P3 | A only | Subagent-only | No | Validation first |

---

## Execution Order (Recommended)

### Batch 1 — Revocation + Broken Logout (P0, no design decisions blocking)
1. **FIX-01** — Jellyfin namespace fix (Stack B, quick win)
2. **FIX-02 + FIX-05** — TTL alignment + error codes (all BackchannelLogoutHandler, 3 stacks)
3. **FIX-03 + FIX-04** — Fail-closed + Redis timeouts (all SessionBlacklistFilter*, 3 stacks)
4. **FIX-12** — sid/sub audit (investigation only, informs if FIX-02/03 need adjustment)

→ Restart all stacks → verify all SLO test cases

### Batch 2 — Investigation for Design Decisions
5. **FIX-06 pre-work** — Codex researches OpenIG 6 env var substitution
6. **FIX-09 pre-work** — Codex audits JwtSession content across B+C
7. **FIX-10 pre-work** — Codex reproduces Token mismatch
8. **FIX-11 pre-work** — Codex checks Jellyfin localStorage requirement
9. **FIX-14 + FIX-15** — Codex validates Codex-only and Subagent-only findings

### Batch 3 — Secret Externalization (P1, after design decided)
10. **FIX-06** — Implement secret externalization across 3 stacks
11. **FIX-07 Phase 7a** — Document TLS limitation

→ Restart all stacks → verify SSO/SLO after secret rotation

### Batch 4 — Remaining P2 + P3 (after investigations complete)
12. **FIX-08** — Pin redirect origins (3 stacks)
13. **FIX-09** — Session storage boundaries (if design decided)
14. **FIX-10** — Wire CookieFilter (if Token mismatch resolved)
15. **FIX-11** — localStorage fix (if feasible) or document limitation
16. **FIX-13** — Log redaction (Stack A)
17. **FIX-14 + FIX-15** — Unsafe method + adapter fail-closed (if validated)

→ Final restart → full test suite

---

## Test Strategy

After each batch:
1. Run restart checklist (`.claude/rules/restart.md`)
2. Run relevant test cases from `docs/test-cases.md`:
   - After Batch 1: TC-0101 through TC-0106 (SSO), TC-0201 through TC-0206 (SLO), TC-1001+ (cross-stack SLO)
   - After Batch 3: All SSO/SLO test cases (secrets rotated = all sessions invalidated)
   - After Batch 4: Full 28 test cases

---

## Files NOT to Touch (Convention Reminder)

Per `.claude/rules/conventions.md` — NEVER modify:
- Application containers, Dockerfiles, or app-level config
- Keycloak realm config (unless registering new backchannel URLs)
- Database schemas or data
- Any container image

ALL fixes are scoped to:
- `openig_home/config/*.json`
- `openig_home/config/routes/*.json`
- `openig_home/scripts/groovy/*.groovy`
- `nginx/nginx.conf` (gateway nginx only)
- `docker-compose.yml` (env vars only)
- `docs/` (documentation updates)
