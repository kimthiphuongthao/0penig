---
title: All Stacks TokenReferenceFilter Concurrent tokenRef reuse
tags:
  - debugging
  - openig
  - oauth2
  - redis
  - grafana
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-22
status: blocked
---

# All Stacks TokenReferenceFilter Concurrent tokenRef reuse

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Request: fix concurrent unauthenticated SPA requests generating competing token reference IDs in `TokenReferenceFilter.groovy` across all stacks.
- Affected flow: [[Stack C]] Grafana initial page load can trigger multiple parallel OAuth2 authorizations before the first callback completes.
- Constraint: gateway-only Groovy change; no target application changes.

> [!warning]
> The previous `.then()` block always created a fresh UUID for `newTokenRefId`. With multiple in-flight requests, each response wrote a different Redis key and session pointer, so the last response could overwrite the cookie-backed session state and cause earlier Keycloak callbacks to fail state validation.

## Root Cause

- `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`

All three copies unconditionally executed:

```groovy
String newTokenRefId = UUID.randomUUID().toString()
```

That behavior was unsafe once the earlier `tokenRefKey` isolation work made the per-endpoint session key stable. Concurrent requests for the same endpoint should reuse the current session token reference and update Redis in place, not allocate a new key for every response.

## Fix Applied

- Added `existingTokenRefId = session[tokenRefKey] as String` in the `.then()` offload block in all three stacks.
- Changed `newTokenRefId` assignment to reuse the existing non-blank session value before falling back to `UUID.randomUUID().toString()`.
- Left the surrounding Redis write and session cleanup logic unchanged.

> [!success]
> Exact replacement applied in all three files:
>
> ```groovy
> String existingTokenRefId = session[tokenRefKey] as String
> String newTokenRefId = (existingTokenRefId?.trim()) ? existingTokenRefId : UUID.randomUUID().toString()
> ```

## Verification

- Confirmed the git diff only changes the token reference assignment in each file.
- Captured updated line locations after the patch:
  - [[Stack A]] `TokenReferenceFilter.groovy`: lines 253-254
  - [[Stack B]] `TokenReferenceFilter.groovy`: lines 253-254
  - [[Stack C]] `TokenReferenceFilter.groovy`: lines 298-299

> [!warning]
> Runtime verification is currently blocked in this Codex sandbox. `docker restart sso-openig-1 sso-openig-2 sso-b-openig-1 sso-b-openig-2 stack-c-openig-c1-1 stack-c-openig-c2-1` failed with `permission denied while trying to connect to the Docker daemon socket at unix:///Users/duykim/.docker/run/docker.sock`.

- Because Docker access is blocked here, I could not complete:
  - container restarts
  - `docker logs stack-c-openig-c1-1 2>&1 | grep 'Loaded the route'`

## Files Changed

- `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
