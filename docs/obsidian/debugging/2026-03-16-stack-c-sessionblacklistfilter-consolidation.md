---
title: Stack C SessionBlacklistFilter Consolidation
tags:
  - openig
  - stack-c
  - debugging
  - session-blacklist
date: 2026-03-16
status: done
---

# Stack C SessionBlacklistFilter Consolidation

Related: [[OpenIG]], [[Keycloak]], [[Stack C]], [[Vault]]

## Context

Stack C's shared `SessionBlacklistFilter.groovy` used the wrong OpenIG 6 binding pattern by reading `args as Map`, and it also hardcoded fallback behavior for App5 vs App6 inside the script.

The target state was the same parameterized pattern already used in Stack A, where each route passes `clientEndpoint`, `sessionCacheKey`, `canonicalOrigin`, and `canonicalOriginEnvVar` as top-level Groovy bindings.

## What Changed

- Replaced `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy` with the Stack A canonical template.
- Removed Stack C-specific branch logic that guessed App5/App6 behavior from hostnames and fallback cache keys.
- Updated `10-grafana.json` to pass:
  - `clientEndpoint=/openid/app5`
  - `sessionCacheKey=oidc_sid_app5`
  - `canonicalOrigin=http://grafana-c.sso.local:18080`
  - `canonicalOriginEnvVar=CANONICAL_ORIGIN_APP5`
- Updated `11-phpmyadmin.json` to pass:
  - `clientEndpoint=/openid/app6`
  - `sessionCacheKey=oidc_sid_app6`
  - `canonicalOrigin=http://phpmyadmin-c.sso.local:18080`
  - `canonicalOriginEnvVar=CANONICAL_ORIGIN_APP6`

## Validation

> [!success]
> Verified Stack C runtime config supplies `REDIS_HOST=redis-c` for both `openig-c1` and `openig-c2` in `stack-c/docker-compose.yml`.

> [!tip]
> The copied Stack A template still contains Stack A literal fallbacks such as `redis-a` and `http://openiga.sso.local:80`, but Stack C runtime environment variables override those values, so effective behavior remains correct.

> [!warning]
> Docker restart and post-restart log inspection could not be executed from the Codex sandbox because access to the local Docker socket was denied.

## Current State

- Stack C now uses one parameterized `SessionBlacklistFilter.groovy` shared by Grafana and phpMyAdmin.
- Route-specific redirect and session key behavior is driven through route `args`, not script-side heuristics.
- Manual runtime verification is still required outside the sandbox:
  - `docker restart stack-c-openig-c1-1 stack-c-openig-c2-1`
  - wait 10 seconds
  - `docker logs stack-c-openig-c1-1 2>&1 | grep -E 'Loaded the route|ERROR'`

## Files Changed

- `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `stack-c/openig_home/config/routes/10-grafana.json`
- `stack-c/openig_home/config/routes/11-phpmyadmin.json`
