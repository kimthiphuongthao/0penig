---
# Standard OpenIG SSO/SLO Gateway Pattern
**Version:** 1.4
**Date:** 2026-04-02
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
| Vault Transit encryption for Redis token payloads | Implemented | TokenReferenceFilter — all mechanisms |
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

Active shared-runtime routing and isolation matrix:

| App | Hostname | clientEndpoint | Keycloak client | Session heap | Cookie name |
|-----|----------|----------------|-----------------|--------------|-------------|
| WordPress | `http://wp-a.sso.local` | `/openid/app1` | `openig-client` | `SessionApp1` | `IG_SSO_APP1` |
| WhoAmI | `http://whoami-a.sso.local` | `/openid/app2` | `openig-client` | `SessionApp2` | `IG_SSO_APP2` |
| Redmine | `http://redmine-b.sso.local` | `/openid/app3` | `openig-client-b` | `SessionApp3` | `IG_SSO_APP3` |
| Jellyfin | `http://jellyfin-b.sso.local` | `/openid/app4` | `openig-client-b-app4` | `SessionApp4` | `IG_SSO_APP4` |
| Grafana | `http://grafana-c.sso.local` | `/openid/app5` | `openig-client-c-app5` | `SessionApp5` | `IG_SSO_APP5` |
| phpMyAdmin | `http://phpmyadmin-c.sso.local` | `/openid/app6` | `openig-client-c-app6` | `SessionApp6` | `IG_SSO_APP6` |

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

Current per-app Redis ACL mapping:

| App | Redis user | Key prefix | Allowed commands |
|-----|------------|------------|------------------|
| WordPress | `openig-app1` | `~app1:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| WhoAmI | `openig-app2` | `~app2:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| Redmine | `openig-app3` | `~app3:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| Jellyfin | `openig-app4` | `~app4:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| Grafana | `openig-app5` | `~app5:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| phpMyAdmin | `openig-app6` | `~app6:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |

`TokenReferenceFilter.groovy` rules:

- Place it immediately after `OAuth2ClientFilter`
- Use a unique `tokenRefKey` per app (`token_ref_id_app1..6`)
- Skip Redis restore on `<clientEndpoint>/callback`
- Skip Redis offload when the OAuth2 namespace has no real token data yet
- Remove only the current app's discovered OAuth2 keys from session state

Redis payloads are AES-256-GCM encrypted via Vault Transit before storage. Ciphertext format: `vault:v1:...`. On read, the filter detects the `vault:v1:` prefix and decrypts; legacy plaintext entries are accepted during rollout. Encrypt failure is fail-closed (no plaintext fallback). Decrypt failure on the SLO path logs a warning and proceeds.

Every app route using `TokenReferenceFilter` MUST configure: `transitKeyName`, `appRoleName`, `vaultRoleIdFile`, `vaultSecretIdFile`. Missing `transitKeyName` causes immediate script failure.

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

Current per-app Vault AppRole mapping:

| App | AppRole | Policy | Secret path scope |
|-----|---------|--------|-------------------|
| WordPress | `openig-app1` | `openig-app1-policy` | `secret/data/wp-creds/*` |
| WhoAmI | `openig-app2` | `openig-app2-policy` | `secret/data/dummy/*` |
| Redmine | `openig-app3` | `openig-app3-policy` | `secret/data/redmine-creds/*` |
| Jellyfin | `openig-app4` | `openig-app4-policy` | `secret/data/jellyfin-creds/*` |
| Grafana | `openig-app5` | `openig-app5-policy` | `secret/data/grafana-creds/*` |
| phpMyAdmin | `openig-app6` | `openig-app6-policy` | `secret/data/phpmyadmin/*` |

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
- [ ] Provision a Transit encryption key in Vault (`vault write transit/keys/<app>-key type=aes256-gcm96`)
- [ ] Add transit encrypt/decrypt paths to the app AppRole policy
- [ ] No downstream credentials or Vault tokens are serialized into `JwtSession`
- [ ] Credential rotation owner is documented

### Logout

