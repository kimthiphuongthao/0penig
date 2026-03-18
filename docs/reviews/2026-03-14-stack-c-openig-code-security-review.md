# Stack C OpenIG Code + Security Evidence Review

Date: 2026-03-14  
Scope: Evidence review for Stack C OpenIG gateway mechanisms only: Grafana trusted-header injection, phpMyAdmin HTTP Basic Auth injection, shared OIDC session handling, RP-initiated logout, backchannel logout, revocation enforcement, and Vault-backed credential retrieval.

## 1) Executive Summary

Stack C carries the same core gateway weaknesses already seen in earlier stacks: committed secrets, revocation entries that expire before the OpenIG session, and fail-open revocation checks when Redis is unavailable. Stack C also adds two pattern-specific risks that matter for a reusable standard: phpMyAdmin credentials and Vault tokens are stored inside the browser-bound `JwtSession`, and the dedicated `PhpMyAdminCookieFilter.groovy` safeguard is not wired into the route chain at all.

**[UPDATED 2026-03-17]** Current repo state beyond this historical review:
- F1 resolved across STEP-02 (`37672ed`) and STEP-03 (`b738577`) — Stack C OIDC secrets rotated, compose secrets moved to `.env`, and OpenIG pinned to `6.0.1`.
- F2 resolved in live state (`9cbf71a`) — blacklist TTL now aligns with `JwtSession.sessionTimeout: "30 minutes"`.
- F3 resolved in FIX-03 (`278a29c`) — blacklist checks now fail closed.
- F5 resolved in FIX-09 (`76b648a`, `c0c491d`) — Vault tokens moved server-side and phpMyAdmin credentials moved to transient `attributes`.
- F6 retired in STEP-01 (`20d523f`) — `PhpMyAdminCookieFilter.groovy` was deleted after the WONT_FIX decision.
- F7 resolved in FIX-03/04 (`278a29c`) — Redis socket timeouts added.
- F8 resolved in FIX-05 (`9b770cd`) — internal backchannel failures now return `500`.
- F9 resolved in FIX-08 (`7fc73ba`) plus Step 5 env rollout (`aaf66d5`) — redirects now use pinned origins.
- F4 remains an explicit lab exception (HTTP transport). Operational follow-up closed 2026-03-18: Grafana SSO/SLO re-validation passed after rotating APP5 to a strong alphanumeric-only secret; the prior padding theory was superseded by the confirmed OpenIG `client_secret` URL-encoding limitation.

The strongest implementation in Stack C is still the backchannel logout token validator. It performs explicit `alg` pinning, JWKS lookup, RSA signature verification, and `iss`/`aud`/`events`/`iat`/`exp` checks before writing revocation state. The problem is not token validation correctness; it is the surrounding session, transport, and failure-mode design.

## 2) Mechanisms Reviewed

- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-c/openig_home/scripts/groovy/PhpMyAdminCookieFilter.groovy`
- `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `stack-c/openig_home/scripts/groovy/SloHandlerGrafana.groovy`
- `stack-c/openig_home/scripts/groovy/SloHandlerPhpMyAdmin.groovy`
- `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
- `stack-c/openig_home/config/config.json`
- `stack-c/openig_home/config/admin.json`
- `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json`
- `stack-c/openig_home/config/routes/00-backchannel-logout-app6.json`
- `stack-c/openig_home/config/routes/00-grafana-logout.json`
- `stack-c/openig_home/config/routes/00-phpmyadmin-logout.json`
- `stack-c/openig_home/config/routes/10-grafana.json`
- `stack-c/openig_home/config/routes/11-phpmyadmin.json`

**[UPDATED 2026-03-17]** Status as of Pattern Consolidation Steps 2-4: the `SloHandlerGrafana.groovy` and `SloHandlerPhpMyAdmin.groovy` entries above are historical review targets from 2026-03-14. Step 4 consolidated the active Stack C logout logic into the parameterized `SloHandler.groovy` template, resolved the missing try-catch finding (`H-1`), and updated the phpMyAdmin `failureHandler` in commit `3b8a6d8`.

## 3) Confirmed Strengths

- Backchannel logout validation is materially stronger than the rest of the stack. `BackchannelLogoutHandler.groovy` validates the JWT structure, requires `alg=RS256`, resolves the signing key by `kid`, verifies the RSA signature, and checks `iss`, `aud`, `events`, `iat`, and `exp` before accepting a logout token (`BackchannelLogoutHandler.groovy:145-215`, `BackchannelLogoutHandler.groovy:221-289`).
- Route ordering is correct for logout flows. The backchannel and RP-initiated logout routes are placed in `00-*` files ahead of the app routes in `10-grafana.json` and `11-phpmyadmin.json`, which reduces accidental route shadowing (`00-backchannel-logout-app5.json:1-11`, `00-backchannel-logout-app6.json:1-11`, `00-grafana-logout.json:1-11`, `00-phpmyadmin-logout.json:1-11`).
- phpMyAdmin credentials are retrieved from Vault at runtime rather than committed directly into route JSON. That is stronger than static per-app passwords in config, although the session-storage model still weakens the overall design (`VaultCredentialFilter.groovy:96-157`).

## 4) Confirmed Findings

### F1. Committed session and OIDC client secrets

- Severity: HIGH
- Evidence:
  - `stack-c/openig_home/config/config.json:14-16`
  - `stack-c/openig_home/config/config.json:24-29`
  - `stack-c/openig_home/config/routes/10-grafana.json:22-23`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json:22-23`
- Issue: the PKCS12 password, `JwtSession` password, `JwtSession.sharedSecret`, and both OIDC `clientSecret` values are committed in plaintext config.
- Impact: anyone with repository or filesystem access can mint or decrypt session material, impersonate OIDC clients, and enlarge the blast radius of any cookie theft or config leak.
- Reusable-pattern implication: a standard gateway pattern cannot treat gateway crypto keys or client secrets as deploy-time literals.
- Minimal fix: move all gateway and OIDC secrets to runtime secret sources and rotate the exposed values before treating Stack C as a reusable reference.

### F2. Revocation TTL is shorter than the OpenIG session lifetime

- Severity: HIGH
- Evidence:
  - `stack-c/openig_home/config/config.json:20-29`
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:304-318`
- Issue: `JwtSession` lasts `8 hours`, but backchannel logout writes `blacklist:<sid>` with `EX 3600`.
- Impact: a revoked session can become valid again after one hour even though the browser still carries a live `IG_SSO` cookie.
- Reusable-pattern implication: logout correctness depends on revocation state living at least as long as the session it invalidates.
- Minimal fix: set blacklist TTL to the session maximum or to the remaining session lifetime plus clock skew.

### F3. Revocation enforcement fails open when Redis cannot be queried

- Severity: HIGH
- Evidence:
  - `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:123-155`
- Issue: if Redis lookup throws, the catch block logs a warning and continues the request.
- Impact: any Redis outage, timeout, or packet loss turns revocation into best-effort behavior and allows revoked sessions to continue reaching Grafana or phpMyAdmin.
- Reusable-pattern implication: SLO/backchannel logout guarantees are only meaningful if the request path fails closed when revocation state is indeterminate.
- Minimal fix: for authenticated sessions, clear the local session and deny or re-authenticate when revocation status cannot be confirmed.

### F4. Stack C still permits plaintext HTTP for OIDC, logout, Vault, and gateway session traffic

- Severity: HIGH
- Evidence:
  - `stack-c/openig_home/config/routes/10-grafana.json:3`
  - `stack-c/openig_home/config/routes/10-grafana.json:10-15`
  - `stack-c/openig_home/config/routes/10-grafana.json:45-47`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json:3`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json:10-15`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json:45-47`
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:18-19`
  - `stack-c/openig_home/scripts/groovy/SloHandlerGrafana.groovy:9,30-39`
  - `stack-c/openig_home/scripts/groovy/SloHandlerPhpMyAdmin.groovy:9,30-38`
  - `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy:38,41,96-105,129-134`
- Issue: route `baseURI` values are HTTP, OIDC endpoints are HTTP, both `OAuth2ClientFilter` instances have `requireHttps: false`, Vault defaults to `http://vault:8200`, and logout flows construct HTTP redirect targets.
- Impact: OIDC codes/tokens, `id_token_hint`, Vault tokens, phpMyAdmin credentials, and the browser session cookie can all traverse the lab over plaintext links.
- Reusable-pattern implication: a standard gateway pattern must define TLS as part of the mechanism, not as an optional deployment detail.
- Minimal fix: require HTTPS end-to-end, pin secure public URLs in config, and treat HTTP-only lab wiring as non-reference scaffolding.

