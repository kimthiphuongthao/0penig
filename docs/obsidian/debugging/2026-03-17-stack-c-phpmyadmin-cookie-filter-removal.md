---
title: Stack C phpMyAdmin Cookie Filter Removal
tags:
  - openig
  - stack-c
  - debugging
  - phpmyadmin
  - security-hardening
date: 2026-03-17
status: done
---

# Stack C phpMyAdmin Cookie Filter Removal

Related: [[OpenIG]], [[Keycloak]], [[Vault]], [[Stack C]]

## Context

Task executed directly from `.omc/plans/phase2-security-hardening.md` for Stack C only. No investigation was needed because `[L-5]` had already been confirmed.

The target was to remove the unused `PhpMyAdminCookieFilter.groovy` artifact from Stack C and confirm that no OpenIG route in Stack C still referenced it.

## What Changed

- Deleted `stack-c/openig_home/scripts/groovy/PhpMyAdminCookieFilter.groovy`.
- Verified no Stack C route JSON references `PhpMyAdminCookieFilter` under `stack-c/openig_home/config/routes/`.

> [!success]
> `tilth` search over `stack-c/openig_home/config/routes` returned `0 matches` for `PhpMyAdminCookieFilter`.

> [!tip]
> The requested shell verification `grep -r PhpMyAdminCookieFilter stack-c/openig_home/config/routes/` exited with code `1`, which is the expected result for no matches.

## Validation

- Confirmed the script no longer appears in `stack-c/openig_home/scripts/groovy/`.
- Remaining Stack C Groovy scripts are:
  - `BackchannelLogoutHandler.groovy`
  - `SessionBlacklistFilter.groovy`
  - `SloHandler.groovy`
  - `VaultCredentialFilter.groovy`

> [!warning]
> Docker restart and log inspection could not be executed from the Codex sandbox. Access to the local Docker socket at `/Users/duykim/.docker/run/docker.sock` was denied with `connect: operation not permitted`.

## Current State

- The unused phpMyAdmin cookie filter artifact has been removed from Stack C.
- Stack C route JSON files do not reference the deleted script.
- Runtime restart and post-restart route-load verification still need to be run outside the sandbox:
  - `docker restart stack-c-openig-c1-1 stack-c-openig-c2-1`
  - `docker logs stack-c-openig-c1-1 2>&1 | grep 'Loaded the route'`

## Files Changed

- `stack-c/openig_home/scripts/groovy/PhpMyAdminCookieFilter.groovy` (deleted)
- `docs/obsidian/debugging/2026-03-17-stack-c-phpmyadmin-cookie-filter-removal.md`
