---
title: TokenReferenceFilter Vault Transit step 3
tags:
  - openig
  - vault
  - redis
  - routes
  - token-reference
  - transit
  - sso
  - slo
date: 2026-04-03
status: completed
---

# TokenReferenceFilter Vault Transit step 3

Related: [[OpenIG]] [[Vault]] [[Redis]] [[Keycloak]]

## Context

Task: implement `VAULT-TRANSIT-001` Step 3 by wiring the new Vault Transit args into every route that uses `TokenReferenceFilter.groovy`, then update the shared gateway pattern document to describe the Transit-encrypted Redis contract.

Scope:

- add `transitKeyName`, `appRoleName`, `vaultRoleIdFile`, and `vaultSecretIdFile` to all auth and logout routes that use `TokenReferenceFilter.groovy`
- leave `VaultCredentialFilter.groovy`, logout handlers, and other filters unchanged
- update `docs/deliverables/standard-gateway-pattern.md` with the Transit security control, required route args, onboarding checklist items, template description, and hardening note

## What Done

- Updated 11 route JSON files so each `TokenReferenceFilter.groovy` `args` block now includes the four Vault Transit bindings after `redisKeyPrefix`.
- Corrected `10-grafana.json` so the new args stay only on `TokenReferenceFilterApp5` and do not leak into `SpaBlacklistGuardApp5`.
- Added the Transit encryption row to the Security Controls table in `docs/deliverables/standard-gateway-pattern.md`.
- Added the dual-format Redis payload description: `vault:v1:` ciphertext decrypts through Vault Transit, legacy plaintext remains readable during rollout, encrypt failure stays fail-closed, and SLO-path decrypt failure warns and proceeds.
- Added the mandatory `TokenReferenceFilter` route-arg requirement for `transitKeyName`, `appRoleName`, `vaultRoleIdFile`, and `vaultSecretIdFile`.
- Added onboarding checklist items for Transit key provisioning, AppRole policy updates, and wiring the four args on both auth and logout routes.
- Updated the `TokenReferenceFilter.groovy` template description and appended the `VAULT-TRANSIT-001` hardening note.

> [!success]
> All 11 `TokenReferenceFilter` route configs now pass the Vault Transit bindings required by Step 2, and the main gateway pattern document reflects the new Redis encryption contract.

> [!warning]
> This step only wires route arguments and documentation. Vault still needs matching `transit/keys/<app>-key` keys and per-app encrypt/decrypt policy paths at runtime.

> [!tip]
> Keep Transit bindings route-local and app-scoped so Redis token payload access remains isolated even inside the shared [[OpenIG]] runtime.

## Decisions

- Keep the new Vault Transit bindings on `TokenReferenceFilter.groovy` only; do not duplicate them in `VaultCredentialFilter.groovy` or unrelated filters.
- Mirror the same four Transit args on both auth and logout routes so restore, offload, and SLO/logout paths all use the same per-app Vault identity.
- Document dual-format Redis reads in the gateway pattern so rollout from plaintext to ciphertext is explicit for future operators.

## Current State

- Every route in the shared runtime that uses `TokenReferenceFilter.groovy` now supplies `transitKeyName`, `appRoleName`, `vaultRoleIdFile`, and `vaultSecretIdFile`.
- `docs/deliverables/standard-gateway-pattern.md` includes all six requested Transit additions.
- JSON parsing and filter-placement verification passed across all 11 routes after the edit.

## Next Steps

- Apply the matching Vault Transit policy updates so each AppRole can call `transit/encrypt/<key>` and `transit/decrypt/<key>`.
- Validate the shared runtime against mixed Redis data: existing plaintext token payloads and newly written `vault:v1:` ciphertext payloads.
- Continue the remaining `VAULT-TRANSIT-001` rollout steps in the implementation plan.

## Files Changed

- `shared/openig_home/config/routes/01-wordpress.json`
- `shared/openig_home/config/routes/02-app2.json`
- `shared/openig_home/config/routes/02-redmine.json`
- `shared/openig_home/config/routes/01-jellyfin.json`
- `shared/openig_home/config/routes/10-grafana.json`
- `shared/openig_home/config/routes/11-phpmyadmin.json`
- `shared/openig_home/config/routes/00-wp-logout.json`
- `shared/openig_home/config/routes/00-redmine-logout.json`
- `shared/openig_home/config/routes/00-jellyfin-logout.json`
- `shared/openig_home/config/routes/00-grafana-logout.json`
- `shared/openig_home/config/routes/00-phpmyadmin-logout.json`
- `docs/deliverables/standard-gateway-pattern.md`
- `docs/obsidian/how-to/2026-04-03-token-reference-filter-vault-transit-step-3.md`
