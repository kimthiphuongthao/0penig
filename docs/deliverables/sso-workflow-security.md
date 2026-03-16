# SSO/SLO Workflow Security Assessment

## 1. Workflow Overview

```text
SSO path
========
[Browser]
   |
   | H1  Browser -> nginx
   v
[nginx]
   |
   | H2  nginx -> OpenIG
   v
[OpenIG] --------------------------- H3 --------------------------> [Keycloak]
   |
   | H4  OpenIG -> Vault
   v
[Vault]

[OpenIG] ---------------------------- H5 --------------------------> [Legacy App]


SLO path
========
[Browser]
   |
   | H6  Logout request -> OpenIG
   v
[OpenIG] --------------------------- H7 --------------------------> [Keycloak end_session]
                                                                    |
                                                                    | H8  logout_token
                                                                    v
                                                                 [OpenIG]
                                                                    |
                                                                    | H9  blacklist write
                                                                    v
                                                                  [Redis]
                                                                    |
                                                                    | H10 blacklist check on next request
                                                                    v
                                                                 [OpenIG]
```

| Component | Role in workflow |
|-----------|------------------|
| `nginx` | Public ingress, upstream balancing, forwarded headers, and special handling for logout/backchannel endpoints |
| `OpenIG` | OIDC client, session holder, credential injector, logout orchestrator, and blacklist enforcement point |
| `Keycloak` | Identity provider for OIDC login, RP-initiated logout, and backchannel logout notifications |
| `Vault` | Secret source for legacy-app credentials via AppRole login and per-user secret lookup |
| `Redis` | Revocation cache used to blacklist OIDC `sid`/`sub` values after backchannel logout |

## 2. Hop-by-Hop Assessment

### H1: Browser -> nginx

| Aspect | Finding |
|--------|---------|
| Implementation status | `PARTIAL` |
| Code evidence | `stack-a/docker-compose.yml:28-34`, `stack-b/docker-compose.yml:21-27`, `stack-c/docker-compose.yml:8-15`; `stack-a/nginx/nginx.conf:46-57`, `stack-b/nginx/nginx.conf:26-40`, `stack-b/nginx/nginx.conf:84-99`, `stack-c/nginx/nginx.conf:17-24`, `stack-c/nginx/nginx.conf:41-48`; header strip only in `stack-c/nginx/nginx.conf:27-34`, `stack-c/nginx/nginx.conf:51-58` |
| Architecture assessment | `ADEQUATE` |
| Security risk | All public listeners are HTTP-only, HSTS is not evidenced in any nginx config, and only Stack C strips inbound auth headers before proxying. This leaves transport confidentiality and header-spoofing defenses inconsistent by stack. |
| Standards reference | OWASP Transport Layer Security Cheat Sheet; RFC 6797 (HTTP Strict Transport Security) |
| Recommended action | Terminate HTTPS at nginx for every stack, add HSTS, and apply the same inbound header stripping pattern used in Stack C to all gateway server blocks. Keep `proxy_next_upstream off` on logout/backchannel paths. |

### H2: nginx -> OpenIG

| Aspect | Finding |
|--------|---------|
| Implementation status | `PARTIAL` |
| Code evidence | Forwarded headers in `stack-a/nginx/nginx.conf:30-33`, `stack-a/nginx/nginx.conf:49-52`, `stack-b/nginx/nginx.conf:30-33`, `stack-b/nginx/nginx.conf:47-50`, `stack-c/nginx/nginx.conf:20-22`; upstream failover in `stack-a/nginx/nginx.conf:6-14`, `stack-b/nginx/nginx.conf:6-15`, `stack-c/nginx/nginx.conf:6-10`; keepalive only in `stack-c/nginx/nginx.conf:6-10` |
| Architecture assessment | `ADEQUATE` |
| Security risk | nginx forwards client-origin headers, but no explicit OpenIG trusted-proxy handling is evidenced in the reviewed repo. Internal transport is also plaintext, so the trust boundary is implicit rather than enforced. |
| Standards reference | OWASP Transport Layer Security Cheat Sheet; NIST SP 800-207 (Zero Trust Architecture) |
| Recommended action | Standardize upstream connection settings across stacks, prefer TLS or mTLS between nginx and OpenIG, and add an explicit gateway-side policy for which forwarded headers are trusted. |

### H3: OpenIG -> Keycloak

