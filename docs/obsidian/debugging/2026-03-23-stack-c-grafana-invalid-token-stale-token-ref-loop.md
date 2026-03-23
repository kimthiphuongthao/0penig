---
title: Stack C Grafana invalid_token stale token ref loop
tags:
  - openig
  - stack-c
  - debugging
  - grafana
  - oauth2
  - redis
date: 2026-03-23
status: done
---

# Stack C Grafana invalid_token stale token ref loop

Related: [[OpenIG]], [[Keycloak]], [[Vault]], [[Stack C]]

## Context

After Grafana SLO, Keycloak revoked the access token but the browser still sent `IG_SSO_APP5` with `token_ref_id_app5`.

`TokenReferenceFilter` restored revoked OAuth2 entries from Redis, `OidcFilter` received `invalid_token`, and the response path stored the same revoked entries back into Redis under a new UUID. That kept the browser in a permanent restore -> `invalid_token` -> re-store loop.

## What Changed

- Added a response-phase guard in `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`.
- Mirrored the same guard into `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy` because Stack C containers mount `stack-c/openig_home` directly.
- The new branch runs after the `hasRealTokens` check and before Redis offload:
  - detects `401` plus `WWW-Authenticate` `error=...`
  - logs the stale-token cleanup event
  - deletes the current Redis `token_ref:*` key
  - removes `tokenRefKey` and all OAuth2 session entries from session
  - returns the original `401` so the next browser navigation can start a fresh OAuth2 flow

## Route Verification

> [!success]
> `shared/openig_home/config/routes/10-grafana.json` already has `SpaBlacklistGuardApp5` first in the chain and its heap args include `sessionCacheKey: oidc_sid_app5` and `redisKeyPrefix: app5`.

> [!warning]
> `stack-c/openig_home/config/routes/10-grafana.json` is still an older stack-local route shape. It does not use the shared prefixed blacklist flow, so I did not force the shared `SpaBlacklistGuardApp5` args into the stack-local route during this bugfix.

## Validation

> [!success]
> Restarted `stack-c-openig-c1-1`, waited 5 seconds, then restarted `stack-c-openig-c2-1`.

> [!success]
> `docker logs stack-c-openig-c1-1 2>&1 | grep -E 'Loaded the route|ERROR' | tail -10` showed `10-grafana` loading cleanly and no `ERROR` lines in the returned tail.

## Current State

- Stack C runtime now clears stale token references instead of re-storing revoked OAuth2 tokens after `invalid_token`.
- The next browser request should be able to begin a clean OAuth2 flow instead of looping on stale Redis state.

## Files Changed

- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
