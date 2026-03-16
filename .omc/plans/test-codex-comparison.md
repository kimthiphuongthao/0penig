# Cross-Stack OpenIG Review Fix Plan

Date: 2026-03-15

Source review files:
- `docs/reviews/2026-03-14-stack-a-openig-code-security-review.md`
- `docs/reviews/2026-03-14-stack-b-openig-code-security-review.md`
- `docs/reviews/2026-03-14-stack-c-openig-code-security-review.md`

Execution guardrails:
- Gateway-side changes only: `.groovy`, route `.json`, `nginx/nginx.conf`, Vault/bootstrap wiring.
- Conservative posture: fail closed is preferred anywhere revocation/auth state is indeterminate.
- One fix = one conversation. Each item below is scoped as a standalone work package.

## 1. Fixes Grouped by Priority

### CRITICAL

No confirmed `CRITICAL` findings were assigned in the three review files. The highest confirmed severity is `HIGH`, so the `HIGH` batch should be treated as immediate work.

### HIGH

#### H1. Externalize committed secrets and rotate exposed values

- Scope: Cross-stack
- Files to change:
  - `stack-a/openig_home/config/config.json`
  - `stack-b/openig_home/config/config.json`
  - `stack-b/openig_home/config/routes/01-jellyfin.json`
  - `stack-b/openig_home/config/routes/02-redmine.json`
  - `stack-c/openig_home/config/config.json`
  - `stack-c/openig_home/config/routes/10-grafana.json`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- What to change:
  - Replace plaintext PKCS12 password, `JwtSession.password`, `JwtSession.sharedSecret`, and OIDC `clientSecret` values with runtime-injected values.
  - Wire those values through the chosen gateway bootstrap/env/Vault path instead of repo-managed JSON.
  - Rotate all exposed values and explicitly invalidate existing `IG_SSO` cookies after cutover.
- Acceptance criteria:
  - No repo-managed JSON file contains plaintext `sharedSecret`, session password, PKCS12 password, or OIDC `clientSecret`.
  - OpenIG boots successfully with only injected secret values present.
  - Pre-rotation `IG_SSO` cookies stop working and force re-authentication.
  - Login and logout still succeed for Stack A, Stack B, and Stack C after rotation.

#### H2. Align revocation TTL with real session lifetime

- Scope: Cross-stack
- Files to change:
  - `stack-a/openig_home/config/config.json`
  - `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-b/openig_home/config/config.json`
  - `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-c/openig_home/config/config.json`
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- What to change:
  - Remove the hardcoded Redis revocation TTL of `3600`.
  - Derive the blacklist TTL from `JwtSession.sessionTimeout`, or from remaining session lifetime plus a small skew buffer.
  - Keep one source of truth so TTL cannot drift from session lifetime again.
- Acceptance criteria:
  - Backchannel logout writes a Redis key whose TTL is greater than or equal to the configured session lifetime.
  - A revoked `IG_SSO` cookie is still blocked after one hour and remains blocked until the gateway session expires.
  - No backchannel handler contains a fixed `EX 3600` for session revocation.

#### H3. Fail closed when revocation status cannot be checked

