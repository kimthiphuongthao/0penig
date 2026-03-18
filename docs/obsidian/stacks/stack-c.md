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
- Stack C OIDC clients were rotated away from weak literal `secret-c` in Phase 2 STEP-02 (M-5/S-9). APP5 was re-rotated on 2026-03-18 to a strong alphanumeric-only secret because OpenIG `OAuth2ClientFilter` does not URL-encode `client_secret`.
- Phase 2 hardening `[H-5/S-3]`: secret-bearing Compose values moved out of `stack-c/docker-compose.yml` into local `stack-c/.env`; committed `stack-c/.env.example` documents the required variables and K8s Secret bootstrap flow.
- Phase 2 hardening `[H-4/S-2]`: `redis-c` now enforces `--requirepass ${REDIS_PASSWORD}`; `openig-c1` and `openig-c2` receive `REDIS_PASSWORD`; both `SessionBlacklistFilter.groovy` and `BackchannelLogoutHandler.groovy` send RESP `AUTH` before the existing Redis `GET`/`SET` calls. Validation on 2026-03-18: unauthenticated `redis-cli PING` returned `NOAUTH Authentication required.` and `openig-c1` reloaded all routes after restart.
- `openig-c1` and `openig-c2` are pinned to `openidentityplatform/openig:6.0.1`; `openidentityplatform/openig:latest` moved to a Tomcat 11 build that breaks OpenIG 6 startup.

> [!success]
> SSO/SLO WORKING for app5 (Grafana) and app6 (phpMyAdmin). Post-audit cleanup confirmed the old `SloHandlerGrafana.groovy` and `SloHandlerPhpMyAdmin.groovy` files were leftover artifacts from Step 4 and have been deleted.
> Phase 2 STEP-01 (L-5): `PhpMyAdminCookieFilter.groovy` dead code also deleted — file was never wired into any route; no runtime impact.

> [!success]
> `stack-c/docker-compose.yml` no longer commits literal values for `JWT_SHARED_SECRET`, `KEYSTORE_PASSWORD`, `OIDC_CLIENT_SECRET_APP5`, `OIDC_CLIENT_SECRET_APP6`, `MYSQL_ROOT_PASSWORD`, or `MYSQL_PASSWORD`. Validation on 2026-03-17: `docker compose config` resolved the env-backed values correctly, and the committed Compose file no longer matched the previous secret prefixes.

> [!success]
> Validation on `2026-03-17`: Stack C started cleanly with the pinned image and loaded all 6 routes.

## 2026-03-18 Compose baseline alignment

- Context:
  - Implemented confirmed Phase 2 item `[H-7/A-1]` directly for [[Stack C]] with Stack B as the Compose baseline reference.
- What done:
  - Added `platform: linux/amd64`, fixed `container_name`, `user: root   # lab only`, `restart: unless-stopped`, `OPENIG_NODE_NAME`, and `/openig/api/info` healthchecks to `openig-c1` and `openig-c2`.
  - Added fixed `container_name` and `restart: unless-stopped` to `nginx-c`, `grafana`, `mariadb`, `phpmyadmin`, and `vault-c`.
  - Added `backend-c` to the `nginx-c` networks list.
  - Added a MariaDB healthcheck using `mariadb-admin ping -h localhost -uroot -p$${MYSQL_ROOT_PASSWORD}`.
  - Left `redis-c` unchanged because it already had `restart: unless-stopped`.
- Decisions:
  - Kept all existing images, environment variables, volumes, and network definitions intact, adding only the missing Compose fields requested for the lab baseline.
- Current state:
  - Ran `docker compose down` and `docker compose up -d` in `stack-c/`, then waited 10 seconds before bringing [[Stack A]] back up.
  - `docker ps --filter 'name=stack-c' --format 'table {{.Names}}\t{{.Status}}'` showed `stack-c-openig-c1-1` and `stack-c-openig-c2-1` healthy, and `stack-c-mariadb-1` healthy.
  - `docker logs stack-c-openig-c1-1 2>&1 | grep 'Loaded the route'` confirmed route load for `00-backchannel-logout-app5`, `00-phpmyadmin-logout`, `00-backchannel-logout-app6`, `00-grafana-logout`, `11-phpmyadmin`, and `10-grafana`.
