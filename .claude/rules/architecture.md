# Architecture

## Shared-infra baseline

The active lab deployment is `shared/`: one nginx, two OpenIG nodes, one Redis, one Vault, and all six apps behind hostname routing on port 80.

- Keycloak remains the shared IdP at `http://auth.sso.local:8080`
- Browser entrypoints are `http://<hostname>.sso.local` on port 80
- `stack-a/`, `stack-b/`, and `stack-c/` are legacy rollback assets, not the active deployment

## Isolation model

- App isolation is per app inside the shared runtime, not per legacy stack
- Every app has a unique `clientEndpoint`
- Every app has a route-local `JwtSession` heap: `SessionApp1..6`
- Every app has a unique host-only browser cookie: `IG_SSO_APP1..APP6`
- Every app has a unique Redis ACL user and key prefix: `openig-app1..6`, `app1:*..app6:*`
- Every app has a unique Vault AppRole and scoped policy: `openig-app1..6`
- `CANONICAL_ORIGIN_APP1..6` are the only allowed bases for redirect/logout construction

## Stack overview

| Runtime | Public access | Apps | Auth mechanisms |
|---------|---------------|------|-----------------|
| `shared/` | Hostname routing on port 80 | WordPress, WhoAmI, Redmine, Jellyfin, Grafana, phpMyAdmin | Form, header, token, HTTP Basic |

## App routing and session map

| App | Hostname | Internal upstream | clientEndpoint | Keycloak client | Session heap | Cookie |
|-----|----------|-------------------|----------------|-----------------|--------------|--------|
| WordPress | `http://wp-a.sso.local` | `http://shared-wordpress` | `/openid/app1` | `openig-client` | `SessionApp1` | `IG_SSO_APP1` |
| WhoAmI | `http://whoami-a.sso.local` | `http://shared-whoami` | `/openid/app2` | `openig-client` | `SessionApp2` | `IG_SSO_APP2` |
| Redmine | `http://redmine-b.sso.local` | `http://shared-redmine:3000` | `/openid/app3` | `openig-client-b` | `SessionApp3` | `IG_SSO_APP3` |
| Jellyfin | `http://jellyfin-b.sso.local` | `http://shared-jellyfin:8096` | `/openid/app4` | `openig-client-b-app4` | `SessionApp4` | `IG_SSO_APP4` |
| Grafana | `http://grafana-c.sso.local` | `http://shared-grafana:3000` | `/openid/app5` | `openig-client-c-app5` | `SessionApp5` | `IG_SSO_APP5` |
| phpMyAdmin | `http://phpmyadmin-c.sso.local` | `http://shared-phpmyadmin:80` | `/openid/app6` | `openig-client-c-app6` | `SessionApp6` | `IG_SSO_APP6` |

## HA pattern

- `shared-nginx` uses `ip_hash` to `shared-openig-1` and `shared-openig-2`
- `TokenReferenceFilter.groovy` offloads heavyweight `oauth2:*` state to Redis and keeps only per-app token-reference keys in the cookie (`token_ref_id_app1..6`)
- `SessionBlacklistFilter.groovy` checks Redis on every authenticated request
- `StripGatewaySessionCookies.groovy` removes gateway cookies before proxying to backends
- All redirect and logout logic must use `CANONICAL_ORIGIN_APP1..6`, never the inbound `Host`

## Cookie session

- Shared-infra routes override the global `Session` heap from `shared/openig_home/config/config.json`
- Active app flows use route-local heaps `SessionApp1..6` with cookies `IG_SSO_APP1..APP6`
- Route-local cookies do not set `cookieDomain`, so they are host-only
- `shared/openig_home/config/config.json` still contains the fallback global heap `Session` with cookie `IG_SSO`, but active shared-infra routes do not use it

## Container names

