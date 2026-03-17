---
# Standard OpenIG SSO/SLO Gateway Pattern
**Version:** 1.1
**Date:** 2026-03-15
**Derived from:** Code and security review of 3 integration stacks (WordPress, Redmine+Jellyfin, Grafana+phpMyAdmin)
**Scope:** OpenIG 6 + Keycloak + Vault + Redis

> Update 2026-03-17: Pattern Consolidation Steps 1-6 are complete. The lab implementation now also matches more of this pattern operationally: Redmine no longer exposes host port `3000`, Stack C nginx carries the same proxy buffer settings as A/B, all 3 stacks declare app-specific `CANONICAL_ORIGIN_APP*` env vars, Stack C OIDC secrets were rotated, and compose secrets now live in gitignored `.env` files while OpenIG stays pinned to `6.0.1`.

---

## Overview

This pattern defines the reference gateway contract for OpenIG-based SSO and SLO when OpenIG sits between the browser and heterogeneous downstream applications. It standardizes revocation, secret handling, session storage, logout sequencing, transport security, redirect integrity, and adapter wiring so the gateway remains correct even when applications use different login mechanisms. Derived from: Cross-Stack Summary Universal Findings; Stack A `§5 F1-F5`; Stack B `F1-F11`; Stack C `§4 F1-F9`.

The three reviewed stacks cover five login mechanisms that a reusable gateway pattern must support: standard OIDC login without downstream credential injection (Stack A), downstream credential injection and token injection into API-first applications (Stack B), and trusted-header plus HTTP Basic Auth injection (Stack C). The common failures across those stacks were not app-specific bugs; they were gateway-contract failures around revocation, secrets, transport, and origin handling. Derived from: Cross-Stack Summary "Login Mechanism Pattern Risk Matrix"; Stack A `§3`; Stack B "Summary" and `F5-F8`; Stack C `§5`.

This document describes the correct pattern, not the current state of the lab stacks. Where the reviewed stacks show the right mechanism shape, that shape is retained; where they show unsafe behavior, the pattern replaces it with a prescriptive control. Derived from: Cross-Stack Summary "Recommended Standard Pattern"; Stack A `§4` and `§6`; Stack B "Confirmed Strengths"; Stack C `§3` and `§6`.

## Login Mechanism Coverage

Derived from: Cross-Stack Summary "Login Mechanism Pattern Risk Matrix"; Stack B "Summary"; Stack C `§5`.

| Pattern | Representative App Type | Session Entry Point | Session Exit Point |
|---|---|---|---|
| OIDC Standard | Form-based or redirect-based app with no downstream credential injection (WordPress, WhoAmI) | Browser is redirected by `OAuth2ClientFilter` to Keycloak; downstream app relies on the established OpenIG session | RP-initiated logout handler plus backchannel logout enforcement on subsequent requests |
| Credential Injection | Server-rendered app whose native login requires username/password (Redmine) | OpenIG completes OIDC, retrieves downstream credentials, and performs app login on behalf of the user | RP-initiated logout plus adapter-specific downstream session cleanup and revocation enforcement |
| Token Injection + browser storage | API-first app that expects bearer token state in the browser (Jellyfin) | OpenIG completes OIDC, obtains downstream token material, and adapter bridges it to the browser-facing app session | RP-initiated logout plus backchannel logout; browser token state must be cleared by the adapter contract |
| Trusted Header Injection | App that trusts gateway-supplied identity headers (Grafana) | OpenIG completes OIDC and injects identity into a trusted header on proxied requests | Gateway logout and revocation; downstream identity ends when trusted-header injection stops |
| HTTP Basic Auth Injection | App authenticated by `Authorization: Basic` header (phpMyAdmin) | OpenIG completes OIDC, retrieves downstream credentials, and injects `Authorization: Basic` on proxied requests | Gateway logout and revocation plus downstream cookie reconciliation where the app also issues its own cookie |

## Pattern Architecture

Derived from: Cross-Stack Summary "Recommended Standard Pattern"; Stack B `F4`, `F7`, `F9-F10`; Stack C `§3`, `§4 F4`, `§4 F6-F9`; Stack A `§4`, `§5 F2-F5`, `§6`.

