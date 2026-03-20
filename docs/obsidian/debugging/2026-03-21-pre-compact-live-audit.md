---
title: 2026-03-21 Pre-Compact Live Audit
tags:
  - audit
  - openig
  - vault
  - git
  - runtime
date: 2026-03-21
status: complete
---

# 2026-03-21 Pre-Compact Live Audit

Context: pre-compact audit of live repo/runtime state for [[OpenIG]], [[Vault]], [[Keycloak]], [[Stack A]], [[Stack B]], and [[Stack C]].

## Findings

> [!success] Live runtime baseline
> `docker ps -a` showed all stack containers up. All containers with health checks reported healthy. All three Vault instances returned `sealed=false`.

> [!warning] Git and memory drift
> Live branch is `fix/jwtsession-production-pattern`, not `fix/phpmyadmin-slo-regression`. `fix/phpmyadmin-slo-regression` and `fix/jwtsession-production-pattern` now point to the same head commit `62ff2b4`, so the merge step recorded in `MEMORY.md` is obsolete.

> [!warning] Dirty worktree
> Dirty state was limited to runtime Vault AppRole files and new Obsidian notes. The AppRole files must not be committed because they contain live credentials regenerated during runtime.

> [!success] Audit verdict
> Full pre-compact audit passed: all containers were up, all Vaults were unsealed, and no active errors were blocking the merge. `bob` was also provisioned in Stack C MariaDB at runtime, so `INFRA-C-BOB` is now a persistence/documentation follow-up rather than an active runtime blocker.

## Evidence Summary

- Branch heads: `main=4ab4865`, `fix/jwtsession-production-pattern=62ff2b4`, `fix/phpmyadmin-slo-regression=62ff2b4`
- Branch relationship: both fix branches are 2 commits ahead of `main`; no divergence between the two fix branches
- Stack C runtime follow-up: MariaDB user `bob` was created on `2026-03-21`; Vault secret alignment was already in place
- Vault AppRole files:
  - Stack A: `stack-a/vault/file/openig-role-id`, `stack-a/vault/file/openig-secret-id`
  - Stack B: `stack-b/vault/file/openig-role-id`, `stack-b/vault/file/openig-secret-id`
  - Stack C: `stack-c/vault/init/role_id`, `stack-c/vault/init/secret_id`

## Next Step

> [!tip] True next action
> For branch workflow: merge `fix/jwtsession-production-pattern` into `main`.
> For backlog workflow: persist Stack C `bob` provisioning in `vault-bootstrap.sh`, then update the remaining hardening/docs items tracked in `master-backlog.md`.