- Scope: Cross-stack
- Files to change:
  - `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp3.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy`
  - `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- What to change:
  - In Redis error/timeout paths, do not call `next.handle(...)` for authenticated sessions.
  - Clear the local gateway session and either deny the request or force a fresh authentication flow.
  - Keep unauthenticated requests able to start the normal login flow.
- Acceptance criteria:
  - With Redis unavailable, a request carrying an authenticated session cookie never reaches the upstream app.
  - The gateway clears the local session or forces re-authentication instead of proxying the request.
  - Normal unauthenticated login still works.

#### H4. Enforce secure public URLs and HTTPS semantics

- Scope: Cross-stack
- Confidence note: Confirmed in Stack B and Stack C. Stack A transport findings in the review are retained from Codex-only evidence and should be validated as part of the same task.
- Files to change:
  - `stack-a/openig_home/config/routes/01-wordpress.json`
  - `stack-a/openig_home/config/routes/02-app2.json`
  - `stack-a/openig_home/scripts/groovy/SloHandler.groovy`
  - `stack-a/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
  - `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
  - `stack-b/openig_home/config/config.json`
  - `stack-b/openig_home/config/routes/01-jellyfin.json`
  - `stack-b/openig_home/config/routes/01-dotnet.json`
  - `stack-b/openig_home/config/routes/02-redmine.json`
  - `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
  - `stack-b/openig_home/scripts/groovy/SloHandlerRedmine.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp3.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy`
  - `stack-b/openig_home/scripts/groovy/VaultCredentialFilterJellyfin.groovy`
  - `stack-b/openig_home/scripts/groovy/VaultCredentialFilterRedmine.groovy`
  - `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy`
  - `stack-c/openig_home/config/routes/10-grafana.json`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json`
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-c/openig_home/scripts/groovy/SloHandlerGrafana.groovy`
  - `stack-c/openig_home/scripts/groovy/SloHandlerPhpMyAdmin.groovy`
  - `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
  - `nginx/nginx.conf`
- What to change:
  - Set `requireHttps: true` on OIDC filters where supported.
  - Replace gateway-emitted public URLs, redirect URLs, logout URLs, and Vault/OP endpoints with canonical `https://` values from pinned config.
  - Add gateway-side HTTP to HTTPS redirect behavior in `nginx/nginx.conf`.
  - Do not silently claim full end-to-end TLS for app-container `baseURI` hops that are outside gateway-only scope.
- Acceptance criteria:
  - Gateway-generated redirect targets and OIDC/logout URLs use `https://` for the chosen public origins.
  - Requests arriving over HTTP are redirected to HTTPS at the gateway boundary.
  - `requireHttps` is enabled anywhere the gateway owns the OIDC filter config.
  - Any remaining internal HTTP hop that cannot be changed gateway-side is explicitly documented as residual risk.

#### H5. Fix Jellyfin RP-initiated logout namespace mismatch

- Scope: Stack-specific, Stack B
- Files to change:
  - `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
  - `stack-b/openig_home/config/routes/01-jellyfin.json`
- What to change:
  - Change the OIDC session lookup from `app3` to `app4` so it matches Jellyfin's registered `OAuth2ClientFilter`.
  - Refuse to build a logout redirect if `id_token_hint` cannot be found for the correct namespace.
- Acceptance criteria:
  - Jellyfin logout resolves `id_token_hint` from the `openid-connect.app4` session namespace.
  - RP-initiated logout ends the Keycloak session instead of silently returning without IdP logout.
  - A missing `id_token_hint` produces a controlled error/reauth path, not a silent success.

#### H6. Remove privileged backend material from the browser-bound `JwtSession`

- Scope: Cross-stack for Stack B and Stack C
- Confidence note: Confirmed in Stack C. Stack B keeps this as a retained high-risk finding from one review source and should be validated before final implementation shape is chosen.
- Files to change:
  - `stack-b/openig_home/config/config.json`
  - `stack-b/openig_home/scripts/groovy/VaultCredentialFilterJellyfin.groovy`
  - `stack-b/openig_home/scripts/groovy/VaultCredentialFilterRedmine.groovy`
  - `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy`
  - `stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy`
  - `stack-c/openig_home/config/config.json`
  - `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
- What to change:
  - Stop serializing Vault tokens, downstream app credentials, or downstream session material into `IG_SSO`.
  - Keep only identity/session reference data in the browser cookie.
  - Move privileged material to a server-side store keyed by an opaque session reference, or refetch it from Vault on demand.
- Acceptance criteria:
  - Decoding `IG_SSO` no longer reveals `vault_token*`, `phpmyadmin_username`, `phpmyadmin_password`, downstream app cookies, or similar privileged values.
  - Redmine, Jellyfin, and phpMyAdmin still authenticate successfully after the session model change.
  - Session logout clears both the browser identity state and the server-side privileged state.

