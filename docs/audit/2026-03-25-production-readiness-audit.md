# Production Readiness Audit — 2026-03-25

**Date:** 2026-03-25
**Branch:** feat/shared-infra
**Lab version:** openidentityplatform/openig:6.0.1
**Auditors:** Codex (internal), document-specialist (external/source), gemini-codebase-analyzer (OpenIG source)
**Scope:** shared/ runtime — all 6 apps (WordPress, WhoAmI, Redmine, Jellyfin, Grafana, phpMyAdmin)

## Executive Summary

The shared-infra lab is functionally coherent and the per-app isolation model is largely in place, but it still has production-blocking gaps in transport security, cookie hardening, mutable app image tags, and several infrastructure controls. The 2026-04-02 gateway fixes closed the callback replay, JWKS null-cache, callback-path fail-closed, and shared-infra legacy fallback issues. The current state is suitable for documented lab use, not for production promotion, and the highest-priority items are now concentrated in TLS enablement, `JwtSession` hardening limits, and unresolved infrastructure controls that require server-side session redesign or broader production plumbing.

**Overall verdict:** NOT production-ready — known lab exceptions documented. See Open Items.

**Finding counts (all rounds combined):**
| Severity | Count | Fixed this session | Open | Deferred/WONT_FIX |
|---|---|---|---|---|
| CRITICAL | 2 | 0 | 1 | 1 |
| HIGH | 8 | 2 | 1 | 5 |
| MEDIUM | 16 | 2 | 7 | 7 |
| LOW | 10 | 3 | 3 | 4 |

Note: the required `Deferred/WONT_FIX` bucket also includes `CONFIRMED_OK` findings because the source rounds include positive confirmations with no separate summary column.

## Audit Rounds Index

| Round | Date | Method | Auditor | Detail file | Finding count |
|---|---|---|---|---|---|
| Round 1: Internal comprehensive | 2026-03-24 | Static file audit + docker compose config | Codex | [`docs/obsidian/debugging/2026-03-24-shared-infra-comprehensive-audit.md`](../obsidian/debugging/2026-03-24-shared-infra-comprehensive-audit.md) | 1C+3H+3M+2L |
| Round 2: Built-in gap analysis | 2026-03-25 | Source clone + official docs cross-validation | Explore + document-specialist | [`docs/deliverables/openig-builtin-gap-analysis.md`](../deliverables/openig-builtin-gap-analysis.md) | 0/14 replaceable |
| Round 3: External validation | 2026-03-25 | Official docs + GitHub issues + source code | document-specialist | This file (Section 3) | 10DOC+5BUG+6COM+5SRC |
| Round 4: Critic pass | 2026-03-25 | Full evidence re-read: all 14 Groovy scripts, nginx.conf, 6 route JSONs, docker-entrypoint.sh | Claude Opus | This file (Section 4) | REVISE verdict: 5 severity corrections, 2 scope corrections, 3 blind spots |
| Round 5: Security review | TBD | OWASP Top 10, secrets, unsafe patterns | security-reviewer | TBD | TBD |
| Round 6: Code review | TBD | Logic defects, SOLID, severity findings | code-reviewer | TBD | TBD |

## Master Findings Table

