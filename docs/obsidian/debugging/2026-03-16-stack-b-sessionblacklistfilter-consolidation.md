---
title: Stack B SessionBlacklistFilter Consolidation
tags:
  - openig
  - stack-b
  - debugging
  - session-blacklist
date: 2026-03-16
status: done
---

# Stack B SessionBlacklistFilter Consolidation

Related: [[OpenIG]], [[Keycloak]], [[Stack B]]

## Context

Stack B had three `SessionBlacklistFilter` Groovy files:

- `SessionBlacklistFilter.groovy` was dead code.
- `SessionBlacklistFilterApp3.groovy` handled Redmine.
- `SessionBlacklistFilterApp4.groovy` handled Jellyfin.

The goal was to align Stack B with the parameterized [[OpenIG]] pattern already used in Stack A, where route `args` bind directly to top-level Groovy variables in OpenIG 6.

## What Changed

- Replaced Stack B's shared `SessionBlacklistFilter.groovy` with the Stack A canonical template.
- Deleted `SessionBlacklistFilterApp3.groovy`.
- Deleted `SessionBlacklistFilterApp4.groovy`.
- Updated `01-jellyfin.json` to call `SessionBlacklistFilter.groovy` with:
  - `clientEndpoint=/openid/app4`
  - `sessionCacheKey=oidc_sid_app4`
  - `canonicalOrigin=http://jellyfin-b.sso.local:9080`
  - `canonicalOriginEnvVar=CANONICAL_ORIGIN_APP4`
- Updated `02-redmine.json` to call `SessionBlacklistFilter.groovy` with:
  - `clientEndpoint=/openid/app3`
  - `sessionCacheKey=oidc_sid_app3`
  - `canonicalOrigin=http://redmine-b.sso.local:9080`
  - `canonicalOriginEnvVar=CANONICAL_ORIGIN_APP3`

## Validation

> [!success]
> Verified Stack B runtime config still supplies the correct environment values:
> `REDIS_HOST=redis-b` and `OPENIG_PUBLIC_URL=http://openigb.sso.local:9080` in `stack-b/docker-compose.yml`.

> [!tip]
> The Stack A template still contains Stack A fallback literals (`redis-a`, `http://openiga.sso.local:80`), but Stack B runtime env overrides them, so the copied template remains behaviorally correct without further code changes.

> [!warning]
> Docker restart and post-restart log inspection could not be executed from the Codex sandbox because access to the local Docker socket was denied.

## Current State

- Stack B routes now point to one parameterized `SessionBlacklistFilter.groovy`.
- Route-specific behavior is driven through `args`, not duplicated Groovy files.
- Manual runtime verification is still needed outside the sandbox:
  - `docker restart sso-b-openig-1 sso-b-openig-2`
  - wait 10 seconds
  - `docker logs sso-b-openig-1 2>&1 | grep -E 'Loaded the route|ERROR'`

## Files Changed

- `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp3.groovy`
- `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy`
- `stack-b/openig_home/config/routes/01-jellyfin.json`
- `stack-b/openig_home/config/routes/02-redmine.json`