#### H7. Wire the phpMyAdmin cookie reconciliation control into the active route

- Scope: Stack-specific, Stack C
- Files to change:
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json`
  - `stack-c/openig_home/scripts/groovy/PhpMyAdminCookieFilter.groovy`
- What to change:
  - Add `PhpMyAdminCookieFilter` to the `phpmyadmin-sso` chain in a position that can expire stale downstream cookies before the response reaches the browser.
  - Keep the existing OIDC, blacklist, Vault, and basic-auth flow intact.
- Acceptance criteria:
  - The active `phpmyadmin-sso` route references `PhpMyAdminCookieFilter`.
  - When a browser carries a stale phpMyAdmin cookie for a different SSO user, the gateway expires that cookie and forces a clean downstream session.
  - Normal phpMyAdmin access still works for the current SSO user.

#### H8. Bound Redis latency with explicit connect/read timeouts

- Scope: Cross-stack
- Confidence note: Confirmed as `MEDIUM` in Stack B and Stack C. Stack A has a retained `HIGH` timeout gap for the app1 blacklist filter and should be folded into the same implementation.
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
  - Replace raw unbounded socket usage with explicit connect and read timeouts.
  - Route timeout outcomes into the same fail-closed behavior used for indeterminate revocation checks.
  - Keep backchannel writes retryable by returning a server error on infrastructure failure.
- Acceptance criteria:
  - Delayed or unreachable Redis does not hang OpenIG threads beyond the configured timeout budget.
  - Authenticated requests fail closed when Redis read timeouts occur.
  - Backchannel logout returns a retryable `5xx` on Redis timeout instead of hanging or misclassifying the error.

### MEDIUM

#### M1. Stop logging logout URLs that contain `id_token_hint`

- Scope: Stack-specific, Stack A
- Files to change:
  - `stack-a/openig_home/scripts/groovy/SloHandler.groovy`
- What to change:
  - Remove logging of the fully assembled logout URL.
  - Log only safe metadata such as target realm endpoint, presence/absence of an ID token, and correlation-safe request details.
- Acceptance criteria:
  - Logout logs never contain `id_token_hint=`.
  - Logout logs never contain raw JWT fragments.
  - RP-initiated logout still redirects correctly.

#### M2. Pin redirect origins and OIDC session namespace resolution to trusted config

- Scope: Cross-stack
- Files to change:
  - `stack-a/openig_home/scripts/groovy/SloHandler.groovy`
  - `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy`
  - `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
  - `stack-b/openig_home/scripts/groovy/SloHandlerRedmine.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp3.groovy`
  - `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy`
  - `stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy`
  - `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-c/openig_home/config/routes/10-grafana.json`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- What to change:
  - Stop constructing redirects and session-key candidates from inbound `Host` or `request.uri.host`.
  - Introduce pinned canonical public origins per app and use those values consistently for redirects and OIDC namespace lookup.
  - Reject or normalize unexpected hosts at the gateway boundary.
- Acceptance criteria:
  - Spoofing the inbound `Host` header does not change logout targets or revocation redirect targets.
  - OIDC session lookup keys are deterministic and match configured public origins.
  - Redirects always land on the canonical public origin for the app.

#### M3. Return `5xx` for backchannel infrastructure/runtime failures