| Aspect | Finding |
|--------|---------|
| Implementation status | `PARTIAL` |
| Code evidence | `requireHttps: false` in `stack-a/openig_home/config/routes/01-wordpress.json:37`, `stack-a/openig_home/config/routes/02-app2.json:37`, `stack-b/openig_home/config/routes/01-jellyfin.json:97`, `stack-b/openig_home/config/routes/02-redmine.json:91`, `stack-c/openig_home/config/routes/10-grafana.json:45`, `stack-c/openig_home/config/routes/11-phpmyadmin.json:45`; HTML failure handlers in `stack-a/openig_home/config/routes/01-wordpress.json:48-58`, `stack-c/openig_home/config/routes/11-phpmyadmin.json:48-58` |
| Architecture assessment | `ADEQUATE` |
| Security risk | The gateway uses HTTP for OIDC callbacks and logout redirects. PKCE and full JWT validation are not evidenced in the custom scripts that later consume token claims, so the overall control plane is weaker than current OIDC guidance. |
| Standards reference | OpenID Connect Core 1.0; RFC 7636 (PKCE); RFC 7519 (JWT) |
| Recommended action | Enforce HTTPS for all OpenIG OIDC endpoints, enable PKCE in the OpenIG OIDC client configuration where supported, and validate JWT claims before any Groovy script trusts `id_token` contents. |

### H4: OpenIG -> Vault

| Aspect | Finding |
|--------|---------|
| Implementation status | `IMPLEMENTED` |
| Code evidence | AppRole login and cached token TTL in `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy:82-124`; 403 clears cached token in `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy:140-143`; Vault TLS disabled in `stack-a/vault/config/vault.hcl:5-8`, `stack-b/vault/config/vault.hcl:5-8`, `stack-c/vault/config/vault.hcl:5-8`; JwtSession backing in `stack-c/openig_home/config/config.json:20-29` |
| Architecture assessment | `CONCERN` |
| Security risk | Vault transport is plaintext. ~~The gateway also caches Vault tokens and fetched app credentials inside `JwtSession`-backed session state~~ **RESOLVED (FIX-09)**: Vault tokens now cached in server-side `globals` (ConcurrentHashMap, per-ScriptableFilter instance); phpMyAdmin credentials and Grafana username use transient `attributes` (per-request only, never in cookie). Remaining session data is OAuth2 tokens (required for OIDC) and app session cookies (required for injection pattern). Vault access is performed with shared AppRole credentials, so user-to-secret audit correlation is weak. |
| Standards reference | HashiCorp Vault Production Hardening guidance; OWASP Secrets Management Cheat Sheet |
| Recommended action | Enable Vault TLS and certificate validation, keep Vault token and fetched app credentials on the shortest practical lifetime, and add gateway logs that correlate OIDC user, route, Vault path, and request ID. |

### H5: OpenIG -> App

| Aspect | Finding |
|--------|---------|
| Implementation status | `IMPLEMENTED` |
| Code evidence | WordPress cookie injection in `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy:46-97`; Grafana trusted-header injection in `stack-c/openig_home/config/routes/10-grafana.json:76-87`; Jellyfin token injection in `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy:95-106`; phpMyAdmin basic auth injection in `stack-c/openig_home/config/routes/11-phpmyadmin.json:84-89`; upstream header strip only in `stack-c/nginx/nginx.conf:27-34`, `stack-c/nginx/nginx.conf:51-58` |
| Architecture assessment | `GAP` |
| Security risk | Each legacy app trusts credentials or identity injected by the gateway. Without uniform ingress header stripping and stronger session-to-credential binding, the chain is only as strong as the gateway and its session cookie. |
| Standards reference | NIST SP 800-207 (Zero Trust Architecture); OWASP Session Management Cheat Sheet |
| Recommended action | Strip security-sensitive inbound headers on every stack, minimize how long injected credentials live in the gateway session, and force re-issuance on `401`, blacklist hit, or logout events. |

### H6: Logout -> OpenIG

| Aspect | Finding |
|--------|---------|
| Implementation status | `IMPLEMENTED` |
| Code evidence | App-specific intercept routes in `stack-a/openig_home/config/routes/00-wp-logout.json:1-10`, `stack-b/openig_home/config/routes/00-jellyfin-logout.json:1-10`, `stack-c/openig_home/config/routes/00-grafana-logout.json:1-10`, `stack-c/openig_home/config/routes/00-phpmyadmin-logout.json:1-10`; session clear in `stack-a/openig_home/scripts/groovy/SloHandler.groovy:22`, `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy:77`, `stack-c/openig_home/scripts/groovy/SloHandlerGrafana.groovy:28`, `stack-c/openig_home/scripts/groovy/SloHandlerPhpMyAdmin.groovy:28` |
| Architecture assessment | `ADEQUATE` |
| Security risk | Logout works when the browser hits one of the known app-specific intercepts, but coverage is fragmented per app. Browser close, bookmarked deep links, or alternate app logout paths can leave orphan sessions until expiry or blacklist enforcement. |
| Standards reference | OpenID Connect RP-Initiated Logout 1.0; OWASP Session Management Cheat Sheet |
| Recommended action | Keep logout interception at the gateway, but expand it to every proxied app entry/logout path and align OpenIG `JwtSession` timeout with the IdP session timeout. |

