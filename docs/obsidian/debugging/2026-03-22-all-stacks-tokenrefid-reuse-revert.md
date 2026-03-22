---
title: All stacks TokenReferenceFilter tokenRefId reuse revert
tags:
  - debugging
  - openig
  - token-reference
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-22
status: done
---

# TokenReferenceFilter tokenRefId reuse revert

## Context

Reverted the same-day `tokenRefId` reuse change in [[OpenIG]] `TokenReferenceFilter.groovy` across [[Stack A]], [[Stack B]], and [[Stack C]].

## What Changed

- Removed the temporary `existingTokenRefId` read from session state.
- Restored unconditional token reference generation with `UUID.randomUUID().toString()`.
- Left all surrounding Redis/session handling intact.

> [!success] Reverted
> `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
> `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
> `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`

## Verification

- [[Stack A]] now assigns `String newTokenRefId = UUID.randomUUID().toString()` at line 253.
- [[Stack B]] now assigns `String newTokenRefId = UUID.randomUUID().toString()` at line 253.
- [[Stack C]] now assigns `String newTokenRefId = UUID.randomUUID().toString()` at line 298.
- [[Stack C]] still contains `String configuredSessionCacheKey = binding.hasVariable('sessionCacheKey') ? (sessionCacheKey as String)?.trim() : null` at line 19.

> [!warning] Scope
> This note documents a pure revert only. No route JSON, nginx, [[Vault]], or target application files were changed as part of this task.

## Current State

All three stacks are back to generating a fresh token reference ID for each write path in `TokenReferenceFilter.groovy`.

## Next Steps

- If the earlier reuse fix needs to return, rework it with explicit concurrency/session safety analysis before reapplying.