The standard deployment places nginx in front of OpenIG and treats OpenIG as the enforcement point for session, revocation, logout, and adapter logic. Keycloak remains the shared OIDC provider and backchannel logout initiator. Vault is the runtime source of gateway and downstream secrets. Redis is the revocation store and must be bounded so it cannot silently degrade logout correctness or stall the gateway. The reviewed stacks show that these concerns must be designed as one contract, not as independent scripts.

Text diagram:

```text
Browser
  |
  v
nginx
  - TLS termination
  - sticky routing for HA deployments (inference from Stack B 2-node HA scope)
  - strip or normalize inbound Host and trusted identity headers
  |
  v
OpenIG
  - SessionBlacklistFilter
  - app-specific adapter filters
  - proxy handler
  |
  v
Downstream App

Keycloak <---- OIDC / end_session / backchannel logout ---- OpenIG
Vault    <---- runtime secret retrieval ------------------- OpenIG
Redis    <---- blacklist read/write ----------------------- OpenIG
```

Key components and roles:

- `nginx`: terminate TLS, normalize routing before OpenIG, and remove inbound trusted identity/header inputs that the downstream app must accept only from the gateway. The HA sticky-routing note is an inference from Stack B's reviewed 2-node HA topology rather than a direct finding. Derived from: Stack B "Scope" and "Summary"; Stack B `F4`, `F7`; Stack C `§4 F4`, `§4 F9`.
- `OpenIG` filter chain: enforce revocation first, then run the adapter-specific filters needed for the chosen login mechanism before proxying. App cleanup and logout helpers are part of this route contract only when they are wired into the chain. Derived from: Stack A `§5 F2-F5`, `§6`; Stack B `F5`; Stack C `§4 F6`.
- `Vault`: provide runtime secret material for gateway crypto, OIDC clients, and downstream credentials instead of repo-managed literals. Derived from: Stack A `§5 F1`; Stack B `F1`; Stack C `§4 F1` and `§3`.
- `Redis`: hold revocation state with TTL at least equal to the gateway session lifetime and with bounded socket behavior. Derived from: Stack A `§5 F2-F3`, `§6`; Stack B `F2-F3`, `F9-F10`, `F11`; Stack C `§4 F2-F3`, `§4 F7-F8`.
- `Keycloak`: act as the shared IdP, OIDC issuer, and backchannel logout sender. The reviewed strengths show that OpenIG must validate logout tokens fully before writing revocation state. Derived from: Stack A `§4`; Stack B "Confirmed Strengths"; Stack C `§3`.

## Required Controls (MUST)

### 1. Revocation Contract
[Derived from: A F2/F3, B F2/F3, C F2/F3, B F11 [low confidence: 1/4 reviewers]]

What it is: Redis blacklist TTL MUST be greater than or equal to `JwtSession.sessionTimeout`. On Redis lookup failure, the gateway MUST fail closed for authenticated sessions by returning `503` or forcing re-authentication; it MUST NOT proxy the request onward. On Redis write failure during backchannel logout, the handler MUST return `5xx`, not `4xx`. The same `sid` key MUST be used on both the write path and the read path.

Why: The reviewed stacks show the same two failure modes repeatedly: revocation state expires before the browser session does, and revocation checks continue on Redis failure. Stack B also shows that `sid`/`sub` drift can break enforcement even when both paths exist. [Note: B F11 (sid vs sub mismatch) was confirmed by 1/4 reviewers and flagged for investigation.] Derived from: Stack A `§5 F2-F3`; Stack B `F2-F3`, `F11`; Stack C `§4 F2-F3`; Cross-Stack Summary Universal Findings.

How to implement in OpenIG: `BackchannelLogoutHandler` must validate logout tokens before writing `blacklist:<sid>` to Redis with TTL aligned to session lifetime, and `SessionBlacklistFilter` must read that same `sid` key on every authenticated request. The logout token validator MUST check `alg=RS256`, resolve the signing key from JWKS by `kid`, and validate `iss`, `aud`, `events`, `iat`, and `exp` before writing revocation state. Derived from: Stack A `§4`; Stack B "Confirmed Strengths"; Stack C `§3`.