- Files changed:
  - `stack-c/docker-compose.yml`

> [!success]
> Validation on `2026-03-18`: Stack C restarted cleanly with the new fixed container names and healthchecks, and the OpenIG route set loaded without errors.

> [!tip]
> Keep the `stack-c-openig-c1-1` and `stack-c-openig-c2-1` names stable now that health and log checks reference them directly in runbooks and automation.

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
2. `BackchannelLogoutHandler.groovy` parses `logout_token`, extracts `sid`, sets `blacklist:<sid>` in Redis (TTL 1800s, aligned to `JwtSession.sessionTimeout: "30 minutes"`).
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
  - `openig-client-c-app5` -> strong alphanumeric-only secret via local `OIDC_CLIENT_SECRET_APP5` in `stack-c/.env`
  - `openig-client-c-app6` -> strong env-backed secret via local `OIDC_CLIENT_SECRET_APP6` in `stack-c/.env`

> [!warning]
> For OpenIG OIDC clients, secret format is a compatibility requirement, not only a strength requirement. Avoid `+`, `/`, and `=` in `clientSecret` values consumed by `OAuth2ClientFilter`.
- OpenIG JWT session:
  - Cookie: `IG_SSO_C` on domain `.sso.local`
  - Stack-C specific `sharedSecret` configured in `stack-c/openig_home/config/config.json`, with Compose passing `JWT_SHARED_SECRET` from local env instead of a committed literal
- MariaDB bootstrap (dev):
  - `MYSQL_ROOT_PASSWORD` sourced from local `stack-c/.env`
  - `MYSQL_USER=alice`
  - `MYSQL_PASSWORD` sourced from local `stack-c/.env`
- phpMyAdmin runtime auth:
  - `auth_type=http`
  - actual Basic Auth username/password fetched from Vault path `secret/data/phpmyadmin/{preferred_username}`
  - AppRole files: `/opt/openig/vault/role_id`, `/opt/openig/vault/secret_id`

> [!warning]
> Do not reuse `JwtSession.sharedSecret` across stacks sharing `.sso.local` cookie domain.

## 2026-03-18 implementation note

- Context:
  - Implemented Phase 2 hardening item `[H-4/S-2]` directly for [[Stack C]] after the plan item was already confirmed in `.omc/plans/phase2-security-hardening.md`.
- What done:
  - Generated and set a new Stack C `REDIS_PASSWORD` distinct from Stack A and Stack B.
  - Reused the already-present Compose and Groovy hardening changes that pass `REDIS_PASSWORD` into [[OpenIG]] and require Redis `AUTH` before blacklist `GET` and `SET`.
  - Ran `docker compose up -d`, waited 5 seconds, then restarted `stack-c-openig-c1-1` and `stack-c-openig-c2-1`.
- Decisions:
  - Kept the Groovy change surface limited to Redis connection setup so request flow and blacklist logic stayed unchanged.
- Current state:
  - `stack-c-openig-c1-1` logs show all Stack C routes loading after restart.
  - Unauthenticated `docker exec stack-c-redis-c-1 redis-cli PING` now returns `NOAUTH Authentication required.`.
- Next steps:
  - Keep future Stack C Redis consumers on the same env-driven auth pattern.
- Files changed:
  - `stack-c/.env`
  - `stack-c/.env.example`
  - `stack-c/docker-compose.yml`
  - `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`

> [!tip]
> For Kubernetes deployment, generate the stack secret bundle from `stack-c/.env.example` and load it with `envFrom.secretRef` rather than reintroducing literals into `docker-compose.yml` or Helm values.

> [!tip]
> For SLO regressions, verify route order first (`00-*` logout routes must run before `10/11-*` app routes).

## 2026-03-18 Keycloak browser/internal URL split

- Context:
  - Implemented Phase 2 hardening item `[A-6/A-7/M-13/S-17]` directly for [[Stack C]] after confirmation in `.omc/plans/phase2-security-hardening.md`.
