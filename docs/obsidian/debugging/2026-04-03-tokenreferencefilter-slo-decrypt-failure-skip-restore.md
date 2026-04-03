---
title: TokenReferenceFilter SLO Decrypt Failure Skip Restore
tags:
  - debugging
  - openig
  - vault
  - redis
  - logout
date: 2026-04-03
status: done
---

# TokenReferenceFilter SLO Decrypt Failure Skip Restore

Related: [[OpenIG]] [[Vault]] [[Redis]] [[2026-04-03-vault-transit-implementation-review]]

## Context

- File changed: `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- Scope: shared infra only
- Guardrail source: `.omc/plans/vault-transit-implementation.md`
- Required behavior: on SLO decrypt failure, log a warning and continue logout without restoring OAuth2 session entries

> [!warning] Root cause
> The logout branch caught Transit decrypt errors but assigned the original Redis payload back into `decryptedPayload`. For ciphertext values (`vault:v1:...`), the subsequent `JsonSlurper.parseText(...)` call failed and the outer catch returned `502 BAD_GATEWAY`, which blocked logout.

## What Changed

- Added `skipSessionRestore` in the restore path.
- On `isLogoutRequest` decrypt failure, the code now logs the existing warning and sets `skipSessionRestore = true`.
- Wrapped the restore-and-log block in `if (!skipSessionRestore)` so `JsonSlurper.parseText(...)` is not called after SLO decrypt failure.

> [!success] New behavior
> Logout requests no longer attempt to parse ciphertext as JSON after a Transit decrypt failure. The filter skips session restore and falls through to the existing `next.handle(context, request)` path.

## Decision

- Keep the non-SLO path fail-closed.
- Do not change Redis lookup, decrypt helper behavior, response-phase logic, or any other route behavior.
- Use a narrow guard around the restore block instead of broader control-flow changes.

## Current State

- Static patch applied.
- Normal request decrypt path still throws into the outer catch and remains fail-closed.
- SLO decrypt failure now bypasses restore/parsing and should no longer block logout.

> [!tip] Verification gap
> Runtime validation was not performed in this session because Docker/container access is unavailable in the current mode, and no restart was requested or permitted.

## Next Steps

- When Docker access is available, exercise an SLO path with an invalid or undecryptable `vault:v1:...` Redis payload and confirm logout still proceeds.
- Verify the normal non-SLO path still returns `BAD_GATEWAY` on decrypt failure.

## Files Changed

- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `docs/obsidian/debugging/2026-04-03-tokenreferencefilter-slo-decrypt-failure-skip-restore.md`