| Component | Container name |
|-----------|----------------|
| nginx | `shared-nginx` |
| openig-1 | `shared-openig-1` |
| openig-2 | `shared-openig-2` |
| redis | `shared-redis` |
| vault | `shared-vault` |
| wordpress | `shared-wordpress` |
| whoami | `shared-whoami` |
| redmine | `shared-redmine` |
| jellyfin | `shared-jellyfin` |
| grafana | `shared-grafana` |
| phpMyAdmin | `shared-phpmyadmin` |
| mysql-a | `shared-mysql-a` |
| mysql-b | `shared-mysql-b` |
| mariadb | `shared-mariadb` |
| keycloak | `sso-keycloak` |

`shared/docker-compose.yml` is the source of truth here; the phpMyAdmin database container is `shared-mariadb`.

## URLs

| Service | URL |
|---------|-----|
| Keycloak | `http://auth.sso.local:8080` |
| OpenIG management | `http://openig.sso.local` |
| WordPress | `http://wp-a.sso.local` |
| WhoAmI | `http://whoami-a.sso.local` |
| Redmine | `http://redmine-b.sso.local` |
| Jellyfin | `http://jellyfin-b.sso.local` |
| Grafana | `http://grafana-c.sso.local` |
| phpMyAdmin | `http://phpmyadmin-c.sso.local` |

Keycloak test users: `alice`/`alice123`, `bob`/`bob123`

## Vault AppRoles

All AppRoles are bootstrapped by `shared/vault/init/vault-bootstrap.sh` and write role files to `/vault/file/openig-appN-role-id` and `/vault/file/openig-appN-secret-id`.

| App | AppRole | Policy | Secret path scope | Notes |
|-----|---------|--------|-------------------|-------|
| WordPress | `openig-app1` | `openig-app1-policy` | `secret/data/wp-creds/*` | Active Vault credential lookup |
| WhoAmI | `openig-app2` | `openig-app2-policy` | `secret/data/dummy/*` | Placeholder scope; current route does not fetch Vault credentials |
| Redmine | `openig-app3` | `openig-app3-policy` | `secret/data/redmine-creds/*` | Active Vault credential lookup |
| Jellyfin | `openig-app4` | `openig-app4-policy` | `secret/data/jellyfin-creds/*` | Active Vault credential lookup |
| Grafana | `openig-app5` | `openig-app5-policy` | `secret/data/grafana-creds/*` | Policy exists; current route uses header injection, not Vault credentials |
| phpMyAdmin | `openig-app6` | `openig-app6-policy` | `secret/data/phpmyadmin/*` | Active Vault credential lookup |

AppRole hardening in the lab:

- `token_ttl=1h`
- `token_max_ttl=4h`
- `secret_id_ttl=72h`

## Redis ACL

`shared/redis/acl.conf` disables the default user and gives each app one minimal ACL user.

| App | Redis user | Key prefix | Allowed commands |
|-----|------------|------------|------------------|
| WordPress | `openig-app1` | `~app1:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| WhoAmI | `openig-app2` | `~app2:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| Redmine | `openig-app3` | `~app3:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| Jellyfin | `openig-app4` | `~app4:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| Grafana | `openig-app5` | `~app5:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| phpMyAdmin | `openig-app6` | `~app6:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |

Rules:

- Redis auth is `AUTH <username> <password>`, not password-only `AUTH`
- No app may read or write another app's keys
- Redis key namespaces must stay app-scoped for both blacklist and token-reference data

## Production gaps

These are known lab limitations, not production-ready controls:

- Traffic between nginx, OpenIG, Vault, Redis, Keycloak, and backends is still HTTP-only; production requires TLS or mTLS and `requireHttps: true`
- Redis token-reference and blacklist payloads are not wrapped with Vault Transit; production should use Transit or equivalent envelope encryption
- The lab uses flat Docker bridge networks; production should segment browser, app, and admin/control-plane traffic
- Vault is single-node file storage with manual bootstrap and manual AppRole `secret_id` regeneration; production needs HA and rotation workflow
- Local Docker volumes do not imply OS-level disk encryption; production needs encrypted storage and backup handling
