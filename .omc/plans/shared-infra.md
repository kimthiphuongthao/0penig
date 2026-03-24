# Shared Infrastructure Consolidation Plan

> Status update 2026-03-24: Shared runtime is active on branch `feat/shared-infra`. `shared-openig-1/2` serve the shared mount at `shared/openig_home`, `cd shared && docker compose config` validates, and `stack-c-openig-c1-1` / `stack-c-openig-c2-1` were confirmed orphaned and stopped. The blocking SSO-after-SLO bug is now fixed in shared infra by `5fb549d`; full Step 4/5 closeout still needs the remaining Redis ACL CLI checks, Vault isolation checks, packaging, and final documentation migration.

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

- [x] **BUG-SSO2-AFTER-SLO fixed in shared infra.** Confirmed root cause: `TokenReferenceFilter.groovy` `.then()` mishandled mixed state after SLO, where pending OAuth2 authorization state coexisted with stale real-token entries under different URL-format keys; the next SSO callback then failed with `no authorization in progress` and a 500. Current fix: `hasPendingState` cleanup in `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy` (`5fb549d`), verified after restart `2026-03-24T02:29:40Z` with clean post-test logs on `shared-openig-2` (no `invalid_token`, `no authorization in progress`, or `Missing Redis`).

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

**Status (2026-03-24):** MOSTLY DONE. The shared directory, merged nginx/OpenIG/Redis/Vault layout, and compose wiring are present and runnable. The remaining unchecked acceptance item is secret hygiene in committed bootstrap material.

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

1. **docker-compose.yml** — Single compose file with all services:
   - `nginx`: port 80:80, single upstream pool
   - `openig-1`, `openig-2`: pinned `openidentityplatform/openig:6.0.1`, shared volumes, all env vars for 6 apps (OIDC secrets, canonical origins, Redis user/password per app, Vault per-app role files)
   - `redis`: Redis 7 Alpine with ACL config mount (`--aclfile /etc/redis/acl.conf`)
   - `vault`: single Vault 1.15 instance
   - `wordpress`, `whoami`, `redmine`, `jellyfin`, `grafana`, `phpmyadmin`: all on backend network
   - `mysql-a`, `mysql-b`, `mariadb-c`: all app databases
   - Networks: `frontend` (nginx only) + `backend` (everything else)

2. **redis/acl.conf** — Redis ACL configuration:
   ```
   user default off
   user openig-app1 on ><password> ~app1:* +@all
   user openig-app2 on ><password> ~app2:* +@all
   user openig-app3 on ><password> ~app3:* +@all
   user openig-app4 on ><password> ~app4:* +@all
   user openig-app5 on ><password> ~app5:* +@all
   user openig-app6 on ><password> ~app6:* +@all
   ```
   Note: passwords injected via `docker-entrypoint.sh` sed or Redis `CONFIG SET` at bootstrap. Key prefixes must align with Groovy script key patterns (see Step 2).