- What done:
  - Added `KEYCLOAK_INTERNAL_URL=http://host.docker.internal:8080` to both `openig-c1` and `openig-c2` in `stack-c/docker-compose.yml`, immediately after `KEYCLOAK_BROWSER_URL`.
  - Updated `stack-c/openig_home/config/routes/10-grafana.json` and `stack-c/openig_home/config/routes/11-phpmyadmin.json` so `issuer` and `authorizeEndpoint` stay browser-facing via `KEYCLOAK_BROWSER_URL`, while `tokenEndpoint`, `userInfoEndpoint`, and `jwksUri` use `KEYCLOAK_INTERNAL_URL`.
  - Removed `registrationEndpoint`, `endSessionEndpoint`, and `defaultLogoutGoto` only from the two requested app routes.
  - Updated `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json` and `stack-c/openig_home/config/routes/00-backchannel-logout-app6.json` so backchannel JWT verification uses env-backed `jwksUri` and browser-facing `issuer`.
- Decisions:
  - Kept issuer validation tied to [[Keycloak]] browser URL so external redirect and token issuer expectations remain aligned with `http://auth.sso.local:8080`.
  - Moved only server-to-server endpoints to `KEYCLOAK_INTERNAL_URL`; no [[OpenIG]] Groovy scripts or app container configs were changed.
- Current state:
  - `docker compose up -d` recreated `stack-c-openig-c1-1` and `stack-c-openig-c2-1`; explicit `docker restart stack-c-openig-c1-1 stack-c-openig-c2-1` completed successfully afterward.
  - `docker logs stack-c-openig-c1-1 2>&1 | grep 'Loaded the route'` showed `00-backchannel-logout-app5`, `00-backchannel-logout-app6`, `10-grafana`, and `11-phpmyadmin` loading after restart.
  - Route verification is clean: no remaining `auth.sso.local:8080` or `host.docker.internal:8080` literals under `stack-c/openig_home/config/routes/`.
- Next steps:
  - Keep future Stack C OIDC route edits on the same env-backed browser/internal split and avoid reintroducing literal Keycloak hostnames into route JSON.
- Files changed:
  - `stack-c/docker-compose.yml`
  - `stack-c/openig_home/config/routes/10-grafana.json`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json`
  - `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json`
  - `stack-c/openig_home/config/routes/00-backchannel-logout-app6.json`

> [!success]
> The Stack C route set reloaded cleanly after the restart, and the requested literal-host cleanup in `stack-c/openig_home/config/routes/` is complete.

## 2026-03-18 nginx proxy timeout hardening

- Context:
  - Implemented Phase 2 item `[A-3/S-14]` directly for [[Stack C]] after it was already confirmed in `.omc/plans/phase2-security-hardening.md`.
- What done:
  - Updated `stack-c/nginx/nginx.conf` so both `location /` blocks for Grafana and phpMyAdmin now set `proxy_connect_timeout 3s`, `proxy_read_timeout 60s`, `proxy_send_timeout 60s`, `proxy_next_upstream error timeout http_502 http_503 http_504`, and `proxy_next_upstream_tries 2` before proxying to `openig_c_pool`.
  - Updated both backchannel logout exact-match locations to add `proxy_connect_timeout 3s`, `proxy_read_timeout 60s`, and `proxy_send_timeout 60s`.
  - Left the `upstream openig_c_pool`, `server_name` values, and existing `proxy_set_header` directives unchanged.
- Current state:
  - `docker exec stack-c-nginx-c-1 nginx -t` returned syntax OK and configuration test successful.
  - `docker restart stack-c-nginx-c-1` completed successfully.
  - `curl -s -o /dev/null -w '%{http_code}' http://grafana-c.sso.local:18080/` returned `302`.
- Files changed:
  - `stack-c/nginx/nginx.conf`

> [!success]
> [[OpenIG]] ingress timeout hardening for [[Stack C]] is active and nginx accepted the updated config without errors.

> [!tip]
> Keep timeout and upstream retry behavior only on the user-facing `location /` proxy paths; leave backchannel logout endpoints without upstream retry directives so logout POST handling stays single-attempt and predictable.