> **Implementation (2026-03-17):** SessionBlacklistFilter.groovy — parameterized template, configured via route args: clientEndpoint, sessionCacheKey, canonicalOrigin.

### 2. Secret Externalization
[Derived from: A F1, B F1, C F1]

What it is: All `JwtSession.sharedSecret`, OIDC `clientSecret`, and keystore passwords MUST come from Vault or environment at runtime. They MUST NOT appear in `config.json`, route JSON, or Groovy source.

Why: All three stacks exposed gateway or OIDC secrets in repo-managed config, which turns a stack-local mistake into a reusable gateway flaw and expands the blast radius of cookie theft or config disclosure. Derived from: Stack A `§5 F1`; Stack B `F1`; Stack C `§4 F1`; Cross-Stack Summary Universal Findings.

How to implement in OpenIG: Use a `VaultCredentialFilter`-style runtime secret source and inject the resulting values into route/filter configuration without serializing them into `JwtSession`. An implementation pattern inferred from the reviewed Vault-backed adapters is: fetch at startup, cache with TTL, and refresh before expiry rather than storing fetched secrets in browser-bound session state. Derived from: Stack A `§4`; Stack C `§3`; Stack C `§4 F5`.

Deployment rule: compose-managed secrets MUST live in gitignored `.env` files or an equivalent runtime secret source. Commit `.env.example` as the contract, never commit `.env`, and never hardcode secret literals in `docker-compose.yml`.

Image rule: OpenIG containers MUST use an explicit image tag and MUST NOT use `:latest`. The validated lab baseline is `openidentityplatform/openig:6.0.1`; the mutable `latest` tag moved to a Tomcat 11 build and broke OpenIG 6 startup.

### 3. Transport Security
[Derived from: B F4, C F4, A §6]

What it is: All OIDC flows, Vault API calls, and downstream app proxying MUST use HTTPS in production. `JwtSession` cookies MUST carry the `Secure` flag, and every `OAuth2ClientFilter` instance MUST set `requireHttps: true`.

Why: Stack B and Stack C explicitly allow plaintext HTTP for OIDC, logout, Vault, and session traffic, and Stack A's additional review notes the same problem on Vault, JWKS, logout, and credential paths. That makes token theft, credential interception, and cookie interception part of the gateway design rather than a deployment mistake. Derived from: Stack B `F4`; Stack C `§4 F4`; Stack A `§6` Codex-only additions; Cross-Stack Summary Universal Findings.

How to implement in OpenIG: Use HTTPS `baseURI` and endpoint values in route config, set `requireHttps: true` on `OAuth2ClientFilter`, and issue only `Secure` cookies from `JwtSession`. If lab scaffolding remains HTTP-only, it is not a reference implementation. Derived from: Stack B `F4`; Stack C `§4 F4`.

> **Lab Exception (FIX-07 Phase 7a):** All traffic in this lab runs over plaintext HTTP. `requireHttps: false` is intentional — setting it to `true` without TLS infrastructure in place causes every OIDC flow to fail immediately. Full HTTPS requires: a self-signed CA distributed to all containers, nginx TLS termination configured per stack, Docker networking changes to route port 443, and the CA cert trusted by the JVM inside OpenIG. Phase 7b (actual TLS with self-signed CA and nginx termination) is deferred to the Vault Production Hardening phase. Until Phase 7b is complete, this lab is not a production reference for transport security.

### 4. Session Storage Boundaries
[Derived from: B F6 [low confidence: 1/4 reviewers], B F8, C F5]

What it is: `JwtSession` MUST NOT store Vault tokens, downstream app credentials, downstream app session cookies, or bearer tokens. Sensitive adapter state MUST use server-side storage with an opaque session reference. Bearer tokens MUST NOT be injected into browser `localStorage` or any other JavaScript-accessible storage. If browser-side token storage is unavoidable, it MUST use `httpOnly`, `Secure` cookies.