### H7: OpenIG -> Keycloak end_session

| Aspect | Finding |
|--------|---------|
| Implementation status | `IMPLEMENTED` |
| Code evidence | `stack-a/openig_home/scripts/groovy/SloHandler.groovy:24-31`, `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy:89-95`, `stack-c/openig_home/scripts/groovy/SloHandlerGrafana.groovy:35-41`, `stack-c/openig_home/scripts/groovy/SloHandlerPhpMyAdmin.groovy:34-40` |
| Architecture assessment | `CONCERN` |
| Security risk | The handlers build plain-HTTP logout URLs and fall back to redirecting without `id_token_hint` when no token is found. `post_logout_redirect_uri` values are hardcoded in gateway code, but Keycloak-side allow-listing was not verified in this review. |
| Standards reference | OpenID Connect RP-Initiated Logout 1.0 |
| Recommended action | Use HTTPS Keycloak browser URLs, keep redirect targets on a local hardcoded allow-list in Groovy, and emit explicit telemetry whenever logout proceeds without `id_token_hint`. |

### H8: Keycloak -> OpenIG backchannel

| Aspect | Finding |
|--------|---------|
| Implementation status | `IMPLEMENTED` |
| Code evidence | Backchannel routes in `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json:2-8`, `stack-c/openig_home/config/routes/00-backchannel-logout-app6.json:2-8`; RS256 algorithm check in `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:252-256`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:259-263`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:251-255`; JWKS fetch with `kid` lookup and 10-minute cache in `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:59-89`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:58-88`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:58-88`; RSA signature verification via `SHA256withRSA` in `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:124-143`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:123-142`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:123-142`; `iss`/`aud`/`events`/`iat`/`exp`/`sid` claims validation in `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:146-216`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:145-223`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:145-215`; kid-miss triggers JWKS refetch before reject in `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:267-282`; JWKS failure returns `500` in `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:261-264`; invalid token returns `400` in `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:286-289`; expected audiences per stack: Stack A `openig-client` (line 20), Stack B `['openig-client-b', 'openig-client-b-app4']` (line 22), Stack C `['openig-client-c-app5', 'openig-client-c-app6']` (line 20) |
| Architecture assessment | `ADEQUATE` |
| Security risk | Full JWT validation is in place across all three stacks. Remaining hardening items: nginx IP allow-listing for the backchannel endpoint is not evidenced, and `requireHttps: false` means the backchannel endpoint is reachable over HTTP (addressed under H3). No replay controls beyond `iat`/`exp` window checks are implemented. |
| Standards reference | OpenID Connect Back-Channel Logout 1.0; RFC 7519 (JWT) |
| Recommended action | Optionally add nginx IP allow-listing for the backchannel endpoint to limit exposure to Keycloak's egress address, and address the HTTP transport gap under H3. |

### H9: OpenIG -> Redis

| Aspect | Finding |
|--------|---------|
| Implementation status | `IMPLEMENTED` |
| Code evidence | Redis write path in `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:305-319`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:312-326`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:304-318`; fixed TTL `EX 3600` in `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:310`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:317`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:309` |
| Architecture assessment | `ADEQUATE` |
| Security risk | Revocation storage exists, but the TTL is fixed at one hour instead of the remaining user session lifetime. No Redis persistence setting is evidenced in the reviewed repo, so restart durability of blacklist entries is uncertain. |
| Standards reference | OpenID Connect Back-Channel Logout 1.0; RFC 7009 (revocation reliability expectations) |
| Recommended action | Derive blacklist TTL from validated token expiry or the OpenIG session expiry. If Redis persistence remains outside the remediation boundary, add a gateway-managed durable revocation journal that can be replayed after restart. |

### H10: SessionBlacklistFilter -> Redis

| Aspect | Finding |
|--------|---------|
| Implementation status | `IMPLEMENTED` |
| Code evidence | Per-request `sid` recovery and Redis GET in `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:109-151`; ~~fail-open path~~ **RESOLVED** (FIX-03, commit 278a29c): now returns 500 on Redis error in `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:152-154` |
| Architecture assessment | `CONCERN` |
| Security risk | ~~Blacklist enforcement exists, but Redis failure logs a warning and still passes the request through~~ **RESOLVED (FIX-03, commit 278a29c)**: Redis failure now returns 500 (fail-closed). Session preserved for automatic recovery when Redis returns. |
| Standards reference | OWASP ASVS fail-safe defaults principle; OWASP Session Management Cheat Sheet |
| Recommended action | ~~Make blacklist enforcement fail-closed~~ **IMPLEMENTED** (FIX-03). Remaining: make degraded-mode response configurable per-route if needed. |

