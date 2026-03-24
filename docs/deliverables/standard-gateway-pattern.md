---
# Standard OpenIG SSO/SLO Gateway Pattern
**Version:** 1.3
**Date:** 2026-03-24
**Derived from:** Shared-infra validation across WordPress, WhoAmI, Redmine, Jellyfin, Grafana, and phpMyAdmin
**Scope:** OpenIG 6 + Keycloak + Vault + Redis

> Update 2026-03-24: The active lab runtime is now `shared/`: one nginx, two OpenIG nodes, one Redis, and one Vault serving all 6 apps on port 80 via hostname routing. Shared-infra isolation is enforced with route-local `JwtSession` heaps (`SessionApp1..6`), host-only cookies (`IG_SSO_APP1..APP6`), per-app Redis ACL users (`openig-app1..6`), per-app Redis key prefixes (`app1:*..app6:*`), and per-app Vault AppRoles (`openig-app1..6`).

---

## Overview

This document defines the reference gateway contract for OpenIG-based SSO and SLO when one shared gateway runtime fronts multiple heterogeneous downstream applications. The gateway owns login orchestration, session storage, revocation, logout propagation, secret retrieval, redirect integrity, and adapter wiring. Downstream apps keep their native login mechanisms.

The active runtime shape is:

- `shared-nginx` terminates browser traffic on port 80 and routes by hostname
- `shared-openig-1` and `shared-openig-2` run the full route set
- `shared-redis` stores revocation and token-reference data
- `shared-vault` stores downstream credentials and gateway secrets
- Keycloak remains the shared IdP at `http://auth.sso.local:8080`

## Shared-infra deployment contract

| Layer | Current shared-infra baseline |
|-------|-------------------------------|
| Browser entrypoint | Hostname routing on port 80 |
| Session model | Route-local `JwtSession` per app |
| Browser cookies | `IG_SSO_APP1..APP6` |
| Redis isolation | `openig-app1..6` with `~appN:*` ACL prefixes |
| Vault isolation | `openig-app1..6` AppRoles with path-scoped policies |
| Redirect base | `CANONICAL_ORIGIN_APP1..6` env vars |
| OpenIG image | `openidentityplatform/openig:6.0.1` |

## Security control status

| Control | Shared-infra status | Notes |
|---------|---------------------|-------|
| Route-local `JwtSession` cookies | Implemented | `SessionApp1..6` with `IG_SSO_APP1..APP6` |
| Per-app Redis ACL | Implemented | `openig-app1..6`, minimal command set |
| Per-app Vault AppRole isolation | Implemented | `openig-app1..6`, path-scoped policies |
| Strip gateway session cookies before proxy | Implemented | `StripGatewaySessionCookies.groovy` on all app routes |
| Backchannel logout with JWT validation | Implemented | `RS256` and `ES256` supported |
| TLS between components | Lab exception - deferred to production | Current lab remains HTTP-only |

## Login mechanism coverage

| Pattern | Representative app type | Gateway action |
|---------|--------------------------|----------------|
| Form login injection | WordPress, Redmine | Complete OIDC, fetch app credentials from Vault, submit native login flow |
| Token injection | Jellyfin | Complete OIDC, obtain downstream token state, bridge it into the app contract |
| Trusted header injection | Grafana, WhoAmI | Complete OIDC, inject identity header after auth and blacklist checks |
| HTTP Basic injection | phpMyAdmin | Complete OIDC, fetch credentials from Vault, inject `Authorization: Basic` |
| LDAP | Future pattern | Requires app-specific assessment; not part of the validated 6-app baseline |

## Required controls

### 1. Session isolation

Each app in the shared runtime MUST have:

- A unique `clientEndpoint`
- A unique route-local session heap (`SessionAppN`)
- A unique cookie name (`IG_SSO_APPN`)
- A unique `tokenRefKey`
- A unique Redis namespace and Redis user
- A unique Vault AppRole when Vault is used

The fallback global `Session` heap in `shared/openig_home/config/config.json` is not the active session model for app routes. Shared-infra routes override it explicitly.

### 2. Redis revocation and token-reference contract

Redis is responsible for:

- `blacklist:<sid>` style revocation state
- Token-reference state that keeps heavy `oauth2:*` blobs out of the browser cookie

Rules:

- Blacklist TTL MUST be greater than or equal to `JwtSession.sessionTimeout`
- Redis read failure on an authenticated request MUST fail closed
- Redis write failure during backchannel logout MUST return `5xx`, not `4xx`
- OpenIG MUST authenticate with `AUTH <username> <password>`
- Redis ACL users MUST remain limited to `SET`, `GET`, `DEL`, `EXISTS`, `PING`
- Redis keys MUST remain app-scoped (`app1:*..app6:*`)

`TokenReferenceFilter.groovy` rules:

- Place it immediately after `OAuth2ClientFilter`
- Use a unique `tokenRefKey` per app (`token_ref_id_app1..6`)
- Skip Redis restore on `<clientEndpoint>/callback`
- Skip Redis offload when the OAuth2 namespace has no real token data yet
- Remove only the current app's discovered OAuth2 keys from session state

### 3. Vault secret model

Vault is responsible for:

- Gateway crypto material and runtime secrets
- OIDC client secrets when applicable
- Downstream app credentials for form/basic/token injection patterns

Rules:

- Secrets MUST come from Vault or environment at runtime
- Secrets MUST NOT be hardcoded in route JSON, Groovy, or committed `.env`
- Each app gets its own AppRole: `openig-app1..6`
- Each AppRole is scoped to its own secret path only
- AppRole files are distinct per app: `/vault/file/openig-appN-role-id`, `/vault/file/openig-appN-secret-id`

