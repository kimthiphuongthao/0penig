# Cross-Stack OpenIG Reference Fix Plan

Date: 2026-03-15

Source review files:
- `docs/reviews/2026-03-14-stack-a-openig-code-security-review.md`
- `docs/reviews/2026-03-14-stack-b-openig-code-security-review.md`
- `docs/reviews/2026-03-14-stack-c-openig-code-security-review.md`

Guardrails:
- Gateway-side only: `.groovy`, route `.json`, `nginx/nginx.conf`, Vault/bootstrap wiring.
- Never modify app servers, databases, Keycloak realm exports, or app-side compose/config.
- Conservative posture: revocation/auth state must fail closed when it becomes indeterminate.
- One fix = one conversation: every item below is scoped as an independently executable work package.

Reference-solution standardization target:
- Secrets and crypto material come only from runtime secret sources, never repo-managed JSON.
- Revocation uses one identifier contract, one TTL contract, explicit Redis time bounds, and fail-closed read behavior.
- Public origins and logout redirects come from pinned config, never the inbound `Host` header.
- Browser-facing traffic is HTTPS-only in the reference pattern; internal HTTP is either removed or documented as an explicit non-reference exception.
- Adapter mechanisms follow one trust-boundary rule:
  - Form injection: downstream credentials/cookies are created server-side and failures deny or re-authenticate.
  - Token injection: downstream tokens are not stored in browser-readable storage.
  - Header injection: trusted headers are set only after OIDC session validation and revocation checks pass.
  - HTTP Basic Auth injection: Vault/app credentials remain server-side and are injected only on the outbound request.

## 1. Fixes Grouped by Priority

### CRITICAL

No finding is labeled `CRITICAL` in the three review files. Do not promote any item to `CRITICAL` without a fresh code read in the execution conversation. Treat the top `HIGH` batch as immediate work.

### HIGH

#### H1. Externalize committed gateway and OIDC secrets, then rotate

- Scope: Cross-stack
- Review anchors: A-F1, B-F1, C-F1
- Files to change:
  - `stack-a/openig_home/config/config.json`
  - `stack-a/openig_home/config/routes/01-wordpress.json`
  - `stack-a/openig_home/config/routes/02-app2.json`
  - `stack-b/openig_home/config/config.json`
  - `stack-b/openig_home/config/routes/01-jellyfin.json`
  - `stack-b/openig_home/config/routes/02-redmine.json`
  - `stack-c/openig_home/config/config.json`
  - `stack-c/openig_home/config/routes/10-grafana.json`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- What to change:
  - Replace plaintext PKCS12 passwords, `JwtSession.password`, `JwtSession.sharedSecret`, and OIDC `clientSecret` values with runtime-injected secret references.
  - Standardize one gateway-side secret-loading pattern across all stacks: env vars, mounted secret files, or Vault/bootstrap material chosen once and reused everywhere.
  - Rotate every exposed value and invalidate existing `IG_SSO*` cookies after cutover.
- Acceptance criteria:
  - No committed route/config JSON contains plaintext `sharedSecret`, session password, keystore password, or OIDC `clientSecret`.
  - Each stack boots with secrets supplied only by the chosen runtime mechanism.
  - Pre-rotation `IG_SSO`, `IG_SSO_B`, and any equivalent legacy cookies no longer authenticate.
  - SSO login and both RP-initiated and backchannel logout still work after rotation.

#### H2. Align revocation TTL with the real gateway session lifetime

- Scope: Cross-stack
- Review anchors: A-F3, B-F2, C-F2
- Files to change:
  - `stack-a/openig_home/config/config.json`
  - `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-b/openig_home/config/config.json`
  - `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-c/openig_home/config/config.json`
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- What to change:
  - Remove hardcoded revocation TTL values such as `EX 3600`.
  - Derive blacklist TTL from the effective `JwtSession.sessionTimeout`, with a small skew buffer if needed.
  - Keep one source of truth so revocation TTL cannot drift from session lifetime again.
- Acceptance criteria:
  - A backchannel logout writes a Redis key whose TTL is greater than or equal to the configured gateway session lifetime.
  - A revoked browser session remains blocked after one hour and stays blocked until the session itself would have expired.
  - No `BackchannelLogoutHandler.groovy` still contains a fixed one-hour revocation TTL.