## 3. Critical Gaps

| Gap | Hop | Severity | Risk | Recommended fix |
|-----|-----|----------|------|-----------------|
| ~~`logout_token` JWT signature and claim validation is missing~~ | ~~`H8`~~ | ~~`CRITICAL`~~ | ~~Forged backchannel logout requests can blacklist arbitrary user sessions~~ | **RESOLVED** — RS256 signature verification, JWKS-with-kid lookup, and full `iss`/`aud`/`events`/`iat`/`exp` claims validation implemented in all three stacks |
| ~~`SessionBlacklistFilter` is fail-open~~ | ~~`H10`~~ | ~~`HIGH`~~ | ~~Redis outage turns SLO enforcement off while requests still reach protected apps~~ | **RESOLVED** — FIX-03 (commit 278a29c): catch block now returns 500 INTERNAL_SERVER_ERROR instead of passing request through. Session preserved for automatic recovery when Redis returns. |
| Vault TLS is disabled | `H4` | `HIGH` | Vault tokens and app credentials travel in plaintext on the internal network | Enable TLS on the Vault listener, point `VAULT_ADDR` to `https://...`, and validate the presented certificate in the gateway JVM trust store |
| `requireHttps: false` on all reviewed OIDC client routes | `H3` | `HIGH` | OIDC redirects and callbacks stay on HTTP, weakening confidentiality and cookie safety | Change OpenIG route config to require HTTPS and front the gateway with TLS-only nginx listeners |
| Blacklist durability is not evidenced and TTL is fixed at `3600` | `H9` | `MEDIUM` | Redis restart can erase revocations, and active sessions can outlive blacklist entries | Derive TTL from token/session expiry and add a gateway-managed durable revocation journal for replay after restart |
| ~~Sensitive upstream credentials are cached inside `JwtSession`-backed state~~ | ~~`H4` / `H5`~~ | ~~`MEDIUM`~~ | ~~Shared-secret compromise exposes cached Vault tokens, app passwords, cookies, or media tokens~~ | **RESOLVED (FIX-09)**: Vault tokens → `globals` cache (server memory); phpMyAdmin creds + Grafana username → transient `attributes` (per-request). Remaining session items (wp_session_cookies, redmine_session_cookies, jellyfin_token) are required for app injection patterns. |
| Ingress header stripping is inconsistent across stacks | `H1` / `H5` | `MEDIUM` | Header confusion or spoofing risk varies by application path | Apply Stack C style header clearing to every stack before OpenIG receives the request |

## 4. Cross-cutting Concerns

### Token lifecycle mismatch

| Artifact | Current evidence | Risk | OpenIG-side action |
|---------|------------------|------|--------------------|
| OpenIG `JwtSession` | `stack-a/openig_home/config/config.json:20-29`, `stack-b/openig_home/config/config.json:11-18`, `stack-c/openig_home/config/config.json:20-29` show `sessionTimeout: "8 hours"` | Gateway session can outlive blacklist TTL and some upstream app sessions | Align `JwtSession` timeout with Keycloak realm session lifetime and use the same expiry basis for revocation TTL |
| Redis blacklist entry | `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:310`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:317`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:309` write `EX 3600` | User can remain valid in OpenIG after blacklist entry expires | Compute TTL from validated `exp` or from the gateway session expiry |
| Vault token cache | **FIX-09 RESOLVED**: All 3 stacks now cache Vault token in `globals` (server-side ConcurrentHashMap), not JwtSession. Stack A uses Vault lease duration; Stack C uses 5 min TTL. 403 invalidates cache. | Vault lease and gateway cache can drift; stale cached token handling depends on 403 retry path | Tie cache lifetime strictly to Vault lease and re-acquire only when needed |
| Upstream app credentials/session material | WordPress cookies in `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy:94-96`; Jellyfin token in `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy:95-97`; **phpMyAdmin creds RESOLVED (FIX-09)**: now in transient `attributes` per-request (`stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy:150-151`) | Remaining app session material (WP cookies, Jellyfin token, Redmine cookies) persists in session — required for injection patterns | Reissue or clear cached app credentials whenever logout, blacklist, or re-auth boundaries are crossed |

### Failure cascade analysis

