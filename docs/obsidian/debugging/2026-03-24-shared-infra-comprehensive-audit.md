---
title: Shared Infra Comprehensive Audit
tags:
  - audit
  - debugging
  - shared-infra
  - openig
  - vault
  - redis
date: 2026-03-24
status: complete
---

# Shared Infra Comprehensive Audit

Audit scope covered the consolidated `shared/` runtime across [[OpenIG]], [[Vault]], Redis ACL, nginx, route wiring, and plan/gotcha compliance. Review method was static file audit plus `docker compose config -q`; browser SSO/SLO flows were not re-executed in this session.

## Summary

- Overall verdict: `NEEDS_FIXES`
- Critical findings: `1`
- High findings: `3`
- Medium findings: `3`
- Low findings: `2`

> [!danger] Critical
> `AUD-001` Tracked bootstrap file `shared/vault/init/vault-bootstrap.sh` contains concrete application passwords for WordPress, Redmine, Jellyfin, and phpMyAdmin. This is the main blocking security issue in the current shared-infra implementation.

> [!warning] High
> `AUD-002` `shared/openig_home/config/routes/` has no `00-backchannel-logout-app2.json`, so app2 ([[WhoAmI]]) does not receive shared-infra backchannel revocation.
>
> `AUD-003` `BackchannelLogoutHandler.groovy` caches failed JWKS fetches for the full cache TTL, so one transient Keycloak/JWKS outage can suppress backchannel logout verification for up to one hour.
>
> `AUD-004` `shared/docker-compose.yml` still defines known secret defaults via `${VAR:-...}` fallbacks, including a concrete MariaDB password default.

> [!warning] Medium
> `AUD-005` `TokenReferenceFilter.groovy` does not fail closed in the Redis offload path inside `.then()`; it logs and continues.
>
> `AUD-006` `shared/nginx/nginx.conf` still forwards `X-Forwarded-Proto` even though the shared-infra rule says to defer that header until TLS is introduced.
>
> `AUD-007` Healthchecks are incomplete in `shared/docker-compose.yml`; only OpenIG and the database containers have them.

> [!tip] Low
> `AUD-008` Backchannel route TTL args point to `REDIS_BLACKLIST_TTL`, but compose only defines `REDIS_BLACKLIST_TTL_APP1..6`.
>
> `AUD-009` Several shared Groovy scripts still keep legacy fallback hosts/ports such as `openiga`, `openigb`, `:9080`, and `:18080`.

## Findings

### AUD-001

- Severity: `CRITICAL`
- File: `shared/vault/init/vault-bootstrap.sh`
- Issue: Concrete seeded passwords are tracked in the repository.
- Evidence: lines 8-18 define `WP_ALICE_PASSWORD`, `REDMINE_ALICE_PASSWORD`, `PHPMYADMIN_ALICE_PASSWORD`, `JELLYFIN_ALICE_PASSWORD`, and corresponding `bob` values.
- Recommended fix: Remove tracked credential literals. Generate credentials at bootstrap time or source them from untracked env/input, then persist only in [[Vault]].

### AUD-002

- Severity: `HIGH`
- File: `shared/openig_home/config/routes/`
- Issue: There is no `00-backchannel-logout-app2.json`, so app2 has login/session filters but no backchannel blacklist writer.
- Evidence: route directory contains `00-backchannel-logout-app1.json`, `app3`, `app4`, `app5`, `app6` only; `02-app2.json` still wires `SessionBlacklistFilter` with `redisKeyPrefix: "app2"`.
- Recommended fix: Add `00-backchannel-logout-app2.json` using `BackchannelLogoutHandler.groovy` with audience `openig-client`, `redisUser: openig-app2`, `redisPasswordEnvVar: REDIS_PASSWORD_APP2`, and `redisKeyPrefix: app2`.

### AUD-003

- Severity: `HIGH`
- File: `shared/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- Issue: Failed JWKS fetches are cached as `[keys: null, cachedAt: now]`, so subsequent requests reuse the null cache entry until `JWKS_CACHE_TTL_SECONDS` expires.
- Evidence: lines 138-145 return `existing` whenever cache age is below TTL, and line 145 stores `newKeys` even when `fetchJwksKeys(...)` returned `null`.
- Recommended fix: Only cache successful JWKS fetches, or cache failures for a very short backoff window and force immediate retry on the next request.

### AUD-004

- Severity: `HIGH`
- File: `shared/docker-compose.yml`
- Issue: Multiple secrets still have inline defaults, including concrete fallback credentials.
- Evidence: lines 7-25 use `changeme_*` fallbacks for OIDC/JWT/Redis secrets; lines 188, 204, 232-233, 247-250, and 309-312 define default database/application secrets, including `MYSQL_PASSWORD_C:-AlicePass123`.
- Recommended fix: Remove secret fallbacks and fail startup when required env vars are unset.

### AUD-005

- Severity: `MEDIUM`
- File: `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- Issue: Redis offload errors inside the response `.then()` path are logged but not surfaced as a failure.
- Evidence: lines 336-357 call `setInRedis(...)` and then swallow all exceptions with `logger.error(...)`, after which line 359 returns the original response.
- Recommended fix: Convert the offload failure into a 500/502 response or implement an explicit, audited fallback mode instead of silent continuation.