#### H3. Fail closed when revocation status cannot be confirmed

- Scope: Cross-stack
- Review anchors: A-F2, B-F3, C-F3
- Files to change:
  - `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp3.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy`
  - `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- What to change:
  - Replace Redis exception paths that currently continue with `next.handle(...)`.
  - For authenticated sessions, clear the local gateway session and deny or force a fresh login instead of proxying to the legacy app.
  - Keep unauthenticated requests able to begin the normal login flow.
- Acceptance criteria:
  - With Redis unavailable, a request carrying a valid-looking authenticated gateway cookie never reaches WordPress, WhoAmI, Redmine, Jellyfin, Grafana, or phpMyAdmin.
  - The gateway clears local state or starts re-authentication instead of failing open.
  - Unauthenticated users can still initiate login normally.

#### H4. Fix the broken Jellyfin RP-initiated logout namespace

- Scope: Stack-specific, Stack B
- Review anchors: B-F5
- Files to change:
  - `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
  - `stack-b/openig_home/config/routes/01-jellyfin.json`
- What to change:
  - Change Jellyfin logout to read the same OIDC session namespace used by the registered `OAuth2ClientFilter`.
  - Refuse to construct an RP-initiated logout redirect with a null or wrong-session `id_token_hint`.
  - Preserve local session invalidation even when the IdP logout handoff cannot be completed.
- Acceptance criteria:
  - Jellyfin logout resolves `id_token_hint` from the correct OIDC session namespace.
  - Logging out of Jellyfin ends the Keycloak session instead of silently keeping the IdP session alive.
  - Missing `id_token_hint` produces a controlled deny/reauth outcome, not silent success.

#### H5. Enforce the HTTPS and canonical-public-URL contract at the gateway boundary

- Scope: Cross-stack
- Review anchors: B-F4, C-F4, plus Stack A validation items retained in the review appendix
- Files to change:
  - `stack-a/openig_home/config/routes/01-wordpress.json`
  - `stack-a/openig_home/config/routes/02-app2.json`
  - `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-a/openig_home/scripts/groovy/SloHandler.groovy`
  - `stack-a/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
  - `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
  - `stack-b/openig_home/config/routes/01-jellyfin.json`
  - `stack-b/openig_home/config/routes/02-redmine.json`
  - `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
  - `stack-b/openig_home/scripts/groovy/SloHandlerRedmine.groovy`
  - `stack-c/openig_home/config/routes/10-grafana.json`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json`
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-c/openig_home/scripts/groovy/SloHandlerGrafana.groovy`
  - `stack-c/openig_home/scripts/groovy/SloHandlerPhpMyAdmin.groovy`
  - `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
  - `nginx/nginx.conf`
- What to change:
  - Set `requireHttps: true` anywhere the gateway owns the OIDC filter configuration.
  - Replace HTTP IdP/logout/public URLs with pinned `https://` origins owned by the gateway contract.
  - Add or tighten gateway-side HTTP-to-HTTPS redirect handling in `nginx/nginx.conf`.
  - Treat any remaining internal HTTP hop as a documented exception, not part of the reference solution.
- Acceptance criteria:
  - All browser-facing OIDC, logout, and redirect URLs emitted by the gateway use `https://`.
  - Requests that arrive over HTTP are redirected to HTTPS before the request reaches the OpenIG route chain.
  - No route kept in the reference set still runs with `requireHttps: false`.
  - Any still-unavoidable internal plaintext hop is explicitly documented as residual non-reference wiring.

#### H6. Remove privileged backend state from the browser-bound gateway session

