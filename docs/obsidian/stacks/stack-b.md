---
title: Stack B
tags:
  - sso
  - stack-b
  - openig
  - redmine
  - jellyfin
  - security-hardening
date: 2026-03-20
status: complete
---

# Stack B

Related: [[Stack A]] [[Stack C]] [[OpenIG]] [[Keycloak]] [[Vault]]

## Entry

- Gateway port: `9080`
- Hosts: `redmine-b.sso.local`, `jellyfin-b.sso.local`

## Services

| Service | Compose/Container | Ports |
|---|---|---|
| `nginx-b` | `nginx-b` / `sso-b-nginx` | `9080:80` |
| `openig-b1` | `openig-b1` / `sso-b-openig-1` | `8080` (internal) |
| `openig-b2` | `openig-b2` / `sso-b-openig-2` | `8080` (internal) |
| `redmine-b` | `redmine` / `sso-b-redmine` | `3000` (internal) |
| `jellyfin-b` | `jellyfin` / `sso-b-jellyfin` | `8096` (internal) |
| `redis-b` | `redis-b` / `sso-redis-b` | `6379` (internal) |
| `vault-b` | `vault-b` / `sso-b-vault` | `8200` (internal) |

## Auth Mechanisms

- Redmine: form-based intercept (`VaultCredentialFilterRedmine.groovy` + `RedmineCredentialInjector.groovy`) to perform `/login` CSRF + form POST and inject Redmine cookies.
- Jellyfin: token injection via API (`/Users/AuthenticateByName`) in `JellyfinTokenInjector.groovy`, then MediaBrowser `Authorization` header injection.

## Routes

| Route file | Purpose |
|---|---|
| `00-backchannel-logout-app3.json` | Handles Keycloak `POST /openid/app3/backchannel_logout` for Redmine and runs `BackchannelLogoutHandler.groovy`. |
| `00-backchannel-logout-app4.json` | Handles Keycloak `POST /openid/app4/backchannel_logout` for Jellyfin and runs `BackchannelLogoutHandler.groovy`. |
| `00-dotnet-logout.json` (DELETED) | Legacy `/app3/Account/Logout` intercept on `openigb.sso.local`, handled by `DotnetSloHandler.groovy`. |
| `00-jellyfin-logout.json` | Intercepts Jellyfin logout requests, runs `TokenReferenceFilter.groovy` for `/openid/app4`, then delegates to `SloHandlerJellyfin.groovy`. |
| `00-redmine-logout.json` | Intercepts Redmine `POST /logout`, runs `TokenReferenceFilter.groovy` for `/openid/app3`, then delegates to the consolidated `SloHandler.groovy`; the old `SloHandlerRedmine.groovy` file was leftover only and has been deleted. |
| `01-dotnet.json` (DELETED) | Legacy .NET SSO chain for `/app3*` and `/openid/app3*` to `dotnet-app:5000`, with OAuth2 + blacklist + credential injection. |
| `01-jellyfin.json` | Main Jellyfin SSO chain to `jellyfin:8096`: OAuth2 (`/openid/app4`) + TokenReferenceFilter + app4 blacklist + Vault creds + token injector + response rewrite. |
| `02-redmine.json` | Main Redmine SSO chain to `redmine:3000`: OAuth2 (`/openid/app3`) + TokenReferenceFilter + app3 blacklist + Vault creds + form-login credential injector. |

## Groovy Scripts

