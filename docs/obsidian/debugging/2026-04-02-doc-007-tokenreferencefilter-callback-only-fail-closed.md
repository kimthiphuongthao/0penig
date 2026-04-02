---
title: DOC-007 TokenReferenceFilter Callback-Only Fail-Closed
tags:
  - debugging
  - openig
  - oauth2
  - slo
  - redis
  - shared-stack
date: 2026-04-02
status: complete
---

# DOC-007 TokenReferenceFilter Callback-Only Fail-Closed

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack B]] [[Stack C]]

## Context

- Regression from DOC-007: `TokenReferenceFilter.groovy` started returning `502` when no oauth2 session keys were present during the response phase.
- In the shared runtime, app4 and app5 can legitimately hit `.then()` without oauth2 keys after SLO backchannel logout clears the session, because tokens were already offloaded to Redis on prior requests.
- Scope was gateway-only in `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`.

## Root Cause

- `shouldFailClosedForMissingOauth2Keys` still evaluated true on non-logout, non-backchannel requests whenever a `tokenRefId` existed.
- That made established sessions on normal app routes behave like incomplete callback flows.
- Result: app4 and app5 logged `CRITICAL` and returned `502` even though missing oauth2 keys were expected after backchannel cleanup.

> [!warning]
> Missing oauth2 keys in `.then()` is only a hard failure on the OAuth2 callback response path. On later application requests, it is compatible with successful Redis offload and SLO cleanup.

## Fix Applied

- Added `isCallbackPath = requestPath?.contains('/callback')`.
- Narrowed `shouldFailClosedForMissingOauth2Keys` to require `isCallbackPath`.
- Preserved the callback fail-closed behavior while restoring WARN + passthrough for all non-callback paths.

> [!success]
> The fail-closed guard now applies only where incomplete OAuth2 state is actually suspicious: callback handling.

## Verification

- Restarted `shared-openig-1` and `shared-openig-2`.
- Ran `docker logs shared-openig-1 2>&1 | grep -E 'CRITICAL|ERROR' | tail -5`.
- The requested tail still shows historical `OAuth2ClientFilter` errors and earlier `TokenReferenceFilterApp4/App5` `CRITICAL` lines.
- Because that command is not scoped to post-restart time, it does not prove fresh recurrence after the patch by itself.
- Verified post-restart log windows using container start times:
  - `shared-openig-1` since `2026-04-02T03:49:13.166859428Z`: no `CRITICAL` or `ERROR`
  - `shared-openig-2` since `2026-04-02T03:49:13.76665722Z`: no `CRITICAL` or `ERROR`

> [!tip]
> For clean confirmation, drive one fresh app4/app5 request after restart and inspect logs with a `--since` window tied to the container start time.

## Files Changed

- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