**[UPDATED 2026-03-17]** Status as of Pattern Consolidation Steps 2-4: the `SloHandlerGrafana.groovy` and `SloHandlerPhpMyAdmin.groovy` evidence lines above are historical references only. Step 4 consolidated those Stack C handlers into the shared parameterized `SloHandler.groovy` template and resolved the missing try-catch issue (`H-1`), but the plaintext-HTTP finding remains open.

### F5. Vault token and downstream phpMyAdmin credentials are stored in the browser-bound `JwtSession`

- Severity: HIGH
- Evidence:
  - `stack-c/openig_home/config/config.json:20-29`
  - `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy:78-80`
  - `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy:122-125`
  - `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy:156-157`
- Issue: ~~Stack C writes `vault_token`, `vault_token_expiry`, `phpmyadmin_username`, and `phpmyadmin_password` into the OpenIG session~~ **RESOLVED (FIX-09, commits 76b648a + c0c491d)**: Vault token → server-side `globals` cache; phpMyAdmin creds → transient `attributes` (per-request only, never in cookie). `HttpBasicAuthFilter` supports `${attributes.key}` EL (confirmed from OpenIG source).
- Impact: ~~theft of the session cookie exposes Vault session material and downstream app credentials~~ **Mitigated**: only OAuth2 tokens (required for OIDC) remain in JwtSession cookie.
- Reusable-pattern implication: HTTP Basic Auth injection now uses transient `attributes` — no browser-carried copies of privileged backend credentials.
- ~~Minimal fix: keep Vault and downstream credentials in a server-side store~~ **Implemented**: `globals` (ConcurrentHashMap) for Vault token; `attributes` (per-request) for credentials.

### F6. The phpMyAdmin cookie-reconciliation control exists in code but is not wired into the route chain

- Severity: HIGH
- Evidence:
  - `stack-c/openig_home/scripts/groovy/PhpMyAdminCookieFilter.groovy:100-120`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json:107-115`
- Issue: `PhpMyAdminCookieFilter.groovy` is written to detect when the browser still holds a `phpMyAdmin` cookie for a different SSO user and to expire that cookie before forwarding the response, but the `phpmyadmin-sso` chain never includes that filter.
- Impact: stale downstream phpMyAdmin cookies can survive SSO user changes or partial logout flows, weakening the binding between the current OIDC user and the downstream phpMyAdmin session.
- Reusable-pattern implication: app-specific session-cleanup controls must be part of the configured chain, not just present in the scripts directory.
- Minimal fix: either wire the filter into `11-phpmyadmin.json` or remove it from the reference set and document that Stack C currently lacks downstream cookie cleanup.

### F7. Redis socket operations have no explicit connect or read timeout in either the write or read path

- Severity: MEDIUM
- Evidence:
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:305-318`
  - `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:123-136`
