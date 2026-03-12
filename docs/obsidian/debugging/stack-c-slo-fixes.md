---
title: Stack C SLO Fixes (2026-03-11)
tags:
  - debugging
  - stack-c
  - slo
  - openig
  - keycloak
date: 2026-03-12
status: done
---

# Stack C SLO Fixes

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Root causes, verification, and fixes

### 1) Backchannel route condition depended on `Host`

- Root cause:
  - `00-backchannel-logout-app5.json` and `00-backchannel-logout-app6.json` previously required `request.headers['Host'][0]` to match app hostnames.
  - Keycloak backchannel logout calls target `http://host.docker.internal:18080/openid/app5/backchannel_logout` and `.../app6/backchannel_logout`, so `Host` is not guaranteed to be `grafana-c.sso.local` / `phpmyadmin-c.sso.local`.
- Verification used:
  - `curl -i -X POST http://localhost:18080/openid/app5/backchannel_logout --data "logout_token=<jwt>"`
  - `curl -i -X POST http://localhost:18080/openid/app6/backchannel_logout --data "logout_token=<jwt>"`
  - Log analysis to confirm `BackchannelLogoutHandler.groovy` was not reached before condition fix.
- Exact fix applied:
  - Removed `Host` checks from both route conditions.
  - New conditions only require `POST` + exact `request.uri.path`.
  - Files:
    - `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json`
    - `stack-c/openig_home/config/routes/00-backchannel-logout-app6.json`

### 2) Missing `id_token_hint` on RP-initiated logout

- Root cause:
  - Static `defaultLogoutGoto` alone could not reliably include the runtime `id_token_hint`.
  - Without `id_token_hint`, Keycloak logout with `post_logout_redirect_uri` did not consistently complete redirect behavior.
- Verification used:
  - `curl -I "http://grafana-c.sso.local:18080/logout"` and `curl -I "http://phpmyadmin-c.sso.local:18080/?logout=1"` to inspect redirect location.
  - OpenIG log analysis for missing token cases (new warnings: `No id_token found in session during logout`).
- Exact fix applied:
  - Added custom handlers that resolve `id_token` from session key candidates and append `id_token_hint`:
    - `stack-c/openig_home/scripts/groovy/SloHandlerGrafana.groovy`
    - `stack-c/openig_home/scripts/groovy/SloHandlerPhpMyAdmin.groovy`
  - Added dedicated intercept routes:
    - `stack-c/openig_home/config/routes/00-grafana-logout.json`
    - `stack-c/openig_home/config/routes/00-phpmyadmin-logout.json`

### 3) `KEYCLOAK_BROWSER_URL` was null in runtime context

- Root cause:
  - OpenIG instances did not have `KEYCLOAK_BROWSER_URL` explicitly set in `stack-c/docker-compose.yml`.
  - Logout URL construction depended on this base URL.
- Verification used:
  - Log analysis for missing-env signal and invalid/missing logout base URL behavior.
- Exact fix applied:
  - Added `KEYCLOAK_BROWSER_URL: "http://auth.sso.local:8080"` for both `openig-c1` and `openig-c2` services in `stack-c/docker-compose.yml`.
  - Added defensive fallback + warning logging in both SLO handlers.

### 4) Shared `JwtSession.sharedSecret` across stacks

- Root cause:
  - Stack C previously used the same `JwtSession.sharedSecret` value used by Stack A.
  - With cookie domain `.sso.local` and cookie name `IG_SSO`, cross-stack token acceptance risked session ambiguity.
- Verification used:
  - Config diff analysis (`stack-a` and old `stack-c` value were identical).
  - Log analysis during session resolution mismatch scenarios.
- Exact fix applied:
  - Updated `stack-c/openig_home/config/config.json`:
    - from `U1NPLUxhYi1Kd3RTZWNyZXQtMzJieXRlcy1LRVlcIVwh`
    - to `U1NPLUMtSnd0U2VjcmV0LTMyYnl0ZXMtS0VZXyFAIyQ=`

> [!warning]
> Backchannel logout endpoints must not rely on browser-facing `Host` headers.

> [!tip]
> Keep per-stack `JwtSession.sharedSecret` values unique when sharing cookie domain space.

> [!success]
> Stack C logout now supports both RP-initiated (front-channel) and Keycloak backchannel invalidation paths.