Why: Stack B stores Vault tokens and downstream session material in the browser-bound gateway session and exposes Jellyfin access tokens in `localStorage`. Stack C stores Vault token and phpMyAdmin credentials inside the browser-bound `JwtSession`. Those findings show that adapter convenience can collapse the boundary between gateway identity state and privileged backend material. [Note: B F6 (tokens in JwtSession) was confirmed by 1/4 reviewers.] Derived from: Stack B `F6`, `F8`; Stack C `§4 F5`; Cross-Stack Summary Stack-Specific Findings.

How to implement in OpenIG: Keep only opaque identity/session references in `JwtSession`; keep privileged adapter material server-side and rehydrate it in the adapter filter chain as needed. For browser-facing token delivery, prefer `httpOnly`, `Secure` cookies over response rewriting into JavaScript-visible storage. Derived from: Stack B `F6`, `F8`; Stack C `§4 F5`.

### 5. Pinned Origins and Redirect Integrity
[Derived from: A F5 [low confidence: 2/4 reviewers], B F7, C F9, B F5]

What it is: All redirect base URLs, post-logout targets, and OAuth2 session namespace roots MUST be pinned as static config constants. The gateway MUST NOT derive redirect targets or session namespace roots from inbound request headers such as `Host` or `X-Forwarded-Host`. The `OAuth2ClientFilter` client namespace used in session storage MUST match exactly the client registration used by the route.

Why: Stack A, Stack B, and Stack C all derive redirect or session-resolution behavior from inbound host data, and Stack B shows a separate integrity failure where the logout handler reads the wrong OIDC namespace and silently fails RP-initiated logout. [Note: A F5 (Host-derived redirects) was confirmed by 2/4 reviewers.] Derived from: Stack A `§5 F5`; Stack B `F5`, `F7`; Stack C `§4 F9`; Cross-Stack Summary Universal Findings and Stack-Specific Findings.

How to implement in OpenIG: Define canonical public origin constants per route and use them for redirect construction, post-logout redirect URIs, and OIDC session-key lookup. Verify that the namespace used by SLO handlers matches the route's registered `OAuth2ClientFilter` client ID, and configure nginx to strip or normalize inbound host-related headers before the request reaches OpenIG. **Stack B note:** The namespace drift between `SloHandlerJellyfin` and the active `OAuth2ClientFilter` client ID (`openig-client-b-app4`) was Stack B's priority #1 remediation item. **RESOLVED** (commit a3cb6c3, 2026-03-15): namespace corrected to `app4`, `OIDC_CLIENT_ID_APP4` env var added, `post_logout_redirect_uri` restored with null-check for `id_token_hint`. Derived from: Stack B `F5`, `F7`; Stack C `§4 F9`; Stack A `§5 F5`.

### 6. Bounded Dependency Behavior
[Derived from: B F9, C F7, B F10, C F8]

What it is: All Redis socket operations MUST set explicit `connectTimeout` and `soTimeout`; the reviewed recommendation is `200ms` connect and `500ms` read. `BackchannelLogoutHandler` MUST return `400` only for malformed or invalid logout tokens and MUST return `500` for Redis, JWKS, or runtime failures. Redis unavailability SHOULD be wrapped in a circuit-breaker or equivalent bounded-failure mechanism.

Why: Stack B and Stack C both show unbounded Redis socket behavior, and both downgrade internal backchannel failures to `400`, which can stop logout retry behavior at the IdP side. Those are not correctness bugs only; they are latency and delivery-contract bugs. Derived from: Stack B `F9-F10`; Stack C `§4 F7-F8`; Cross-Stack Summary "Recommended Standard Pattern".

How to implement in OpenIG: Replace raw unbounded socket usage with explicit connect/read timeouts on both revocation reads and writes, and map error classes so token validation errors return `400` while infrastructure/runtime faults return `5xx`. Add a circuit-breaker or equivalent short-circuit around Redis if repeated failures would otherwise pin worker threads. Derived from: Stack B `F9-F10`; Stack C `§4 F7-F8`.

### 7. Adapter Contract Enforcement
[Derived from: C F6, A §6, B F5]

What it is: Each login mechanism adapter MUST define its required filter-chain components as non-optional route contract elements. App-specific cleanup controls, downstream cookie expiry, and session invalidation hooks MUST be wired into the route chain rather than left as helper scripts. Adapter logout hooks MUST be verified end to end, including confirmation that `id_token_hint` is non-null before the end-session URL is built.