| ID | Severity | Area | Status | Action required | Detail location |
|---|---|---|---|---|---|
| AUD-001 | CRITICAL | Vault bootstrap / secrets | OPEN | Remove seeded passwords from `vault-bootstrap.sh`; `db77728` only redacted one Obsidian note | Section 1 / AUD-001 |
| AUD-002 | HIGH | Backchannel logout / app2 | DEFERRED | Add app2 backchannel route or keep the current explicit lab-only exclusion | Section 1 / AUD-002 |
| AUD-003 | HIGH | Backchannel logout / JWKS cache | FIXED (38ad45d) | No open action; `BackchannelLogoutHandler` now caches only successful JWKS fetches and uses a 60s failure backoff | Section 1 / AUD-003 |
| AUD-004 | HIGH | Docker Compose / secret defaults | OPEN | Remove remaining startup fallbacks and close the issue left partially improved by `e7a223f` | Section 1 / AUD-004 |
| AUD-005 | MEDIUM | TokenReferenceFilter / Redis offload | FIXED (d7b4f3b, 05341f9) | No open action; preserve fail-closed behavior and configured Redis port wiring | Section 1 / AUD-005 |
| AUD-006 | MEDIUM | nginx / X-Forwarded-Proto | DEFERRED | Revisit only when TLS termination is introduced | Section 1 / AUD-006 |
| AUD-007 | MEDIUM | Docker Compose / healthchecks | OPEN | Add healthchecks for nginx, Redis, Vault, and app containers or document exclusions | Section 1 / AUD-007 |
| AUD-008 | LOW | Backchannel routes / TTL env wiring | FIXED (e7a223f) | No open action; keep per-app `REDIS_BLACKLIST_TTL_APP*` route wiring intact | Section 1 / AUD-008 |
| AUD-009 | LOW | Groovy scripts / legacy fallbacks | FIXED (38ad45d) | No open action; `SloHandler.groovy` now fails closed if `OPENIG_PUBLIC_URL` is missing and `SloHandlerJellyfin.groovy` no longer falls back to `:9080` | Section 1 / AUD-009 |
| DOC-001 | CRITICAL | JwtSession / HA | DEFERRED | Replace `JwtSession` with a server-side store or serialize per-session requests. Note: `ip_hash` mitigates cross-node race only; intra-node concurrent write race persists (confirmed by Critic Round 4). | Section 3.1 / DOC-001 |
| DOC-002 | HIGH | OAuth2ClientFilter / HTTPS | DEFERRED | Enable TLS and set `requireHttps: true` on all 6 routes | Section 3.1 / DOC-002 |
| DOC-003 | MEDIUM | JwtSession / JWE algorithm | DEFERRED | Track upstream RSA-OAEP support; exploitability blocked by verify-before-decrypt pattern (SRC-004). Severity downgraded from HIGH by Critic (Round 4). | Section 3.1 / DOC-003 |
| DOC-004 | MEDIUM | Groovy globals / HA cache | DEFERRED | Document per-node cache behavior in `gotchas.md`. TTL relationship already satisfied (3600s < 14400s max_ttl). Severity downgraded from HIGH by Critic (Round 4). | Section 3.1 / DOC-004 |
| DOC-005 | MEDIUM | ScriptableFilter / blocking I/O | DEFERRED | Refactor blocking Vault and login calls to async OpenIG client usage for production | Section 3.1 / DOC-005 |
| DOC-006 | MEDIUM | SpaBlacklistGuardFilter / Redis args | OPEN | Remove `redisAuth` arg and standardize on `redisPasswordEnvVar`. Fix scope: `SpaBlacklistGuardFilter.groovy` line 12 (remove `redisAuth` code path) + `10-grafana.json` line 84 (remove `redisAuth` arg). Scope corrected by Critic (Round 4): `11-phpmyadmin.json` has no `SpaBlacklistGuardFilter`, no change needed there. | Section 3.1 / DOC-006 |
| DOC-007 | MEDIUM | TokenReferenceFilter / cookie overflow guard | FIXED (38ad45d, eb19994) | No open action; `TokenReferenceFilter` now fails closed only on callback path when OAuth2 session keys are unexpectedly empty | Section 3.1 / DOC-007 |
| DOC-008 | MEDIUM | JwtSession / cookie flags | OPEN | Append `HttpOnly` now and `Secure` once TLS is enabled | Section 3.1 / DOC-008 |
| DOC-009 | LOW | Docker entrypoint / sharedSecret validation | OPEN | Add startup validation for minimum `JWT_SHARED_SECRET` length | Section 3.1 / DOC-009 |
| DOC-010 | LOW | nginx / HSTS and CSP | DEFERRED | Add CSP now and enable HSTS only after TLS | Section 3.1 / DOC-010 |
| BUG-001 | HIGH | JwtSession / race confirmation | DEFERRED | Same production remedy as `DOC-001`: move off client-cookie session state. Note: `ip_hash` mitigates cross-node race only; intra-node concurrent write race persists (confirmed by Critic Round 4). | Section 3.2 / BUG-001 |
| BUG-002 | HIGH | nginx / OAuth2 callback retry | FIXED (38ad45d) | No open action; `/openid/app*/callback` now has `proxy_next_upstream off` on all 6 app vhosts | Section 3.2 / BUG-002 |
| BUG-003 | MEDIUM | BackchannelLogoutHandler / JWKS herd | DEFERRED | Use a shared heap singleton or central cache for JWKS | Section 3.2 / BUG-003 |
| BUG-004 | MEDIUM | VaultCredentialFilter / globals.compute race | CONFIRMED_OK | No action required; keep the atomic `globals.compute()` pattern | Section 3.2 / BUG-004 |
| BUG-005 | LOW | JwtSession / 3072-char warning | CONFIRMED_OK | No action required; monitor only if callback warning volume increases | Section 3.2 / BUG-005 |
| COM-001 | MEDIUM | Redis / raw socket client | DEFERRED | Replace raw per-request sockets with a shared Jedis or Lettuce client for production. Severity downgraded from HIGH by Critic (Round 4): harmless at lab scale. | Section 3.3 / COM-001 |
| COM-002 | HIGH | WordPress / blocking login POST | DEFERRED | Replace blocking `HttpURLConnection` login with async client flow or a session probe | Section 3.3 / COM-002 |
| COM-003 | MEDIUM | Docker Compose / mutable image tags | OPEN | Pin WordPress, phpMyAdmin, and Grafana image versions | Section 3.3 / COM-003 |
| COM-004 | MEDIUM | SpaAuthGuardFilter / fail-open | OPEN | Fail closed on exception, especially for XHR paths. Scope extended by Critic (Round 4): fix must cover both `SpaAuthGuardFilter.groovy` (lines 43-51) AND `SpaBlacklistGuardFilter.groovy` (lines 132-134) - same fail-open catch block in both. | Section 3.3 / COM-004 |
| COM-005 | LOW | TokenReferenceFilter / WARN log noise | OPEN | Downgrade or remove the per-request WARN log | Section 3.3 / COM-005 |
| COM-006 | LOW | Routes directory / `.omc` state | OPEN | Move `.omc/` outside `config/routes/` or exclude it from runtime inputs | Section 3.3 / COM-006 |
| SRC-001 | HIGH | JwtCookieSession / eager RSA decrypt | DEFERRED | Profile CPU under load and revisit session design if overhead is material | Section 3.4 / SRC-001 |
| SRC-002 | MEDIUM | JwtCookieSession / no HttpOnly | OPEN | Apply the same fix as `DOC-008` with response post-processing | Section 3.4 / SRC-002 |
| SRC-003 | LOW | BackchannelLogoutHandler / non-atomic JWKS refetch | FIXED (38ad45d) | No open action; the `kid` refresh path no longer clears `jwks_cache` before forcing a refetch | Section 3.4 / SRC-003 |
| SRC-004 | LOW | JwtCookieSession / encrypt-then-sign | CONFIRMED_OK | No action required; keep documented as a positive control | Section 3.4 / SRC-004 |
| SRC-005 | LOW | ScriptableFilter / per-invocation bindings | CONFIRMED_OK | No action required; keep documented as a confirmed threading property | Section 3.4 / SRC-005 |
| BS-001 | MEDIUM | Redis / plaintext token payload | OPEN | TokenReferenceFilter.groovy stores full oauth2 session blob (access_token, id_token) as plaintext JSON in Redis. Per-app ACL (openig-app1..6) mitigates cross-app access. Production requires Vault Transit envelope encryption. Blind spot found by Critic (Round 4). | Section 4 / BS-001 |

## Section 1: Internal Comprehensive Audit (Round 1)

Full findings in [`docs/obsidian/debugging/2026-03-24-shared-infra-comprehensive-audit.md`](../obsidian/debugging/2026-03-24-shared-infra-comprehensive-audit.md).

| ID | Severity | Status | Notes |
|---|---|---|---|
| AUD-001 | CRITICAL | OPEN | Partial cleanup only: `db77728` redacted plaintext credentials from an Obsidian note, but `shared/vault/init/vault-bootstrap.sh` still carries seeded downstream passwords. |
| AUD-002 | HIGH | DEFERRED | `3071a51` removed the nginx `/openid/app2/backchannel_logout` endpoint; app2 / WhoAmI remains intentionally skipped by user decision as a demo app. |
| AUD-003 | HIGH | FIXED (`38ad45d`) | `BackchannelLogoutHandler.groovy` now keeps the JWKS cache null-safe and backs off repeated fetch failures for 60 seconds. |
| AUD-004 | HIGH | OPEN | `e7a223f` removed the original inline `changeme_*` secret defaults and tightened compose secrets, but the audit item is left open pending full closure of the startup-default risk. |
| AUD-005 | MEDIUM | FIXED (`d7b4f3b`, `05341f9`) | Redis offload now fails closed and no longer hardcodes Redis port `6379`. |
| AUD-006 | MEDIUM | DEFERRED | Still intentionally deferred until TLS exists. |
| AUD-007 | MEDIUM | OPEN | Missing healthchecks remain for nginx, Redis, Vault, and most app containers. |
| AUD-008 | LOW | FIXED (`e7a223f`) | Verified in current routes: backchannel handlers now use `REDIS_BLACKLIST_TTL_APP1..6`; there are no live `REDIS_BLACKLIST_TTL` route references. |
| AUD-009 | LOW | FIXED (`38ad45d`) | Commits `6aefd00` and `d9a121e` removed the earlier legacy fallbacks, and `38ad45d` closed the remaining gaps: `SloHandler.groovy` now fails closed if `OPENIG_PUBLIC_URL` is missing, and `SloHandlerJellyfin.groovy` no longer falls back to the legacy `:9080` origin. |

## Section 2: Built-in Gap Analysis (Round 2)

0/14 Groovy scripts replaceable by OpenIG 6 built-ins. 12 capability gaps. Full analysis in [`docs/deliverables/openig-builtin-gap-analysis.md`](../deliverables/openig-builtin-gap-analysis.md).

| Verdict | Count |
|---|---|
| CUSTOM-NEEDED | 6 |
| PARTIALLY-REPLACEABLE | 8 |
| REPLACEABLE | 0 |

Pending: `REC-001` (`StripGatewaySessionCookies` → `CookieFilter`, deferred).

## Section 3: External Validation (Round 3)

