# Shared Infrastructure Consolidation Plan

## Context

The SSO Lab currently runs 3 independent stacks, each with its own nginx, 2 OpenIG HA nodes, Redis, and Vault instance. This creates 6 OpenIG containers, 3 Redis containers, 3 Vault containers, and 3 nginx containers serving 6 legacy apps. After pattern consolidation (Steps 1-6), Groovy scripts are already parameterized templates shared across stacks via route `args`. The goal is to consolidate into a single shared infrastructure with banking-grade isolation at the application layer.

### Current State

| Component | Stack A (port 80) | Stack B (port 9080) | Stack C (port 18080) |
|-----------|-------------------|---------------------|----------------------|
| nginx | `sso-nginx` | `sso-b-nginx` | `stack-c-nginx-c-1` |
| OpenIG x2 | `sso-openig-1/2` | `sso-b-openig-1/2` | `stack-c-openig-c1/c2-1` |
| Redis | `sso-redis-a` | `sso-redis-b` | `stack-c-redis-c-1` |
| Vault | `sso-vault` | `sso-b-vault` | `stack-c-vault-c-1` |
| Apps | WordPress, WhoAmI | Redmine, Jellyfin | Grafana, phpMyAdmin |
| Cookie | `IG_SSO` | `IG_SSO_B` | `IG_SSO_C` |
| Vault AppRole | `openig` | `openig-role-b` | `openig-role-c` |
| Redis AUTH | `requirepass` (single pw) | same | same |

- 25 Groovy scripts (12 unique templates, rest are copies)
- 16 route JSON files
- 3 docker-compose.yml, 3 `.env` files, 3 vault bootstrap scripts
- Keycloak shared at `auth.sso.local:8080` (already single instance)

### Target State

| Component | Shared Infra (port 80) |
|-----------|----------------------|
| nginx | 1 instance, all 6 server blocks |
| OpenIG x2 | 2 nodes, all 16 routes, all scripts |
| Redis | 1 instance, Redis 7 ACL (6 per-app users) |
| Vault | 1 instance, 6 per-app AppRoles |
| Apps | All 6 apps + their databases |
| Cookie | Single `IG_SSO`, `cookieDomain: ".sso.local"` |
| Port | 80 (hostname-based routing for all apps) |

Container count: 18 -> 11 (nginx 3->1, OpenIG 6->2, Redis 3->1, Vault 3->1, apps+DBs unchanged at 6)

## Prerequisites

- **BUG-SSO2-AFTER-SLO must be fixed and merged first.** Consolidation should start from a known-good state. The 3-part fix (SloHandler Redis cleanup, SloHandlerJellyfin Redis cleanup, TokenReferenceFilter targeted removal) is designed and ready for implementation. Attempting consolidation with this bug present would make debugging significantly harder.

## Design Decisions (User-Confirmed)

1. **Migration strategy:** Incremental, stack-by-stack (A first, then B, then C). Each stack validated before proceeding. Old stacks remain available for rollback.
2. **Vault AppRole design:** Per-app (6 AppRoles). Each app gets its own role, scoped to its secret path only. Blast radius contained. Extends existing `VaultCredentialFilter.groovy` parameterized pattern with `appRoleName` arg.
3. **Redis design:** Single Redis 7 instance with ACL. Each app gets its own Redis user + password, scoped to its key prefix only (e.g., `user openig-app1 ~app1:* +@read +@write`). Extends cleanly to Redis Sentinel/Cluster. RESP AUTH command changes from `AUTH <password>` to `AUTH <username> <password>`.

## Guardrails