Why: Stack C shows a safeguard filter that exists in code but is absent from the route chain. Stack B shows that an SLO handler can drift away from the active OAuth2 namespace and silently stop working. Stack A's additional review notes show that helper-side login/retry logic can degrade into unsafe behavior when the route contract does not enforce it. Derived from: Stack C `§4 F6`; Stack B `F5`; Stack A `§6` Codex-only additions and Subagent-only findings.

How to implement in OpenIG: Treat route JSON and Groovy scripts as one adapter unit: the route must declare every required cleanup and identity filter explicitly, and logout handlers must validate that the expected OIDC namespace and token are present before redirecting to Keycloak. Presence of a script in `scripts/` is not evidence that the control is active. Derived from: Stack C `§4 F6`; Stack B `F5`; Stack A `§6`.

> **Implementation (2026-03-17):** SloHandler.groovy — parameterized template, configured via route args: clientEndpoint, clientId, canonicalOrigin, postLogoutPath. App-specific variant: SloHandlerJellyfin.groovy for apps requiring pre-logout API calls.

## Recommended Controls (SHOULD)

### 8. Logout Observability
[Derived from: A F4]

Logout handlers SHOULD NOT log full redirect URLs that contain `id_token_hint` or any other token value. They SHOULD log only timestamp, opaque session identifier, logout type (`RP-initiated` or `backchannel`), and result (`success` or `failure`).

Why: Stack A logs the full logout URL, which leaks `id_token_hint` into logs. Stack C is a useful positive contrast because it logs request metadata and missing-token warnings without logging the assembled token-bearing URL. Derived from: Stack A `§5 F4`; Stack C `§7` Cross-Stack Comparison Anchors.

### 9. Unsafe Method Reauth Safety
[Derived from: A §6]

If a session-expired request uses `POST`, `PUT`, `PATCH`, or `DELETE`, the gateway SHOULD NOT silently redirect and lose the request body. It SHOULD either return `401` with a re-authentication hint or preserve sufficient request context for replay after re-authentication.

Why: Stack A's additional code-review finding shows that redirect-based retry logic can drop the original request body. That is a request-semantics risk in adapters that assume every expired request is safe to redirect. Derived from: Stack A `§6` Codex-only additions.

### 10. Adapter Failure Mode
[Derived from: A §6]

If adapter credential injection fails because Vault is unreachable, credential lookup fails, or downstream synthetic login cannot complete, the gateway SHOULD fail closed and return `503` rather than proxy the request unauthenticated.

Why: Stack A's additional review notes identify a synthetic login failure path that can degrade into unauthenticated proxying. The pattern consequence is that adapter failure must preserve authentication guarantees, not bypass them. Derived from: Stack A `§6` Subagent-only findings.

## Confirmed Strengths (reference implementations)

The following patterns were validated across the reviewed stacks and demonstrate correct implementation shapes that should be retained in new integrations.

### Route ordering

Logout and backchannel routes MUST be registered before app routes (e.g. `00-*.json` before `10-*.json`). Both Stack B and Stack C demonstrate this as a confirmed working pattern. This ordering ensures that logout and session-revocation endpoints are reached before the main application gateway logic, preventing stale session checks from interrupting the logout flow. Derived from: Stack B "Confirmed Strengths"; Stack C `§3`.

### Credential injection self-healing

For credential-injection adapters (Redmine, form-login apps), the adapter SHOULD retry credential injection on upstream 401 responses rather than failing the session. This pattern handles credential rotation and session expiry gracefully. When downstream app credentials expire but the gateway session remains valid, a transparent retry with refreshed credentials allows the user to continue without re-authentication. Derived from: Stack B "Confirmed Strengths".

### Identity injection ordering

For trusted-header adapters (Grafana-style), the gateway MUST inject the identity header (e.g. `X-WEBAUTH-USER`) only after OIDC session validation and revocation checks are complete. The identity header must never be injected on unauthenticated or revocation-indeterminate requests. This ensures that the downstream app never receives a spoofed or revoked identity. Derived from: Stack C `§5`.

