---
title: Stack A
tags:
  - sso
  - stack-a
  - openig
  - wordpress
date: 2026-03-20
status: complete
---

# Stack A

Related: [[Stack B]] [[Stack C]] [[OpenIG]] [[Keycloak]] [[Vault]]

## Services

| Service | Compose service / container | Ports | Purpose |
|---|---|---|---|
| `nginx-a` | `nginx` / `sso-nginx` | `80:80` (host:container) | Public entrypoint and LB to OpenIG HA pool. |
| `openig-a1/a2` | `openig-1` + `openig-2` / `sso-openig-1` + `sso-openig-2` | `8080` (internal only) | OpenIG gateway nodes handling OIDC, session, and app routing. |
| `wordpress-a` | `wordpress` / `sso-wordpress` | `80` (internal only) | Legacy app behind OpenIG. |
| `redis-a` | `redis-a` / `sso-redis-a` | `6379` (internal only) | Session blacklist store for backchannel logout invalidation. |
| `vault-a` | `vault` / `sso-vault` | `8200` (internal only) | Secret store + AppRole auth for WP credentials. |

## Public Entry

- Host: `wp-a.sso.local`
- Port: `80`
- URL: `http://wp-a.sso.local`

## 2026-03-17 Security Hardening

- Externalized Compose-managed secrets from committed literals into local `stack-a/.env`.
- Added committed `stack-a/.env.example` with Kubernetes handoff comments for `envFrom secretRef`.
- Switched `openig-1` and `openig-2` to interpolate `JWT_SHARED_SECRET`, `KEYSTORE_PASSWORD`, and `OIDC_CLIENT_SECRET`.
- Pinned `openig-1` and `openig-2` to `openidentityplatform/openig:6.0.1` because `openidentityplatform/openig:latest` (`6.0.2`) is currently broken in this lab while `6.0.1` is the verified good tag.
- Switched `mysql` to interpolate `MYSQL_ROOT_PASSWORD` and changed the healthcheck to shell form so the container resolves `-p$${MYSQL_ROOT_PASSWORD}` at runtime.
- Switched `wordpress` to interpolate `WORDPRESS_DB_PASSWORD`.
- Left `REDIS_PASSWORD` out of Stack A by design because STEP-04 handles Redis auth separately.

> [!success]
> `docker compose config` on `2026-03-17` rendered the expected `JWT_SHARED_SECRET` value for both OpenIG nodes with no Compose interpolation errors.

> [!success]
> Validation on `2026-03-17`: Stack A started cleanly with the pinned image and loaded all 4 routes.

> [!tip]
> For Kubernetes migration, keep `stack-a/.env.example` as the committed contract and create the runtime secret with `kubectl create secret generic stack-a-secrets --from-env-file=.env`.

## 2026-03-18 Redis auth hardening

- Added `REDIS_PASSWORD` to the Stack A runtime env contract and example file.
- Updated `redis-a` to start with `--requirepass ${REDIS_PASSWORD}`.
- Passed `REDIS_PASSWORD` into both OpenIG nodes so Groovy Redis clients can authenticate.
- Added RESP `AUTH` before blacklist `GET` in `SessionBlacklistFilter.groovy`.
- Added RESP `AUTH` before blacklist `SET` in `BackchannelLogoutHandler.groovy`.

> [!success]
> Validation on `2026-03-18`: unauthenticated `redis-cli PING` returned `NOAUTH Authentication required.` and authenticated `PING` returned `PONG`.

> [!success]
> Runtime verification on `2026-03-18`: `docker compose up -d` completed, `docker restart sso-openig-1 sso-openig-2` completed, and both OpenIG logs showed all 4 routes loaded (`00-wp-logout`, `00-backchannel-logout-app1`, `01-wordpress`, `02-app2`).

> [!tip]
> This closes the prior Stack A note that Redis auth was intentionally deferred. Current Stack A state assumes a non-empty `REDIS_PASSWORD` is provided at runtime.

## 2026-03-18 Keycloak URL externalization and logout cleanup

