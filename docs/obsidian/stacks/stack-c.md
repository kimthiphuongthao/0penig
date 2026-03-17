---
title: Stack C Status
tags:
  - stacks
  - stack-c
  - openig
  - keycloak
  - sso
  - slo
date: 2026-03-12
status: active
---

# Stack C

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Current status

- SSO: ✅
- SLO: ✅
- OIDC_CLIENT_SECRET_APP5 and OIDC_CLIENT_SECRET_APP6 rotated to strong 44-char secrets (Phase 2 STEP-02, M-5/S-9). Keycloak clients openig-client-c-app5 and openig-client-c-app6 updated to match.

> [!success]
> SSO/SLO WORKING for app5 (Grafana) and app6 (phpMyAdmin). Post-audit cleanup confirmed the old `SloHandlerGrafana.groovy` and `SloHandlerPhpMyAdmin.groovy` files were leftover artifacts from Step 4 and have been deleted.
> Phase 2 STEP-01 (L-5): `PhpMyAdminCookieFilter.groovy` dead code also deleted — file was never wired into any route; no runtime impact.

## Architecture

- Ingress:
  - `nginx-c` listens on host `:18080`, routes to OpenIG pool (`openig-c1`, `openig-c2`).
- Identity:
  - Keycloak realm: `sso-realm` at `http://auth.sso.local:8080`.
  - OIDC clients: `openig-client-c-app5`, `openig-client-c-app6`.
- App 5 (Grafana):
  - OpenIG route `10-grafana.json`.
  - User identity is injected with header `X-WEBAUTH-USER` from `attributes.openid['user_info']['preferred_username']` (transient per-request, not stored in session).
- App 6 (phpMyAdmin):
  - OpenIG route `11-phpmyadmin.json`.
  - Credentials are fetched from [[Vault]] via `VaultCredentialFilter.groovy`.
  - OpenIG injects HTTP Basic credentials using `HttpBasicAuthFilter`.

## SLO flow per app

### Grafana (app5)

1. User hits `GET /logout` on `grafana-c.sso.local:18080`.
2. `00-grafana-logout.json` intercepts and calls the consolidated `SloHandler.groovy`.
3. Handler reads `id_token` from session, clears session, redirects to Keycloak end-session with:
   - `client_id=openig-client-c-app5`
   - `post_logout_redirect_uri=http://grafana-c.sso.local:18080/`
   - `id_token_hint=<id_token>` when available.

### phpMyAdmin (app6)

1. User triggers `/?logout=1` on `phpmyadmin-c.sso.local:18080`.
2. `00-phpmyadmin-logout.json` intercepts and calls the consolidated `SloHandler.groovy`.
3. Handler reads `id_token` from session, clears session, redirects to Keycloak end-session with:
   - `client_id=openig-client-c-app6`
   - `post_logout_redirect_uri=http://phpmyadmin-c.sso.local:18080/`
   - `id_token_hint=<id_token>` when available.
4. `HttpBasicAuthFilter.failureHandler` also points to the same `SloHandler.groovy` for the `401` logout/auth challenge path.

### Backchannel (both apps)

1. Keycloak sends `POST /openid/app5/backchannel_logout` or `/openid/app6/backchannel_logout`.
2. `BackchannelLogoutHandler.groovy` parses `logout_token`, extracts `sid`, sets `blacklist:<sid>` in Redis (TTL 3600s).
3. `SessionBlacklistFilter.groovy` checks Redis each request; if blacklisted, session is cleared and request is redirected to same URL for fresh OIDC flow.

## Key files

- Routing:
  - `stack-c/openig_home/config/routes/00-grafana-logout.json`
  - `stack-c/openig_home/config/routes/00-phpmyadmin-logout.json`
  - `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json`
  - `stack-c/openig_home/config/routes/00-backchannel-logout-app6.json`
  - `stack-c/openig_home/config/routes/10-grafana.json`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- Handlers/filters:
  - `stack-c/openig_home/scripts/groovy/SloHandler.groovy`
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
- Platform:
  - `stack-c/docker-compose.yml`
  - `stack-c/openig_home/config/config.json`
  - `keycloak/realm-import/realm-export.json`

## Ports and endpoints

- External:
  - `http://grafana-c.sso.local:18080`
  - `http://phpmyadmin-c.sso.local:18080`
  - `http://auth.sso.local:8080` (Keycloak browser endpoints)
- Internal:
  - OpenIG nodes: `openig-c1:8080`, `openig-c2:8080`
  - Grafana: `grafana:3000`
  - phpMyAdmin: `phpmyadmin:80`
  - Redis: `redis-c:6379`
  - Vault: `vault-c:8200`
  - MariaDB: `mariadb:3306`

## Credentials and secrets

- Keycloak client secrets:
  - `openig-client-c-app5` -> strong 44-char secret via `OIDC_CLIENT_SECRET_APP5`
  - `openig-client-c-app6` -> strong 44-char secret via `OIDC_CLIENT_SECRET_APP6`
- OpenIG JWT session:
  - Cookie: `IG_SSO_C` on domain `.sso.local`
  - Stack-C specific `sharedSecret` configured in `stack-c/openig_home/config/config.json`
- MariaDB bootstrap (dev):
  - `MYSQL_ROOT_PASSWORD=rootpass`
  - `MYSQL_USER=alice`
  - `MYSQL_PASSWORD=AlicePass123`
- phpMyAdmin runtime auth:
  - `auth_type=http`
  - actual Basic Auth username/password fetched from Vault path `secret/data/phpmyadmin/{preferred_username}`
  - AppRole files: `/opt/openig/vault/role_id`, `/opt/openig/vault/secret_id`

> [!warning]
> Do not reuse `JwtSession.sharedSecret` across stacks sharing `.sso.local` cookie domain.

> [!tip]
> For SLO regressions, verify route order first (`00-*` logout routes must run before `10/11-*` app routes).