- [ ] Configure `transitKeyName`, `appRoleName`, `vaultRoleIdFile`, `vaultSecretIdFile` in both auth and logout route `TokenReferenceFilter` args
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
| `TokenReferenceFilter.groovy` | Offload oauth2 state to Redis with Vault Transit encryption; keep cookies small |
| `SessionBlacklistFilter.groovy` | Enforce SLO revocation on every request |
| `BackchannelLogoutHandler.groovy` | Validate logout JWT and write Redis blacklist state |
| `SloHandler.groovy` | Handle RP-initiated logout for standard routes |
| `SloHandlerJellyfin.groovy` | Jellyfin-specific logout helper |
| `VaultCredentialFilter.groovy` | Fetch downstream credentials from Vault with route args |

Args-binding rule for OpenIG 6.0.1:

- Route `args` keys become top-level Groovy binding variables
- Use `binding.hasVariable('name')`
- Do not rely on `args.name` or `(args as Map).name`

## Implementation corrections (2026-03-31)

The current shared-runtime baseline includes the following implementation corrections validated after the initial v1.3 document snapshot:

- `BUG-002`: nginx has `proxy_next_upstream` disabled on all six callback paths (`/openid/app1/callback` through `/openid/app6/callback`) to prevent duplicate OIDC code exchange during upstream retry.
- `AUD-003`: `BackchannelLogoutHandler.groovy` now keeps a null-safe JWKS cache and applies a `60s` failure backoff after JWKS fetch failure to avoid hammering Keycloak.
- `DOC-007`: `TokenReferenceFilter.groovy` fail-closed behavior applies only on the callback path, not on every authenticated request, to avoid false `500` responses on legitimate traffic.
- `AUD-009`: `SloHandler.groovy` and `SloHandlerJellyfin.groovy` no longer use legacy hostname fallbacks and now fail closed with `500` if `OPENIG_PUBLIC_URL` or `CANONICAL_ORIGIN_APP4` are missing.
- `VAULT-TRANSIT-001`: Redis token payloads are now encrypted via Vault Transit (AES-256-GCM) before storage. Per-app keys (`app1-key` through `app6-key`) with scoped AppRole policies ensure blast-radius isolation. Dual-format read supports rollout from plaintext.

## Production deployment on Kubernetes

For production deployments on Kubernetes, use Vault's Kubernetes authentication method instead of AppRole. This aligns with HashiCorp best practices for trusted third-party authentication.

> **Production note:** The current lab AppRole pattern is not production-ready for long-lived Kubernetes workloads. Once the 72h secret_id expires, VaultCredentialFilter.groovy can no longer obtain a fresh Vault token after its cached Vault token ages out, breaking all Vault-backed downstream login flows until an operator rotates the AppRole material.