| Dependency failure | SSO effect | SLO effect | Evidence |
|--------------------|-----------|-----------|----------|
| Vault unavailable | OIDC auth can still complete, but Vault-backed app session establishment fails with gateway `500` HTML pages; already-cached credentials may continue to work temporarily | No direct effect on blacklist, but user can be authenticated at IdP and still blocked from app access | `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy:111-165`; `stack-c/openig_home/config/routes/11-phpmyadmin.json:48-58` |
| Redis unavailable | Initial SSO still works | ~~Backchannel logout writes fail with `400`, and per-request blacklist checks fail open~~ **RESOLVED (FIX-03+05)**: backchannel returns 500 (FIX-05, Keycloak may retry), blacklist checks fail-closed with 500 (FIX-03). Global logout propagation disabled during outage but sessions blocked until Redis recovers. | `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:327-329`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:334-336`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:326-328`; `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:152-154` |
| Keycloak unavailable | OIDC login falls into static `500` error pages for reviewed routes | Browser logout redirects to an unavailable end-session endpoint; existing local sessions continue until cleared or expired | `stack-a/openig_home/config/routes/01-wordpress.json:48-58`; `stack-c/openig_home/config/routes/11-phpmyadmin.json:48-58`; `stack-a/openig_home/scripts/groovy/SloHandler.groovy:24-35` |

### Orphan session scenarios

- Browser closes without hitting an app logout intercept: OpenIG keeps an `8 hours` session and may retain upstream app state in that same session (`stack-a/openig_home/config/config.json:24`, `stack-c/openig_home/config/config.json:24`, `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy:94-96`).
- Backchannel logout is accepted but Redis later restarts or blacklist TTL expires first: the revocation marker disappears while the OpenIG session may still be valid (`stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:310`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:317`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:309`; `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:109-151`).
- Redis is down during blacklist check: protected traffic is allowed through because the filter fails open (`stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:152-154`).
- Upstream app sessions can remain usable until the app or gateway notices a `401` or explicit user mismatch, especially for Jellyfin and WordPress (`stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy:109-117`, `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy:46-105`).

### Audit trail: current state vs recommended

| Area | Current state | Recommended |
|------|---------------|-------------|
| Logout auditing | Script handlers log warnings or errors locally, but no end-to-end correlation field is evident (`stack-a/openig_home/scripts/groovy/SloHandler.groovy:32`, `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:324-329`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:331-336`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:323-328`, `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:152-154`) | Add request ID, `client_id`, `sid`, `sub`, outcome, and route name to every logout and blacklist event |
| Vault auditing | Vault fetch uses a shared AppRole path; token now cached in server-side `globals` (**FIX-09 RESOLVED** — no longer in JwtSession cookie) | Log OIDC username, Vault secret path, and request ID at the gateway before and after each Vault fetch |
| Credential-caching visibility | Remaining session-cached items: WP cookies, Jellyfin token, Redmine cookies (required for injection patterns). **phpMyAdmin creds RESOLVED (FIX-09)**: now transient `attributes`. No structured cache lifecycle logging beyond failures (`stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy:95-97`) | Emit structured cache lifecycle logs for issue, reuse, expiry, blacklist clear, and 401 clear events |

## 5. Summary Score

| Hop | Status | Severity | Priority |
|-----|--------|----------|----------|
| `H1` | `PARTIAL` | `MEDIUM` | `P2` |
| `H2` | `PARTIAL` | `MEDIUM` | `P2` |
| `H3` | `PARTIAL` | `HIGH` | `P1` |
| `H4` | `IMPLEMENTED` | `HIGH` | `P1` |
| `H5` | `IMPLEMENTED` | `MEDIUM` | `P2` |
| `H6` | `IMPLEMENTED` | `MEDIUM` | `P2` |
| `H7` | `IMPLEMENTED` | `MEDIUM` | `P2` |
| `H8` | `IMPLEMENTED` | `LOW` | `P3` |
| `H9` | `IMPLEMENTED` | `MEDIUM` | `P2` |
| `H10` | `IMPLEMENTED` | `HIGH` | `P1` |

**Overall workflow security tier:** `Tier 2 - Functionally complete with critical gap closed; remaining gaps are hardening`.

Interpretation: the gateway implements the full SSO/SLO chain and backchannel logout JWT validation is now fully implemented across all three stacks (H8 resolved). The remaining open items — ~~fail-open blacklist enforcement (`H10`)~~ (RESOLVED), plaintext Vault transport (`H4`), HTTP-only OIDC endpoints (`H3`), and TTL/durability gaps (`H9`) — are hardening tasks rather than functional holes. The remediation wave should prioritize `H4` and `H3` next.
