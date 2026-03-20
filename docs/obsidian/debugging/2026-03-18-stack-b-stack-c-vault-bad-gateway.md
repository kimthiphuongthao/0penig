---
title: Stack B and Stack C Vault Bad Gateway Mapping
tags:
  - debugging
  - stack-b
  - stack-c
  - openig
  - vault
  - security-hardening
date: 2026-03-18
status: complete
---

# Stack B and Stack C Vault Bad Gateway Mapping

Related: [[OpenIG]] [[Vault]] [[Stack B]] [[Stack C]]

> [!tip]
> 2026-03-20 follow-up: Stack B later consolidated `VaultCredentialFilterRedmine.groovy` and `VaultCredentialFilterJellyfin.groovy` into the single parameterized `VaultCredentialFilter.groovy` (`e22a855`). This note remains the historical record of the pre-consolidation `502` mapping change.

## Context

- Requested scope: Stack B and Stack C only.
- Input status: `[M-9/Code-M6]` already confirmed in `.omc/plans/phase2-security-hardening.md`, so implementation proceeded directly with no extra investigation.
- Goal: return `502 Bad Gateway` for Vault upstream failures instead of `500 Internal Server Error` in the affected credential filters.

## Root Cause

- Stack B Redmine and Jellyfin Vault credential filters still mapped Vault fetch failures to `Status.INTERNAL_SERVER_ERROR`.
- Stack C phpMyAdmin Vault credential filter also mapped the Vault fetch failure path to `Status.INTERNAL_SERVER_ERROR`.
- These paths represent upstream dependency failure in [[Vault]], so `Status.BAD_GATEWAY` is the correct OpenIG response classification.

> [!warning]
> The change was intentionally limited to response status constants. Existing error messages, exception flow, and credential parsing logic were left untouched.

## What Changed

- In `stack-b/openig_home/scripts/groovy/VaultCredentialFilterRedmine.groovy`, changed the expired-token response and the catch-all Vault error response from `Status.INTERNAL_SERVER_ERROR` to `Status.BAD_GATEWAY`.
- In `stack-b/openig_home/scripts/groovy/VaultCredentialFilterJellyfin.groovy`, changed the expired-token response and the catch-all Vault error response from `Status.INTERNAL_SERVER_ERROR` to `Status.BAD_GATEWAY`.
- In `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy`, changed the catch-all Vault error response from `Status.INTERNAL_SERVER_ERROR` to `Status.BAD_GATEWAY`.

> [!success]
> Stack A was intentionally left unchanged because its Vault credential filter already used `Status.BAD_GATEWAY`.

## Validation

- Ran `docker restart sso-b-openig-1 sso-b-openig-2 stack-c-openig-c1-1 stack-c-openig-c2-1`.
- Verified `docker logs sso-b-openig-1 2>&1 | grep 'Loaded the route'` returned the expected Stack B route registrations after restart.
- Verified `docker logs stack-c-openig-c1-1 2>&1 | grep 'Loaded the route'` returned the expected Stack C route registrations after restart.

> [!tip]
> For OpenIG filters that proxy or depend on external systems, prefer `502` when the handler is healthy but the upstream dependency fails.

## Current State

- Stack B Redmine and Jellyfin Vault filters now return `502` on Vault error paths.
- Stack C phpMyAdmin Vault filter now returns `502` on its Vault error path.
- The affected Stack B and Stack C OpenIG containers restarted successfully and reloaded routes.

## Files Changed

- `stack-b/openig_home/scripts/groovy/VaultCredentialFilterRedmine.groovy`
- `stack-b/openig_home/scripts/groovy/VaultCredentialFilterJellyfin.groovy`
- `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
- `docs/obsidian/debugging/2026-03-18-stack-b-stack-c-vault-bad-gateway.md`