- Scope: Cross-stack for Stack B and Stack C
- Review anchors: B-F6 retained for validation, C-F5 confirmed
- Files to change:
  - `stack-b/openig_home/config/config.json`
  - `stack-b/openig_home/scripts/groovy/VaultCredentialFilterJellyfin.groovy`
  - `stack-b/openig_home/scripts/groovy/VaultCredentialFilterRedmine.groovy`
  - `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy`
  - `stack-c/openig_home/config/config.json`
  - `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- What to change:
  - Stop storing Vault tokens, app passwords, Basic Auth credentials, or downstream access tokens inside `JwtSession`.
  - Move privileged adapter state to a server-side store keyed by an opaque session reference, or re-fetch per request if the chosen design allows it.
  - Keep the browser cookie limited to low-sensitivity identity/session reference material.
- Acceptance criteria:
  - Decoding the gateway session cookie no longer reveals Vault tokens, phpMyAdmin credentials, Jellyfin access tokens, or equivalent downstream secrets.
  - Grafana/header injection, phpMyAdmin basic-auth injection, Redmine/Jellyfin login flows, and logout flows still work with the new server-side state boundary.
  - Rotation of the `JwtSession` secret is no longer equivalent to disclosure of downstream app credentials.

#### H7. Wire the phpMyAdmin cookie reconciliation control into the live route chain

- Scope: Stack-specific, Stack C
- Review anchors: C-F6
- Files to change:
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json`
  - `stack-c/openig_home/scripts/groovy/PhpMyAdminCookieFilter.groovy`
- What to change:
  - Add `PhpMyAdminCookieFilter.groovy` to the active phpMyAdmin chain at the correct point relative to OIDC, revocation, Vault lookup, and proxying.
  - Preserve fail-closed behavior if the filter detects a stale downstream cookie that cannot be safely reconciled.
- Acceptance criteria:
  - Switching from one SSO user to another no longer reuses a stale `phpMyAdmin` cookie.
  - The configured `phpmyadmin-sso` route actually executes the reconciliation filter.
  - Logout/login cycles keep phpMyAdmin session state aligned with the current Keycloak user.

### MEDIUM

#### M1. Pin redirect origins and session-namespace roots instead of trusting inbound `Host`

- Scope: Cross-stack
- Review anchors: A-F5, B-F7, C-F9
- Files to change:
  - `stack-a/openig_home/config/routes/01-wordpress.json`
  - `stack-a/openig_home/config/routes/02-app2.json`
  - `stack-a/openig_home/scripts/groovy/SloHandler.groovy`
  - `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy`
  - `stack-b/openig_home/config/routes/01-jellyfin.json`
  - `stack-b/openig_home/config/routes/02-redmine.json`
  - `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp3.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy`
  - `stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy`
  - `stack-c/openig_home/config/routes/10-grafana.json`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json`
  - `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- What to change:
  - Define canonical public origins per app and read them from config, not request headers.
  - Use those pinned origins for redirect construction, logout return URLs, and any OAuth2 session-namespace resolution derived from hostnames today.
  - Tighten broad route host regexes so they accept only intended public origins.
- Acceptance criteria:
  - Changing the inbound `Host` header does not change the redirect target, OIDC session lookup key, or logout return URL.
  - Route conditions match only the approved public hosts for each app.
  - All redirects and namespace lookups still work for the intended public domains.

#### M2. Add explicit Redis connect/read timeouts everywhere Redis is called

- Scope: Cross-stack
- Review anchors: A retained validation item, B-F9, C-F7
- Files to change:
  - `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy`
  - `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp3.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy`
  - `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- What to change:
  - Replace bare `new Socket(host, port)` usage with explicit connect and read timeouts.
  - Standardize timeout values once for the reference pattern and reuse them across revocation read/write paths.
  - Couple timeout outcomes to the fail-closed behavior defined in `H3`.
- Acceptance criteria:
  - Every Redis read/write call site sets explicit connect and read timeouts.
  - A slow or blackholed Redis server causes bounded latency, not indefinite request hangs.
  - Timeout failures trigger the same fail-closed outcome as any other indeterminate revocation check.

#### M3. Return `5xx` for backchannel infrastructure/runtime failures, reserve `400` for invalid logout tokens

- Scope: Cross-stack for Stack B and Stack C
- Review anchors: B-F10, C-F8
- Files to change:
  - `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- What to change:
  - Split validation failures from infrastructure/runtime failures.
  - Keep malformed/invalid logout token cases as `400`.
  - Return `500` or another retryable `5xx` for Redis, JWKS, parsing, or unexpected internal failures.
- Acceptance criteria:
  - A bad logout token still returns `400`.
  - Simulated Redis or JWKS failure returns `5xx`, not `400`.
  - The OP can distinguish retryable gateway faults from permanent client/input errors.

#### M4. Remove token-bearing logout URL logging

