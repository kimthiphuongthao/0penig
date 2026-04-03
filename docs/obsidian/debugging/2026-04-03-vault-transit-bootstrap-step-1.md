---
title: Vault Transit Bootstrap Step 1
tags:
  - debugging
  - vault
  - openig
  - transit
date: 2026-04-03
status: completed
---

# Vault Transit Bootstrap Step 1

Related: [[Vault]] [[OpenIG]] [[Keycloak]] [[Stack C]]

## Context

- Task: implement `.omc/plans/vault-transit-implementation.md` Step 1 for `shared/vault/init/vault-bootstrap.sh`.
- Scope constraint: modify only Vault bootstrap logic, not target application or [[Keycloak]] config.
- Requested behavior:
  - enable the Transit secrets engine idempotently
  - create per-app Transit keys `app1-key` through `app6-key`
  - grant each `openig-appN-policy` read access to its KV path and update access to its Transit encrypt/decrypt paths
  - grant the `vault-admin` policy Transit mount/key management capabilities

> [!warning] Re-run requirement
> The new `enable_transit()` call now runs before `write_policies()`, so any admin token used on re-runs must already be bound to a `vault-admin` policy that includes `sys/mounts/transit`, `sys/mounts/transit/tune`, and `transit/*`.

## What Changed

- Added `enable_transit()` after `ensure_approle_auth()` to enable `transit/` and create six AES-256-GCM96 keys with suppressed duplicate-resource errors.
- Rewrote all six `openig-appN-policy` heredocs into multi-line HCL and added per-app Transit `encrypt` and `decrypt` paths while preserving the existing KV read path.
- Extended the `vault-admin` policy heredoc with Transit mount and key-management capabilities.
- Inserted `enable_transit` in the main bootstrap flow between `ensure_approle_auth` and `write_policies`.

> [!success] Verified statically
> `enable_transit()` is defined at lines 86-92, all six app policies include both KV and Transit paths at lines 94-166, `vault-admin` includes the requested Transit paths at lines 243-258, and the main flow calls `enable_transit` at line 296 before `write_policies` at line 297.

## Decisions

- Kept the policy heredocs in expanded multi-line HCL, matching the requested format and improving future diffs.
- Used `2>/dev/null || true` for both Transit mount enablement and key creation so the function is safe to re-run.
- Did not execute Docker or Vault runtime commands during this task; validation was limited to static file inspection.

> [!tip]
> After the Transit-enabled Groovy filters land, validate one app end-to-end first, then compare Redis ciphertext prefixes and Vault audit logs before rolling the pattern to all six routes.

## Current State

- `shared/vault/init/vault-bootstrap.sh` contains the requested Step 1 Transit bootstrap support.
- No commit has been created.

## Next Steps

- Implement the Transit encrypt/decrypt helpers in the shared [[OpenIG]] Groovy layer.
- Pass per-route Transit key names so each app uses its own `appN-key`.
- Plan a rollout strategy for any existing plaintext Redis entries.

## Files Changed

- `shared/vault/init/vault-bootstrap.sh`
- `docs/obsidian/debugging/2026-04-03-vault-transit-bootstrap-step-1.md`
