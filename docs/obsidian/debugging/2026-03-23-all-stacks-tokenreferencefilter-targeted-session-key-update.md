---
title: All stacks TokenReferenceFilter targeted session key update
tags:
  - debugging
  - all-stacks
  - openig
  - oauth2
  - jwt-session
  - slo
  - redmine
  - grafana
date: 2026-03-23
status: fixed
---

# All stacks TokenReferenceFilter targeted session key update

Related: [[OpenIG]] [[Keycloak]] [[Stack A]] [[Stack B]] [[Stack C]] [[Redmine]] [[Grafana]] [[Jellyfin]] [[phpMyAdmin]]

## Context

- Confirmed failure mode: after SLO, a later background response could serialize a stale shared `JwtSession` cookie and drop another app's pending `oauth2:*` authorization state.
- Impacted user-visible flows:
  - [[Stack B]] Redmine `SSO -> SLO -> SSO2`
  - [[Stack C]] Grafana `SSO -> SLO -> SSO2`
- Existing implementation detail:
  - `TokenReferenceFilter.groovy` offload response phase called `session.clear()` and then rebuilt the whole session from a filtered snapshot.
  - That pattern made the winning response rewrite the full cookie image instead of only updating this app's token-reference metadata.

> [!warning]
> With shared cookie-backed `JwtSession` names such as `IG_SSO_B` and `IG_SSO_C`, whole-session rewrites are unsafe when multiple app responses can complete out of order.

## What changed

- Updated `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- Updated `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- Updated `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`

New response-phase behavior:

1. Discover this app's `oauth2:*` keys with `discoverOauth2SessionKeys()`.
2. Remove only those keys with `session.remove(...)`.
3. Write only this app's `tokenRefKey`.
4. On [[Stack C]], also write only `sessionCacheKey` when a valid `sid` is extracted from the ID token.

Removed behavior:

- No `session.clear()`
- No snapshot-and-repopulate of unrelated session keys
- No rewrite of other apps' pending OAuth state, token references, or markers

> [!success]
> The targeted update keeps unrelated session keys untouched, which removes the specific cross-app pending-state loss caused by late background responses.

## Stack C note

- `cacheSidFromOauth2Session()` now returns the decoded `sid` without mutating the session directly.
- The session write for `configuredSessionCacheKey` now happens only inside the targeted update helper, alongside `tokenRefKey`.
- This reduces the number of concurrent writes and avoids carrying both app5 and app6 OAuth payloads through a full session rebuild path.

> [!tip]
> `discoverOauth2SessionKeys()` is already endpoint-scoped because it matches keys that both start with `oauth2:` and end with the current `configuredClientEndpoint`.

## Verification

- Restarted OpenIG containers:
  - `sso-b-openig-1`
  - `sso-b-openig-2`
  - `stack-c-openig-c1-1`
  - `stack-c-openig-c2-1`
  - `sso-openig-1`
  - `sso-openig-2`
- Checked `docker logs sso-b-openig-1 2>&1 | grep 'Loaded the route'`
- Confirmed Stack B route set loaded after restart:
  - `00-redmine-logout`
  - `02-redmine`
  - `00-jellyfin-logout`
  - `01-jellyfin`
  - `00-backchannel-logout-app3`
  - `00-backchannel-logout-app4`

## Current state

- Gateway fix applied across all three stacks.
- `session.clear()` is no longer present in any `TokenReferenceFilter.groovy`.
- Remaining validation to run manually in-browser:
  - [[Stack B]] Redmine `SSO -> SLO -> SSO2`
  - [[Stack C]] Grafana `SSO -> SLO -> SSO2`
  - Parallel background traffic case with Jellyfin and phpMyAdmin open

## Files changed

- `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