### Backchannel logout token validation (H8)

All three stacks implement full RS256 logout token validation in `BackchannelLogoutHandler`: algorithm check (`alg=RS256`), JWKS lookup by `kid` with cache and kid-triggered re-fetch on miss, signature verification, and `iss`, `aud`, `events`, `iat`, `exp` claims validation before writing revocation state. This is a confirmed correct implementation shape that satisfies the validation requirements in Control 1 and the Backchannel Logout sequence. **IMPLEMENTED** across all stacks (H8 security hardening, 2026-03-14). Derived from: Stack A `§4`; Stack B "Confirmed Strengths"; Stack C `§3`.

## SLO Flow — Standard Sequence

This sequence standardizes both RP-initiated and backchannel logout so logout correctness does not depend on stack-specific scripts or best-effort Redis behavior. Derived from: Stack A `§4`, `§5 F2-F5`; Stack B "Confirmed Strengths", `F2-F5`, `F10-F11`; Stack C `§3`, `§4 F2-F3`, `§4 F7-F9`.

### RP-Initiated Logout (user clicks logout)

Derived from: Stack A `§5 F4-F5`; Stack B `F5`, `F7`; Stack C `§4 F9`.

1. Browser calls the OpenIG `SloHandler`.
2. `SloHandler` reads `id_token` from `JwtSession`, and the namespace it reads MUST match the `OAuth2ClientFilter` client ID configured for that route.
3. `SloHandler` builds the Keycloak `end_session` URL with `id_token_hint` and `post_logout_redirect_uri`, and that redirect target MUST come from pinned config rather than inbound `Host`.
4. `SloHandler` invalidates the local `JwtSession` so the browser cannot continue on a still-live local session while remote logout is in progress.
5. Browser is redirected to the Keycloak `end_session` endpoint.
6. Keycloak triggers backchannel logout to all registered OpenIG clients.

### Backchannel Logout (Keycloak-initiated)

Derived from: Stack A `§4`, `§5 F2-F3`; Stack B "Confirmed Strengths", `F2-F3`, `F10-F11`; Stack C `§3`, `§4 F2-F3`, `§4 F7-F8`.

1. Keycloak sends `POST /backchannel_logout` with a signed logout token.
2. `BackchannelLogoutHandler` validates the token: `alg=RS256`, JWKS lookup by `kid`, signature verification, and `iss`, `aud`, `events`, `iat`, `exp`.
3. On a valid token, the handler writes `blacklist:<sid>` to Redis with TTL equal to `sessionTimeout`.
4. Handler returns `200` to Keycloak.
5. On the next authenticated request, `SessionBlacklistFilter` checks Redis for that `sid`.
6. If the `sid` is blacklisted, the gateway fails closed: clears local session state and redirects to login or denies access.
7. If Redis is unreachable, the gateway fails closed with `503` or re-authentication; it MUST NOT pass the request through as authenticated.

## Anti-Patterns (MUST NOT)

Derived from: Cross-Stack Summary Universal Findings and Stack-Specific Findings; Stack A `§5 F1-F5`, `§6`; Stack B `F1-F11`; Stack C `§4 F1-F9`.

