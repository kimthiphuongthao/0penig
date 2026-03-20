---
title: Backchannel Logout Handler Second-Pass Review
tags:
  - review
  - debugging
  - openig
  - keycloak
  - jwks
date: 2026-03-13
status: in-progress
---

# Backchannel Logout Handler Second-Pass Review

## Context

Second-pass technical review of the backchannel logout handler after fixes for:

- realm name correction from `sso-lab` to `sso-realm`
- RSA JWK reconstruction using `RSAPublicKeySpec`
- Groovy empty-map handling in `events`
- JWKS caching

Reviewed files:

- `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`

Related systems: [[OpenIG]], [[Keycloak]], [[Stack A]], [[Stack B]], [[Stack C]]

## What Was Verified

- Realm fix is correct in all three stacks and matches `keycloak/realm-import/realm-export.json`.
- RSA fix is correct for Keycloak RSA JWKs: `n` and `e` are decoded as unsigned integers and passed to `RSAPublicKeySpec`.
- The `logoutEvent == null || !(logoutEvent instanceof Map)` check fixes the original Groovy truthiness bug for an empty `{}` logout event payload.
- Redis defaults remain stack-local:
  - stack A -> `redis-a`
  - stack B -> `redis-b`
  - stack C -> `redis-c`

> [!success] Confirmed
> The critical realm and RSA issues appear fixed across all three handlers.

## Remaining Problems

### JWKS cache rotation handling is still incomplete

All stacks cache JWKS for 10 minutes, but none of them invalidate and refetch when the incoming JWT `kid` is missing from the cached set.

Impact:

- after Keycloak rotates signing keys
- if a new `kid` is used before cache TTL expires
- logout validation returns `400` instead of refreshing JWKS and retrying once

This is a functional regression introduced by the cache layer.

> [!warning]
> TTL-only invalidation is not sufficient for key rotation. The handler should refetch JWKS once on `kid` miss before rejecting the token.

### Stack C cache fields are not published safely across threads

`JWKS_CACHE` and `JWKS_CACHE_EXPIRES_AT` are static fields, but unlike stacks A/B they are not `volatile`.

Impact:

- concurrent OpenIG requests may observe stale cache contents or stale expiry timestamps
- behavior becomes timing-dependent under load

### Stacks A/B still allow concurrent refresh races

Stacks A and B use `volatile`, which is better than stack C, but there is still no synchronization around refresh.

Impact:

- multiple threads can refresh JWKS at the same time
- redundant network fetches are possible
- publication is not atomic as a single cache entry object

This is mainly an efficiency/concurrency issue. The more serious functional issue remains the missing retry on `kid` miss.

## Cross-Stack Notes

- Stack C uses milliseconds (`600000L`) while stacks A/B use seconds (`600`).
- This is only a style difference because each stack compares values in the same unit system.
- Stack C audience validation handles both `String` and `List` forms of `aud` correctly for the current implementation.

## Import Check

- No leftover `X509EncodedKeySpec` import.
- No leftover unused `ByteArrayOutputStream` import; the class is used in `readRespLine` and comes from Groovy's default `java.io.*` imports.

## Current State

The patch set is not ready to approve because the JWKS cache change still has correctness gaps.

## Next Steps

1. Add a single refresh-and-retry path when cached JWKS does not contain the requested `kid`.
2. Make the cache publication consistent across stacks, preferably by storing JWKS plus expiry in one immutable cache object.
3. In stack C, mark shared cache state with safe publication semantics at minimum.
