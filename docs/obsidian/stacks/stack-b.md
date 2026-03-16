---
title: Stack B
tags:
  - sso
  - stack-b
  - openig
  - redmine
  - jellyfin
date: 2026-03-12
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
| `redmine-b` | `redmine` / `sso-b-redmine` | `3000:3000` |
| `jellyfin-b` | `jellyfin` / `sso-b-jellyfin` | `8096` (internal) |
| `redis-b` | `redis-b` / `sso-redis-b` | `6379` (internal) |
| `vault-b` | `vault-b` / `sso-b-vault` | `8200` (internal) |

## Auth Mechanisms

- Redmine: form-based intercept (`VaultCredentialFilterRedmine.groovy` + `RedmineCredentialInjector.groovy`) to perform `/login` CSRF + form POST and inject Redmine cookies.
- Jellyfin: token injection via API (`/Users/AuthenticateByName`) in `JellyfinTokenInjector.groovy`, then MediaBrowser `Authorization` header injection.

## Routes

| Route file | Purpose |
|---|---|
| `00-backchannel-logout-app3.json` | Handles Keycloak `POST /openid/app3/backchannel_logout` and runs `BackchannelLogoutHandler.groovy`. |
| `00-backchannel-logout-app4.json` | Handles Keycloak `POST /openid/app4/backchannel_logout` and runs `BackchannelLogoutHandler.groovy`. |
| `00-dotnet-logout.json` (DELETED) | Legacy `/app3/Account/Logout` intercept on `openigb.sso.local`, handled by `DotnetSloHandler.groovy`. |
| `00-jellyfin-logout.json` | Intercepts Jellyfin logout requests and delegates to `SloHandlerJellyfin.groovy`. |
| `00-redmine-logout.json` | Intercepts Redmine `POST /logout` and delegates to `SloHandlerRedmine.groovy`. |
| `01-dotnet.json` (DELETED) | Legacy .NET SSO chain for `/app3*` and `/openid/app3*` to `dotnet-app:5000`, with OAuth2 + blacklist + credential injection. |
| `01-jellyfin.json` | Main Jellyfin SSO chain to `jellyfin:8096`: OAuth2 (`/openid/app3`) + app3 blacklist + Vault creds + token injector + response rewrite. |
| `02-redmine.json` | Main Redmine SSO chain to `redmine:3000`: OAuth2 (`/openid/app4`) + app4 blacklist + Vault creds + form-login credential injector. |

## Groovy Scripts

| Script file | Purpose |
|---|---|
| `BackchannelLogoutHandler.groovy` | Parses Keycloak `logout_token`, extracts `sid/sub`, writes Redis `blacklist:{sid}` with TTL. |
| `DotnetCredentialInjector.groovy` (DELETED) | Legacy ASP.NET login automation (antiforgery token + cookies) using Vault-backed creds; injects upstream `Cookie`. |
| `DotnetSloHandler.groovy` (DELETED) | Legacy .NET logout redirect to Keycloak end-session with optional `id_token_hint`. |
| `JellyfinResponseRewriter.groovy` | Rewrites Jellyfin HTML to seed `localStorage` credentials and steer browser flow away from local login. |
| `JellyfinTokenInjector.groovy` | Calls Jellyfin auth API, stores token/user/device in session, injects MediaBrowser `Authorization`, clears on `401`. |
| `RedmineCredentialInjector.groovy` | Performs Redmine `/login` GET+POST (CSRF + credentials), caches `_redmine_session`, injects cookies, retries on `/login` redirect. |
| `SessionBlacklistFilter.groovy` | Generic blacklist filter (legacy/shared) resolving sid from OIDC token and checking Redis blacklist. |
| `SessionBlacklistFilterApp3.groovy` | App3-specific blacklist check for Jellyfin session keys and host-aware OAuth2 session lookup. |
| `SessionBlacklistFilterApp4.groovy` | App4-specific blacklist check for Redmine session keys and host-aware OAuth2 session lookup. |
| `SloHandlerJellyfin.groovy` | Calls Jellyfin `/Sessions/Logout` when token exists, clears OpenIG session, redirects to Keycloak logout with `id_token_hint` if found. |
| `SloHandlerRedmine.groovy` | Clears OpenIG session and redirects to Keycloak logout for app4 with `id_token_hint` when available. |
| `VaultCredentialFilterJellyfin.groovy` | AppRole login to Vault and fetch `secret/data/jellyfin-creds/{email}` into `attributes.jellyfin_credentials`. |
| `VaultCredentialFilterRedmine.groovy` | AppRole login to Vault and fetch `secret/data/redmine-creds/{email}` into `attributes.redmine_credentials`. |

> [!success]
> SSO+SLO confirmed working for both apps: `redmine-b.sso.local` and `jellyfin-b.sso.local`.

> [!warning]
> Known pending:
> - Jellyfin WebSocket `http://` -> `ws://` bug (`01-jellyfin.json`)
> - Missing `cookieDomain` in Stack B `config.json` (LOW priority)