### AUD-006

- Severity: `MEDIUM`
- File: `shared/nginx/nginx.conf`
- Issue: `X-Forwarded-Proto` is still forwarded in every server block and the management block.
- Evidence: header assignment appears throughout the file, including lines 32, 50, 72, 90, 126, 144, 168, 182, 198, 222, 240, 262, 280, 303, and 317.
- Recommended fix: Remove `proxy_set_header X-Forwarded-Proto $scheme;` until TLS termination semantics are finalized.

### AUD-007

- Severity: `MEDIUM`
- File: `shared/docker-compose.yml`
- Issue: Healthchecks are missing for nginx, Redis, Vault, and most app containers.
- Evidence: `shared-nginx` (lines 91-107), `shared-redis` (143-160), `shared-vault` (162-178), `shared-wordpress` (180-198), `shared-whoami` (218-223), `shared-redmine` (225-241), `shared-jellyfin` (263-272), `shared-grafana` (274-288), and `shared-phpmyadmin` (290-303) have no `healthcheck`.
- Recommended fix: Add service-appropriate healthchecks or document deliberate exclusions and their operational impact.

### AUD-008

- Severity: `LOW`
- File: `shared/openig_home/config/routes/00-backchannel-logout-app1.json`
- Issue: Backchannel routes reference `REDIS_BLACKLIST_TTL`, but compose defines only per-app `REDIS_BLACKLIST_TTL_APP1..6`.
- Evidence: route line 16 uses `${env['REDIS_BLACKLIST_TTL'] ...}`, while `shared/docker-compose.yml` lines 51-56 define only `REDIS_BLACKLIST_TTL_APP1` through `APP6`.
- Recommended fix: Either declare one shared `REDIS_BLACKLIST_TTL` env var or wire each route to its per-app TTL env.

### AUD-009

- Severity: `LOW`
- File: `shared/openig_home/scripts/groovy/`
- Issue: Several shared Groovy scripts still keep legacy fallback hosts/ports from the pre-consolidation stacks.
- Evidence: `SloHandler.groovy:85`, `SessionBlacklistFilter.groovy:93`, `RedmineCredentialInjector.groovy:11`, `SloHandlerJellyfin.groovy:126`, `SloHandlerJellyfin.groovy:128`, `PhpMyAdminAuthFailureHandler.groovy:9`, `PhpMyAdminAuthFailureHandler.groovy:126`, `JellyfinResponseRewriter.groovy:30`.
- Recommended fix: Remove legacy fallbacks and require shared-infra-specific envs/args.

## Plan Compliance