Round 3 consolidates the official-doc, source, and external validation findings that complement the internal audit. Supporting references include [`docs/external/openig_audit_results.md`](../external/openig_audit_results.md), [`docs/deliverables/openig-builtin-gap-analysis.md`](../deliverables/openig-builtin-gap-analysis.md), [`.claude/rules/architecture.md`](../../.claude/rules/architecture.md), [`shared/openig_home/scripts/groovy/SpaBlacklistGuardFilter.groovy`](../../shared/openig_home/scripts/groovy/SpaBlacklistGuardFilter.groovy), and [`shared/openig_home/config/routes/10-grafana.json`](../../shared/openig_home/config/routes/10-grafana.json).

### 3.1 Official Doc Findings

**DOC-001** [Severity: CRITICAL]
- **Finding:** Official docs state "OpenIG does not share JwtSessions across threads... last-write-wins race condition for concurrent requests from the same browser session."
- **Current state:** Lab uses JwtSession (SessionApp1..6) with ip_hash for sticky routing (mitigates cross-node races). However concurrent requests within a single node (e.g., Grafana SPA XHR) each read the same cookie, modify independently, write competing Set-Cookie headers.
- **Gap:** No request serialization at JwtSession layer. TokenReferenceFilter compounds this: reads session[tokenRefKey], POSTs to Redis, writes new UUID — all in async callback where concurrent requests share the same JwtCookieSession object (backed by LinkedHashMap, JwtCookieSession.java line 155, not thread-safe).
- **Evidence:** JwtCookieSession.java lines 64, 154-155 (super(new LinkedHashMap<>())); official doc https://doc.openidentityplatform.org/openig/reference/misc-conf
- **Recommendation:** For production, replace JwtSession with server-side session store. If JwtSession must be kept, enforce single-request-at-a-time per session via nginx limit_req. Document race window in architecture notes.

**DOC-002** [Severity: HIGH]
- **Finding:** Official doc states requireHttps: true is required in production. Filter rejects non-HTTPS requests when enabled.
- **Current state:** All 6 app routes set "requireHttps": false.
- **Gap:** All OAuth2/OIDC flows over HTTP. Authorization codes, access tokens, ID tokens, id_token_hint during SLO all traverse path unencrypted.
- **Evidence:** shared/openig_home/config/routes/01-wordpress.json line 49; 10-grafana.json line 57; official doc https://doc.openidentityplatform.org/openig/gateway-guide/chap-oauth2-client
- **Recommendation:** Enable TLS at nginx before any production promotion. Set requireHttps: true in all 6 routes. Known lab exception documented in standard-gateway-pattern.md.

**DOC-003** [Severity: HIGH]
- **Finding:** JwtCookieSession.java hardcodes RSAES_PKCS1_V1_5 + A128CBC_HS256. RFC 7518 marks RSA1_5 as legacy. Bleichenbacher attack applies.
- **Current state:** All 6 JwtSession heaps use JwtKeyStore PKCS12 with RSA keys. Algorithm not configurable.
- **Gap:** Session cookie encryption uses deprecated key-encapsulation algorithm. No configuration knob to upgrade in OpenIG 6.0.1.
- **Evidence:** JwtCookieSession.java lines 346-351 (hardcoded algorithm); RFC 7518 §4.2
- **Recommendation:** File upstream issue requesting RSA-OAEP support. Rotate keys on schedule. Document in production gap register.

**DOC-004** [Severity: HIGH]
- **Finding:** Official deployment guide requires shared keys for JwtSession HA. globals (ConcurrentHashMap) is per-node, per-ScriptableFilter-instance (AbstractScriptableHeapObject.java line 174).
- **Current state:** JWKS and Vault token caches are NOT shared between shared-openig-1 and shared-openig-2. After Vault token expires on node-1, node-2 may still hold valid cache — asymmetric credential lookup failures under load.
- **Gap (partial):** Known HA trade-off. Each node independently refreshes its own cache.
- **Evidence:** AbstractScriptableHeapObject.java line 174; BackchannelLogoutHandler.groovy line 138; VaultCredentialFilter.groovy line 84
- **Recommendation:** Ensure JWKS_CACHE_TTL_SECONDS (3600s) and token_ttl are shorter than Vault token_max_ttl (4h). Document per-node cache behavior in gotchas.md.

**DOC-005** [Severity: MEDIUM]
- **Finding:** Official doc warns never to use Promise blocking methods. Scripts correctly avoid .get()/.getOrThrow() but perform synchronous blocking I/O: CredentialInjector HttpURLConnection to wp-login.php (5s timeouts); VaultCredentialFilter HttpURLConnection to Vault; all Redis scripts open raw Socket connections synchronously.
- **Current state:** 200ms connect/500ms read on Redis; 5s on Vault/WP. Acceptable for lab.
- **Gap:** Under Tomcat thread-per-request model tolerable, but blocking network I/O reduces throughput and can cause thread pool exhaustion.
- **Evidence:** CredentialInjector.groovy lines 219-230; TokenReferenceFilter.groovy lines 76-90; official doc https://doc.openidentityplatform.org/openig/reference/handlers-conf
- **Recommendation:** For production, use OpenIG async http client binding (ClientHandler) instead of HttpURLConnection for Vault calls.

**DOC-006** [Severity: MEDIUM]
- **Finding:** Official docs confirm args bound as top-level Groovy variables. SpaBlacklistGuardFilter.groovy line 12 accepts legacy redisAuth arg alongside standard redisPasswordEnvVar. Route 10-grafana.json passes both args (lines 84-86). Dual-path can cause wrong credential if env var resolution differs.
- **Current state:** All other scripts standardized on redisPasswordEnvVar. SpaBlacklistGuardFilter and grafana route have both.
- **Gap:** Inconsistent arg naming; potential wrong-credential path.
- **Evidence:** AbstractScriptableHeapObject.java lines 258-264; SpaBlacklistGuardFilter.groovy lines 12, 21; 10-grafana.json lines 84-86
- **Recommendation:** Remove redisAuth arg from SpaBlacklistGuardFilter.groovy and from 10-grafana.json/11-phpmyadmin.json. Standardize on redisPasswordEnvVar.

**DOC-007** [Severity: MEDIUM]
- **Finding:** Official doc confirms JwtSession warns at 3072 chars and throws at 4096 chars. TokenReferenceFilter.groovy: if discoverOauth2SessionKeys() returns empty, .then() hook logs "No oauth2 session value found" and returns response without offloading — leaving full oauth2:* entries in cookie. Silent fall-through → potential 4KB overflow.
- **Current state:** Post-fix cookies ~849-971 chars. But during OAuth2 callback, before offload, temporary session holds full token payload.
- **Gap:** session.keySet() failure path = silent correctness bypass.
- **Evidence:** JwtCookieSession.java lines 292-303 (4096 limit); TokenReferenceFilter.groovy lines 250-255
- **Recommendation:** When collectOauth2SessionEntries() returns empty in response hook, emit ERROR log rather than WARN, or fail closed with 502 to prevent cookie overflow.
- **Status update (2026-04-02):** FIXED in `38ad45d`; regression narrowed in `eb19994` so fail-closed applies only on the OAuth2 callback path.

