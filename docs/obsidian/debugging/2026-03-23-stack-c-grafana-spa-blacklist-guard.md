---
title: Stack C Grafana SPA Blacklist Guard
tags:
  - openig
  - stack-c
  - debugging
  - grafana
  - oauth2
  - race-condition
date: 2026-03-23
status: done
---

# Stack C Grafana SPA Blacklist Guard

Related: [[OpenIG]], [[Keycloak]], [[Stack C]], [[Vault]]

## Context

Grafana SPA background XHRs could race with the main browser navigation after backchannel logout blacklisted `oidc_sid_app5`.

The broken sequence was:

1. `TokenReferenceFilterApp5` restored pending OAuth2 state for both the main request and concurrent `/api/login/ping` XHRs.
2. `SessionBlacklistFilterApp5` later cleared the session and returned a redirect.
3. `TokenReferenceFilterApp5.then()` observed an empty session but `JwtSession` still committed an empty `IG_SSO_APP5` cookie because the session had already been dirtied by the restore.
4. A late XHR `Set-Cookie` overwrote the fresh pending nonce/state written by the main OAuth2 redirect.
5. Keycloak callback then failed with `state parameter contained an unexpected value`.

## What Changed

- Added `shared/openig_home/scripts/groovy/SpaBlacklistGuardFilter.groovy`.
- The new guard reads `oidc_sid_app5` from session, checks `app5:blacklist:{sid}` in Redis, and returns `401` immediately for JSON XHR requests without calling `next`.
- Browser requests are still passed downstream so `SessionBlacklistFilterApp5` keeps owning the clear-and-redirect flow.
- Updated `shared/openig_home/config/routes/10-grafana.json` to register `SpaBlacklistGuardApp5` and move it to the front of the Grafana filter chain:
  - `SpaBlacklistGuardApp5`
  - `TokenReferenceFilterApp5`
  - `SpaAuthGuardApp5`
  - `OidcFilterApp5`
  - `SessionBlacklistFilterApp5`
  - `StripGatewaySessionCookiesApp5`
  - `GrafanaUserHeader`

## Validation

> [!success]
> Restarted `stack-c-openig-c1-1`, waited 5 seconds, then restarted `stack-c-openig-c2-1`.
> Post-restart logs for both containers show `Loaded the route with id '10-grafana' registered with the name 'grafana-sso'`.

> [!warning]
> The exact requested `docker logs stack-c-openig-c1-1 2>&1 | grep -E 'Loaded the route|ERROR|WARN' | tail -20` output still includes older `OAuth2ClientFilter` `invalid_token` errors and `TokenReferenceFilterApp5` warnings that predate or are unrelated to route-load validation.
> A narrower `--since 10m` log window showed the route loading cleanly with no new `SpaBlacklistGuard` startup errors.

> [!tip]
> The new guard supports the requested `redisHost` / `redisPort` / `redisAuth` inputs, but it also preserves the repo's current app-specific Redis auth pattern using `redisUser` and `redisPasswordEnvVar`, matching existing Stack C OpenIG routes.

## Current State

- Blacklisted Grafana SPA XHRs now stop before `TokenReferenceFilterApp5` can restore OAuth2 session data.
- That prevents the empty-session `Set-Cookie` write that was clobbering freshly issued OAuth2 nonce/state.
- Non-XHR browser requests still take the existing redirect path through `SessionBlacklistFilterApp5`.
- No git commit was created.

## Files Changed

- `shared/openig_home/scripts/groovy/SpaBlacklistGuardFilter.groovy`
- `shared/openig_home/config/routes/10-grafana.json`
