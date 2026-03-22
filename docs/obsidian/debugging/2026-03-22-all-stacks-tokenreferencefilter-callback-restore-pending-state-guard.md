---
title: All Stacks TokenReferenceFilter callback restore and pending state guard
tags:
  - debugging
  - openig
  - oauth2
  - redis
  - slo
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-22
status: completed
---

# All Stacks TokenReferenceFilter callback restore and pending state guard

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Request: commit the `TokenReferenceFilter.groovy` fix across all three stacks.
- Symptom: re-SSO after SLO could fail with `Authorization call-back failed because there is no authorization in progress`.
- Scope: gateway-only Groovy changes in the OpenIG filter copies for [[Stack A]], [[Stack B]], and [[Stack C]].

> [!warning]
> The restore path previously treated the OAuth2 callback like any other request. If the cookie-backed session only contained a pending OAuth2 state entry, the filter could restore stale Redis data before the callback completed, then offload that pending state back into Redis in `.then()`.

## Root Cause

- `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`

Two missing guards caused the failure mode:

1. No callback-path exclusion before Redis restore.
2. No validation that the discovered OAuth2 session entries contained real tokens before Redis offload.

That combination could replace the in-progress callback session with stale Redis content or persist a callback-pending state that only had authorization bookkeeping, not usable tokens.

## Fix Applied

- Added `isOauthCallback = request.uri.path?.contains(configuredClientEndpoint + '/callback')` before the Redis restore branch in all three stacks.
- Changed the restore condition to skip Redis restore entirely when the request is already on the OAuth2 callback path.
- Added `hasRealTokens` in the `.then()` block to require either `atr` or `access_token` before Redis offload.
- Returned early with a warning log when only pending OAuth2 state exists.

> [!success]
> Commit created: `91e9cb0` with message `fix(token-ref): skip Redis restore on callback + skip offload on pending state`.

## Follow-up Fix — Shared JwtSession Scoped Strip

- A second same-day bug remained after `91e9cb0`: `stripOauth2EntriesFromSession` called `session.clear()` and then re-added only non-`oauth2:*` entries.
- On stacks where multiple apps share one `JwtSession` cookie, that logic deleted every app's OAuth2 namespace from the shared session, not just the current app namespace.
- Failure mode: App 1 could finish response processing while App 2 still had pending OAuth2 callback state, which deleted App 2's nonce/state and made the callback fail with `Authorization call-back failed because there is no authorization in progress`.

> [!warning]
> The deeper root cause for both fixes is the same shared-cookie constraint: one OpenIG `JwtSession` cookie is intentionally shared by multiple apps inside a stack, so every `TokenReferenceFilter` mutation must be scoped to the current app namespace.

> [!success]
> Follow-up fix committed as `742dc32`: `discoverOauth2SessionKeys().toSet()` now defines `keysToRemove`, so the filter strips only the current app's discovered OAuth2 keys from the shared session.

## Verification

- Confirmed `91e9cb0` only changes the three `TokenReferenceFilter.groovy` copies to add the callback restore guard plus the pending-state / real-token guard.
- Confirmed `742dc32` keeps the same three-file scope and changes `stripOauth2EntriesFromSession` to remove only the current app namespace from the shared session.
- Follow-up validation after both fixes: tests PASS across all 3 stacks on 2026-03-22.

> [!tip]
> If this issue recurs, inspect callback requests first. Any Redis restore attempt on `.../callback` or offload attempt where the OAuth2 session only contains pending state is a regression signal.

## Files Changed

- `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