- Issue: both revocation write and revocation read use raw `new Socket(host, port)` calls without `connectTimeout` or `soTimeout`.
- Impact: a slow or half-open Redis connection can pin OpenIG worker threads, expand latency, and turn revocation checks into an availability risk.
- Reusable-pattern implication: revocation design needs bounded latency semantics, not just logical correctness.
- Minimal fix: set explicit connection and read timeouts and decide how timeout outcomes map to fail-closed behavior.

### F8. Backchannel internal failures are downgraded to HTTP 400

- Severity: MEDIUM
- Evidence:
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:323-328`
- Issue: unexpected runtime failures fall into the generic `Exception` handler, which returns `400 Bad Request` instead of a server error.
- Impact: Keycloak or any OP delivering backchannel logout can misinterpret infrastructure failures as permanent client-side validation failures and may not retry delivery.
- Reusable-pattern implication: logout endpoint status codes need to distinguish malformed logout tokens from transient infrastructure faults.
- Minimal fix: reserve `400` for invalid logout tokens and return `5xx` for Redis/JWKS/runtime failures.

### F9. Public URL and redirect behavior still depend on the inbound `Host` header

- Severity: MEDIUM
- Evidence:
  - `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:52-55`
  - `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:76-89`
  - `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:141-147`
  - `stack-c/openig_home/config/routes/10-grafana.json:4`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json:4`
- Issue: session-key lookup candidates and blacklist redirect targets are derived from the request `Host`, and the main app-route host regexes are broad (`grafana-c.sso.local.*`, `phpmyadmin-c.sso.local.*`) rather than pinned public origins.
- Impact: if the upstream proxy does not normalize `Host`, the gateway can build session lookup keys or redirects from attacker-controlled hostnames, creating domain confusion and redirect integrity risk.
- Reusable-pattern implication: redirect origins and session namespace roots should come from pinned config, not inbound request headers.
- Minimal fix: define canonical public origins per app and use those values for redirect construction and OAuth2 session key resolution.

## 5) Stack-C-Specific Pattern Notes

- Grafana represents the trusted-header injection pattern. The positive control is that the gateway injects a derived username into `X-WEBAUTH-USER` only after OIDC/session processing (`10-grafana.json:76-87`, `SessionBlacklistFilter.groovy:95-105`). The negative control is that the same session object now becomes the trust anchor for both logout enforcement and identity injection.
- phpMyAdmin represents the HTTP Basic Auth injection pattern. Stack C improves secret sourcing by pulling credentials from Vault, but weakens the pattern again by serializing those credentials into the client-facing OpenIG session (`VaultCredentialFilter.groovy:122-157`, `11-phpmyadmin.json:84-89`).
- Stack C is the first reviewed stack where downstream app cookie reconciliation is clearly designed in code but absent from the configured filter chain. That makes it a useful evidence case for why adapter-specific cleanup controls must be mandatory in the reference pattern, not optional.

## 6) Priority for Standardization Work

1. Close revocation correctness gaps first: fail-open reads, short blacklist TTL, and unbounded Redis socket behavior.
2. Remove committed secrets and stop storing Vault/app credentials in the browser-bound gateway session.
3. Treat HTTPS as mandatory for all OIDC, Vault, logout, and app-facing traffic.
4. Make app-specific cleanup logic part of the route contract so representative adapters cannot silently drift from the intended design.
5. Pin public origins in config and remove remaining `Host`-derived redirect/session-key behavior.

## 7) Cross-Stack Comparison Anchors

- Same as Stack A and Stack B: committed secrets, revocation TTL mismatch, and fail-open revocation logic remain the dominant shared findings.
- Stronger than Stack A/B in one area: Stack C keeps token-bearing logout URLs out of logs; the SLO handlers log request metadata and missing-token warnings, but not the assembled URL.
- Unique to Stack C: Vault-backed HTTP Basic Auth injection, browser-stored Vault/app credentials, and an unwired phpMyAdmin cookie cleanup control.
