---
title: 2026-03-20 Stack C phpMyAdmin SLO Loop and SID Cache Fix
tags:
  - debugging
  - stack-c
  - openig
  - slo
  - oauth2
  - redis
date: 2026-03-20
status: done
---

# 2026-03-20 Stack C phpMyAdmin SLO Loop and SID Cache Fix

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Request: fix two Stack C gateway bugs only, without touching Stack A, Stack B, or any target application.
- Primary failure: after SLO and re-login, phpMyAdmin could enter an infinite loop because every backend `401` from `PhpMyAdminBasicAuth` executed `SloHandler`, which called Keycloak end-session even for non-logout failures.
- Secondary failure: `oidc_sid_app6` was not guaranteed to exist after OAuth2 callback because `OAuth2ClientFilter` short-circuits the callback chain before `SessionBlacklistFilter` can derive and cache the `sid`.

## Root cause and fix

### 1. phpMyAdmin `401` loop after SLO + re-login

- `stack-c/openig_home/config/routes/11-phpmyadmin.json` previously wired `PhpMyAdminBasicAuth.failureHandler` directly to `SloHandler.groovy`.
- That was correct for real phpMyAdmin logout traffic, but incorrect for ordinary downstream `401` responses after re-login.
- New handler added: `stack-c/openig_home/scripts/groovy/PhpMyAdminAuthFailureHandler.groovy`
- New behavior:
  - logout-shaped request (`path` contains `logout` or query contains `logout=1`) keeps the Keycloak end-session redirect behavior
  - all other `401` responses clear only app6 auth state (`oauth2:*` for `/openid/app6`, `oidc_sid_app6`, `token_ref_id_app6`) and redirect the browser to `http://phpmyadmin-c.sso.local:18080/`

> [!warning]
> phpMyAdmin logout still depends on the `401` path. The fix was to split logout `401`s from non-logout `401`s, not to remove the failure handler entirely.

### 2. `oidc_sid_app5` / `oidc_sid_app6` cache on OAuth2 callback

- `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy` now accepts optional `sessionCacheKey`.
- In the `.then()` response hook, after writing the OAuth2 session blob to Redis and before stripping `oauth2:*` entries from `JwtSession`, the filter now:
  - locates the current route's `oauth2:*` session entry
  - reads `atr.id_token`
  - base64url-decodes the JWT payload
  - extracts the `sid` claim
  - writes that value into `session[sessionCacheKey]`
- Route wiring added:
  - `stack-c/openig_home/config/routes/10-grafana.json` passes `sessionCacheKey: oidc_sid_app5`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json` passes `sessionCacheKey: oidc_sid_app6`

> [!tip]
> This keeps `SessionBlacklistFilter.groovy` independent from the OAuth2 callback short-circuit path because the `sid` is cached before the `oauth2:*` payload is removed from the browser session.

## Verification

- Restarted:
  - `stack-c-openig-c1-1`
  - `stack-c-openig-c2-1`
- Verified route load from container logs:
  - `Loaded the route with id '11-phpmyadmin' registered with the name 'phpmyadmin-sso'`
  - `Loaded the route with id '10-grafana' registered with the name 'grafana-sso'`
- Checked fresh startup logs for `error|exception|failed` in the last 2 minutes on both containers: no matches.

> [!success]
> Stack C OpenIG accepted the updated route JSON and reloaded both affected routes after restart.

## Files changed

- `stack-c/openig_home/config/routes/10-grafana.json`
- `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-c/openig_home/scripts/groovy/PhpMyAdminAuthFailureHandler.groovy`

## Current state

- The gateway-side loop condition is broken at the handler level.
- `sid` caching is now performed before `oauth2:*` session stripping for Stack C Grafana and phpMyAdmin.
- Live browser validation of phpMyAdmin logout, re-login, and post-relogin navigation is still the final end-to-end proof.