### Must Have
- Per-app Redis ACL users with key-prefix scoping (no app can read/write another app's keys)
- Per-app Vault AppRoles with scoped policies (no app can read another app's secrets)
- All 6 apps SSO/SLO validated after each migration step
- Rollback path: old stack directories preserved until final validation
- Single `docker compose up -d` starts entire shared infra
- Keycloak client configurations updated for new port scheme

### Must NOT Have
- Shared Redis password (defeats ACL purpose)
- Shared Vault AppRole (defeats per-app isolation)
- Modification of any legacy app code/config (OpenIG gateway only)
- Breaking changes to existing Keycloak realm/user configuration
- New dependencies or frameworks (pure Docker Compose + existing tooling)

---

## Task Flow

### Step 1: Foundation Infrastructure
**Goal:** Create the `shared/` directory with all infrastructure components configured and ready, before wiring any apps.

**Directory structure:**
```
shared/
  docker-compose.yml          # All services: nginx, 2 OpenIG, Redis, Vault, 6 apps, 3 DBs
  .env.example                # All secrets (merged from 3 .env.example files)
  .env                        # Actual secrets (gitignored)
  nginx/
    nginx.conf                # Merged: all 6 server blocks, single upstream pool, port 80
  redis/
    acl.conf                  # Redis ACL: 6 per-app users + default user disabled
  openig_home/
    config/
      config.json             # Merged: single JwtSession (cookie=IG_SSO), all OIDC client secrets
      routes/                 # All 16 route files (merged from 3 stacks)
    scripts/groovy/           # Single copy of each Groovy script (12 files)
    vault/                    # role_id/secret_id per-app files (created by bootstrap)
    phpmyadmin/
      config.user.inc.php     # Copied from stack-c
  docker/
    openig/
      docker-entrypoint.sh    # Merged: all env var substitutions
      server.xml              # Copied from stack-a
    wordpress/
      app1.conf               # Copied from stack-a
    mysql/
      init.sql                # Copied from stack-a
  vault/
    config/
      vault.hcl               # Copied (identical across stacks)
    init/
      vault-bootstrap.sh      # NEW: 6 AppRoles, 6 policies, all secret seeds
    data/                     # Vault persistent data
    file/                     # Role IDs, secret IDs, audit log
    keys/                     # Unseal + admin keys (gitignored)
```

**Sub-tasks:**

1. **docker-compose.yml** -- Single compose file with all services:
   - `nginx`: port 80:80, single upstream pool
   - `openig-1`, `openig-2`: pinned `openidentityplatform/openig:6.0.1`, shared volumes, all env vars for 6 apps (OIDC secrets, canonical origins, Redis user/password per app, Vault per-app role files)
   - `redis`: Redis 7 Alpine with ACL config mount (`--aclfile /etc/redis/acl.conf`), appendonly yes, appendfsync everysec
   - `vault`: single Vault 1.15 instance
   - `wordpress`, `whoami`, `redmine`, `jellyfin`, `grafana`, `phpmyadmin`: all on backend network
   - `mysql-a`, `mysql-b`, `mariadb-c`: all app databases
   - Networks: `frontend` (nginx only) + `backend` (everything else)

2. **redis/acl.conf** -- Redis ACL configuration:
   ```
   user default off
   user openig-app1 on >__REDIS_PASSWORD_APP1__ ~app1:* +@all
   user openig-app2 on >__REDIS_PASSWORD_APP2__ ~app2:* +@all
   user openig-app3 on >__REDIS_PASSWORD_APP3__ ~app3:* +@all
   user openig-app4 on >__REDIS_PASSWORD_APP4__ ~app4:* +@all
   user openig-app5 on >__REDIS_PASSWORD_APP5__ ~app5:* +@all
   user openig-app6 on >__REDIS_PASSWORD_APP6__ ~app6:* +@all
   ```
   Note: passwords injected via `docker-entrypoint.sh` sed or Redis `CONFIG SET` at bootstrap. Key prefixes must align with Groovy script key patterns (see Step 2).

3. **vault/init/vault-bootstrap.sh** -- Consolidated bootstrap:
   - 6 AppRoles: `openig-app1` through `openig-app6`
   - 6 policies, each scoped:
     - `openig-app1-policy`: `path "secret/data/wp-creds/*" { capabilities = ["read"] }`
     - `openig-app3-policy`: `path "secret/data/redmine-creds/*" { capabilities = ["read"] }`
     - `openig-app4-policy`: `path "secret/data/jellyfin-creds/*" { capabilities = ["read"] }`
     - `openig-app6-policy`: `path "secret/data/phpmyadmin/*" { capabilities = ["read"] }`
     - `openig-app2-policy`, `openig-app5-policy`: minimal (WhoAmI/Grafana don't use Vault creds directly, but AppRole still needed for future extensibility -- policy can be empty `path "secret/data/noop" { capabilities = [] }`)
   - All secret seeds in one bootstrap:
     - `secret/wp-creds/alice` (username=alice_wp, password from current Stack A)
     - `secret/wp-creds/bob` (username=bob_wp, password from current Stack A)
     - `secret/redmine-creds/alice@lab.local` (from current Stack B)
     - `secret/redmine-creds/bob@lab.local` (from current Stack B)
     - `secret/jellyfin-creds/alice` (from current Stack B)
     - `secret/jellyfin-creds/bob` (from current Stack B)
     - `secret/phpmyadmin/alice` (password=AlicePass123, aligned with MariaDB)
     - `secret/phpmyadmin/bob` (from current Stack C)
   - Per-app role_id/secret_id files written to `/vault/file/openig-app{1..6}-{role-id,secret-id}`
   - Admin token management (same pattern as current stacks, with expanded admin policy covering all 6 roles)
   - `secret_id_ttl: 72h` on all AppRoles (same as current)
   - Audit logging enabled (same as current)
   - `max_versions=5` on secret engine (same as current)

4. **nginx/nginx.conf** -- Merged from 3 stacks:
   - Single upstream: `upstream openig_pool { ip_hash; server openig-1:8080 max_fails=3 fail_timeout=10s; server openig-2:8080 max_fails=3 fail_timeout=10s; keepalive 16; }`
   - Proxy buffers at http level: `proxy_buffer_size 128k; proxy_buffers 4 256k; proxy_busy_buffers_size 256k;`
   - Security headers at http level: `X-Frame-Options SAMEORIGIN`, `X-Content-Type-Options nosniff`, `Referrer-Policy strict-origin-when-cross-origin`
   - 7 server blocks, all `listen 80`:

     **wp-a.sso.local:**
     - `location = /openid/app1/backchannel_logout` (backchannel, proxy_request_buffering off, proxy_next_upstream off)
     - `location /openig` (return 403)
     - `location /` (proxy to openig_pool, strip X-Authenticated-User + X-WEBAUTH-USER, X-Forwarded-Proto $scheme, proxy_cookie_flags IG_SSO samesite=lax)

     **whoami-a.sso.local:**
     - `location /openig` (return 403)
     - `location /` (same proxy settings, proxy_cookie_flags IG_SSO samesite=lax)

     **redmine-b.sso.local:**
     - `location = /openid/app3/backchannel_logout` (backchannel)
     - `location = /logout` (proxy, for Redmine logout interception)
     - `location /openig` (return 403)
     - `location /` (proxy, WebSocket upgrade headers, proxy_cookie_flags IG_SSO samesite=lax)

     **jellyfin-b.sso.local:**
     - `location = /openid/app4/backchannel_logout` (backchannel)
     - `location = /web/index.html` (proxy, for Jellyfin post-logout redirect)
     - `location /openig` (return 403)
     - `location /` (proxy, WebSocket upgrade headers, proxy_read_timeout 300s, proxy_send_timeout 300s, proxy_cookie_flags IG_SSO samesite=lax)

     **grafana-c.sso.local:**
     - `location = /openid/app5/backchannel_logout` (backchannel)
     - `location /openig` (return 403)
     - `location /` (proxy, strip X-Authenticated-User + X-WEBAUTH-USER, proxy_cookie_flags IG_SSO samesite=lax)

     **phpmyadmin-c.sso.local:**
     - `location = /openid/app6/backchannel_logout` (backchannel)
     - `location /openig` (return 403)
     - `location /` (proxy, strip X-Authenticated-User + X-WEBAUTH-USER + Authorization, proxy_cookie_flags IG_SSO samesite=lax)

     **openig.sso.local:** (management, replaces openiga/openigb/openig-c)
     - `location /openig` (return 403)
     - `location /` (proxy, generic)

5. **openig_home/config/config.json** -- Merged JwtSession config:
   - Heap object name: `"Session"` (required for OpenIG auto-wire)
   - Cookie name: `IG_SSO` (single cookie for all apps)
   - `cookieDomain: ".sso.local"`
   - `sharedSecret`: from `__JWT_SHARED_SECRET__` placeholder (sed in entrypoint)
   - PKCS12 keystore: `__KEYSTORE_PASSWORD__` placeholder
   - OIDC client secrets via env var EL in route files: `${env['OIDC_CLIENT_SECRET']}`, `${env['OIDC_CLIENT_SECRET_APP4']}`, `${env['OIDC_CLIENT_SECRET_APP5']}`, `${env['OIDC_CLIENT_SECRET_APP6']}`
   - Note: Stack A app1+app2 share `openig-client` (same secret). Stack B app3 uses `openig-client-b`. Stack B app4 uses `openig-client-b-app4`. Stack C app5 uses `openig-client-c-app5`. Stack C app6 uses `openig-client-c-app6`.

6. **Groovy scripts** -- Single copy of each, 12 files total:
   - `TokenReferenceFilter.groovy` (shared by all 6 app routes)
   - `BackchannelLogoutHandler.groovy` (shared by all 6 backchannel routes)
   - `SessionBlacklistFilter.groovy` (shared by all 6 app routes)
   - `SloHandler.groovy` (shared by app1, app2, app3, app5, app6 logout routes)
   - `SloHandlerJellyfin.groovy` (app4 logout route only)
   - `VaultCredentialFilter.groovy` (app1, app3, app4, app6 app routes)
   - `CredentialInjector.groovy` (app1 -- WordPress cookie pass-through)
   - `RedmineCredentialInjector.groovy` (app3 -- Redmine cookie pass-through)
   - `JellyfinTokenInjector.groovy` (app4 -- Jellyfin API token injection)
   - `JellyfinResponseRewriter.groovy` (app4 -- Jellyfin response rewriting)
   - `StripGatewaySessionCookies.groovy` (shared by all 6 app routes)
   - `PhpMyAdminAuthFailureHandler.groovy` (app6 -- phpMyAdmin 401 detection)
   - `SpaAuthGuardFilter.groovy` (app5 -- Grafana SPA guard)

7. **Route files** -- All 16 from 3 stacks, no name conflicts:
   - From Stack A: `01-wordpress.json`, `02-app2.json`, `00-backchannel-logout-app1.json`, `00-wp-logout.json`
   - From Stack B: `02-redmine.json`, `01-jellyfin.json`, `00-backchannel-logout-app3.json`, `00-backchannel-logout-app4.json`, `00-redmine-logout.json`, `00-jellyfin-logout.json`
   - From Stack C: `10-grafana.json`, `11-phpmyadmin.json`, `00-backchannel-logout-app5.json`, `00-backchannel-logout-app6.json`, `00-grafana-logout.json`, `00-phpmyadmin-logout.json`

8. **.env.example** -- Merged secrets template:
   ```
   # OpenIG JwtSession
   JWT_SHARED_SECRET=<shared secret for JwtSession across both nodes>
   KEYSTORE_PASSWORD=<password for the OpenIG PKCS12 keystore>

   # OIDC client secrets (must match Keycloak, alphanumeric-only)
   OIDC_CLIENT_SECRET=<openig-client secret>
   OIDC_CLIENT_SECRET_APP4=<openig-client-b-app4 secret>
   OIDC_CLIENT_SECRET_APP5=<openig-client-c-app5 secret>
   OIDC_CLIENT_SECRET_APP6=<openig-client-c-app6 secret>

   # Redis ACL per-app passwords
   REDIS_PASSWORD_APP1=<generate-strong-password>
   REDIS_PASSWORD_APP2=<generate-strong-password>
   REDIS_PASSWORD_APP3=<generate-strong-password>
   REDIS_PASSWORD_APP4=<generate-strong-password>
   REDIS_PASSWORD_APP5=<generate-strong-password>
   REDIS_PASSWORD_APP6=<generate-strong-password>

   # WordPress DB (mysql-a)
   MYSQL_ROOT_PASSWORD_A=<MySQL root password>
   WORDPRESS_DB_PASSWORD=<WordPress database password>

   # Redmine DB (mysql-b)
   MYSQL_ROOT_PASSWORD_B=<MySQL root password>
   REDMINE_DB_PASSWORD=<Redmine database password>
   REDMINE_SECRET_KEY_BASE=<Redmine secret_key_base>

   # phpMyAdmin DB (mariadb-c)
   MYSQL_ROOT_PASSWORD_C=<MariaDB root password>
   MYSQL_PASSWORD_C=<MariaDB user password for alice>

   # Jellyfin
   JELLYFIN_SERVER_ID=8a4467ecf1d4422583f472d90cb8c78f
   ```

9. **docker/openig/docker-entrypoint.sh** -- Merged entrypoint:
   - Same pattern as current stacks: copy `/opt/openig` to `/tmp/openig`, sed placeholders
   - Placeholders: `__JWT_SHARED_SECRET__`, `__KEYSTORE_PASSWORD__`
   - `rm -rf "$DST"` before `cp -r` (prevents stale config on restart)
   - Set `OPENIG_BASE=/tmp/openig`

**Acceptance criteria:**
- [ ] `shared/` directory created with all files listed above
- [ ] `cd shared && docker compose config` validates without error
- [ ] Redis ACL config has 6 users with distinct key prefixes (`app1:*` through `app6:*`)
- [ ] Vault bootstrap script creates 6 AppRoles with scoped policies
- [ ] nginx.conf has all 6 app server blocks + management block, all on port 80
- [ ] config.json has single JwtSession heap object named `"Session"` with cookie `IG_SSO`
- [ ] No hardcoded secrets in committed files (all via `.env` or `__PLACEHOLDER__` patterns)
- [ ] .gitignore covers `.env`, `vault/keys/`, `vault/file/openig-app*`

---

### Step 2: Groovy Script Adaptation for Redis ACL + Per-App Vault
**Goal:** Modify the 4 Groovy scripts that connect to Redis or Vault so they work with ACL authentication and per-app AppRoles. Update all 16 route files with new args.

**Sub-tasks:**

1. **Redis ACL AUTH in Groovy scripts** -- 3 files to modify:

   **TokenReferenceFilter.groovy** (current `buildAuthCommand` closure, lines 56-62):
   - Current RESP: `*2\r\n$4\r\nAUTH\r\n$<pwLen>\r\n<pw>\r\n` (password-only)
   - Target RESP: `*3\r\n$4\r\nAUTH\r\n$<uLen>\r\n<user>\r\n$<pLen>\r\n<pw>\r\n` (username+password)
   - New route arg: `redisUser` (read via `binding.hasVariable('redisUser')`)
   - New route arg: `redisPasswordEnvVar` (e.g., `REDIS_PASSWORD_APP1`; script reads `System.getenv(configuredRedisPasswordEnvVar)`)
   - New route arg: `redisKeyPrefix` (e.g., `app1`)
   - Key pattern changes:
     - `token_ref:${tokenRefId}` becomes `${redisKeyPrefix}:token_ref:${tokenRefId}`
   - Backward compatibility: if `redisUser` arg is absent, fall back to current password-only AUTH (so existing per-stack setups still work during incremental migration)

   **BackchannelLogoutHandler.groovy** (current inline AUTH block, lines 418-430):
   - Same AUTH pattern change from `*2` to `*3` RESP format
   - New route args: `redisUser`, `redisPasswordEnvVar`, `redisKeyPrefix`
   - Key pattern change: `blacklist:${sid}` becomes `${redisKeyPrefix}:blacklist:${sid}`
   - Backward compatibility: if `redisUser` absent, fall back to password-only AUTH

   **SessionBlacklistFilter.groovy** (current inline AUTH block, lines 110-122):
   - Same AUTH pattern change
   - New route args: `redisUser`, `redisPasswordEnvVar`, `redisKeyPrefix`
   - Key pattern change: `blacklist:${sid}` becomes `${redisKeyPrefix}:blacklist:${sid}`
   - Backward compatibility: same fallback pattern

   **CRITICAL consistency rule:** BackchannelLogoutHandler writes `${prefix}:blacklist:${sid}` and SessionBlacklistFilter reads it. Both MUST use the same `redisKeyPrefix` and `redisUser` for the same app. This is enforced via identical route args on each app's backchannel route and app route.

   **Key prefix mapping (Redis ACL user -> key prefix -> key patterns):**

   | App | Redis user | Key prefix | ACL pattern | Token ref key | Blacklist key |
   |-----|-----------|------------|-------------|---------------|---------------|
   | WordPress (app1) | `openig-app1` | `app1` | `~app1:*` | `app1:token_ref:<uuid>` | `app1:blacklist:<sid>` |
   | WhoAmI (app2) | `openig-app2` | `app2` | `~app2:*` | `app2:token_ref:<uuid>` | `app2:blacklist:<sid>` |
   | Redmine (app3) | `openig-app3` | `app3` | `~app3:*` | `app3:token_ref:<uuid>` | `app3:blacklist:<sid>` |
   | Jellyfin (app4) | `openig-app4` | `app4` | `~app4:*` | `app4:token_ref:<uuid>` | `app4:blacklist:<sid>` |
   | Grafana (app5) | `openig-app5` | `app5` | `~app5:*` | `app5:token_ref:<uuid>` | `app5:blacklist:<sid>` |
   | phpMyAdmin (app6) | `openig-app6` | `app6` | `~app6:*` | `app6:token_ref:<uuid>` | `app6:blacklist:<sid>` |

2. **Per-app Vault AppRole in VaultCredentialFilter.groovy:**
   - Current: `globals.compute('vault_token') { ... }` -- single cache entry for all apps in a stack
   - Target: `globals.compute("vault_token_${configuredAppRoleName}") { ... }` -- per-app cache entry
   - New route arg: `appRoleName` (e.g., `openig-app1`); used for cache key differentiation
   - New route args: `vaultRoleIdFile` (e.g., `/vault/file/openig-app1-role-id`), `vaultSecretIdFile` (e.g., `/vault/file/openig-app1-secret-id`)
   - These REPLACE the current env var reads `VAULT_ROLE_ID_FILE` and `VAULT_SECRET_ID_FILE`; route args take precedence, env vars become fallback
   - The AppRole login endpoint stays `auth/approle/login` (same for all AppRoles). Per-app isolation comes from distinct `role_id`/`secret_id` pairs pointing to differently-scoped AppRoles in Vault.
   - Cache key separation prevents one app's token refresh or 403-retry from invalidating another app's cached token
   - Backward compatibility: if `appRoleName` absent, fall back to `vault_token` cache key and env var file paths (existing behavior)

3. **Route files update** -- All 16 routes need new args:

   **App routes (6 files) -- add to all ScriptableFilter `args` blocks that reference TokenReferenceFilter and SessionBlacklistFilter:**

   `01-wordpress.json`:
   ```json
   "args": {
     "redisHost": "redis",
     "redisUser": "openig-app1",
     "redisPasswordEnvVar": "REDIS_PASSWORD_APP1",
     "redisKeyPrefix": "app1",
     "tokenRefKey": "token_ref_id_app1",
     "redisTtl": 1800,
     "clientEndpoint": "/openid/app1",
     "sessionCacheKey": "sid_cache_app1",
     "canonicalOriginEnvVar": "CANONICAL_ORIGIN_APP1",
     "appRoleName": "openig-app1",
     "vaultRoleIdFile": "/vault/file/openig-app1-role-id",
     "vaultSecretIdFile": "/vault/file/openig-app1-secret-id",
     "secretPathPrefix": "wp-creds",
     "appDisplayName": "WordPress"
   }
   ```

   `02-app2.json` (WhoAmI -- no Vault):
   ```json
   "args": {
     "redisHost": "redis",
     "redisUser": "openig-app2",
     "redisPasswordEnvVar": "REDIS_PASSWORD_APP2",
     "redisKeyPrefix": "app2",
     "tokenRefKey": "token_ref_id_app2",
     "redisTtl": 1800,
     "clientEndpoint": "/openid/app2",
     "sessionCacheKey": "sid_cache_app2",
     "canonicalOriginEnvVar": "CANONICAL_ORIGIN_APP2"
   }
   ```

   `02-redmine.json`:
   ```json
   "args": {
     "redisHost": "redis",
     "redisUser": "openig-app3",
     "redisPasswordEnvVar": "REDIS_PASSWORD_APP3",
     "redisKeyPrefix": "app3",
     "tokenRefKey": "token_ref_id_app3",
     "redisTtl": 1800,
     "clientEndpoint": "/openid/app3",
     "sessionCacheKey": "sid_cache_app3",
     "canonicalOriginEnvVar": "CANONICAL_ORIGIN_APP3",
     "appRoleName": "openig-app3",
     "vaultRoleIdFile": "/vault/file/openig-app3-role-id",
     "vaultSecretIdFile": "/vault/file/openig-app3-secret-id",
     "secretPathPrefix": "redmine-creds",
     "appDisplayName": "Redmine"
   }
   ```

   `01-jellyfin.json`:
   ```json
   "args": {
     "redisHost": "redis",
     "redisUser": "openig-app4",
     "redisPasswordEnvVar": "REDIS_PASSWORD_APP4",
     "redisKeyPrefix": "app4",
     "tokenRefKey": "token_ref_id_app4",
     "redisTtl": 1800,
     "clientEndpoint": "/openid/app4",
     "sessionCacheKey": "sid_cache_app4",
     "canonicalOriginEnvVar": "CANONICAL_ORIGIN_APP4",
     "appRoleName": "openig-app4",
     "vaultRoleIdFile": "/vault/file/openig-app4-role-id",
     "vaultSecretIdFile": "/vault/file/openig-app4-secret-id",
     "secretPathPrefix": "jellyfin-creds",
     "appDisplayName": "Jellyfin"
   }
   ```

   `10-grafana.json` (no Vault):
   ```json
   "args": {
     "redisHost": "redis",
     "redisUser": "openig-app5",
     "redisPasswordEnvVar": "REDIS_PASSWORD_APP5",
     "redisKeyPrefix": "app5",
     "tokenRefKey": "token_ref_id_app5",
     "redisTtl": 1800,
     "clientEndpoint": "/openid/app5",
     "sessionCacheKey": "sid_cache_app5",
     "canonicalOriginEnvVar": "CANONICAL_ORIGIN_APP5"
   }
   ```

   `11-phpmyadmin.json`:
   ```json
   "args": {
     "redisHost": "redis",
     "redisUser": "openig-app6",
     "redisPasswordEnvVar": "REDIS_PASSWORD_APP6",
     "redisKeyPrefix": "app6",
     "tokenRefKey": "token_ref_id_app6",
     "redisTtl": 1800,
     "clientEndpoint": "/openid/app6",
     "sessionCacheKey": "sid_cache_app6",
     "canonicalOriginEnvVar": "CANONICAL_ORIGIN_APP6",
     "appRoleName": "openig-app6",
     "vaultRoleIdFile": "/vault/file/openig-app6-role-id",
     "vaultSecretIdFile": "/vault/file/openig-app6-secret-id",
     "secretPathPrefix": "phpmyadmin",
     "appDisplayName": "phpMyAdmin"
   }
   ```

   **Backchannel routes (6 files) -- add Redis ACL args to BackchannelLogoutHandler:**

   Each backchannel route (`00-backchannel-logout-appN.json`) adds:
   ```json
   "args": {
     "redisHost": "redis",
     "redisUser": "openig-appN",
     "redisPasswordEnvVar": "REDIS_PASSWORD_APPN",
     "redisKeyPrefix": "appN",
     "redisPort": "6379",
     "ttlSeconds": 28800,
     "audiences": ["<keycloak-client-id>"],
     "jwksUri": "${env['KEYCLOAK_INTERNAL_URL']}/realms/sso-realm/protocol/openid-connect/certs",
     "issuer": "${env['KEYCLOAK_BROWSER_URL']}/realms/sso-realm"
   }
   ```

   **Logout routes (4-6 files):** SloHandler and SloHandlerJellyfin do NOT directly connect to Redis (they do session.clear() + Keycloak redirect). No Redis ACL args needed. Existing args (clientEndpoint, canonicalOriginEnvVar, etc.) remain unchanged. After BUG-SSO2-AFTER-SLO fix, SloHandler will iterate `token_ref_id_*` keys and delete from Redis before session.clear() -- at that point, SloHandler will also need `redisHost`, `redisUser`, `redisPasswordEnvVar`, `redisKeyPrefix` args. Plan for this in the SloHandler fix.

**Acceptance criteria:**
- [ ] TokenReferenceFilter, BackchannelLogoutHandler, SessionBlacklistFilter all support `AUTH <username> <password>` via `redisUser` arg
- [ ] All 3 Redis-touching scripts prefix keys with `redisKeyPrefix` arg
- [ ] Backward compatibility: scripts still work with password-only AUTH when `redisUser` arg absent
- [ ] VaultCredentialFilter uses per-app cache key `vault_token_${appRoleName}`
- [ ] VaultCredentialFilter reads per-app role_id/secret_id file paths from args (with env var fallback)
- [ ] All 16 route files have correct new args for their respective app
- [ ] Args are consistent: same `redisKeyPrefix` + `redisUser` used for an app's main route and its backchannel route
- [ ] No Groovy compilation errors (verify via container startup log -- `Loaded the route`)

---

### Step 3: Migrate Stack A (WordPress + WhoAmI) + Validate
**Goal:** Bring Stack A apps into the shared infrastructure, update Keycloak if needed, and validate SSO/SLO.

**Sub-tasks:**

1. **Keycloak client URL verification** (runtime, via admin API):
   - `openig-client`: redirect URIs are already on port 80 (`http://wp-a.sso.local/*`, `http://whoami-a.sso.local/*`) -- no change needed
   - Backchannel logout URLs: already `http://host.docker.internal:80/openid/app1/backchannel_logout` -- no change needed
   - `post_logout_redirect_uris`: already on port 80 -- no change needed
   - Stack A is the simplest migration because its port (80) matches the shared infra port

2. **Stop old Stack A** (keep old directory for rollback):
   ```bash
   cd /Volumes/OS/claude/openig/sso-lab/stack-a
   docker compose down
   ```

3. **Start shared infrastructure:**
   ```bash
   cd /Volumes/OS/claude/openig/sso-lab/shared
   docker compose up -d
   # Wait for Vault to start
   docker cp vault/init/vault-bootstrap.sh shared-vault:/tmp/vault-bootstrap.sh
   docker exec shared-vault sh /tmp/vault-bootstrap.sh
   # Restart OpenIG to pick up fresh role_id/secret_id
   docker restart shared-openig-1 shared-openig-2
   ```

4. **Verify infrastructure health:**
   - All containers running: `docker compose ps`
   - OpenIG routes loaded: `docker logs shared-openig-1 2>&1 | grep 'Loaded the route'` (expect 16 routes)
   - Redis ACL active: `docker exec shared-redis redis-cli --user openig-app1 --pass <pw> PING` returns PONG
   - Vault unsealed: `docker exec shared-vault vault status`

5. **Verify Redis ACL isolation:**
   ```bash
   # app1 user can write app1 key
   docker exec shared-redis redis-cli --user openig-app1 --pass <pw> SET app1:test 1
   # Expected: OK

   # app1 user CANNOT write app2 key
   docker exec shared-redis redis-cli --user openig-app1 --pass <pw> SET app2:test 1
   # Expected: (error) NOPERM
   ```

6. **Validate SSO/SLO for app1 (WordPress):**
   - Browse `http://wp-a.sso.local` -- should redirect to Keycloak
   - Login as alice -- should SSO into WordPress dashboard
   - Verify browser cookie: `IG_SSO` present (not `IG_SSO_B` or `IG_SSO_C` -- those no longer exist)
   - Check Redis: `docker exec shared-redis redis-cli --user openig-app1 --pass <pw> KEYS 'app1:*'` shows `app1:token_ref:*`
   - Logout -- verify Keycloak end-session page -- redirect back to WordPress
   - Check Redis: `app1:blacklist:*` key exists (backchannel logout wrote it)
   - Re-access WordPress -- should redirect to Keycloak login (not auto-SSO)

7. **Validate SSO/SLO for app2 (WhoAmI):**
   - Login as alice via `http://whoami-a.sso.local`
   - If already logged into Keycloak, should SSO directly
   - Verify WhoAmI response shows `X-Authenticated-User` or `X-WEBAUTH-USER` header
   - Logout from WordPress -- WhoAmI should also be logged out (cross-app SLO via backchannel)

8. **Verify no key prefix leakage:**
   - `docker exec shared-redis redis-cli --user openig-app1 --pass <pw> KEYS '*'` -- should only return `app1:*` keys
   - No unprefixed `token_ref:*` or `blacklist:*` keys should exist

**Acceptance criteria:**
- [ ] `docker compose up -d` starts all services without errors
- [ ] All 16 routes loaded in OpenIG logs
- [ ] WordPress SSO login works for alice and bob
- [ ] WhoAmI SSO login works
- [ ] WordPress SLO logout works (backchannel fires, blacklist written with `app1:` prefix)
- [ ] Cross-app SLO: logout from WordPress also logs out WhoAmI
- [ ] Redis keys are prefixed: `app1:token_ref:*`, `app1:blacklist:*`, `app2:token_ref:*`, `app2:blacklist:*`
- [ ] Redis ACL isolation verified (app1 user cannot access app2 keys)
- [ ] No `JWT session is too large` errors in OpenIG logs
- [ ] Old Stack A directory preserved at `/Volumes/OS/claude/openig/sso-lab/stack-a/` for rollback

---

### Step 4: Migrate Stack B + C (Redmine, Jellyfin, Grafana, phpMyAdmin) + Full Validation
**Goal:** Add remaining 4 apps to shared infrastructure, update Keycloak client URLs for port changes, and validate all 6 apps together.

**Sub-tasks:**

1. **Keycloak client URL updates** (runtime, persistent via admin API or admin console):

   **Port changes (9080 -> 80):**
   - `openig-client-b` (Redmine): update `Valid Redirect URIs` from `http://redmine-b.sso.local:9080/*` to `http://redmine-b.sso.local/*`; update `post_logout_redirect_uris`; update `Backchannel Logout URL` from `http://host.docker.internal:9080/openid/app3/backchannel_logout` to `http://host.docker.internal:80/openid/app3/backchannel_logout`
   - `openig-client-b-app4` (Jellyfin): same pattern, `9080` -> `80`; redirect URIs, post_logout_redirect_uris, backchannel URL

   **Port changes (18080 -> 80):**
   - `openig-client-c-app5` (Grafana): update `Valid Redirect URIs` from `http://grafana-c.sso.local:18080/*` to `http://grafana-c.sso.local/*`; update `post_logout_redirect_uris`; update `Backchannel Logout URL` from `http://host.docker.internal:18080/openid/app5/backchannel_logout` to `http://host.docker.internal:80/openid/app5/backchannel_logout`
   - `openig-client-c-app6` (phpMyAdmin): same pattern, `18080` -> `80`

   **Backchannel logout URL consolidation:** All 5 backchannel URLs (app1 already on :80, plus app3/4/5/6) now point to `http://host.docker.internal:80/openid/appN/backchannel_logout`. Since all share the same nginx on port 80, the path-based matching in route JSON handles routing correctly.

2. **Update CANONICAL_ORIGIN env vars** in shared docker-compose.yml:
   - `CANONICAL_ORIGIN_APP3`: `http://redmine-b.sso.local` (was `http://redmine-b.sso.local:9080`)
   - `CANONICAL_ORIGIN_APP4`: `http://jellyfin-b.sso.local` (was `http://jellyfin-b.sso.local:9080`)
   - `CANONICAL_ORIGIN_APP5`: `http://grafana-c.sso.local` (was `http://grafana-c.sso.local:18080`)
   - `CANONICAL_ORIGIN_APP6`: `http://phpmyadmin-c.sso.local` (was `http://phpmyadmin-c.sso.local:18080`)
   - `CANONICAL_ORIGIN_APP1` and `CANONICAL_ORIGIN_APP2` remain unchanged (already port 80)

3. **App-specific environment considerations:**
   - Grafana: `GF_SERVER_ROOT_URL` must change from `http://grafana-c.sso.local:18080` to `http://grafana-c.sso.local` in docker-compose
   - Jellyfin: `JELLYFIN_SERVER_ID` env var (`8a4467ecf1d4422583f472d90cb8c78f`) must be set on both shared OpenIG nodes
   - phpMyAdmin: `config.user.inc.php` mount path must be correct in shared compose (`./openig_home/phpmyadmin/config.user.inc.php:/etc/phpmyadmin/config.user.inc.php:ro`)
   - Redmine: `REDMINE_SECRET_KEY_BASE` must match existing Redmine data volume; if creating fresh volume, any value works; if reusing volume, must match

4. **Stop old Stack B and Stack C:**
   ```bash
   cd /Volumes/OS/claude/openig/sso-lab/stack-b && docker compose down
   cd /Volumes/OS/claude/openig/sso-lab/stack-c && docker compose down
   ```

5. **Restart shared infrastructure** (already running from Step 3, but needs restart for new env vars/routes):
   ```bash
   cd /Volumes/OS/claude/openig/sso-lab/shared
   docker compose up -d
   docker restart shared-openig-1 shared-openig-2
   ```

6. **Validate each app SSO/SLO** (alice + bob where applicable):

   **Redmine (`http://redmine-b.sso.local` -- note: no port now):**
   - Login as alice -- Keycloak SSO -- Vault credential injection -- Redmine dashboard
   - Verify Redis key: `app3:token_ref:*` exists
   - Logout -- SLO -- backchannel fires -- `app3:blacklist:*` written
   - Login as bob -- same flow

   **Jellyfin (`http://jellyfin-b.sso.local`):**
   - Login as alice -- Keycloak SSO -- Jellyfin token injection -- Jellyfin dashboard
   - Verify deviceId derived from SHA-256 of sub (stable across sessions)
   - Logout -- SloHandlerJellyfin fires -- Keycloak end-session -- backchannel fires
   - Verify Redis: `app4:blacklist:*` written

   **Grafana (`http://grafana-c.sso.local`):**
   - Login as alice -- Keycloak SSO -- X-WEBAUTH-USER header injection -- Grafana dashboard
   - Verify auto-provisioned user in Grafana
   - Logout (intercept via `00-grafana-logout.json`) -- SLO -- backchannel fires
   - Verify Redis: `app5:blacklist:*` written

   **phpMyAdmin (`http://phpmyadmin-c.sso.local`):**
   - Login as alice -- Keycloak SSO -- Vault credential injection -- HTTP Basic injection -- phpMyAdmin dashboard
   - Login as bob -- same flow (MariaDB user bob must exist)
   - Logout (401 detection via PhpMyAdminAuthFailureHandler) -- SLO -- backchannel fires
   - Verify Redis: `app6:blacklist:*` written

7. **Cross-app SLO validation:**
   - Login to all 6 apps as alice (single Keycloak SSO session)
   - Logout from WordPress
   - Verify: Keycloak sends backchannel logout to all 5 other clients
   - Check Redis: `app1:blacklist:*` through `app6:blacklist:*` keys all exist (same SID)
   - Re-access any app -- should redirect to Keycloak login

8. **Redis ACL comprehensive isolation test:**
   ```bash
   # For each app user, verify can access own keys, cannot access others
   for N in 1 2 3 4 5 6; do
     # Own key: should succeed
     docker exec shared-redis redis-cli --user openig-app${N} --pass <pw> SET app${N}:test 1
     # Other key: should fail with NOPERM
     OTHER=$((N % 6 + 1))
     docker exec shared-redis redis-cli --user openig-app${N} --pass <pw> SET app${OTHER}:test 1
   done
   ```

9. **Vault per-app AppRole isolation test:**
   ```bash
   # Get app1 token
   APP1_TOKEN=$(docker exec shared-vault vault write -field=token auth/approle/login \
     role_id=$(cat vault/file/openig-app1-role-id) \
     secret_id=$(cat vault/file/openig-app1-secret-id))

   # app1 token can read wp-creds
   docker exec -e VAULT_TOKEN=$APP1_TOKEN shared-vault vault kv get secret/wp-creds/alice
   # Expected: success

   # app1 token CANNOT read redmine-creds
   docker exec -e VAULT_TOKEN=$APP1_TOKEN shared-vault vault kv get secret/redmine-creds/alice@lab.local
   # Expected: permission denied
   ```

**Acceptance criteria:**
- [ ] All 6 apps SSO login works (alice for all, bob for WordPress/Redmine/Jellyfin/phpMyAdmin)
- [ ] All 6 apps SLO logout works (backchannel fires, blacklist written with correct prefix)
- [ ] Cross-app SLO works (logout from any app triggers backchannel for all 5 Keycloak clients)
- [ ] Redis keys properly prefixed per app: `app1:*` through `app6:*` (no unprefixed keys)
- [ ] Redis ACL blocks cross-app access (6x6 matrix verified)
- [ ] Vault per-app AppRoles scoped correctly (cross-path read denied)
- [ ] No `JWT session is too large` errors in OpenIG logs
- [ ] TokenReferenceFilter store/restore works for all 6 apps (check logs for `Stored oauth2 session` and `Restored oauth2 session`)
- [ ] Jellyfin deviceId stable (check `jellyfin_device_id` in logs)
- [ ] phpMyAdmin alice and bob login works
- [ ] Keycloak client redirect URIs all updated to port 80
- [ ] Keycloak backchannel logout URLs all updated to port 80
- [ ] Old Stack B and Stack C directories preserved for rollback

---

### Step 5: Cleanup + Documentation Update
**Goal:** Archive old stack directories, update all documentation to reflect shared infrastructure, and perform final validation after a clean restart cycle.

**Sub-tasks:**

1. **Archive old stacks:**
   - `mkdir -p /Volumes/OS/claude/openig/sso-lab/archive`
   - `mv stack-a archive/stack-a`
   - `mv stack-b archive/stack-b`
   - `mv stack-c archive/stack-c`
   - Delete `.env` files from archived stacks (contain secrets)
   - Keep directory structure as reference for pattern documentation

2. **Update .claude/rules/architecture.md:**
   - Container names table: single row for shared infra (nginx=`shared-nginx`, openig-1=`shared-openig-1`, etc.)
   - URLs table: all apps on port 80
   - Cookie session: single `IG_SSO` for all apps
   - New section: Redis ACL user mapping table (6 users, key prefixes)
   - New section: Vault AppRole mapping table (6 roles, policies, secret paths)
   - Update `clientEndpoint` namespace table (unchanged but now all in single OpenIG)
   - Update SLO mechanism section (single Redis instance)
   - Update Vault section (single instance, 6 AppRoles)
   - Remove per-stack container name tables

3. **Update .claude/rules/restart.md:**
   - Replace 4-section checklist (Keycloak + Stack A + Stack B + Stack C) with single section:
     ```
     cd /Volumes/OS/claude/openig/sso-lab/shared
     docker compose up -d
     docker cp vault/init/vault-bootstrap.sh shared-vault:/tmp/vault-bootstrap.sh
     docker exec shared-vault sh /tmp/vault-bootstrap.sh
     docker restart shared-openig-1 shared-openig-2
     docker logs shared-openig-1 2>&1 | grep 'Loaded the route'
     ```

4. **Update .claude/rules/gotchas.md:**
   - Add: Redis ACL `NOPERM` error when app uses wrong user/key prefix
   - Add: Per-app Vault token cache key must match `appRoleName` route arg
   - Add: All backchannel logout URLs must use port 80 (not old 9080/18080)
   - Remove/update: per-stack gotchas that no longer apply (e.g., "Cookie name phải unique per stack" -- now single cookie)
   - Update: Vault `secret_id_ttl: 72h` gotcha now applies to 6 AppRoles instead of 3

5. **Update CLAUDE.md:**
   - Roadmap: mark "Shared infrastructure consolidation" as done
   - Stack overview: single shared stack replacing 3 independent stacks
   - Update file paths (shared/ instead of stack-a/stack-b/stack-c)

6. **Update docs/deliverables/legacy-app-team-checklist.md:**
   - Infrastructure section: shared Redis ACL + Vault per-app AppRole pattern
   - Gateway deployment: single compose file, single nginx

7. **Update docs/deliverables/standard-gateway-pattern.md:**
   - Redis section: ACL pattern with per-app users
   - Vault section: per-app AppRoles with scoped policies
   - Deployment section: shared infrastructure model

8. **Update /etc/hosts documentation:**
   - Remove `openiga.sso.local`, `openigb.sso.local`, `openig-c.sso.local` references
   - Add `openig.sso.local` as unified management endpoint
   - Note: actual /etc/hosts entries don't need changing (all resolve to 127.0.0.1)

9. **Final clean restart validation:**
   ```bash
   cd /Volumes/OS/claude/openig/sso-lab/shared
   docker compose down
   # Remove transient data (Redis cleared, Vault data preserved)
   docker compose up -d
   # Re-bootstrap Vault (unseal + refresh secret_ids)
   docker cp vault/init/vault-bootstrap.sh shared-vault:/tmp/vault-bootstrap.sh
   docker exec shared-vault sh /tmp/vault-bootstrap.sh
   docker restart shared-openig-1 shared-openig-2
   # Full validation: all 6 apps SSO/SLO
   ```

10. **Git commit:**
    - Single atomic commit with: shared/ directory, archived stacks, all documentation updates
    - Commit message: `feat: consolidate 3 stacks into shared infrastructure with Redis ACL + per-app Vault`

**Acceptance criteria:**
- [ ] Old stack directories archived under `archive/`
- [ ] No `.env` secret files in archived directories
- [ ] architecture.md accurately reflects shared infra (container names, URLs, Redis ACL table, Vault AppRole table)
- [ ] restart.md has single simplified checklist
- [ ] gotchas.md updated with new Redis ACL / Vault per-app gotchas
- [ ] CLAUDE.md roadmap updated
- [ ] legacy-app-team-checklist.md infrastructure section updated
- [ ] standard-gateway-pattern.md Redis/Vault sections updated
- [ ] Single `docker compose up -d && bootstrap` starts entire lab from cold
- [ ] Full test suite passes after clean restart cycle (all 6 apps SSO/SLO)
- [ ] Redis AOF persistence survives restart (blacklist keys preserved)
- [ ] Vault unseal + AppRole refresh works after restart

---

## Success Criteria (Plan-Level)

1. All 6 apps SSO/SLO works from a single `docker compose up -d` in `shared/`
2. Redis ACL enforces per-app key isolation (verified by attempted cross-access, 6x6 matrix)
3. Vault per-app AppRoles enforce secret path isolation (verified by attempted cross-read)
4. Container count reduced from 18 to 11
5. Single JwtSession cookie (`IG_SSO`) with per-app tokenRefKey isolation (`token_ref_id_app1..app6`)
6. All apps accessible on port 80 via hostname-based routing
7. Zero `JWT session is too large` errors
8. Backchannel SLO works cross-app (logout any app -> all 5 other apps receive backchannel)
9. Groovy scripts maintain backward compatibility (still work with old per-stack args if needed)
10. Single restart checklist replaces 4-section checklist

## Open Questions

See `.omc/plans/open-questions.md` for tracked items.

Key items:
1. **Redis ACL password injection:** Should `acl.conf` use `__PLACEHOLDER__` + sed at container startup, or should passwords be set via `CONFIG SET` after Redis starts? Sed approach is consistent with OpenIG config.json pattern. CONFIG SET approach avoids file templating but requires a bootstrap script.
2. **Data volume strategy:** Start fresh (clean volumes) vs. migrate existing data. Fresh is simpler but loses Redmine data, WordPress posts, Grafana dashboards, phpMyAdmin MariaDB state. Recommendation: fresh for lab (data is synthetic); document volume mapping for production.
3. **Keycloak backchannel retry on port change:** During transition (old stack down, shared not yet up), Keycloak may cache old backchannel URLs. Verify Keycloak picks up new URLs immediately after admin API update.
4. **SloHandler Redis cleanup (BUG-SSO2-AFTER-SLO dependency):** After the bug fix, SloHandler will need Redis connection args (`redisHost`, `redisUser`, `redisPasswordEnvVar`, `redisKeyPrefix`) for the pre-session-clear token_ref cleanup. This is noted in Step 2 but the actual SloHandler modification happens as part of the bug fix, not this plan. Ensure the bug fix uses the same arg naming convention.

## Estimated Complexity

**HIGH** -- Cross-cutting changes to Groovy scripts (Redis AUTH protocol change, key prefixing, per-app Vault cache), Vault bootstrap redesign (1 -> 6 AppRoles), nginx consolidation (3 -> 1), docker-compose merge (3 -> 1), Keycloak client URL updates (4 clients), full regression test across 6 apps. Estimated 5-8 Codex sessions across all steps.

## Risk Mitigation

- **Rollback:** Old stack directories preserved in `archive/` until final validation passes. Can `docker compose down` shared infra and `cd archive/stack-X && docker compose up` any old stack.
- **Incremental validation:** Each step validates before proceeding. Step 3 validates Stack A alone before adding B+C in Step 4.
- **Redis data loss:** New Redis instance starts empty. Old Redis instances preserved in old stacks (separate Docker volumes). No migration needed -- session data is transient (token refs + blacklist keys both have TTLs).
- **Vault data loss:** New Vault bootstraps fresh with all secret seeds in the bootstrap script. Old Vault instances preserved in old stacks. Secret seeds are deterministic and documented.
- **Keycloak changes:** All URL updates via admin API are reversible. Take realm export as backup before making changes. Keycloak DB is persistent across restarts.
- **Port change impact:** Stack B and C apps change from 9080/18080 to 80. This affects: browser bookmarks, Keycloak redirect URIs, CANONICAL_ORIGIN env vars, Grafana GF_SERVER_ROOT_URL. All are configuration-level changes, no code changes needed.
```