- Scope: Stack-specific, Stack A
- Review anchors: A-F4
- Files to change:
  - `stack-a/openig_home/scripts/groovy/SloHandler.groovy`
- What to change:
  - Stop logging the full logout URL when it contains `id_token_hint`.
  - Replace it with redacted metadata: stack, route, session correlation ID, and logout target host only.
- Acceptance criteria:
  - Triggering RP-initiated logout no longer writes `id_token_hint` or full token-bearing URLs to logs.
  - Logout behavior remains unchanged apart from logging.

#### M5. Remove Jellyfin access-token persistence in browser `localStorage`

- Scope: Stack-specific, Stack B
- Review anchors: B-F8
- Files to change:
  - `stack-b/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy`
  - `stack-b/openig_home/config/routes/01-jellyfin.json`
- What to change:
  - Stop injecting JavaScript that stores Jellyfin credentials/tokens in `localStorage`.
  - Replace it with a gateway-owned handoff pattern: server-side session-backed proxying or an `httpOnly` cookie if strictly required by the app flow.
  - Ensure the browser never needs raw Jellyfin credentials in JavaScript-accessible storage.
- Acceptance criteria:
  - No response body produced by the gateway writes Jellyfin tokens or credentials into `localStorage`.
  - Jellyfin still establishes a working downstream session after SSO login.
  - Browser-side XSS no longer has direct access to the injected Jellyfin token via storage APIs.

#### M6. Audit and standardize revocation keying on `sid`

- Scope: Cross-stack
- Review anchors: B-F11 retained for investigation
- Files to change:
  - `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy`
  - `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp3.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy`
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- What to change:
  - Verify every revocation write/read pair uses the same identifier.
  - Standardize on `sid` unless a documented fallback is unavoidable.
  - Document the chosen key contract in code comments and task notes.
- Acceptance criteria:
  - Backchannel logout for a token containing `sid` blocks the exact matching browser session in each stack.
  - No read path still looks up `sub` while the write path stores `sid`.
  - The Redis key format is identical across all three stacks.

#### M7. Deduplicate WordPress auth cookies before proxying

- Scope: Stack-specific, Stack A
- Review anchors: Stack A retained validation item
- Files to change:
  - `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
- What to change:
  - Harden cookie merge logic so the outbound request forwards only one active cookie per WordPress auth-cookie name.
  - Prefer fresh synthetic-login cookies over stale browser-carried WordPress cookies when both exist.
- Acceptance criteria:
  - The forwarded `Cookie` header never contains duplicate `wordpress_*` or `wordpress_logged_in_*` names.
  - Switching users does not preserve stale WordPress auth state.

#### M8. Do not auto-retry unsafe methods after WordPress session expiry

- Scope: Stack-specific, Stack A
- Review anchors: Stack A retained validation item
- Files to change:
  - `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
- What to change:
  - Limit automatic re-auth/retry behavior to safe methods such as `GET` and `HEAD`.
  - For `POST`, `PUT`, `PATCH`, and `DELETE`, return a controlled deny or re-auth response instead of redirecting blindly back to the same URL.
- Acceptance criteria:
  - An expired downstream WordPress session on a `POST` does not trigger a blind redirect that loses the request body.
  - Safe-method recovery still works.

#### M9. Fail closed when the synthetic WordPress login path cannot establish downstream state

