---
title: TokenReferenceFilter Vault Transit step 2
tags:
  - openig
  - vault
  - redis
  - groovy
  - token-reference
  - transit
  - sso
  - slo
date: 2026-04-03
status: completed
---

# TokenReferenceFilter Vault Transit step 2

Related: [[OpenIG]] [[Vault]] [[Redis]]

## Context

Task: implement `.omc/plans/vault-transit-implementation.md` Step 2 in `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`.

Scope:

- add Vault Transit route bindings for `transitKeyName`, `appRoleName`, `vaultRoleIdFile`, and `vaultSecretIdFile`
- require `transitKeyName` inside the main `try` block before request handling continues
- add Vault AppRole token caching with `globals.compute()` using the same pattern as `VaultCredentialFilter.groovy`
- decrypt Redis payloads on restore only when the stored value uses the `vault:v1:` prefix
- allow logout and SLO restore to warn and continue when decrypt fails
- encrypt new Redis payloads before `SET` so write failures stay fail-closed through the existing `INTERNAL_SERVER_ERROR` path

## What Done

- Added `HttpURLConnection` and `URL` imports for Vault login and transit calls.
- Added the four new route args immediately after `tokenRefKey`.
- Added `readVaultResponseBody`, `getVaultTokenEntry`, `invokeVaultTransit`, `vaultTransitEncrypt`, and `vaultTransitDecryptIfNeeded` before the main `try`.
- Implemented Vault AppRole login with cached token entries in `globals`, plus 403 eviction and retry for transit operations.
- Updated the restore path to support dual-format reads: `vault:v1:` values are decrypted, legacy plaintext JSON passes through unchanged.
- Added the logout-path exception handling that logs a warning and continues with the original Redis payload if transit decrypt fails.
- Updated the store path so `oauth2Entries` are serialized to plaintext JSON, encrypted through Vault Transit, and only then written to Redis.

> [!success]
> `TokenReferenceFilter.groovy` now offloads OAuth2 session payloads to Redis as Vault Transit ciphertext while preserving legacy reads and logout survivability.

> [!tip]
> The dual-format restore path keeps migration simple because existing plaintext Redis entries remain readable until they are rewritten through the new encrypt-on-store path.

> [!warning]
> Non-logout restore still fails closed on transit decrypt errors because that path remains inside the existing outer restore exception handling.

## Decisions

- Keep the Vault helper closures local to `TokenReferenceFilter.groovy` to match the current script-based OpenIG pattern and avoid widening the change beyond Step 2.
- Reuse the AppRole token caching model from `VaultCredentialFilter.groovy` so Vault auth behavior stays consistent across OpenIG Groovy filters.
- Restrict the warn-and-proceed decrypt fallback to logout and SLO traffic only; normal restore traffic should still surface failures.

## Current State

- `TokenReferenceFilter.groovy` requires `transitKeyName` at runtime in addition to the existing Redis configuration.
- Redis writes now store Vault Transit ciphertext and Redis reads accept both ciphertext and legacy plaintext JSON.
- Logout restore can proceed even if decrypt fails, but normal restore and store still fail closed.

## Next Steps

- Wire `transitKeyName` into each route that uses `TokenReferenceFilter.groovy`.
- Validate the migration path with mixed Redis contents: preexisting plaintext entries and newly encrypted entries.
- Confirm Vault policy grants `transit/encrypt/<key>` and `transit/decrypt/<key>` for the OpenIG AppRole used by this filter.

## Files Changed

- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `docs/obsidian/how-to/2026-04-03-token-reference-filter-vault-transit-step-2.md`