- Updated `stack-a/openig_home/config/routes/01-wordpress.json` and `stack-a/openig_home/config/routes/02-app2.json` so `KeycloakIssuer` now uses `${env['KEYCLOAK_BROWSER_URL']}` for `issuer` and `authorizeEndpoint`.
- Switched `tokenEndpoint`, `userInfoEndpoint`, and `jwksUri` in those same routes to `${env['KEYCLOAK_INTERNAL_URL']}` so OpenIG resolves backend OIDC traffic on the container-reachable address.
- Removed `registrationEndpoint` and `endSessionEndpoint` from both issuer blocks.
- Removed `defaultLogoutGoto` from both `OAuth2ClientFilter` configs because `SloHandler.groovy` remains the single frontchannel logout path for [[OpenIG]] in [[Stack A]].
- Updated `stack-a/openig_home/config/routes/00-backchannel-logout-app1.json` args so logout-token validation uses internal JWKS lookup and the browser-facing [[Keycloak]] issuer value.

> [!success]
> Validation on `2026-03-18`: `docker restart sso-openig-1 sso-openig-2` completed successfully and `docker logs sso-openig-1 2>&1 | grep 'Loaded the route'` showed `00-wp-logout`, `00-backchannel-logout-app1`, `01-wordpress`, and `02-app2`.

> [!success]
> Verification on `2026-03-18`: `grep -r 'auth.sso.local:8080' stack-a/openig_home/config/routes/` and `grep -r 'host.docker.internal:8080' stack-a/openig_home/config/routes/` both returned empty results.

## 2026-03-18 OpenIG healthcheck baseline

- Context:
  - Implemented confirmed Phase 2 item `[H-7/A-1]` directly for [[Stack A]].
- What done:
  - Added Docker healthchecks to `openig-1` and `openig-2` in `stack-a/docker-compose.yml`.
  - Used `curl -f http://localhost:8080/openig/api/info` with `interval: 30s`, `timeout: 10s`, `retries: 5`, and `start_period: 60s`.
- Decisions:
  - Kept the Stack A change surface limited to the two [[OpenIG]] services only.
  - Left existing images, environment variables, volumes, networks, and the existing MySQL healthcheck unchanged.
- Current state:
  - `docker compose up -d` recreated `sso-openig-1` and `sso-openig-2`.
  - `docker ps --filter 'name=sso-openig-' --format 'table {{.Names}}\t{{.Status}}'` showed both OpenIG nodes `healthy`.
- Files changed:
  - `stack-a/docker-compose.yml`

> [!success]
> Validation on `2026-03-18`: both [[OpenIG]] nodes in [[Stack A]] came back healthy immediately after the compose update.

> [!tip]
> Keep future Stack A Compose hardening aligned with the Stack B and Stack C OpenIG healthcheck contract so restart and smoke-test automation can use the same `/openig/api/info` probe.

## Auth Mechanism

- Form-based login intercept via OpenIG:
  - `OAuth2ClientFilter` enforces OIDC login with Keycloak.
  - `TokenReferenceFilter.groovy` offloads heavyweight `oauth2:*` state to Redis so `IG_SSO` stays under the browser cookie budget.
  - `VaultCredentialFilter.groovy` loads mapped WordPress credentials from Vault.
  - `CredentialInjector.groovy` performs backend POST to `wp-login.php`, rewrites returned `Set-Cookie` headers back to the browser, and removes any legacy `wp_session_cookies` residue from old sessions.

> [!success]
> Full 2026-03-19 validation PASS: `IG_SSO` present, TokenRef Store/Restore OK, and backchannel Redis blacklist logout works for Stack A. The missed `BackchannelLogoutHandler.groovy` consolidation from Step 3 was corrected in the post-audit pass (`f85a3f2`), so Stack A now matches the Stack B/C shared template and is ready for merge with the rest of `fix/jwtsession-production-pattern`.

> [!warning]
> Remaining notes:
> - OpenIG non-root remains lab-deferred on macOS host mounts.
> - Operational gotcha for Stack A: empty `/vault/file/openig-role-id` or `/vault/file/openig-secret-id` on one OpenIG node can trigger `"SSO authentication failed"`.

## 2026-03-20 Groovy Redis port and log prefix cleanup

