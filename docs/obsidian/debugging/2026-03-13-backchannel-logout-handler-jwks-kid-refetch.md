---
title: Backchannel Logout Handler JWKS kid Refetch Fix
tags:
  - debugging
  - openig
  - keycloak
  - jwks
  - backchannel-logout
date: 2026-03-13
status: done
---

# Backchannel Logout Handler JWKS kid Refetch Fix

## Context

Applied the remaining JWKS cache fixes in the three [[OpenIG]] backchannel logout handlers after the earlier cache rollout left one rotation gap open.

Affected files:

- `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`

Related systems: [[OpenIG]], [[Keycloak]], [[Stack A]], [[Stack B]], [[Stack C]]

## Root Cause

The handlers were caching JWKS for a fixed TTL and immediately returning `400` if `getPublicKeyFromJwks` could not find the incoming JWT `kid`.

That meant [[Keycloak]] signing key rotation could break backchannel logout during the cache window:

- cached JWKS still contained only old keys
- token header referenced a new `kid`
- handler rejected the request before attempting a fresh JWKS fetch

Stack C also had unsafely published shared cache fields because `JWKS_CACHE` and `JWKS_CACHE_EXPIRES_AT` were static but not `volatile`.

> [!warning]
> TTL-only JWKS invalidation is not enough for rotating signing keys. A `kid` miss needs one forced refresh before the request is rejected.

## What Changed

### Cross-stack retry on `kid` miss

In all three handlers, the main validation path now:

1. tries `getPublicKeyFromJwks(jwks, kid)`
2. invalidates the in-memory cache on miss
3. refetches JWKS from `KEYCLOAK_JWKS_URI`
4. retries `getPublicKeyFromJwks`
5. returns `400` only if the second lookup still fails

Stack-specific cache fields were preserved:

- Stack A/B use `cachedJwks` and `jwksCacheExpiry` in seconds
- Stack C uses `JWKS_CACHE` and `JWKS_CACHE_EXPIRES_AT` in millis

### Stack C volatile fix

Added `volatile` to:

- `JWKS_CACHE`
- `JWKS_CACHE_EXPIRES_AT`

> [!success]
> Stack C now matches stacks A/B for basic cache visibility semantics while keeping its existing millis-based expiry logic.

## Decisions

- Kept the current per-stack cache variable names unchanged.
- Kept existing time units unchanged: seconds in stacks A/B, milliseconds in stack C.
- Limited the patch to the requested `kid`-miss retry and stack C field visibility fix only.

## Current State

- Backchannel logout will now recover from a rotated signing key if the first lookup hits stale cached JWKS.
- If the forced JWKS refresh fails, the handler returns `500`.
- If the refreshed JWKS still does not contain the `kid`, the handler returns `400`.

## Files Changed

- `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `docs/obsidian/debugging/2026-03-13-backchannel-logout-handler-jwks-kid-refetch.md`
