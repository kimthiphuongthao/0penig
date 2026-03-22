---
title: Docs stale sync after main merge and INFRA-C-BOB
tags:
  - docs
  - openig
  - vault
  - stack-c
date: 2026-03-23
status: fixed
---

# Docs stale sync after main merge and INFRA-C-BOB

Related: [[OpenIG]] [[Vault]] [[Keycloak]] [[Stack C]]

## Root cause

Living docs still described `fix/jwtsession-production-pattern` as the active branch and still tracked Stack C phpMyAdmin `bob` as an open infra gap even though:

- the branch had already been merged into `main`
- `INFRA-C-BOB` was already completed in commit `306f4b4`
- Vault AppRole `secret_id` expiry after long lab shutdowns had been observed but was not recorded in `.claude/rules/gotchas.md`

## Fix

Updated `CLAUDE.md`:

- changed the JwtSession branch note to reflect `main` as the active baseline
- checked off the merge task
- checked off the MariaDB `bob` provisioning/doc task

Updated `.claude/rules/gotchas.md`:

- removed the stale Stack C `bob` infra-gap wording from Known gaps
- added the Vault AppRole `secret_id` stale-after-72h gotcha with the regenerate command pattern
- replaced the stale table row that said phpMyAdmin `bob` login could not work with the current drift-based explanation

Updated external memory:

- changed active branch to `main` with merge note
- changed `SEC-COOKIE-STRIP` status to committed `3f0f731`
- moved the branch-merge task out of pending
- added the dirty-state warning for `stack-a/vault/file/openig-secret-id` and `stack-b/vault/file/openig-secret-id`

> [!success]
> Repo docs were synced and pushed in commit `d01bc9a` on `main`.

## Decision rationale

Only living docs were updated. Historical session notes that still mention the merge as a pending task were left untouched because they describe past session state rather than current source-of-truth status.

> [!tip]
> Treat `stack-a/vault/file/openig-secret-id` and `stack-b/vault/file/openig-secret-id` as runtime artifacts. Restore them with `git checkout -- ...` before any repo commit.

## Current state

- `CLAUDE.md` now matches the current branch baseline.
- `.claude/rules/gotchas.md` no longer reports `bob` as an open Stack C blocker.
- External memory now records `SEC-COOKIE-STRIP` as committed and documents the `openig-secret-id` dirty-state rule.

> [!warning]
> Vault AppRole `secret_id` still expires after `72h`; a long lab shutdown can still produce Vault `403` responses until the file is regenerated and OpenIG is restarted.

## Files changed

- `CLAUDE.md`
- `.claude/rules/gotchas.md`
- `/Users/duykim/.claude/projects/-Volumes-OS-claude-openig-sso-lab/memory/MEMORY.md`