Operational note:

- `secret_id_ttl` is `72h` in the lab, so regeneration is a normal operational step after long downtime

### 4. Redirect and logout integrity

Rules:

- Redirect and logout targets MUST use `CANONICAL_ORIGIN_APP1..6`
- Do not derive redirect base from inbound `Host`
- RP-initiated logout MUST read the correct OIDC namespace for that route
- Backchannel logout MUST validate `alg`, `kid`, signature, `iss`, `aud`, `events`, `iat`, and `exp`

### 5. Header and credential injection ordering

Trusted headers and downstream credentials MUST be injected only after:

1. OIDC authentication succeeds
2. Revocation check succeeds
3. Gateway session cookies are stripped from the upstream `Cookie` header

This prevents spoofed or revoked identity from reaching the backend.

### 6. Secret and image hygiene

Rules:

- Keep runtime secrets in gitignored `.env` or Vault-backed runtime injection
- Commit `.env.example`, never `.env`
- OpenIG `OAuth2ClientFilter` client secrets must be strong alphanumeric-only values
- Do not use `openidentityplatform/openig:latest`
- Pin `openidentityplatform/openig:6.0.1`

## SLO flow

### RP-initiated logout

1. Browser calls the route-specific logout handler
2. `SloHandler` reads the route's `id_token`
3. `SloHandler` builds Keycloak `end_session` with `id_token_hint` and pinned `post_logout_redirect_uri`
4. Local route session is invalidated
5. Browser is redirected to Keycloak logout
6. Keycloak sends backchannel logout to registered gateway endpoints

### Backchannel logout

1. Keycloak sends signed `logout_token`
2. `BackchannelLogoutHandler.groovy` validates JWT and claims
3. Handler writes blacklist state in the app's Redis namespace
4. Next authenticated request hits `SessionBlacklistFilter.groovy`
5. Blacklisted session fails closed and requires re-authentication

## Anti-patterns

| Anti-pattern | Risk | Correct approach |
|--------------|------|------------------|
| Hardcoded secrets in routes or scripts | Repo disclosure becomes credential disclosure | Externalize to Vault or runtime env |
| Shared Redis password for all apps | Cross-app revocation and token-reference access | Use per-app ACL users |
| Shared Vault AppRole for all apps | One route can read another route's secrets | Use per-app AppRoles |
| One shared browser cookie for all apps in current shared runtime | Cross-app blast radius and debugging ambiguity | Use route-local cookies `IG_SSO_APP1..APP6` |
| Host-derived redirect base | Open redirect or wrong logout origin | Use pinned canonical origins |
| Forwarding gateway cookies downstream | Backends receive gateway-only state | Strip `IG_SSO_APP*` before proxy |
| Restoring token reference on callback | OAuth2 pending state is overwritten | Skip restore on callback |
| Clearing every `oauth2:*` namespace | One app can break another app's pending login | Remove only current app's discovered keys |
| Using `latest` OpenIG image | Runtime drift and broken startup | Pin `6.0.1` |

## New integration checklist

### Session and revocation

- [ ] Route has unique `clientEndpoint`
- [ ] Route has unique `SessionAppN` and `IG_SSO_APPN`
- [ ] Route has unique `tokenRefKey`
- [ ] Route uses app-scoped Redis user and prefix
- [ ] `TokenReferenceFilter.groovy` is wired immediately after `OAuth2ClientFilter`
- [ ] `SessionBlacklistFilter.groovy` checks the same `sid` namespace written by backchannel logout

### Vault and credentials

- [ ] AppRole is unique to the app
- [ ] Policy is scoped to the app secret path only
- [ ] No downstream credentials or Vault tokens are serialized into `JwtSession`
- [ ] Credential rotation owner is documented

### Logout

- [ ] RP-initiated logout reads the correct OIDC namespace
- [ ] Backchannel logout URL is registered
- [ ] Backchannel logout validates JWT before writing Redis state
- [ ] Post-logout redirect target is pinned

### Proxy boundary

- [ ] Gateway-owned session cookies are stripped before proxy
- [ ] Trusted identity headers are stripped from client input and injected only by gateway
- [ ] Adapter-specific filters are explicit in the route chain

### Transport

> Lab exception: current shared infra remains HTTP-only. This lab validates the integration pattern, not production transport hardening.

- [ ] Production deployment uses TLS for browser, Vault, Redis, and internal control-plane traffic
- [ ] `requireHttps: true` is enabled in production
- [ ] `JwtSession` cookies are `Secure` in production
- [ ] Network segmentation exists between app, browser, and admin/control-plane paths

## Parameterized template architecture

The shared runtime uses one copy of each gateway Groovy template and configures behavior per route with JSON `args`.

Validated templates:

| Template | Purpose |
|----------|---------|
| `TokenReferenceFilter.groovy` | Offload `oauth2:*` to Redis and keep cookies small |
| `SessionBlacklistFilter.groovy` | Enforce SLO revocation on every request |
| `BackchannelLogoutHandler.groovy` | Validate logout JWT and write Redis blacklist state |
| `SloHandler.groovy` | Handle RP-initiated logout for standard routes |
| `SloHandlerJellyfin.groovy` | Jellyfin-specific logout helper |
| `VaultCredentialFilter.groovy` | Fetch downstream credentials from Vault with route args |

Args-binding rule for OpenIG 6.0.1:

- Route `args` keys become top-level Groovy binding variables
- Use `binding.hasVariable('name')`
- Do not rely on `args.name` or `(args as Map).name`