**DOC-008** [Severity: MEDIUM]
- **Finding:** JwtCookieSession.java buildJwtCookie() (lines 332-338) constructs Cookie with only Path=/, Name, Domain, Value, Expires — no HttpOnly, no Secure. Not configurable via JwtSession heap config.
- **Current state:** nginx.conf adds SameSite=Lax via proxy_cookie_flags. No HttpOnly. No Secure (no TLS). Session cookies readable by JavaScript.
- **Gap:** Any XSS in proxied apps (WordPress, Grafana, etc.) exposes encrypted JWT session cookie.
- **Evidence:** JwtCookieSession.java lines 320-338; nginx.conf line 58
- **Recommendation:** Add post-processing HeaderFilter (or ScriptableFilter in .then() phase) that appends "; HttpOnly" to Set-Cookie headers matching IG_SSO_APP*. Enable Secure after TLS.

**DOC-009** [Severity: LOW]
- **Finding:** sharedSecret minimum byte length not validated at startup.
- **Current state:** sharedSecret set from env var JWT_SHARED_SECRET with :?ERROR guard. No length check.
- **Gap:** If JWT_SHARED_SECRET shorter than 32 bytes, OpenIG won't reject at startup but HMAC-SHA-256 signing uses weakened key.
- **Evidence:** Official doc https://doc.openidentityplatform.org/openig/reference/misc-conf; shared/docker/openig/docker-entrypoint.sh
- **Recommendation:** Add startup assertion in docker-entrypoint.sh: validate ${#JWT_SHARED_SECRET} >= 43 chars (32 bytes in base64). Document minimum in .env.example.

**DOC-010** [Severity: LOW]
- **Finding:** HSTS commented out; CSP header absent.
- **Current state:** nginx.conf line 21 has HSTS commented out. X-Frame-Options, X-Content-Type-Options, Referrer-Policy set. No CSP header.
- **Gap:** HSTS deferred (correct without TLS). CSP could be added today as defense-in-depth.
- **Evidence:** shared/nginx/nginx.conf lines 17-21
- **Recommendation:** Add restrictive Content-Security-Policy header to nginx http block. Uncomment HSTS only after TLS.

### 3.2 Known Bugs / GitHub Issues

**BUG-001** [Severity: HIGH]
- **Finding:** Concurrent requests for same session cookie race. Two threads each deserialize same cookie into own JwtCookieSession (LinkedHashMap, not thread-safe), modify independently, second response's Set-Cookie wins.
- **Current state:** JwtCookieSession / JwtSession heap
- **Gap:** Partial. ip_hash reduces cross-node races. Within single node, concurrent XHR or callback in-flight can trigger race on TokenReferenceFilter token_ref_id_appN.
- **Evidence:** JwtCookieSession.java lines 64, 154-155 (LinkedHashMap); WrenIG fork confirms same implementation; https://github.com/WrenSecurity/wrenig/blob/main/openig-core/src/main/java/org/forgerock/openig/jwt/JwtCookieSession.java
- **Recommendation:** ip_hash + SpaAuthGuardFilter/SpaBlacklistGuardFilter early-exit for XHR reduces window. TokenReferenceFilter short-circuits on OAuth2 callback (isOauthCallback check, line 232). Acceptable for lab.

**BUG-002** [Severity: HIGH]
- **Finding:** When nginx retries a failed request on second upstream node, OAuth2 callback (containing code and state) is replayed on different OpenIG node. Second exchange fails with invalid_grant; OIDC flow broken.
- **Current state:** OAuth2ClientFilter callback handling + nginx proxy_next_upstream
- **Gap:** Real risk. Backchannel logout routes correctly have proxy_next_upstream off (nginx.conf lines 35, 100, 158). BUT main app routes use proxy_next_upstream error timeout http_502 http_503 http_504 with proxy_next_upstream_tries 2 (lines 56-57). OAuth2 callback path /openid/appN/callback is in general location / block which has retry enabled.
- **Evidence:** shared/nginx/nginx.conf lines 55-58 (retry on main location); lines 35-38 (backchannel correctly off); oauth2-proxy community issue pattern https://github.com/oauth2-proxy/oauth2-proxy/issues/817
- **Recommendation:** Add specific location ~ ^/openid/app[0-9]+/callback$ block per vhost with proxy_next_upstream off. Same pattern already applied to backchannel logout endpoints.
- **Status update (2026-04-02):** FIXED in `38ad45d`; all 6 shared app vhosts now have callback-specific retry guards.

**BUG-003** [Severity: MEDIUM]
- **Finding:** Each BackchannelLogoutHandler instance has its own globals (ConcurrentHashMap, AbstractScriptableHeapObject.java line 174). Five backchannel routes = five separate JWKS caches. All expire at same time (3600s TTL) → burst of 5 simultaneous JWKS fetches from Keycloak.
- **Current state:** BackchannelLogoutHandler.groovy / globals cache
- **Gap:** Low at lab scale. At production with high backchannel logout volume, could generate burst during key rotation.
- **Evidence:** AbstractScriptableHeapObject.java line 174 (per-instance ConcurrentHashMap); BackchannelLogoutHandler.groovy lines 136-148
- **Recommendation:** Acceptable for lab. For production, use a shared heap object wrapping a single JWKS cache singleton.

**BUG-004** [Severity: MEDIUM]
- **Finding:** VaultCredentialFilter reads AppRole files inside globals.compute() callback. Concurrent requests racing on same token expiry boundary are safe because ConcurrentHashMap.compute() ensures only one callback executes.
- **Current state:** VaultCredentialFilter.groovy / Vault AppRole token caching
- **Gap:** No issue. globals.compute() is atomic. MEMORY.md "JWKS race (CRITICAL)" refers to older version before globals.compute() was introduced. Current code correct.
- **Evidence:** AbstractScriptableHeapObject.java line 174; VaultCredentialFilter.groovy lines 84-128; BackchannelLogoutHandler.groovy lines 137-148
- **Recommendation:** No action required. Confirmed as resolved. Document as CONFIRMED_OK.

**BUG-005** [Severity: LOW]
- **Finding:** JwtCookieSession.java lines 298-303: WARN fires at 3072 chars but continues. During OAuth2 callback, before TokenReferenceFilter strips oauth2:* entries, in-flight session holds full token payload. If >3072 bytes (reached 4803 chars pre-fix), WARN fires during callback response phase.
- **Current state:** JwtCookieSession / TokenReferenceFilter interaction during OAuth2 callback
- **Gap:** Low post-fix. Cookies ~849-971 chars in steady state. Filter chain ordering is correct (TokenReferenceFilter is filter index 0, outer .then() fires last after OidcFilter populates session).
- **Evidence:** JwtCookieSession.java lines 298-303; TokenReferenceFilter.groovy lines 250-255
- **Recommendation:** No action required. Correct chain ordering already in place.

### 3.3 Community / Best Practice Gaps

**COM-001** [Severity: HIGH]
- **Finding:** 6 Groovy scripts maintain own socket lifecycle (new Socket().withCloseable {}), no connection pooling, no pipelining, no retry with backoff. Creates one TCP connection per Redis operation per request.
- **Current state:** TokenReferenceFilter, BackchannelLogoutHandler, SessionBlacklistFilter, SloHandler, SpaBlacklistGuardFilter, RedmineCredentialInjector (partial)
- **Gap:** Low at lab scale. At production 100 req/s with WordPress (3 Redis calls per route), generates ~300 TCP connections/second to Redis.
- **Evidence:** TokenReferenceFilter.groovy lines 76-91 (withRedisSocket); SessionBlacklistFilter.groovy lines 135-157; BackchannelLogoutHandler.groovy lines 438-455
- **Recommendation:** For production, replace raw-socket pattern with shared Redis client library (Jedis/Lettuce) in globals. Bundle JAR in OpenIG WEB-INF/lib, instantiate once in globals map.

**COM-002** [Severity: HIGH]
- **Finding:** CredentialInjector.groovy performs synchronous HttpURLConnection POST to wp-login.php on every request where browser does not hold valid WP session cookie. Plaintext HTTP to http://shared-wordpress/wp-login.php. 5s timeouts. Credentials transmitted in plaintext on Docker bridge network.
- **Current state:** CredentialInjector.groovy
- **Gap:** On first login or WP session expiry, every request adds ~100-500ms latency. Under load, multiple concurrent users could cause Tomcat thread exhaustion.
- **Evidence:** CredentialInjector.groovy lines 219-230 (raw HttpURLConnection, plaintext URL, 5s timeouts)
- **Recommendation:** Accept for lab. For production, use OpenIG async http client binding or session-validity probe before triggering re-login.

**COM-003** [Severity: MEDIUM]
- **Finding:** docker compose pull silently replaces running version. WordPress update could change login form field names (log, pwd) or session cookie naming (wordpress_*), breaking CredentialInjector.groovy.
- **Current state:** shared/docker-compose.yml lines 182, 291, 275
- **Gap:** Direct breakage risk. This is MEMORY.md M-5 item.
- **Evidence:** shared/docker-compose.yml
- **Recommendation:** Pin wordpress:6.5, phpmyadmin:5.2, grafana/grafana:10.4.x. Already tracked as M-5.

**COM-004** [Severity: MEDIUM]
- **Finding:** SpaAuthGuardFilter.groovy exception handling (lines 50-51) calls next.handle() when session.keySet() throws. Outer catch (lines 43-51) also fails open. XHR request bypasses SPA gate and reaches OAuth2ClientFilter which still enforces OIDC auth (secondary protection intact). Real security boundary maintained but defense-in-depth gap exists.
- **Current state:** SpaAuthGuardFilter.groovy lines 14-21, 43-51
- **Gap:** Partial — OAuth2ClientFilter downstream still enforces auth. Not a primary security bypass.
- **Evidence:** SpaAuthGuardFilter.groovy lines 14-25, 43-51
- **Recommendation:** Change outer catch to fail closed (return 401 JSON for XHR, 500 otherwise). Already tracked as audit finding.

**COM-005** [Severity: LOW]
- **Finding:** logger.warn("[TokenReferenceFilter] Session keys at .then(): " + session.keySet().toString()) fires on every authenticated request per app. At 10 req/s with 6 apps = ~60 WARN lines/second in both OpenIG containers.
- **Current state:** TokenReferenceFilter.groovy line 329
- **Gap:** Log noise. This is MEMORY.md L-1 item.
- **Evidence:** TokenReferenceFilter.groovy lines 327-335
- **Recommendation:** Downgrade to logger.debug() or remove. Tracked as L-1.

**COM-006** [Severity: LOW]
- **Finding:** .omc/ subdirectory with checkpoint JSON files is inside the routes directory mounted into OpenIG container. OpenIG Router scans config/routes/ for .json files. Non-route JSON files in subdirectories ignored by current Router config, but fragile — a Router config change or OpenIG upgrade could begin scanning subdirectories.
- **Current state:** shared/openig_home/config/routes/.omc/
- **Gap:** Low. Router does not recursively scan by default. This is MEMORY.md L-2 item.
- **Evidence:** Route directory listing; 01-wordpress.json Router config
- **Recommendation:** Move .omc/ outside routes directory or add to .dockerignore. Tracked as L-2.

### 3.4 Source Code Observations

**SRC-001** [Severity: HIGH]
- **Finding:** JwtCookieSession.java constructor (line 171) calls loadJwtSession(request) eagerly, decrypting JWT cookie with RSAES_PKCS1_V1_5 RSA private key (line 193) on every request. Source has // TODO Make this lazy comment (line 170) confirming this is known technical debt.
- **Current state:** JwtCookieSession.java lines 170-172, 193
- **Gap:** Negligible at lab scale. In production with many concurrent users, RSA decryption cost per request could become CPU bottleneck.
- **Evidence:** JwtCookieSession.java lines 170-172, 193; comment confirms known debt
- **Recommendation:** Monitor CPU under load. If overhead significant, consider HMAC-only JwtSession variant (sharedSecret without keystore), though this loses RSA encryption layer.

**SRC-002** [Severity: MEDIUM]
- **Finding:** JwtCookieSession.java buildJwtCookie() (lines 320-338) constructs Cookie object without setHttpOnly(true) or setSecure(true). No configuration properties for these flags in JwtSession heap config. Same gap as DOC-008.
- **Current state:** JwtCookieSession.java lines 320-338
- **Gap:** Confirmed — no HttpOnly on IG_SSO_APP1..6 cookies. XSS in any proxied app exposes session cookie.
- **Evidence:** JwtCookieSession.java lines 320-338 (no setHttpOnly() call)
- **Recommendation:** Same as DOC-008: post-processing HeaderFilter to append "; HttpOnly" to Set-Cookie for IG_SSO_APP* cookies.

**SRC-003** [Severity: MEDIUM]
- **Finding:** BackchannelLogoutHandler.groovy line 393 uses two-step: globals.compute('jwks_cache') { k, v -> null } (clear) then loadJwksKeys() (recompute). Between these calls, another thread observes null cache and also triggers JWKS fetch — harmless double-fetch but non-atomic.
- **Current state:** BackchannelLogoutHandler.groovy lines 393-403
- **Gap:** Low. Double-fetch only on unknown kid (rare, during Keycloak key rotation).
- **Evidence:** AbstractScriptableHeapObject.java line 174; BackchannelLogoutHandler.groovy lines 393-406
- **Recommendation:** Replace two-step with single atomic globals.compute('jwks_cache') { k, v -> [keys: fetchJwksKeys(...), cachedAt: now] }.
- **Status update (2026-04-02):** FIXED in `38ad45d`; the forced refresh path now reuses the existing cache state instead of clearing `jwks_cache` before refetch.

**SRC-004** [Severity: LOW]
- **Finding:** JwtCookieSession.java line 184: signature verification happens BEFORE decryption (jwt.decrypt() at line 193). This is the correct "Encrypt-then-Sign" order for EncryptedThenSignedJwt. Forgery attempts fail at signature check before decryption step, preventing Bleichenbacher-style oracle attacks via crafted ciphertext.
- **Current state:** JwtCookieSession.java lines 183-193
- **Gap:** Positive — tampered cookie rejected before RSA decrypt operation.
- **Evidence:** JwtCookieSession.java lines 183-193
- **Recommendation:** No action required. Document as confirmed security control in standard-gateway-pattern.md.

**SRC-005** [Severity: LOW]
- **Finding:** AbstractScriptableHeapObject.java lines 246-270: fresh HashMap per invocation (line 250: new HashMap<>()). Script-local variables thread-isolated per invocation. Only scriptGlobals (ConcurrentHashMap) is shared. All scripts correctly treat top-level variable assignments as per-invocation local bindings.
- **Current state:** AbstractScriptableHeapObject.java lines 246-270
- **Gap:** Positive confirmation — current script design is thread-safe for all per-request state.
- **Evidence:** AbstractScriptableHeapObject.java lines 246-270
- **Recommendation:** No action required. Document as confirmed design property.

## Section 5: Items Fixed This Session (2026-03-25)

| ID | Severity | Description | Commit |
|---|---|---|---|
| C-1 / AUD-001 partial | CRITICAL | Redacted plaintext credentials from Obsidian debugging note | db77728 |
| H-1 | HIGH | JellyfinTokenInjector: strip inbound auth headers (Authorization, X-Emby-Authorization, X-MediaBrowser-Token) | 05252ea |
| H-2 / AUD-002 partial | HIGH | nginx: removed /openid/app2/backchannel_logout endpoint (app2 intentionally skipped) | 3071a51 |
| H-3 / M-1 | HIGH/MEDIUM | vault-bootstrap.sh: moved .bootstrap-done marker to true end of script | 3071a51 |
| M-2 | MEDIUM | Backchannel routes 00-backchannel-logout-app3.json + app4.json: replaced hardcoded jwksUri/issuer with env vars | 95e3807 |
| M-3a | MEDIUM | TokenReferenceFilter: externalized Redis port via configuredRedisPort binding | 05341f9 |
| M-3b | MEDIUM | SloHandler + SloHandlerJellyfin: same configuredRedisPort pattern | 3598284 |
| M-4a | MEDIUM | SessionBlacklistFilter + PhpMyAdminAuthFailureHandler: removed legacy openiga/:18080 fallbacks, fail-closed | 6aefd00 |
| M-4b | MEDIUM | JellyfinResponseRewriter + RedmineCredentialInjector: removed :9080 legacy fallbacks, fail-closed | d9a121e |
| AUD-009 partial | LOW | Legacy fallback cleanup landed across multiple Groovy scripts, but two shared-infra-mismatched logout fallbacks remain open | 6aefd00, d9a121e |
| BUG-002 | HIGH | nginx: disabled upstream retry on OAuth2 callback paths across 6 app vhosts | 38ad45d |
| AUD-003 | HIGH | BackchannelLogoutHandler: null-safe JWKS cache + 60s failure backoff | 38ad45d |
| DOC-007 | MEDIUM | TokenReferenceFilter: fail-closed on empty oauth2 keys, narrowed to callback path only after regression fix | 38ad45d, eb19994 |
| AUD-009 | LOW | SloHandler fail-closed if `OPENIG_PUBLIC_URL` is missing; SloHandlerJellyfin removed the legacy `:9080` fallback | 38ad45d |
| SRC-003 | LOW | BackchannelLogoutHandler: forced `kid` refresh no longer clears `jwks_cache` before refetch | 38ad45d |

## Section 6: Open Items (Priority Order)

### Resolved on 2026-04-02

- BUG-002 (HIGH): nginx OAuth2 callback retry protection landed on all 6 app vhosts (`38ad45d`)
- AUD-003 (HIGH): `BackchannelLogoutHandler` JWKS null-cache bug fixed with 60s failure backoff (`38ad45d`)
- SRC-003 (LOW): forced `kid` refresh no longer clears `jwks_cache` before refetch (`38ad45d`)
- DOC-007 (MEDIUM): `TokenReferenceFilter` fail-closed landed, then narrowed to callback path only (`38ad45d`, `eb19994`)
- AUD-009 (LOW): shared-infra SLO handler legacy fallback cleanup completed (`38ad45d`)

### Must fix before production (CRITICAL/HIGH):

1. DOC-008 + SRC-002 (MEDIUM → HIGH in production): No HttpOnly on session cookies → Post-processing HeaderFilter to append ; HttpOnly
2. DOC-006 (MEDIUM): SpaBlacklistGuardFilter redisAuth dual-path → Remove `redisAuth` code path from `SpaBlacklistGuardFilter.groovy` and `10-grafana.json`; standardize on `redisPasswordEnvVar`
3. COM-004 (MEDIUM): SPA guard filters fail open → Change outer catch to fail closed in both `SpaAuthGuardFilter.groovy` and `SpaBlacklistGuardFilter.groovy`
4. AUD-007 (MEDIUM): Missing healthchecks for nginx, Redis, Vault, app containers
5. COM-003 / M-5 (MEDIUM): Mutable image tags → Pin versions (already in MEMORY.md next steps)
6. DOC-009 (LOW): sharedSecret length validation → startup assertion in docker-entrypoint.sh

### Lab limitations (known, deferred until production infrastructure available):
- DOC-001 / BUG-001: JwtSession last-write-wins race → Replace with server-side session store (requires infrastructure change)
- DOC-002: requireHttps: false → Enable TLS first
- DOC-003: RSAES_PKCS1_V1_5 deprecated → Upstream OpenIG fix required
- DOC-004: Per-node globals cache → Accepted HA trade-off; document TTL relationship
- DOC-005: Blocking I/O in Groovy → Replace with async http client (production refactor)
- COM-001: Raw-socket Redis client → Replace with Jedis/Lettuce connection pool (production refactor)
- COM-002: Synchronous WP login POST → Use async http client (production refactor)
- DOC-010: HSTS + CSP → Enable after TLS; add CSP today
- SRC-001: Eager RSA decrypt CPU cost → Profile under load

### Remaining LOW items (tracked in MEMORY.md):
- L-1: Remove/downgrade TokenReferenceFilter WARN log (COM-005)
- L-2: Remove .omc/ from routes directory (COM-006)
- L-3: Update docs/obsidian/how-to/restart-stack.md
- L-4: Remove full response body log in RedmineCredentialInjector

## Section 7: Deferred / WONT_FIX

| ID | Severity | Description | Reason |
|---|---|---|---|
| AUD-002 | HIGH | app2 (WhoAmI) backchannel logout route absent | User decision: WhoAmI is demo app, low risk, intentionally skipped |
| AUD-006 | MEDIUM | X-Forwarded-Proto forwarded without TLS | Intentionally deferred; meaningless without TLS termination |
| FIX-10 | HIGH | PhpMyAdmin CSRF token incompatibility | WONT_FIX: phpMyAdmin CSRF mechanism incompatible with proxy injection pattern |
| FIX-11 | HIGH | SPA token injection via localStorage | WONT_FIX: SPA localStorage access requires client-side JS, cannot be done by gateway |
| BUG-004 | MEDIUM | VaultCredentialFilter globals.compute() race | CONFIRMED_OK: globals.compute() atomic pattern already correct, no action needed |
| BUG-005 | LOW | 3072-char WARN during OAuth2 callback | CONFIRMED_OK: filter chain ordering correct, WARN is transient during callback only |
| SRC-004 | LOW | Encrypt-then-Sign order | CONFIRMED_OK: positive security finding, no action needed |
| SRC-005 | LOW | ScriptableFilter threading model | CONFIRMED_OK: positive confirmation, no action needed |

## Production Readiness Checklist

### Ready ✅
- [x] Per-app Redis ACL isolation (openig-app1..6, ~appN:* prefix, minimal commands)
- [x] Per-app Vault AppRole isolation (openig-app1..6, scoped policies)
- [x] Per-app JwtSession isolation (SessionApp1..6, IG_SSO_APP1..APP6, route-local)
- [x] TokenReferenceFilter Redis offload (JwtSession 4KB problem solved)
- [x] Backchannel OIDC logout with Redis blacklist (5 apps)
- [x] SLO orchestration with id_token_hint (all apps)
- [x] Fail-closed patterns on infrastructure errors (Redis/Vault)
- [x] Vault secrets externalized to .env files (.gitignored)
- [x] OpenIG image pinned to 6.0.1 (no mutable tag)
- [x] globals.compute() atomic JWKS + Vault token cache
- [x] JwtSession Encrypt-then-Sign order correct
- [x] ScriptableFilter threading model confirmed safe
- [x] ip_hash sticky routing (mitigates JwtSession HA race)
- [x] Redis AOF persistence (blacklist survives restart)
- [x] SameSite=Lax on session cookies (nginx proxy_cookie_flags)
- [x] Security headers (X-Frame-Options, X-Content-Type-Options, Referrer-Policy)
- [x] Bootstrap.sh idempotent Vault init + AppRole regen
- [x] OAuth2 callback retry protection (`proxy_next_upstream off` on `/openid/app*/callback` across all 6 app vhosts)
- [x] Backchannel logout JWKS cache is null-safe and uses a 60s failure backoff
- [x] TokenReferenceFilter fails closed on empty OAuth2 session keys for callback traffic only
- [x] Shared-infra SLO handlers no longer rely on legacy `openiga` or `:9080` fallbacks

### NOT Ready ❌ (blocking for production)
- [ ] TLS (requireHttps: false across all 6 routes)
- [ ] HttpOnly flag on session cookies (JwtCookieSession source limitation)
- [ ] Image version pinning: wordpress, phpmyadmin, grafana (M-5)
- [ ] Vault transit encryption for Redis payloads (production infrastructure gap)
- [ ] TLS between components (nginx↔OpenIG↔Vault↔Redis)

### Known Lab Exceptions (documented, not blocking for lab use)
- HTTP-only transport (lab constraint, no TLS infrastructure)
- RSAES_PKCS1_V1_5 JWE algorithm (OpenIG 6.0.1 upstream limitation; severity corrected to MEDIUM by Critic Round 4 — verify-before-decrypt blocks Bleichenbacher oracle)
- Raw-socket Redis client (performance concern at production scale; severity corrected to MEDIUM by Critic Round 4 — harmless at lab scale)
- Per-node globals JWKS/Vault cache (correct HA behavior; severity corrected to MEDIUM by Critic Round 4 — TTL relationship already satisfied)
- Single-node Vault file storage (lab constraint)

## Source References

| Document | Path | Purpose |
|---|---|---|
| Internal comprehensive audit | [`docs/obsidian/debugging/2026-03-24-shared-infra-comprehensive-audit.md`](../obsidian/debugging/2026-03-24-shared-infra-comprehensive-audit.md) | AUD-001..009 findings detail |
| Session 2 audit summary | [`docs/obsidian/debugging/2026-03-25-shared-infra-sso-lab-audit.md`](../obsidian/debugging/2026-03-25-shared-infra-sso-lab-audit.md) | Summary of session 2 audit |
| OpenIG source audit | [`docs/external/openig_audit_results.md`](../external/openig_audit_results.md) | JwtSession/JwtSessionManager source analysis |
| Built-in gap analysis | [`docs/deliverables/openig-builtin-gap-analysis.md`](../deliverables/openig-builtin-gap-analysis.md) | 0/14 replaceable, 12 capability gaps |
| Architecture reference | [`.claude/rules/architecture.md`](../../.claude/rules/architecture.md) | Active shared-infra architecture |
| OpenIG filter reference | https://doc.openidentityplatform.org/openig/reference/filters-conf | Official filter config docs |
| OpenIG handler reference | https://doc.openidentityplatform.org/openig/reference/handlers-conf | Official handler config docs |
| OpenIG gateway guide | https://doc.openidentityplatform.org/openig/gateway-guide/ | Deployment guide |
| OpenIG source (JwtCookieSession) | `/tmp/openig-src/openig-core/src/main/java/org/forgerock/openig/jwt/JwtCookieSession.java` | Session implementation source |
| OpenIG source (AbstractScriptableHeapObject) | `/tmp/openig-src/openig-core/src/main/java/org/forgerock/openig/script/AbstractScriptableHeapObject.java` | ScriptableFilter threading model source |
| RFC 7518 | https://tools.ietf.org/html/rfc7518 | JWA — RSAES_PKCS1_V1_5 legacy status |
| Critic pass transcript |  | Full per-finding verdicts, evidence verification, blind spots (Round 4) |

## Section 4: Critic Pass (Round 4)

**Date:** 2026-03-25
**Method:** Full evidence re-read — all 14 Groovy scripts, nginx.conf, all 6 route JSONs, docker-entrypoint.sh read directly
**Auditor:** Claude Opus (oh-my-claudecode:critic)
**Overall verdict:** REVISE — audit is structurally sound and evidence citations accurate; 5 severity corrections, 2 scope corrections, 3 blind spots added

### Summary of changes applied

| Category | Count | Details |
|---|---|---|
| Severity corrections (HIGH→MEDIUM) | 3 | DOC-003, DOC-004, COM-001 |
| Severity corrections (MEDIUM→LOW) | 1 | SRC-003 |
| Scope corrections | 2 | DOC-006 (remove phpmyadmin.json), COM-004 (add SpaBlacklistGuardFilter) |
| Status corrections | 1 | AUD-009 reopened OPEN LOW |
| Annotations added | 2 | DOC-001, BUG-001 (ip_hash caveat) |
| New blind spot findings | 1 | BS-001 (Redis plaintext tokens) |
| ACCEPTED with no change | 23 | AUD-001..004, AUD-006..007, DOC-001..002, DOC-005, DOC-007..009, BUG-001..003, BUG-004..005, COM-002..003, COM-005..006, SRC-001..002, SRC-004..005 |

### Per-finding verdicts

**AUD-001 (CRITICAL) — ACCEPT**
Status OPEN CRITICAL is correct. vault-bootstrap.sh still seeds test passwords (alice/alice123, bob/bob123) in a git-tracked file. Critical hygiene failure regardless of lab context.

**AUD-002 (HIGH) — ACCEPT**
DEFERRED by explicit user decision (WhoAmI is a demo app). Correctly documented.

**AUD-003 (HIGH) — ACCEPT, confirmed real**
Verified in BackchannelLogoutHandler.groovy lines 137–148: loadJwksKeys() stores [keys: null, cachedAt: now] even when fetchJwksKeys() returns null. On next call, existing != null is true, freshness check passes for 3600s, and cacheEntry?.keys returns null. Production failure mode: 30-second Keycloak restart poisons JWKS cache for 3600s → all backchannel logout requests fail → blacklist entries never written → users remain logged in after SLO. OPEN HIGH confirmed correct.

**AUD-004 (HIGH) — ACCEPT with note**
docker-entrypoint.sh correctly guards JWT_SHARED_SECRET and KEYSTORE_PASSWORD with empty-check exits. Finding valid but recommendation should enumerate which remaining credential env vars still have weak defaults. Scope is vague.

**AUD-009 (LOW) — REVISE: FIXED → OPEN LOW**
Commits 6aefd00/d9a121e missed two scripts:
- `SloHandler.groovy` line 86: `System.getenv("OPENIG_PUBLIC_URL") ?: "http://openiga.sso.local"` — legacy openiga hostname
- `SloHandlerJellyfin.groovy` line 127: `System.getenv("CANONICAL_ORIGIN_APP4") ?: "http://jellyfin-b.sso.local:9080"` — `:9080` is old Stack B port
Both fallbacks only fire when env vars are missing but would produce wrong logout redirect URIs silently.

**DOC-001 (CRITICAL) — ACCEPT with annotation**
ip_hash mitigates cross-node race (same IP always hits same upstream) but does NOT eliminate intra-node race: two concurrent XHR requests from the same browser session hit the same OpenIG node, each deserializes the same JwtSession cookie into its own LinkedHashMap, both traverse TokenReferenceFilter.then(), both generate new UUIDs, second Set-Cookie wins in browser, first UUID is orphaned in Redis. DEFERRED CRITICAL correct. Annotation: ip_hash = cross-node only mitigation.

**DOC-003 (HIGH) — REVISE: HIGH → MEDIUM**
Verify-before-decrypt pattern (SRC-004, confirmed via ForgeRock JOSE architecture) blocks Bleichenbacher oracle: crafted ciphertexts fail at signature verification before RSA decrypt is ever reached. Risk is 'deprecated algorithm, no upgrade path in 6.0.1' not 'actively exploitable Bleichenbacher'. Severity MEDIUM appropriate for lab.

**DOC-004 (HIGH) — REVISE: HIGH → MEDIUM**
Per-node globals cache is correct HA behavior for stateless nodes — each node independently refreshes, no inconsistency. TTL relationship already satisfied: JWKS_CACHE_TTL_SECONDS (3600s) < token_max_ttl (14400s). No operational failure exists, only a documentation gap for gotchas.md. Severity MEDIUM appropriate.

**DOC-006 (MEDIUM) — REVISE: scope correction**
Confirmed via direct read of 11-phpmyadmin.json: route has NO SpaBlacklistGuardFilter in its filter chain (chain is TokenReferenceFilterApp6 → OidcFilterApp6 → SessionBlacklistFilterApp6 → StripGatewaySessionCookiesApp6 → VaultCredentialFilter → PhpMyAdminBasicAuth). Fix scope is only: SpaBlacklistGuardFilter.groovy line 12 (remove redisAuth code path) + 10-grafana.json line 84 (remove redisAuth arg).

**BUG-001 (HIGH) — ACCEPT with annotation**
Same ip_hash caveat as DOC-001. DEFERRED HIGH correct.

**BUG-002 (HIGH) — ACCEPT, confirmed real**
Verified in nginx.conf: all 6 app server blocks' location / have proxy_next_upstream error timeout http_502 http_503 http_504 + proxy_next_upstream_tries 2. No /openid/app*/callback exception exists. Only backchannel_logout locations have proxy_next_upstream off. When OpenIG-1 is unavailable during OAuth2 callback, nginx retries to OpenIG-2 which receives already-consumed authorization code → Keycloak returns invalid_grant → login fails. OPEN HIGH confirmed. Most actionable fix in the entire audit (1 nginx location block per server).

**COM-001 (HIGH) — REVISE: HIGH → MEDIUM**
Finding's own text says 'low at lab scale'. Raw socket per Redis operation is a production throughput concern (thread exhaustion at 100+ req/s), not a correctness or security issue. At 1–2 lab users with Docker bridge sub-millisecond latency, 200ms connect timeout is never approached. Severity MEDIUM for lab.

**COM-004 (MEDIUM) — REVISE: scope extension**
SpaBlacklistGuardFilter.groovy lines 132–134 has identical fail-open catch block. Both scripts run at front of Grafana filter chain. Fix must cover both. Secondary protection (SessionBlacklistFilter deeper in chain) maintains security boundary, but defense-in-depth is violated in both, not just SpaAuthGuardFilter.

**SRC-003 (MEDIUM) — REVISE: MEDIUM → LOW**
Non-atomic clear+refetch confirmed in BackchannelLogoutHandler.groovy. However loadJwksKeys() itself uses globals.compute() (atomic). When Thread A clears cache and Thread B enters loadJwksKeys(), Thread B wins the compute() lock, fetches JWKS, stores result. Thread A then calls loadJwksKeys(), finds cache already populated by Thread B, reuses it. Double-fetch only when two threads both observe null state simultaneously — ConcurrentHashMap.compute() serializes them, only one does the HTTP fetch. Outcome: one extra JWKS fetch on rare key rotation event. Harmless. Severity LOW.

**SRC-004 (LOW) — ACCEPT (CONFIRMED_OK)**
Verify-before-decrypt pattern architecturally confirmed via ForgeRock JOSE (EncryptedThenSignedJwt). Signature verification occurs before RSA decrypt. CONFIRMED_OK correct.

**SRC-005 (LOW) — ACCEPT (CONFIRMED_OK)**
All 14 Groovy scripts verified: all per-request state assigned from binding.variables at invocation time. Only globals (ConcurrentHashMap) is shared. No static mutable state or class-level fields in any script. Per-invocation isolation confirmed by direct read, not assumed.

### Blind spots (new findings from Critic)

**BS-001 (MEDIUM) — Redis plaintext token payload**
TokenReferenceFilter.groovy line 338: `setRedisJson(tokenRefId, oauth2EntryMap)` stores full oauth2 session blob (access_token, id_token, refresh_token if present) as plaintext JSON in Redis. Per-app ACL (openig-app1..6, ~appN:* prefix) mitigates cross-app access. A Redis auth bypass (weak password, network-adjacent attacker) exposes live tokens directly. Production fix: Vault Transit envelope encryption over the Redis payload before write. Lab: acceptable given ACL isolation, document as production gap.

**Blind spot 2 — AUD-009 incomplete fix (incorporated into AUD-009 correction above)**

**Blind spot 3 — COM-004 scope incomplete (incorporated into COM-004 correction above)**

### Evidence verification summary

| File | Lines verified | Findings confirmed |
|---|---|---|
| shared/nginx/nginx.conf | All server blocks (6 apps + openig.sso.local) | BUG-002 (callback retry), DOC-002 (requireHttps), DOC-008 (no HttpOnly in proxy_cookie_flags) |
| shared/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy | Full (loadJwksKeys, validateLogoutTokenClaims, main) | AUD-003 (null cache), SRC-003 (non-atomic refetch), BUG-001 (HA) |
| shared/openig_home/scripts/groovy/SpaBlacklistGuardFilter.groovy | Full | DOC-006 (dual redisAuth path), COM-004 (fail-open catch) |
| shared/openig_home/config/routes/10-grafana.json | Lines 81–136 | DOC-006 (redisAuth line 84 confirmed) |
| shared/openig_home/config/routes/11-phpmyadmin.json | Full filter chain | DOC-006 scope correction (no SpaBlacklistGuardFilter) |
| shared/openig_home/config/routes/01-wordpress.json | Lines 1–60 | DOC-002 (requireHttps: false line 49) |
| shared/docker/openig/docker-entrypoint.sh | Full | AUD-004 (empty-only validation, no length check) |
| All 14 Groovy scripts | All scripts read for SRC-005 verification | SRC-005 (per-invocation bindings confirmed across all 14) |