- Scope: Cross-stack for Stack B and Stack C
- Files to change:
  - `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- What to change:
  - Reserve `400` for invalid or malformed backchannel logout tokens.
  - Return `500` or `503` when Redis, JWKS retrieval, or other runtime infrastructure fails.
- Acceptance criteria:
  - Invalid logout JWTs still return `400`.
  - Redis/JWKS/runtime failures return `5xx`.
  - OP retry behavior can distinguish transient server faults from permanent client faults.

#### M4. Remove Jellyfin access-token persistence in browser `localStorage`

- Scope: Stack-specific, Stack B
- Files to change:
  - `stack-b/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy`
  - `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy`
  - `stack-b/openig_home/config/routes/01-jellyfin.json`
- What to change:
  - Stop emitting JavaScript that writes Jellyfin credentials into `localStorage`.
  - Replace the browser-visible token handoff with a gateway-managed, `httpOnly` mechanism or a server-side session exchange.
- Acceptance criteria:
  - No gateway response writes Jellyfin credentials into `localStorage`.
  - Browser developer tools show no Jellyfin credential object in `localStorage`.
  - Jellyfin still loads authenticated for the SSO user.

#### M5. Audit and standardize revocation keying on `sid`

- Scope: Cross-stack
- Confidence note: Retained investigation item from Stack B review. Fold the audit across all three stacks because the same blacklist pattern is shared.
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
  - Verify that every blacklist write and read uses the same identifier.
  - Standardize on `sid` unless a clearly documented fallback mapping is required.
- Acceptance criteria:
  - A backchannel logout token carrying `sid` revokes the matching browser session in every stack.
  - No blacklist-read path uses `sub` while the write path uses `sid`.
  - The chosen identifier contract is documented in code comments or task notes.

#### M6. Deduplicate WordPress auth cookies before proxying

- Scope: Stack-specific, Stack A
- Confidence note: Retained validation item from Stack A review.
- Files to change:
  - `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
- What to change:
  - Harden cookie merge logic so only one active cookie per WordPress auth-cookie name is forwarded.
  - Prefer fresh synthetic-login cookies over stale browser-carried WordPress cookies.
- Acceptance criteria:
  - The forwarded `Cookie` header never contains duplicate `wordpress_*` or `wordpress_logged_in_*` entries for the same cookie name.
  - Switching users does not preserve stale WordPress auth cookies.

#### M7. Do not auto-retry unsafe methods after WordPress session expiry

- Scope: Stack-specific, Stack A
- Confidence note: Retained validation item from Stack A review.
- Files to change:
  - `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
- What to change:
  - Keep automatic retry only for safe methods such as `GET` and `HEAD`.
  - For `POST`, `PUT`, `PATCH`, and `DELETE`, return a controlled error or re-auth response instead of redirecting blindly back to the same URL.
- Acceptance criteria:
  - An expired WordPress session on a `POST` does not cause a blind `302` back to the original URL.
  - Safe-method retries still work without losing request semantics.

#### M8. Fail closed when synthetic WordPress login cannot establish a fresh downstream session

- Scope: Stack-specific, Stack A
- Confidence note: Retained validation item from Stack A review.
- Files to change:
  - `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
- What to change:
  - If synthetic WordPress login fails or yields no usable cookies for an authenticated SSO user, deny access or force re-authentication.
  - Do not proxy the request onward as an anonymous downstream user.
- Acceptance criteria:
  - Forced failure of the WordPress synthetic login path never results in anonymous upstream access.
  - The gateway returns a controlled deny/reauth response and logs the failure reason.

## 2. Design Decisions That Need User Input

1. Secret source and rollout method
   - Needed for `H1`.
   - Decide whether runtime secrets come from environment variables, Vault bootstrap, mounted secret files, or another gateway-owned source.
   - Also decide whether global session invalidation at cutover is acceptable.

2. Fail-closed behavior for indeterminate revocation
   - Needed for `H3` and `H8`.
   - Decide whether the standard response is `302` to re-authentication, `401/403`, or `503`.
   - Conservative default is: clear session and force fresh authentication for browser flows; return `503` for non-browser/backchannel infrastructure failures.

3. HTTPS boundary that is actually in scope
   - Needed for `H4`.
   - The gateway can enforce HTTPS for browser-facing traffic and for any upstreams that already expose TLS.
   - It cannot convert app-container `baseURI` hops to HTTPS without touching app-side support or adding a separate TLS-terminating component. Confirm whether residual internal HTTP is acceptable in this lab, or whether a gateway-managed proxy layer is required.

