---
title: DOC-007 TokenReferenceFilter Silent Fail-Through
tags:
  - debugging
  - openig
  - redis
  - jwt-session
  - oauth2
  - shared-infra
date: 2026-04-02
status: complete
---

# DOC-007 TokenReferenceFilter Silent Fail-Through

Related: [[OpenIG]] [[Keycloak]] [[Vault]]

## Context

- Request: fix DOC-007 in the shared OpenIG gateway only.
- Scope: `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`.
- Constraint: do not modify target application code or target app configuration.
- Affected runtime: `shared-openig-1` and `shared-openig-2`.

> [!warning]
> The prior response hook logged a warning and returned the upstream response when oauth2 session key discovery came back empty. On authenticated flows that meant `oauth2:*` entries stayed in the `JwtSession` cookie instead of being offloaded to Redis, which risks exceeding the 4 KB cookie limit.

## Root Cause

- In the `.then()` response hook, `collectOauth2SessionEntries()` could return an empty map after authentication if oauth2 key discovery failed.
- That empty result followed the warning path and returned the original response unchanged.
- Because the script never fail-closed in that branch, the request could continue with oauth2 session state still embedded in the cookie.
- Logout traffic should not use the fail-closed branch because logout handlers intentionally clear state.

## Fix Applied

- Added request-path classification before `next.handle(...)`:
  - OAuth callback detection from `clientEndpoint + '/callback'`
  - logout detection from request path or query
  - backchannel logout detection from request path
- Added `shouldFailClosedForMissingOauth2Keys` so the empty-result branch only fails closed on authenticated flows.
- Changed the authenticated empty-result branch to:
  - log `ERROR [TokenReferenceFilter] CRITICAL: No oauth2 session keys found after authentication - cookie overflow risk`
  - return `502 Bad Gateway`
- Left the existing warning-and-continue behavior in place for non-authenticated flows such as logout/backchannel traffic.

> [!success]
> The change is gateway-side only and stays within the allowed edit surface: one Groovy script under `shared/openig_home/scripts/groovy/`.

## Verification

- Restarted:
  - `shared-openig-1`
  - `shared-openig-2`
- Waited 8 seconds after restart.
- Ran:
  - `docker logs shared-openig-1 2>&1 | grep -E 'Loaded the route|ERROR' | tail -20`
  - `docker logs shared-openig-2 2>&1 | grep -E 'Loaded the route|ERROR' | tail -20`
- Result:
  - both containers reloaded routes successfully
  - the requested log tails showed route load lines and no `ERROR` entries
  - no second patch/restart cycle was required

> [!tip]
> If the new `CRITICAL` error appears in production traffic, treat it as evidence that oauth2 session key discovery failed after authentication and inspect the session key naming for that route before investigating cookie settings.

## Files Changed

- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