| Anti-pattern | Risk | Finding ref | Correct approach |
|---|---|---|---|
| Hardcoded secrets in config or route files | Repository or filesystem access exposes gateway signing material and OIDC client credentials | `A F1`, `B F1`, `C F1` | Externalize gateway and OIDC secrets to Vault or environment at runtime |
| Revocation TTL shorter than session lifetime | Revoked browser session becomes valid again after Redis entry expires | `A F3`, `B F2`, `C F2` | Set blacklist TTL to at least `JwtSession.sessionTimeout` |
| Fail-open on Redis errors | Redis outage turns logout enforcement into best-effort behavior | `A F2`, `B F3`, `C F3` | Fail closed for authenticated sessions when revocation state is indeterminate |
| Host-derived redirects | Redirect integrity and session lookup depend on attacker-influenced request headers | `A F5`, `B F7`, `C F9` | Pin origins and redirect targets in static config |
| Vault or app credentials in `JwtSession` | Cookie theft or shared-secret compromise exposes privileged backend material | `B F6`, `C F5` | Keep privileged adapter state server-side behind an opaque session reference |
| Bearer tokens in `localStorage` | Any same-origin JavaScript can read and persist bearer tokens | `B F8` | Use `httpOnly`, `Secure` cookies or server-side storage |
| Unwired adapter safeguard scripts | Intended cleanup control exists in code but is inactive in the live route chain | `C F6` | Make required adapter filters explicit route-chain elements |
| HTTP `400` for infrastructure failures in backchannel handler | IdP may treat transient failures as permanent and stop retrying logout delivery | `B F10`, `C F8` | Return `400` only for invalid logout tokens and `5xx` for internal failures |
| `id_token_hint` read from wrong OIDC namespace | RP-initiated logout silently fails because the expected OIDC session is not found | `B F5` | Bind logout handlers to the exact `OAuth2ClientFilter` namespace/client ID in the route. **RESOLVED** in Stack B (commit a3cb6c3, 2026-03-15). |
| Missing Redis socket timeouts | Slow or half-open Redis connections can pin worker threads and degrade availability | `A §6`, `B F9`, `C F7` | Set explicit connect and read timeouts on all revocation socket operations |

## Checklist — New Integration Review

Derived from: Cross-Stack Summary "Recommended Standard Pattern" and "Next Steps"; Stack A `§5 F1-F5`, `§6`; Stack B `F1-F11`; Stack C `§4 F1-F9`.

### Secret management

- [ ] `JwtSession.sharedSecret`, OIDC `clientSecret`, and keystore passwords come from Vault or environment at runtime and do not appear in config, routes, or Groovy.
- [ ] Any Vault-backed secret retrieval is cached with bounded TTL and refreshed before expiry without writing the fetched secret into `JwtSession`.
- [ ] OIDC client secrets use strong random values only. Minimum baseline: 32+ random bytes encoded as Base64. Trivially guessable values such as `secret-c` are a P1 security issue. Generate with `openssl rand -base64 32`.
- [ ] When copying Base64 secrets into `.env`, Keycloak, or another secret store, preserve the full value including any trailing `=` padding. Do not trim or re-wrap generated secrets.

### Session and revocation

- [ ] `BackchannelLogoutHandler` writes `blacklist:<sid>` with TTL greater than or equal to `JwtSession.sessionTimeout`.
- [ ] All revocation read paths use the same `sid` key written by the backchannel handler.
- [ ] If Redis lookup fails for an authenticated session, the request fails closed rather than continuing downstream.
- [ ] Redis read and write paths have explicit connect and read timeouts, and backchannel runtime failures return `5xx`.

### Transport

> **Lab Exception (FIX-07 Phase 7a):** `requireHttps: false` across all stacks is intentional for this HTTP-only lab. Phase 7b (TLS with self-signed CA) deferred to Vault Production Hardening phase.

- [ ] All OIDC endpoints, Vault calls, and downstream proxy targets use HTTPS in production.
- [ ] Every `OAuth2ClientFilter` has `requireHttps: true`, and `JwtSession` cookies are `Secure`.

### Adapter contract

- [ ] The route chain explicitly includes every required adapter filter, cleanup hook, and logout handler; no safeguard exists only as an unwired script.
- [ ] Adapter-specific privileged material is stored server-side, not in `JwtSession`, `localStorage`, or other JavaScript-accessible storage.

### Logout flows

- [ ] RP-initiated logout reads the correct OIDC namespace, verifies `id_token_hint` is present, and uses pinned post-logout redirect targets.
- [ ] Backchannel logout validates `alg`, `kid`/JWKS, signature, `iss`, `aud`, `events`, `iat`, and `exp` before writing revocation state.

### Observability

- [ ] Logout logs contain only timestamp, opaque session identifier, logout type, and result; they do not log token-bearing URLs or token values.

## Parameterized Template Architecture (2026-03-17)

All gateway Groovy scripts now use a parameterized template architecture (Pattern Consolidation Steps 1-5). Each stack has its own copy of each template; configuration is per-route via JSON args binding.

See docs/deliverables/legacy-auth-patterns-definitive.md section "Template-Based Integration" for full template catalogue and args reference.