4. Canonical public origins per app
   - Needed for `H4` and `M2`.
   - Confirm the exact public origins for WordPress, WhoAmI, Jellyfin, Redmine, Grafana, and phpMyAdmin so redirects and OIDC namespace keys can be pinned.

5. Session-storage redesign for privileged material
   - Needed for `H6` and `M4`.
   - Decide whether privileged data should move to Redis/server-side session storage, be re-fetched from Vault per request, or use another gateway-owned cache keyed by an opaque session ID.

6. Jellyfin browser-token replacement
   - Needed for `M4`.
   - Decide what replaces the current `localStorage` handoff: `httpOnly` cookie, gateway session exchange endpoint, or another server-side pattern.

7. Stack B auxiliary dotnet route scope
   - Needed for `H4`.
   - `stack-b/openig_home/config/routes/01-dotnet.json` appears in the reviewed gateway config even though the business context names Redmine and Jellyfin. Confirm whether transport hardening should include this route now or be excluded intentionally.

8. phpMyAdmin cookie cleanup intent
   - Needed for `H7`.
   - Confirm that the intended direction is to wire `PhpMyAdminCookieFilter` into the live chain, not to delete it as dead code.

9. WordPress failure-mode policy
   - Needed for `M7` and `M8`.
   - Confirm that failed synthetic-login or expired downstream sessions must always fail closed, even for endpoints that might currently tolerate anonymous access.

## 3. Proposed Execution Order

1. Resolve design decisions `2`, `3`, `4`, `5`, and `6` first.
   - Rationale: these decisions define the response semantics, canonical origins, and storage model that several other fixes depend on.

2. Implement revocation correctness as the first technical batch: `H2`, `H3`, `H8`, `M3`, `M5`.
   - Rationale: this restores the core logout and revocation guarantee under normal operation and under Redis faults.
   - Dependency note: `H8` should land with `H3` so timeout outcomes do not accidentally reintroduce fail-open behavior.

3. Fix direct stack-specific correctness breaks next: `H5` and `H7`.
   - Rationale: these are localized, high-signal defects with low dependency on broader design choices.
   - `H5` is the fastest high-value fix because Jellyfin logout is silently wrong today.

4. Implement secret removal and session-footprint reduction next: `H1`, `H6`, `M4`.
   - Rationale: these changes are high value but more invasive and may require coordinated rollout/testing.
   - Dependency note: `H6` and `M4` should share the same chosen server-side session/token pattern.

5. Implement transport and redirect hardening after canonical-origin decisions are locked: `H4`, `M2`, `M1`.
   - Rationale: these fixes touch many routes/scripts and are easiest to do correctly once the public URL contract is fixed.
   - `M1` can ship with the same Stack A logout update as the redirect hardening.

6. Finish Stack A retained medium items last: `M6`, `M7`, `M8`.
   - Rationale: they are narrower than the cross-stack high items, and they benefit from the fail-closed posture already established earlier.

## 4. Cross-Stack vs Stack-Specific Summary

Cross-stack work packages:
- `H1` Externalize committed secrets and rotate.
- `H2` Align revocation TTL.
- `H3` Fail closed on revocation lookup failure.
- `H4` Enforce secure public URLs and HTTPS semantics.
- `H6` Remove privileged backend material from `JwtSession` for Stack B and Stack C.
- `H8` Add explicit Redis timeouts.
- `M2` Pin redirect origins and namespace resolution.
- `M3` Return `5xx` for backchannel infrastructure failures in Stack B and Stack C.
- `M5` Standardize revocation keying on `sid`.

Stack-specific work packages:
- `H5` Stack B Jellyfin logout namespace mismatch.
- `H7` Stack C phpMyAdmin cookie filter wiring.
- `M1` Stack A logout URL token logging.
- `M4` Stack B Jellyfin `localStorage` token persistence.
- `M6` Stack A WordPress cookie deduplication.
- `M7` Stack A unsafe-method retry behavior.
- `M8` Stack A synthetic WordPress login fail-open path.