**References:**
- HashiCorp Vault Docs: [Kubernetes Auth Method](https://developer.hashicorp.com/vault/docs/auth/kubernetes)
- HashiCorp Vault API: [Kubernetes Auth API](https://developer.hashicorp.com/vault/api-docs/auth/kubernetes)
- HashiCorp Tutorial: [AppRole Best Practices](https://developer.hashicorp.com/vault/tutorials/auth-methods/approle-best-practices)

### Authentication method comparison

| Aspect | Lab (Docker Compose) | Production (Kubernetes) |
|--------|---------------------|-------------------------|
| Auth method | AppRole (`role_id` + `secret_id`) | Kubernetes auth (ServiceAccount JWT) [^1] |
| Credential delivery | Manual (files mounted from `/vault/file/`) | Automatic (K8s auto-mounts SA token) [^1] |
| Credential rotation | Manual (admin regenerates `secret_id` every 72h) | ServiceAccount token lifetime is cluster/pod-configured (no fixed 1h default). Do not document it as a universal value. OpenIG must renew or re-run auth/kubernetes/login when the Vault token expires. |
| Vault config | `POST /auth/approle/login` | `POST /auth/kubernetes/login` [^2] |
| OpenIG code change | None | Update `VaultCredentialFilter.groovy` to use K8s auth endpoint |

[^1]: HashiCorp Vault Docs: [Kubernetes Auth Method](https://developer.hashicorp.com/vault/docs/auth/kubernetes)
[^2]: HashiCorp Vault API: [Kubernetes Auth API](https://developer.hashicorp.com/vault/api-docs/auth/kubernetes)
[^3]: HashiCorp Tutorial: [AppRole Best Practices](https://developer.hashicorp.com/vault/tutorials/auth-methods/approle-best-practices)

> **HashiCorp recommendation:** *"If another platform method of authentication is available via a trusted third-party authenticator, the best practice is to use that instead of AppRole."* [^3]

### Vault configuration for Kubernetes

Configuration commands from HashiCorp official documentation [^1][^2]:

```bash
# Enable Kubernetes auth method
vault auth enable kubernetes

# Configure Vault to communicate with Kubernetes API
# When Vault runs inside K8s, it auto-discovers these values
vault write auth/kubernetes/config \
    kubernetes_host=https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT

# Create per-app roles (replace AppRole pattern)
vault write auth/kubernetes/role/openig-app1 \
    bound_service_account_names=openig-sa \
    bound_service_account_namespaces=sso \
    policies=openig-app1-policy \
    ttl=1h
```

### Kubernetes requirements

Requirements from HashiCorp official documentation [^1]:

| Component | Configuration |
|-----------|---------------|
| ServiceAccount | Create `openig-sa` in `sso` namespace for OpenIG pods |
| ClusterRoleBinding | Grant `system:auth-delegator` to Vault's ServiceAccount for TokenReview API |
| Network | Vault must reach Kubernetes API server (`$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT`) |
| OpenIG pod spec | Mount ServiceAccount token (auto-mounted at `/var/run/secrets/kubernetes.io/serviceaccount/token`) |

### OpenIG code changes

**Implementation note:** The following code is a proposed adaptation based on Vault's Kubernetes Auth API specification [^2]. This code has NOT been tested in this lab (which uses Docker Compose). Test in a Kubernetes staging environment before production deployment.

Update `VaultCredentialFilter.groovy` to use Kubernetes auth endpoint:

```groovy
// Current (AppRole) - Lab implementation:
String payload = JsonOutput.toJson([role_id: roleId, secret_id: secretId])
connection = new URL("${vaultAddr}/v1/auth/approle/login").openConnection()

// Production (Kubernetes) - Proposed implementation:
String saToken = new File('/var/run/secrets/kubernetes.io/serviceaccount/token').text.trim()
String payload = JsonOutput.toJson([jwt: saToken, role: 'openig-app1'])
connection = new URL("${vaultAddr}/v1/auth/kubernetes/login").openConnection()
```

### Recommended production pattern

Prefer Vault Agent Sidecar or Vault Agent Injector with Kubernetes auth for long-lived OpenIG pods. The agent performs Kubernetes auth, renews or re-authenticates Vault tokens, and refreshes rendered secrets without AppRole secret_id files. OpenIG reads rendered files or a local agent endpoint and does not own the Vault token lifecycle.

If OpenIG authenticates to Vault directly (without Vault Agent), it MUST:
- use a projected ServiceAccount token with audience: vault if the Vault role enforces audience validation
- read the ServiceAccount JWT from disk on each auth/kubernetes/login call; do not cache the Kubernetes JWT
- renew the Vault token or re-run auth/kubernetes/login when the cached Vault token expires

### Security best practices for production

Values from HashiCorp official documentation examples [^1][^2]:

| Setting | Recommended value | Purpose |
|---------|------------------|---------|
| Token TTL | `1h` | Short-lived tokens reduce blast radius (from docs example) |
| Bound service accounts | Required | Prevent token reuse across workloads [^1] |
| Bound namespaces | Required | Namespace isolation for multi-tenant clusters [^1] |
| Audience claim | `vault` | Prevent JWT reuse for other purposes [^1] |
| CIDR binding | Optional | Restrict authentication source IP [^1] |

### Migration checklist

**Note:** This checklist is derived from HashiCorp documentation [^1][^2][^3] and has not been validated in this lab.

- [ ] Deploy Vault in Kubernetes cluster (recommended for local SA token rotation)
- [ ] Enable Kubernetes auth method and configure K8s API connection
- [ ] Create ServiceAccount `openig-sa` in target namespace
- [ ] Create ClusterRoleBinding for Vault to access TokenReview API
- [ ] Create per-app Kubernetes roles (one per app, replacing AppRole)
- [ ] Update OpenIG `VaultCredentialFilter.groovy` to use `/auth/kubernetes/login`
- [ ] Test authentication flow with non-production app first
- [ ] Migrate apps one-by-one, keeping AppRole as fallback during transition
