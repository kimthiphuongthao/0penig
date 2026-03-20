---
title: All Stacks TokenReferenceFilter Per-App TokenRefKey
tags:
  - debugging
  - openig
  - redis
  - jwt-session
  - oauth2
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-20
status: complete
---

# All Stacks TokenReferenceFilter Per-App TokenRefKey

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Request: stop cross-app contamination when multiple apps on the same OpenIG instance share one `JwtSession` cookie and the same `TokenReferenceFilter.groovy`.
- Scope: gateway-only route and Groovy changes in [[Stack A]], [[Stack B]], and [[Stack C]].
- Constraint: do not modify target application code or target app configuration.
- Regression window: surfaced after the later Stack B cleanup work around L-4/L-6 and Code-M3 on `fix/jwtsession-production-pattern`, then fixed separately in commit `8e9f729`.

> [!warning]
> The prior implementation stored every app's Redis pointer in the same session field, `token_ref_id`. On shared cookies such as `IG_SSO_B` and `IG_SSO_C`, one app could overwrite another app's pointer before the OAuth2 callback completed.

## Root Cause

- `TokenReferenceFilter.groovy` in all three stacks used a single hardcoded session key, `token_ref_id`.
- Stack B shares `IG_SSO_B` across Redmine and Jellyfin, and Stack C shares `IG_SSO_C` across Grafana and phpMyAdmin.
- When a second app stored its Redis pointer, the first app's in-progress authorization lost the correct reference.
- The observed failure was `Authorization call-back failed because there is no authorization in progress`, followed by oversized `JwtSession` cookies because the wrong state was restored or not cleaned correctly.

## Fix Applied

- Added route-bound `tokenRefKey` support to all three `TokenReferenceFilter.groovy` copies with default fallback to `token_ref_id`.
- Replaced all session reads, writes, and non-OAuth2 preservation checks to use the bound `tokenRefKey`.
- Updated route args so each app uses a unique session pointer key:
  - Stack A: `app1 -> token_ref_id_app1`, `app2 -> token_ref_id_app2`
  - Stack B: `app3 -> token_ref_id_app3`, `app4 -> token_ref_id_app4`
  - Stack C: `app5 -> token_ref_id_app5`, `app6 -> token_ref_id_app6`
- Updated logout intercept routes to pass the same `tokenRefKey` into `TokenReferenceFilter.groovy`.
- Reviewed `SloHandler.groovy` and `SloHandlerJellyfin.groovy`; no code change was required because they do not read `session['token_ref_id']` directly.

> [!success]
> Commit `8e9f729` contains the gateway fix only: `fix: per-app tokenRefKey in TokenReferenceFilter to prevent cross-app session contamination`.

## Verification

- Restarted:
  - `sso-openig-1`
  - `sso-openig-2`
  - `sso-b-openig-1`
  - `sso-b-openig-2`
  - `stack-c-openig-c1-1`
  - `stack-c-openig-c2-1`
- Ran the requested check:
  - `docker logs sso-b-openig-1 2>&1 | grep -E 'Loaded the route|ERROR' | tail -20`
- Historical Stack B log tail still contains earlier runtime errors, including the callback failure that motivated this fix.
- Fresh Stack B log from current start time `2026-03-20T04:06:30.042401797Z` showed the expected route load lines and no new `ERROR` entries:
  - `00-redmine-logout`
  - `02-redmine`
  - `00-jellyfin-logout`
  - `01-jellyfin`
  - `00-backchannel-logout-app3`
  - `00-backchannel-logout-app4`

> [!tip]
> If the callback error appears again, inspect the live cookie contents and the per-app `tokenRefKey` value first. The next likely failure mode would be a route missing the matching `args.tokenRefKey`, not the shared Groovy filter itself.

## Files Changed

- `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-a/openig_home/config/routes/00-wp-logout.json`
- `stack-a/openig_home/config/routes/01-wordpress.json`
- `stack-a/openig_home/config/routes/02-app2.json`
- `stack-b/openig_home/config/routes/00-jellyfin-logout.json`
- `stack-b/openig_home/config/routes/00-redmine-logout.json`
- `stack-b/openig_home/config/routes/01-jellyfin.json`
- `stack-b/openig_home/config/routes/02-redmine.json`
- `stack-c/openig_home/config/routes/00-grafana-logout.json`
- `stack-c/openig_home/config/routes/00-phpmyadmin-logout.json`
- `stack-c/openig_home/config/routes/10-grafana.json`
- `stack-c/openig_home/config/routes/11-phpmyadmin.json`