- Resolved `[L-1]`: [stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy) and [stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy) now read Redis port from route arg `redisPort`, then `REDIS_PORT`, then default `6379`.
- Resolved `[L-3]`: [stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy) now uses the standardized `[TokenReferenceFilter]` log prefix across its restore/offload paths.

> [!success]
> Validation on `2026-03-20`: `docker restart sso-openig-1 sso-openig-2` completed successfully. `docker logs sso-openig-1 2>&1 | grep 'Loaded the route'` showed `00-wp-logout`, `00-backchannel-logout-app1`, `01-wordpress`, and `02-app2` after restart.

## Routes

| File | Purpose |
|---|---|
| `00-backchannel-logout-app1.json` | Matches `POST /openid/app1/backchannel_logout`; passes route `args` (`audiences`, `redisHost`, `jwksUri`, `issuer`, `ttlSeconds`) into the consolidated `BackchannelLogoutHandler.groovy`. |
| `00-wp-logout.json` | Intercepts WordPress logout requests (`wp-login.php?action=logout`) for `/app1` and `wp-a.sso.local`; delegates to `SloHandler.groovy`. |
| `01-wordpress.json` | Main WordPress route for `Host: wp-a.sso.local.*`; chain includes `OAuth2ClientFilter` (`/openid/app1`), `TokenReferenceFilter`, `SessionBlacklistFilter`, `VaultCredentialFilter`, and `CredentialInjector`; proxies to `http://wordpress`. |
| `02-app2.json` | Main WhoAmI route for `Host: whoami-a.sso.local.*`; uses `TokenReferenceFilter.groovy`, the shared `SessionBlacklistFilter.groovy` with route `args` for app2-specific endpoint/session settings, then `App2HeaderFilter`; proxies to `http://whoami`. |

## Groovy Scripts

| Script | Purpose |
|---|---|
| `BackchannelLogoutHandler.groovy` | Shared consolidated handler: parses backchannel `logout_token`, validates `RS256` / `ES256` signatures via atomic `globals.compute()` JWKS cache, extracts `sid/sub`, and writes Redis key `blacklist:{sid}` with expiry. |
| `CredentialInjector.groovy` | Uses Vault-provided app creds to submit the WordPress login form, returns upstream cookies to the browser, and clears any legacy `wp_session_cookies` state left by older sessions. |
| `TokenReferenceFilter.groovy` | Stores and restores the live `oauth2:*` session namespace in Redis so the browser cookie only carries `token_ref_id` plus small identity markers. |
| `VaultCredentialFilter.groovy` | Logs into Vault via AppRole (`role_id`/`secret_id` files), caches Vault token in session, reads `secret/data/wp-creds/{preferred_username}`, sets `attributes.wp_credentials`. |
| `SessionBlacklistFilter.groovy` | Shared blacklist enforcement used by both app1 and app2 via route `args` (`clientEndpoint`, `sessionCacheKey`, `canonicalOrigin`, `canonicalOriginEnvVar`). |
| `SloHandler.groovy` | Frontchannel logout handler: resolve `id_token` from OpenIG session keys, clear session, redirect to Keycloak end-session with dynamic `post_logout_redirect_uri` and `id_token_hint` when available. |

## Vault AppRole Setup Summary

Source: `stack-a/vault/init/vault-bootstrap.sh`

1. Wait for Vault readiness (`vault status` return code `0` unsealed or `2` sealed-but-reachable).
2. Initialize and unseal Vault if needed; persist keys in `/vault/data/.vault-keys.*`.
3. Enable `kv-v2` on `secret/` and enable `approle` auth method.
4. Write policy `openig-readonly` with read capability on `secret/data/wp-creds/*`.
5. Create AppRole `openig` (`token_ttl=1h`, `token_max_ttl=4h`, policy `openig-readonly`).
6. Export AppRole credentials to shared files:
   - `/vault/file/openig-role-id`
   - `/vault/file/openig-secret-id`
7. Seed credentials:
   - `secret/wp-creds/alice`
   - `secret/wp-creds/bob`
8. Mark bootstrap complete with `/vault/data/.bootstrap-done` for idempotency.