| Script file | Purpose |
|---|---|
| `BackchannelLogoutHandler.groovy` | Parses Keycloak `logout_token`, validates `RS256` / `ES256` signatures, extracts `sid/sub`, and writes Redis `blacklist:{sid}` with TTL. |
| `DotnetCredentialInjector.groovy` (DELETED) | Legacy ASP.NET login automation (antiforgery token + cookies) using Vault-backed creds; injects upstream `Cookie`. |
| `DotnetSloHandler.groovy` (DELETED) | Legacy .NET logout redirect to Keycloak end-session with optional `id_token_hint`. |
| `JellyfinResponseRewriter.groovy` | Rewrites Jellyfin HTML to seed `localStorage` credentials and steer browser flow away from local login. |
| `JellyfinTokenInjector.groovy` | Calls Jellyfin auth API, derives a stable Jellyfin `deviceId` from OIDC `sub`, stores token/user/device markers in session, injects MediaBrowser `Authorization`, and clears state on `401`. |
| `RedmineCredentialInjector.groovy` | Performs Redmine `/login` GET+POST (CSRF + credentials), returns upstream cookies to the browser, removes legacy `redmine_session_cookies` state, and retries on `/login` redirect. |
| `TokenReferenceFilter.groovy` | Stores and restores the live `oauth2:*` session namespace in Redis so `IG_SSO_B` only carries `token_ref_id` plus small identity markers. |
| `SessionBlacklistFilter.groovy` | Shared blacklist filter used by both Stack B routes via `args` (`clientEndpoint`, `sessionCacheKey`, `canonicalOrigin`, `canonicalOriginEnvVar`). |
| `SloHandlerJellyfin.groovy` | Calls Jellyfin `/Sessions/Logout` when token exists, rebuilds the stable Jellyfin `deviceId` from OIDC `sub` if needed, clears OpenIG session, and always redirects through Keycloak logout while omitting `id_token_hint` only when it is missing. |
| `SloHandler.groovy` | Consolidated Redmine/logout handler shared after Step 4 parameterization. |
| `VaultCredentialFilterJellyfin.groovy` | AppRole login to Vault and fetch `secret/data/jellyfin-creds/{email}` into `attributes.jellyfin_credentials`. |
| `VaultCredentialFilterRedmine.groovy` | AppRole login to Vault and fetch `secret/data/redmine-creds/{email}` into `attributes.redmine_credentials`. |

> [!success]
> Full 2026-03-19 validation PASS: `IG_SSO_B` present, TokenRef Store/Restore OK, and backchannel Redis blacklist logout works for both `redmine-b.sso.local` and `jellyfin-b.sso.local`. Post-audit cleanup confirmed `SloHandlerRedmine.groovy` was an unreferenced leftover from Step 4 and has been deleted.

> [!warning]
> Known pending:
> - Jellyfin WebSocket `http://` -> `ws://` bug (`01-jellyfin.json`)
> - STEP-14: remove `user: root` from `openig-b1` and `openig-b2` after host-volume compatibility validation

## 2026-03-17 Security Hardening

- Confirmed hardening item `[H-5/S-3]` from `.omc/plans/phase2-security-hardening.md` was implemented directly for [[Stack B]].
- Moved Stack B runtime secrets out of `stack-b/docker-compose.yml` into local `stack-b/.env`.
- Added committed `stack-b/.env.example` placeholders with Kubernetes `envFrom secretRef` guidance for production secret delivery.
- Replaced hardcoded secret values in `openig-b1`, `openig-b2`, `mysql-redmine`, and `redmine` with `${...}` environment references.
- Pinned `openig-b1` and `openig-b2` to `openidentityplatform/openig:6.0.1` because `openidentityplatform/openig:latest` (`6.0.2`) is currently broken in this lab while `6.0.1` is the verified good tag.
- Kept `REDIS_PASSWORD` out of this step intentionally; STEP-04 remains the owner for Redis auth rollout.

> [!success]
> Validation passed: `cd stack-b && docker compose config | grep JWT_SHARED_SECRET` resolved the expected shared secret for both [[OpenIG]] nodes.

> [!success]
> Validation on `2026-03-17`: Stack B started cleanly with the pinned image and loaded all 6 routes.

> [!warning]
> `stack-b/.env` was not covered by existing ignore rules. Root `.gitignore` now includes `stack-b/.env` so Stack B lab secrets stay out of Git.

## 2026-03-18 Redis Auth Hardening