| Acceptance criterion | Status | Notes |
|---|---|---|
| Step 1: `shared/` directory created with all files above | PASS | Directory, routes, scripts, nginx, Redis, and Vault layout are present. |
| Step 1: `cd shared && docker compose config` validates without error | PASS | Re-run in this audit passed. |
| Step 1: Redis ACL config has 6 users with distinct key prefixes | PASS | `shared/redis/acl.conf` defines `openig-app1..6` with `~appN:*`. |
| Step 1: Vault bootstrap creates 6 AppRoles with scoped policies | PASS | `vault-bootstrap.sh` writes `openig-app1..6` policies and roles. |
| Step 1: nginx.conf has all 6 server blocks on port 80 | PASS | All six app hosts plus `openig.sso.local` are defined on port 80. |
| Step 1: `config.json` keeps fallback heap while routes override with `SessionApp1..6` / `IG_SSO_APP1..APP6` | PASS | Global fallback exists; app routes explicitly declare route-local sessions. |
| Step 1: No hardcoded secrets in committed files | FAIL | `vault-bootstrap.sh` and `docker-compose.yml` still contain tracked secret material/defaults. |
| Step 2: TokenReferenceFilter, BackchannelLogoutHandler, SessionBlacklistFilter support `AUTH <username> <password>` | PASS | All three scripts build ACL-style RESP `AUTH` commands. |
| Step 2: All 3 scripts prefix Redis keys with `redisKeyPrefix` | PASS | Token refs and blacklist keys are prefixed consistently. |
| Step 2: VaultCredentialFilter uses per-app cache key `vault_token_${appRoleName}` | PASS | Implemented with `globals.compute(vaultTokenCacheKey)`. |
| Step 2: VaultCredentialFilter reads per-app role_id/secret_id file paths from args | PASS | Implemented via `vaultRoleIdFile` and `vaultSecretIdFile`. |
| Step 2: All 16 route files have correct new args for their respective app | PASS | Present route set uses per-app Redis/Vault args; separate app2 backchannel gap is a Step 3 issue. |
| Step 2: No Groovy compilation errors | PARTIAL | Static review found no syntax breakage, but compile/runtime logs were not rechecked in this audit. |
| Step 3: `docker compose up -d` starts all services without errors | PARTIAL | Compose is syntactically valid; startup was not re-run here. |
| Step 3: All routes loaded | PARTIAL | Route set is present; OpenIG startup logs were not rechecked in this audit. |
| Step 3: WordPress SSO login works | PARTIAL | Wiring looks correct; not browser-tested in this audit. |
| Step 3: WhoAmI SSO login works | PARTIAL | Login path exists, but runtime validation is still pending. |
| Step 3: WordPress SLO logout works | PARTIAL | Wiring looks correct; not revalidated live in this audit. |
| Step 3: Cross-app SLO works (WordPress logout logs out WhoAmI) | FAIL | Missing `00-backchannel-logout-app2.json` blocks app2 revocation. |
| Step 3: Redis keys are prefixed (`app1:*`, `app2:*`) | PASS | Route args and ACL prefixes align on `app1` and `app2`. |
| Step 3: Redis ACL blocks cross-app writes | PASS | ACL file enforces per-user key prefix limits. |
| Step 3: Redis ACL minimal command set verified (`+set`, `+get`, `+del`, `+exists`, `+ping`) | PASS | ACL file matches the minimal command set. |
| Step 3: Old Stack A still functional on port 80 if needed for rollback | PARTIAL | Rollback procedure is described in plan, but not verifiable from shared files alone. |
| Step 4: Redmine SSO/SLO works | PARTIAL | Static wiring looks complete; not re-run live in this audit. |
| Step 4: Jellyfin SSO/SLO works (1 login confirmed) | PARTIAL | Static wiring looks complete; not re-run live in this audit. |
| Step 4: Grafana SSO/SLO works | PARTIAL | Static wiring looks complete; not re-run live in this audit. |
| Step 4: phpMyAdmin SSO/SLO works (alice + bob confirmed) | PARTIAL | Static wiring looks complete; not re-run live in this audit. |
| Step 4: All 6 apps SSO/SLO matrix complete | FAIL | app2 remains incomplete and cross-app SLO is still broken for app2. |
| Step 4: Cross-app SLO works (logout from any app triggers backchannel for all) | FAIL | app2 backchannel route is absent. |
| Step 4: Redis keys properly prefixed per app (no cross-contamination) | PASS | Static route/script/ACL wiring is per-app throughout. |
| Step 4: Redis ACL blocks cross-app access | PASS | Static ACL config enforces separation. |
| Step 4: Redis ACL minimal command set verified (`+set`, `+get`, `+del`, `+exists`, `+ping`) | PASS | Static ACL config matches the target command set. |
| Step 4: Vault per-app AppRoles scoped correctly | PASS | Per-app policies are narrowly scoped in bootstrap. |
| Step 4: No `JWT session is too large` errors in logs | PARTIAL | Token offload logic exists, but logs were not rechecked in this audit. |
| Step 4: TokenReferenceFilter store/restore works for all 6 apps | PARTIAL | Code is present, but runtime verification was not re-run. |
| Step 4: Jellyfin deviceId stable across sessions (SHA-256 from sub) | PASS | `buildDeviceId(sub)` is deterministic in injector and logout handler. |
| Step 4: phpMyAdmin bob login works (MariaDB user + Vault secret aligned) | PARTIAL | Static config aligns, but no live login was re-run here. |

## Gotchas Compliance

| Gotcha | Expected fix | Found in shared-infra? |
|---|---|---|
| Backchannel logout lost when Redis down | Redis AOF enabled so blacklist keys survive restart | Yes. `shared/docker/redis/redis-entrypoint.sh` starts Redis with `--appendonly yes`. |
| Direct host port exposure bypasses gateway controls | No direct app host port publishing | Yes. `shared/docker-compose.yml` only exposes `80:80` on `shared-nginx`. |
| `CANONICAL_ORIGIN_APPx` must exist on every OpenIG node | Define `CANONICAL_ORIGIN_APP1..APP6` in the shared OpenIG env set | Yes. Present in the shared env anchor applied to both OpenIG nodes. |
| Stack C nginx buffer drift | Keep `proxy_buffer_size 128k` and `proxy_buffers 4 256k` | Yes. Present in `shared/nginx/nginx.conf`. |
| JWKS cache race condition | Use `globals.compute("jwks_cache")` atomic cache pattern | Yes. Present in `BackchannelLogoutHandler.groovy`. |
| SloHandler missing `try/catch` | Wrap logout flow in `try/catch` | Yes. Present in `SloHandler.groovy` and `SloHandlerJellyfin.groovy`. |
| Backchannel logout TTL unit mismatch | Use seconds consistently with `28800` | Yes, with caveat. Handler treats TTL as seconds; routes still point to generic `REDIS_BLACKLIST_TTL` instead of per-app envs. |

## Notes

- Runtime-generated [[Vault]] material exists under `shared/vault/file/` and `shared/vault/keys/`, but those files are gitignored. The tracked secret exposure is the bootstrap seed content, not the generated role/secret files.
- Shared route/session isolation is otherwise in good shape: route-local `SessionApp1..6`, per-app `token_ref_id_app1..6`, per-app Redis users, and per-app Redis key prefixes are wired consistently.
