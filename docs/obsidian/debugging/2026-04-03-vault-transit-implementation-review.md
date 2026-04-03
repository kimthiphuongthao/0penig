---
title: VAULT-TRANSIT-001 Implementation Review
tags:
  - debugging
  - vault
  - openig
  - redis
  - review
date: 2026-04-03
status: needs-fix
---

# VAULT-TRANSIT-001 Implementation Review

Related: [[OpenIG]] [[Vault]] [[Redis]] [[2026-04-03-secret-001-vault-transit-evaluation]]

## Context

- Task: verify the uncommitted implementation of `.omc/plans/vault-transit-implementation.md` Steps 1, 2, and 3 against the plan guardrails and acceptance criteria.
- Files reviewed:
  - `shared/vault/init/vault-bootstrap.sh`
  - `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
  - 11 route JSON files under `shared/openig_home/config/routes/`
  - `docs/deliverables/standard-gateway-pattern.md`
- Constraint: static review only. No Docker restart, route-load validation, Vault CLI validation, or live Redis checks were executed.

> [!warning] Blocking defect
> `TokenReferenceFilter.groovy` does not meet the SLO decrypt-failure requirement yet. On the logout path, the code logs a warning but then assigns ciphertext back into `decryptedPayload` and immediately calls `JsonSlurper.parseText(...)`. If the Redis value is `vault:v1:...` ciphertext and decrypt fails, logout still falls into the outer `catch` and returns `502` instead of proceeding.

## Review Result

> [!success] Step 1
> `shared/vault/init/vault-bootstrap.sh` matches the requested Transit bootstrap shape. `enable_transit()` is idempotent, creates `app1-key` through `app6-key`, all six AppRole policies keep their KV path and add Transit encrypt/decrypt paths, and the main flow calls `enable_transit` before `write_policies`.

> [!warning] Step 2
> `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy` matches most of the plan:
> - required Transit args are read at script entry
> - missing `transitKeyName` fails immediately
> - helper closures exist
> - Vault token caching uses `globals.compute('vault_token_' + appRoleName)`
> - Transit calls use `5000ms` connect/read timeouts and `403` cache eviction with one retry
> - store path encrypts before Redis write with no plaintext fallback
> - normal-request decrypt path is fail-closed
>
> The remaining bug is in the SLO exception path at lines 350-360.

> [!success] Step 3
> All 11 route files include `transitKeyName`, `appRoleName`, `vaultRoleIdFile`, and `vaultSecretIdFile`, and the values match the app mapping table. Logout routes reuse the same values as their auth routes. `docs/deliverables/standard-gateway-pattern.md` includes the Security Controls row, mechanism-agnostic Transit description, mandatory route args, onboarding checklist updates, and the updated `TokenReferenceFilter.groovy` template description.

## Root Cause

- Current SLO branch:
  - catches decrypt failure
  - logs warning
  - sets `decryptedPayload = redisPayload`
  - parses `decryptedPayload` as JSON
- For ciphertext entries, `redisPayload` is not JSON. The parse fails and the outer restore-path catch returns `BAD_GATEWAY`.
- Required behavior is different: on SLO decrypt failure, skip restore and continue with logout instead of trying to parse ciphertext.

## Current State

- Step 1: pass
- Step 2: needs fix
- Step 3: pass
- Overall status: not ready to run until the SLO decrypt-failure path is corrected

## Next Steps

- Update the SLO restore branch in `TokenReferenceFilter.groovy` so decrypt failure bypasses restore/parsing and still allows logout to proceed.
- Re-run the same static review criteria after the Groovy fix.
- After the static fix, run the plan validation sequence:
  - re-run `vault-bootstrap.sh`
  - restart OpenIG
  - verify route load
  - test Redis ciphertext and SLO behavior

## Files Changed

- `docs/obsidian/debugging/2026-04-03-vault-transit-implementation-review.md`
