---
title: Stack B
tags:
  - sso
  - stack-b
  - openig
  - redmine
  - jellyfin
  - security-hardening
date: 2026-03-18
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
| `00-jellyfin-logout.json` | Intercepts Jellyfin logout requests and delegates to `SloHandlerJellyfin.groovy`. |
| `00-redmine-logout.json` | Intercepts Redmine `POST /logout` and delegates to the consolidated `SloHandler.groovy`; the old `SloHandlerRedmine.groovy` file was leftover only and has been deleted. |
| `01-dotnet.json` (DELETED) | Legacy .NET SSO chain for `/app3*` and `/openid/app3*` to `dotnet-app:5000`, with OAuth2 + blacklist + credential injection. |
| `01-jellyfin.json` | Main Jellyfin SSO chain to `jellyfin:8096`: OAuth2 (`/openid/app4`) + app4 blacklist + Vault creds + token injector + response rewrite. |
| `02-redmine.json` | Main Redmine SSO chain to `redmine:3000`: OAuth2 (`/openid/app3`) + app3 blacklist + Vault creds + form-login credential injector. |

## Groovy Scripts

| Script file | Purpose |
|---|---|
| `BackchannelLogoutHandler.groovy` | Parses Keycloak `logout_token`, extracts `sid/sub`, writes Redis `blacklist:{sid}` with TTL. |
| `DotnetCredentialInjector.groovy` (DELETED) | Legacy ASP.NET login automation (antiforgery token + cookies) using Vault-backed creds; injects upstream `Cookie`. |
| `DotnetSloHandler.groovy` (DELETED) | Legacy .NET logout redirect to Keycloak end-session with optional `id_token_hint`. |
| `JellyfinResponseRewriter.groovy` | Rewrites Jellyfin HTML to seed `localStorage` credentials and steer browser flow away from local login. |
| `JellyfinTokenInjector.groovy` | Calls Jellyfin auth API, stores token/user/device in session, injects MediaBrowser `Authorization`, clears on `401`. |
| `RedmineCredentialInjector.groovy` | Performs Redmine `/login` GET+POST (CSRF + credentials), caches `_redmine_session`, injects cookies, retries on `/login` redirect. |
| `SessionBlacklistFilter.groovy` | Shared blacklist filter used by both Stack B routes via `args` (`clientEndpoint`, `sessionCacheKey`, `canonicalOrigin`, `canonicalOriginEnvVar`). |
| `SloHandlerJellyfin.groovy` | Calls Jellyfin `/Sessions/Logout` when token exists, clears OpenIG session, redirects to Keycloak logout with `id_token_hint` if found. |
| `SloHandler.groovy` | Consolidated Redmine/logout handler shared after Step 4 parameterization. |
| `VaultCredentialFilterJellyfin.groovy` | AppRole login to Vault and fetch `secret/data/jellyfin-creds/{email}` into `attributes.jellyfin_credentials`. |
| `VaultCredentialFilterRedmine.groovy` | AppRole login to Vault and fetch `secret/data/redmine-creds/{email}` into `attributes.redmine_credentials`. |

> [!success]
> SSO/SLO WORKING for both apps: `redmine-b.sso.local` and `jellyfin-b.sso.local`. Post-audit cleanup confirmed `SloHandlerRedmine.groovy` was an unreferenced leftover from Step 4 and has been deleted.

> [!warning]
> Known pending:
> - Jellyfin WebSocket `http://` -> `ws://` bug (`01-jellyfin.json`)
> - STEP-13: model `SameSite`/`Secure` cookie flags for `IG_SSO_B` in the nginx layer
> - STEP-14: remove `user: root` from `openig-b1` and `openig-b2` after host-volume compatibility validation

## 2026-03-17 Security Hardening

- Confirmed hardening item `[H-5/S-3]` from `.omc/plans/phase2-security-hardening.md` was implemented directly for [[Stack B]].
- Moved Stack B runtime secrets out of `stack-b/docker-compose.yml` into local `stack-b/.env`.
- Added committed `stack-b/.env.example` placeholders with Kubernetes `envFrom secretRef` guidance for production secret delivery.
- Replaced hardcoded secret values in `openig-b1`, `openig-b2`, `mysql-redmine`, and `redmine` with `${...}` environment references.
- Pinned `openig-b1` and `openig-b2` to `openidentityplatform/openig:6.0.1` because `openidentityplatform/openig:latest` now resolves to a Tomcat 11 build that breaks OpenIG 6.
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
