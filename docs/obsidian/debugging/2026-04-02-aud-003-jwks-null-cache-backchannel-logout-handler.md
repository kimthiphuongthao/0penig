---
title: AUD-003 JWKS null-cache fix in Backchannel Logout Handler
tags:
  - debugging
  - openig
  - keycloak
  - jwks
  - backchannel-logout
  - shared
date: 2026-04-02
status: done
---

# AUD-003 JWKS null-cache fix in Backchannel Logout Handler

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

Applied the gateway-side fix for AUD-003 in `shared/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`.

The handler cached JWKS in `globals` with a 3600-second TTL. When a JWKS fetch failed, the old code still wrote a cache entry with `keys: null`, which caused later backchannel logout JWT validations to fail until the TTL expired.

## Root cause

`loadJwksKeys()` always replaced `globals['jwks_cache']` with a fresh entry, even when `fetchJwksKeys()` returned `null`.

That created two bad paths:

- a transient JWKS fetch failure poisoned the cache for one hour
- the `kid`-miss path cleared `jwks_cache` before refetch, so stale usable keys were discarded during upstream fetch failures

> [!warning]
> Scope stayed inside the approved gateway layer only. No target application code, app config, database config, or [[Keycloak]] realm config was changed.

## What changed

- Added `JWKS_FETCH_FAILURE_BACKOFF_SECONDS = 60`.
- Added helpers to track `globals['jwks_fetch_failed_at']` and detect recent fetch failures.
- Reworked `loadJwksKeys(forceRefresh)` so it only writes `jwks_cache` when the fetched JWKS map is non-null and non-empty.
- Kept stale cached keys in place when a refresh fails.
- Short-circuited repeated refetch attempts for 60 seconds after a failure.
- Changed the `kid`-miss path to force a refresh without clearing `jwks_cache` first.
- Returned `500` on `kid` miss when refresh is still in failure backoff, so Keycloak can retry instead of getting a false `400`.

> [!tip]
> For JWKS-backed JWT validation, stale usable keys are safer than caching `null`. The cache should only advance on a verified non-empty key set.

## Verification

> [!success]
> Restarted `shared-openig-1` and `shared-openig-2` on 2026-04-02 and waited 8 seconds after restart completion.

> [!success]
> `docker logs --since 2026-04-02T03:08:28 shared-openig-1 2>&1 | grep -E 'Loaded the route|ERROR' | tail -20` showed route load entries only, with no post-restart `ERROR` lines.

> [!success]
> `docker logs --since 2026-04-02T03:08:29 shared-openig-2 2>&1 | grep -E 'Loaded the route|ERROR' | tail -20` showed route load entries only, with no post-restart `ERROR` lines.

The broader `docker logs ... | tail -120` output did include `OAuth2ClientFilter invalid_token` errors before the restart window. Those were live-traffic runtime errors in other OAuth2 flows, not Groovy parse/load failures from this change.

## Current state

`BackchannelLogoutHandler.groovy` now:

- avoids poisoning `jwks_cache` with `null`
- preserves stale cached JWKS when refresh fails
- backs off repeated JWKS fetches for 60 seconds after a failure
- keeps the forced `kid` refresh path from discarding the last usable cache entry

## Files changed

- `shared/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `docs/obsidian/debugging/2026-04-02-aud-003-jwks-null-cache-backchannel-logout-handler.md`