- Scope: Stack-specific, Stack A
- Review anchors: Stack A retained validation item
- Files to change:
  - `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
- What to change:
  - If synthetic login fails or yields no usable downstream cookies for an authenticated SSO user, deny or re-authenticate.
  - Do not proxy the request onward as an anonymous WordPress user.
- Acceptance criteria:
  - Forcing synthetic-login failure never results in anonymous upstream access.
  - The gateway returns a controlled deny/reauth response and records the failure.

## 2. Design Decisions That Need User Input

1. Runtime secret source and rollout
   - Needed for `H1`.
   - Choose one gateway-owned pattern for secrets: env vars, mounted secret files, Vault/bootstrap material, or another runtime mechanism.
   - Confirm whether forced session invalidation during secret rotation is acceptable.

2. Fail-closed response semantics for browser and non-browser traffic
   - Needed for `H3`, `M2`, `M9`.
   - Decide the standard response for indeterminate revocation or downstream-login failure: re-auth redirect, `401/403`, or `503`.
   - Conservative default: browser flows re-authenticate, machine/infrastructure flows return `5xx`.

3. Canonical public origins per app
   - Needed for `H5` and `M1`.
   - Confirm the exact public origins for WordPress, WhoAmI, Redmine, Jellyfin, Grafana, and phpMyAdmin so route conditions and logout redirects can be pinned.

4. Reference-solution TLS scope
   - Needed for `H5`.
   - Decide whether the reference solution must eliminate every internal HTTP hop, or whether internal plaintext between gateway and lab containers can remain as a documented exception.

5. Server-side adapter-state store design
   - Needed for `H6` and `M5`.
   - Decide where privileged downstream state lives: Redis, another gateway-owned server-side session store, or per-request Vault/app re-fetch.

6. Jellyfin token handoff replacement
   - Needed for `M5`.
   - Choose the replacement for browser `localStorage`: pure gateway proxying, an `httpOnly` cookie, or another server-side exchange pattern.

## 3. Proposed Execution Order

1. Lock the user-input decisions first: `H1`, `H3`, `H5`, `H6`, `M5`.
   - Rationale: secret source, fail-closed semantics, public origins, TLS scope, and server-side state design affect multiple later fixes.

2. Implement revocation correctness as the first technical batch: `H2`, `H3`, `M2`, `M3`, `M6`.
   - Rationale: this restores the core SLO/backchannel guarantee before touching adapter-specific behavior.
   - Dependency: `M2` must land with `H3` so Redis timeouts do not reintroduce fail-open behavior.

3. Fix direct stack-specific correctness breaks next: `H4`, `H7`, `M4`.
   - Rationale: these are localized, high-signal defects with low dependency on the server-side state redesign.
   - `H4` is the fastest path to fixing a user-visible broken logout flow.

4. Execute secret and trust-boundary hardening after the storage/rollout decisions are locked: `H1`, `H6`, `M5`.
   - Rationale: these are the most invasive changes and require a consistent pattern across form injection, token injection, and basic-auth injection.

5. Apply transport and origin hardening once canonical origins are fixed: `H5`, `M1`.
   - Rationale: both tasks touch routes, SLO handlers, and gateway ingress behavior; doing them together prevents partial URL-contract drift.

6. Finish Stack A validation-first adapter hardening last: `M7`, `M8`, `M9`.
   - Rationale: these are narrower than the cross-stack work and benefit from the standard fail-closed posture established earlier.

## 4. Cross-Stack vs Stack-Specific Summary

Cross-stack work packages:
- `H1` Externalize and rotate committed secrets.
- `H2` Align revocation TTL with session lifetime.
- `H3` Fail closed when revocation status is indeterminate.
- `H5` Enforce HTTPS and canonical public URL contract.
- `H6` Remove privileged backend state from browser-bound gateway sessions.
- `M1` Pin redirect/session-origin logic to canonical config, not `Host`.
- `M2` Add Redis connect/read timeouts.
- `M3` Return `5xx` for backchannel infrastructure/runtime failures.
- `M6` Standardize revocation keying on `sid`.

Stack-specific work packages:
- `H4` Stack B Jellyfin logout namespace fix.
- `H7` Stack C phpMyAdmin cookie-filter wiring.
- `M4` Stack A logout log redaction.
- `M5` Stack B Jellyfin `localStorage` removal.
- `M7` Stack A WordPress cookie deduplication.
- `M8` Stack A unsafe-method retry hardening.
- `M9` Stack A synthetic-login fail-closed behavior.

## 5. Executor Notes

- Each execution conversation should begin by re-reading only the files listed in the chosen work package plus any directly referenced route/script siblings needed to keep the chain consistent.
- Validation-first items from the reviews should be confirmed in the execution conversation before code is changed, but they remain in scope for this master plan.
- The intended end state is a reusable enterprise-grade pattern where Keycloak, OpenIG, Redis, and Vault each keep a single clear trust role:
  - Keycloak issues identity and logout events.
  - OpenIG validates identity, enforces revocation, and owns all adapter logic.
  - Redis stores revocation and server-side adapter state with bounded latency.
  - Vault provides secrets and app credentials without exposing them to the browser.