- Implemented `[H-4/S-2]` for [[Stack B]] from `.omc/plans/phase2-security-hardening.md`.
- Generated a Stack B-only `REDIS_PASSWORD` in local `stack-b/.env` and added the matching placeholder to `stack-b/.env.example`.
- Updated `redis-b` to start with `--requirepass ${REDIS_PASSWORD}`.
- Passed `REDIS_PASSWORD` into both [[OpenIG]] nodes and added RESP `AUTH` before blacklist Redis `GET` and `SET`.

> [!success]
> Validation on `2026-03-18`: `docker logs sso-b-openig-1 2>&1 | grep 'Loaded the route'` returned route load entries after the restart, and `docker exec sso-redis-b redis-cli PING` returned `NOAUTH Authentication required.`

> [!success]
> Stack B `JwtSession` now matches the shared cookie-domain baseline: `stack-b/openig_home/config/config.json` sets `cookieDomain: ".sso.local"` for `IG_SSO_B`.

## 2026-03-18 Phase 2b hardening batch

- `STEP-07` / `[M-9/Code-M6]`: `VaultCredentialFilterRedmine.groovy` and `VaultCredentialFilterJellyfin.groovy` now return `502 BAD_GATEWAY` for Vault auth/read upstream failures, aligning Stack B with the shared [[OpenIG]] contract.
- `STEP-08` / `[M-11]`: `BackchannelLogoutHandler.groovy` now throws `IOException("EOF")` on unexpected Redis socket closure so backchannel logout fails closed instead of silently continuing.
- `STEP-11` / `[A-4]`: `openig-b1` and `openig-b2` now declare `extra_hosts: host.docker.internal:host-gateway`, so `KEYCLOAK_INTERNAL_URL` remains portable on Linux Docker hosts as well as Docker Desktop.
- `STEP-12` / `[M-3/S-7]`: `stack-b/nginx/nginx.conf` now sets `X-Frame-Options: SAMEORIGIN`, `X-Content-Type-Options: nosniff`, and `Referrer-Policy: strict-origin-when-cross-origin`; `Content-Security-Policy` stays app-specific and HSTS remains deferred until TLS exists.

> [!success]
> Stack B now matches the post-audit baseline for Vault error handling, fail-closed backchannel logout behavior, Linux `host.docker.internal` portability, and lab-safe nginx security headers.

## 2026-03-20 Jellyfin logout hardening

- Resolved `[L-4]`: [stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy) now always sends the browser through the [[Keycloak]] end-session endpoint for app4 logout, even when the restored OAuth2 session no longer has an `id_token`. `id_token_hint` is appended only when present.
- Resolved `[L-6]`: [stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy) and [stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy) now derive Jellyfin `deviceId` from `SHA-256("jellyfin-<sub>")`, truncated to 32 hex chars, instead of using `session.hashCode()`.
- [stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy) now persists `jellyfin_user_sub` in session and clears it with the rest of the Jellyfin markers on `401`, so [stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy) can rebuild the same device ID before calling Jellyfin `/Sessions/Logout`.

> [!success]
> Validation on `2026-03-20`: `docker restart sso-b-openig-1 sso-b-openig-2` completed successfully. When scoped to the fresh restart window, `docker logs --since 2026-03-20T02:08:00.590644839Z sso-b-openig-1 2>&1 | grep -E 'Loaded the route|ERROR'` showed all six Stack B routes loading and no startup-time `ERROR` lines.

## 2026-03-20 Groovy Redis port and log prefix cleanup

- Resolved `[L-1]`: [stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy) and [stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy) now read Redis port from route arg `redisPort`, then `REDIS_PORT`, then default `6379`.
- Resolved `[L-3]`: [stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy) now uses `[TokenReferenceFilter]`, and [stack-b/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy) now emits `[JellyfinResponseRewriter]`.

> [!success]
> Validation on `2026-03-20`: `docker restart sso-b-openig-1 sso-b-openig-2` completed successfully. `docker logs sso-b-openig-1 2>&1 | grep 'Loaded the route'` showed `00-redmine-logout`, `02-redmine`, `00-jellyfin-logout`, `01-jellyfin`, `00-backchannel-logout-app3`, and `00-backchannel-logout-app4`.