3. **vault/init/vault-bootstrap.sh** — Consolidated bootstrap:
   - 6 AppRoles: `openig-app1` through `openig-app6`
   - 6 policies, each scoped:
     - `openig-app1-policy`: `path "secret/data/wp-creds/*" { capabilities = ["read"] }`
     - `openig-app3-policy`: `path "secret/data/redmine-creds/*" { capabilities = ["read"] }`
     - `openig-app4-policy`: `path "secret/data/jellyfin-creds/*" { capabilities = ["read"] }`
     - `openig-app6-policy`: `path "secret/data/phpmyadmin/*" { capabilities = ["read"] }`
     - `openig-app2-policy`, `openig-app5-policy`: minimal (WhoAmI/Grafana don't use Vault creds)
   - All secret seeds (wp-creds, redmine-creds, jellyfin-creds, phpmyadmin) in one bootstrap
   - Per-app role_id/secret_id files written to `/vault/file/openig-app{1..6}-{role-id,secret-id}`
   - Admin token management (same pattern as current, with expanded admin policy for 6 roles)
   - `secret_id_ttl: 72h` on all AppRoles (same as current)

4. **nginx/nginx.conf** — Merged from 3 stacks:
   - Single upstream: `upstream openig_pool { ip_hash; server openig-1:8080; server openig-2:8080; }`
   - 7 server blocks: `wp-a.sso.local`, `whoami-a.sso.local`, `redmine-b.sso.local`, `jellyfin-b.sso.local`, `grafana-c.sso.local`, `phpmyadmin-c.sso.local`, `openig.sso.local` (management)
   - All on `listen 80` (hostname-based routing)
   - Security headers (X-Frame-Options, X-Content-Type-Options, Referrer-Policy)
   - Backchannel logout locations preserved per-app
   - Cookie flags: `proxy_cookie_flags IG_SSO samesite=lax` (single cookie name)
   - Keep existing proxy buffer, timeout, WebSocket upgrade settings

5. **openig_home/config/config.json** — Merged JwtSession config:
   - Heap object name: `"Session"` (required for OpenIG auto-wire)
   - Cookie name: `IG_SSO` (single cookie for all apps)
   - `cookieDomain: ".sso.local"`
   - `sharedSecret`: from `__JWT_SHARED_SECRET__` placeholder (sed in entrypoint)
   - PKCS12 keystore: `__KEYSTORE_PASSWORD__` placeholder
   - All OIDC client secrets as `${env['OIDC_CLIENT_SECRET']}` etc.

6. **.env.example** — Merged secrets template:
   - `JWT_SHARED_SECRET`, `KEYSTORE_PASSWORD`
   - `OIDC_CLIENT_SECRET` (openig-client), `OIDC_CLIENT_SECRET_APP4`, `OIDC_CLIENT_SECRET_APP5`, `OIDC_CLIENT_SECRET_APP6`
   - `REDIS_PASSWORD_APP1` through `REDIS_PASSWORD_APP6`
   - `VAULT_*` (not needed as env — bootstrap handles internally)
   - `MYSQL_ROOT_PASSWORD_A`, `WORDPRESS_DB_PASSWORD`
   - `MYSQL_ROOT_PASSWORD_B`, `REDMINE_DB_PASSWORD`, `REDMINE_SECRET_KEY_BASE`
   - `MYSQL_ROOT_PASSWORD_C`, `MYSQL_PASSWORD_C`

**Acceptance criteria:**
- [x] `shared/` directory created with all files above
- [x] `cd shared && docker compose config` validates without error
- [x] Redis ACL config has 6 users with distinct key prefixes
- [x] Vault bootstrap creates 6 AppRoles with scoped policies
- [x] nginx.conf has all 6 server blocks on port 80
- [x] config.json has single JwtSession named `"Session"` with cookie `IG_SSO`
- [ ] No hardcoded secrets in committed files (all via `.env` or placeholders)

---

### Step 2: Groovy Script Adaptation for Redis ACL + Per-App Vault
**Goal:** Modify the 4 Groovy scripts that connect to Redis or Vault so they work with ACL authentication and per-app AppRoles. Update all 16 route files with new args.

**Status (2026-03-24):** DONE. Shared Groovy templates and shared route files carry the Redis ACL / per-app Vault adaptation and load successfully in the active shared OpenIG runtime.

**Sub-tasks:**

1. **Redis ACL AUTH in Groovy scripts** — 3 files to modify:

   **TokenReferenceFilter.groovy** (lines 56-62, `buildAuthCommand`):
   - Current: `AUTH <password>` → RESP `*2\r\n$4\r\nAUTH\r\n$<pwLen>\r\n<pw>\r\n`
   - Target: `AUTH <username> <password>` → RESP `*3\r\n$4\r\nAUTH\r\n$<uLen>\r\n<user>\r\n$<pLen>\r\n<pw>\r\n`
   - New route arg: `redisUser` (read via `binding.hasVariable('redisUser')`)
   - Password: continue using `REDIS_PASSWORD` env var, but support per-app override via `redisPasswordEnvVar` arg (e.g., `REDIS_PASSWORD_APP1`)
   - Key prefix: change `token_ref:${tokenRefId}` to `${redisKeyPrefix}:token_ref:${tokenRefId}` where `redisKeyPrefix` comes from route args (e.g., `app1`)

   **BackchannelLogoutHandler.groovy** (lines 418-430, inline AUTH):
   - Same AUTH pattern change
   - New route args: `redisUser`, `redisPasswordEnvVar`
   - Key prefix: change `blacklist:${sid}` to `${redisKeyPrefix}:blacklist:${sid}`

   **SessionBlacklistFilter.groovy** (lines 110-122, inline AUTH):
   - Same AUTH pattern change
   - New route args: `redisUser`, `redisPasswordEnvVar`
   - Key prefix: change `blacklist:${sid}` to `${redisKeyPrefix}:blacklist:${sid}`

   **CRITICAL:** BackchannelLogoutHandler writes `blacklist:${sid}` and SessionBlacklistFilter reads it. Both MUST use the same key prefix for the same app. Since backchannel logout for app3 writes a key that SessionBlacklistFilter for app3 reads, they share the same Redis user and key prefix. This is correct by design.

   **CRITICAL:** Redis key prefix must be consistent:
   | App | Redis user | Key prefix | Token ref key pattern | Blacklist key pattern |
   |-----|-----------|------------|----------------------|----------------------|
   | WordPress (app1) | `openig-app1` | `app1` | `app1:token_ref:<uuid>` | `app1:blacklist:<sid>` |
   | WhoAmI (app2) | `openig-app2` | `app2` | `app2:token_ref:<uuid>` | `app2:blacklist:<sid>` |
   | Redmine (app3) | `openig-app3` | `app3` | `app3:token_ref:<uuid>` | `app3:blacklist:<sid>` |
   | Jellyfin (app4) | `openig-app4` | `app4` | `app4:token_ref:<uuid>` | `app4:blacklist:<sid>` |
   | Grafana (app5) | `openig-app5` | `app5` | `app5:token_ref:<uuid>` | `app5:blacklist:<sid>` |
   | phpMyAdmin (app6) | `openig-app6` | `app6` | `app6:token_ref:<uuid>` | `app6:blacklist:<sid>` |

2. **Per-app Vault AppRole in VaultCredentialFilter.groovy:**
   - Current: `globals.compute('vault_token') { ... }` — single cache entry for all apps in a stack
   - Target: `globals.compute("vault_token_${configuredAppRoleName}") { ... }` — per-app cache entry
   - New route args: `appRoleName` (e.g., `openig-app1`), `vaultRoleIdFile`, `vaultSecretIdFile`
   - AppRole login path changes from hardcoded `auth/approle/login` to `auth/approle/role/${configuredAppRoleName}/login`... wait, AppRole login is always `auth/approle/login` with `role_id`+`secret_id`. The AppRole NAME determines which role_id/secret_id to use. So the login endpoint stays the same, but the role_id/secret_id files are per-app.
   - Current code reads `VAULT_ROLE_ID_FILE` and `VAULT_SECRET_ID_FILE` env vars. For per-app, route args provide file paths: `vaultRoleIdFile` and `vaultSecretIdFile`.
   - Cache key: `globals.compute("vault_token_${configuredAppRoleName}")` prevents one app's token refresh from invalidating another's cache

3. **Route files update** — All 16 routes need new args:

   **App routes (6 files):** Add `redisUser`, `redisPasswordEnvVar`, `redisKeyPrefix` args to TokenReferenceFilter and SessionBlacklistFilter config. App routes with VaultCredentialFilter (4 files: wordpress, redmine, jellyfin, phpmyadmin) also add `appRoleName`, `vaultRoleIdFile`, `vaultSecretIdFile`.

   **Backchannel routes (6 files):** Add `redisUser`, `redisPasswordEnvVar`, `redisKeyPrefix` args to BackchannelLogoutHandler config.

   **Logout routes (4-6 files):** If they reference Redis (SloHandler doesn't directly use Redis in current code), no Redis args needed. SloHandler only does `session.clear()` + Keycloak redirect.

   Example route args addition for `01-wordpress.json`:
   ```json
   "args": {
     "redisHost": "redis",
     "redisUser": "openig-app1",
     "redisPasswordEnvVar": "REDIS_PASSWORD_APP1",
     "redisKeyPrefix": "app1",
     "tokenRefKey": "token_ref_id_app1",
     "clientEndpoint": "/openid/app1",
     "appRoleName": "openig-app1",
     "vaultRoleIdFile": "/vault/file/openig-app1-role-id",
     "vaultSecretIdFile": "/vault/file/openig-app1-secret-id"
   }
   ```

**Acceptance criteria:**
- [x] TokenReferenceFilter, BackchannelLogoutHandler, SessionBlacklistFilter all support `AUTH <username> <password>` via `redisUser` arg
- [x] All 3 scripts prefix Redis keys with `redisKeyPrefix` arg
- [x] VaultCredentialFilter uses per-app cache key `vault_token_${appRoleName}`
- [x] VaultCredentialFilter reads per-app role_id/secret_id file paths from args
- [x] All 16 route files have correct new args for their respective app
- [x] No Groovy compilation errors (verify via container startup log)

---

### Step 3: Migrate Stack A (WordPress + WhoAmI) + Validate
**Goal:** Bring Stack A apps into the shared infrastructure, update Keycloak, and validate SSO/SLO.

**Status (2026-03-24):** PARTIAL. Shared services are up and the shared route set loads, but this audit did not replay the full Stack A validation matrix, so only the infrastructure-level confirmations are closed here.

**Sub-tasks:**

1. **Keycloak client URL updates** (runtime change via admin API, persistent):
   - `openig-client`: redirect URIs stay the same (already on port 80)
   - Backchannel logout URLs: already `http://host.docker.internal:80/openid/app1/backchannel_logout` — no change needed for Stack A
   - Verify: `post_logout_redirect_uris` still correct

2. **Start shared infrastructure:**
   ```bash
   cd shared
   docker compose up -d
   docker cp vault/init/vault-bootstrap.sh shared-vault:/tmp/vault-bootstrap.sh
   docker exec shared-vault sh /tmp/vault-bootstrap.sh
   # Copy per-app role_id/secret_id files to shared mount
   docker restart shared-openig-1 shared-openig-2
   ```

3. **Verify Redis ACL isolation:**
   ```bash
   # Connect as openig-app1, try to read app2 key → should fail
   docker exec shared-redis redis-cli --user openig-app1 --pass <pw> SET app2:test 1
   # Expected: (error) NOPERM this user has no permissions to run the 'set' command on key 'app2:test'
   ```

4. **Validate SSO/SLO for app1 (WordPress):**
   - Login as alice via `http://wp-a.sso.local` → redirected to Keycloak → SSO → WordPress dashboard
   - Verify `IG_SSO` cookie present (not `IG_SSO_B` or `IG_SSO_C`)
   - Logout → Keycloak end-session → backchannel logout fires → Redis blacklist written
   - Re-access WordPress → redirected to Keycloak login

5. **Validate SSO/SLO for app2 (WhoAmI):**
   - Login as alice via `http://whoami-a.sso.local` → SSO (if already logged into Keycloak)
   - Verify WhoAmI headers show authenticated user
   - Logout → SLO across both apps

6. **Verify Redis key isolation:**
   - After login, check Redis keys: `app1:token_ref:*` exists, no unprefixed `token_ref:*`
   - After SLO backchannel, check: `app1:blacklist:*` exists

**Acceptance criteria:**
- [x] `docker compose up -d` starts all services without errors
- [x] All routes loaded (grep `Loaded the route` in OpenIG logs)
- [ ] WordPress SSO login works (alice + bob)
- [ ] WhoAmI SSO login works
- [ ] WordPress SLO logout works (backchannel fires, blacklist written)
- [ ] Cross-app SLO works (logout from WordPress logs out WhoAmI)
- [ ] Redis keys are prefixed (`app1:*`, `app2:*`)
- [ ] Redis ACL blocks cross-app key access
- [ ] Old Stack A still functional on port 80 if needed for rollback (stop shared nginx first)

---

### Step 4: Migrate Stack B + C (Redmine, Jellyfin, Grafana, phpMyAdmin) + Full Validation
**Goal:** Add remaining 4 apps to shared infrastructure and validate all 6 apps together.

**Status (2026-03-24):** PARTIAL. The shared runtime is serving Stack B/C, the `TokenReferenceFilter` mixed-state regression is fixed by `5fb549d`, and post-fix user SSO/SLO testing on `shared-openig-2` stayed free of `invalid_token`, `no authorization in progress`, and `Missing Redis`. The remaining CLI isolation checks and full acceptance-matrix replay are still open.

**Sub-tasks:**

1. **Keycloak client URL updates** (runtime, persistent via admin API):
   - `openig-client-b`: update redirect URIs from `*:9080*` to `*:80*` (port change)
   - `openig-client-b-app4`: same port change
   - `openig-client-c-app5`: update redirect URIs from `*:18080*` to `*:80*`
   - `openig-client-c-app6`: same port change
   - Backchannel logout URLs: update port from 9080/18080 to 80
   - `post_logout_redirect_uris`: update ports

2. **Update CANONICAL_ORIGIN env vars** in shared docker-compose:
   - `CANONICAL_ORIGIN_APP3`: `http://redmine-b.sso.local` (was `:9080`)
   - `CANONICAL_ORIGIN_APP4`: `http://jellyfin-b.sso.local` (was `:9080`)
   - `CANONICAL_ORIGIN_APP5`: `http://grafana-c.sso.local` (was `:18080`)
   - `CANONICAL_ORIGIN_APP6`: `http://phpmyadmin-c.sso.local` (was `:18080`)

3. **Start/restart shared infrastructure** with all apps.

4. **Validate each app SSO/SLO** (alice + bob where applicable):
   - Redmine (`http://redmine-b.sso.local`): SSO login, credential injection via Vault, SLO
   - Jellyfin (`http://jellyfin-b.sso.local`): SSO login, token injection, SLO (deviceId from sub hash)
   - Grafana (`http://grafana-c.sso.local`): SSO login, X-WEBAUTH-USER header injection, SLO
   - phpMyAdmin (`http://phpmyadmin-c.sso.local`): SSO login, HTTP Basic injection via Vault, SLO

5. **Cross-app SLO validation:**
   - Login to all 6 apps → logout from one → verify backchannel logout fires for all relevant clients
   - Verify `appN:blacklist:*` keys appear in Redis for each app's SID

6. **Redis ACL comprehensive test:**
   - Verify each app user can only access its own prefix
   - Verify no unprefixed keys exist

7. **Vault per-app isolation test:**
   - Verify each app's AppRole can only read its designated secret path
   - `vault read secret/data/wp-creds/alice` with app3 token → access denied

**Acceptance criteria:**
- [ ] All 6 apps SSO login works (alice for all, bob where applicable)
- [ ] All 6 apps SLO logout works (backchannel fires, blacklist written)
- [ ] Cross-app SLO works (logout from any app triggers backchannel for all)
- [ ] Redis keys properly prefixed per app (no cross-contamination)
- [ ] Redis ACL blocks cross-app access (verified via CLI)
- [ ] Vault per-app AppRoles scoped correctly (verified via CLI)
- [ ] No `JWT session is too large` errors in logs
- [x] TokenReferenceFilter store/restore works for all 6 apps
- [ ] Jellyfin deviceId stable across sessions (SHA-256 from sub)
- [ ] phpMyAdmin bob login works (MariaDB user + Vault secret aligned)

---

### Step 5: Cleanup + Documentation Update
**Goal:** Archive old stack directories, update all documentation to reflect shared infrastructure.

**Sub-tasks:**

1. **Archive old stacks:**
   - Move `stack-a/`, `stack-b/`, `stack-c/` to `archive/` (or delete if confirmed)
   - Keep old docker-compose files as reference
   - Remove old `.env` files (secrets)

2. **Update documentation:**
   - `CLAUDE.md`: roadmap (mark shared infra consolidation as done), update stack overview
   - `.claude/rules/architecture.md`: new container names, single Redis/Vault/nginx, per-app AppRole table, Redis ACL table, updated URLs (port changes)
   - `.claude/rules/conventions.md`: update if any conventions change
   - `.claude/rules/gotchas.md`: add Redis ACL gotchas, remove per-stack gotchas that no longer apply
   - `.claude/rules/restart.md`: single restart checklist (was 4 sections, now 1)
   - `docs/deliverables/legacy-app-team-checklist.md`: update infrastructure section
   - `docs/deliverables/standard-gateway-pattern.md`: update Redis/Vault sections

3. **Update /etc/hosts:**
   - No changes needed (all hostnames already point to 127.0.0.1)
   - But document: `openig.sso.local` replaces `openiga/openigb/openig-c.sso.local`

4. **Final cross-stack validation:**
   - Full test suite run (all TC-* test cases)
   - Verify single `docker compose up -d` brings up everything
   - Verify `docker compose down` + `docker compose up -d` cycle works (Redis AOF persistence, Vault unseal)

**Acceptance criteria:**
- [ ] Old stack directories archived or removed
- [ ] All documentation updated to reflect shared infra
- [ ] Restart checklist simplified to single section
- [ ] Architecture.md has accurate container names, URLs, AppRole table
- [ ] Single `docker compose up -d && bootstrap` starts entire lab
- [ ] Full test suite passes after clean restart cycle

---

## Success Criteria (Plan-Level)

1. All 6 apps SSO/SLO works from a single `docker compose up -d` in `shared/`
2. Redis ACL enforces per-app key isolation (verified by attempted cross-access)
3. Vault per-app AppRoles enforce secret path isolation (verified by attempted cross-read)
4. Container count reduced from 18 to 11
5. Single JwtSession cookie (`IG_SSO`) with per-app tokenRefKey isolation (`token_ref_id_app1..app6`)
6. All apps accessible on port 80 via hostname-based routing
7. Zero `JWT session is too large` errors
8. Backchannel SLO works cross-app (logout any app -> all apps receive backchannel)

## Open Questions

See `.omc/plans/open-questions.md` for tracked items.

## Estimated Complexity

**HIGH** — Cross-cutting changes to Groovy scripts (Redis AUTH protocol), Vault bootstrap redesign, nginx consolidation, docker-compose merge, Keycloak client updates, full regression test across 6 apps. Estimated 5-8 Codex sessions across all steps.

## Risk Mitigation

- **Rollback:** Old stack directories preserved until Step 5. Can `docker compose down` shared and `docker compose up` old stacks.
- **Incremental validation:** Each step validates before proceeding. Step 3 validates Stack A alone before adding B+C.
- **Redis data loss:** New Redis instance starts empty. Old Redis instances preserved in old stacks. No migration needed (session data is transient).
- **Vault data loss:** New Vault bootstraps fresh. Old Vault instances preserved. Secret seeds are in bootstrap script (deterministic).
- **Keycloak changes:** All via admin API (reversible). Realm export available as backup.
