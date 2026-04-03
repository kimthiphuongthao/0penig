---
title: Vault Transit Section 3.4 Doc Fix
tags:
  - debugging
  - vault
  - openig
  - redis
  - docs
date: 2026-04-03
status: completed
---

# Vault Transit Section 3.4 Doc Fix

Related: [[OpenIG]] [[Vault]] [[Redis]] [[2026-04-03-secret-001-vault-transit-evaluation]]

## Context

- Task: correct section 3.4 in `docs/audit/2026-04-02-vault-transit-encryption-evaluation.md`.
- Inputs reviewed:
  - `docs/audit/2026-04-02-vault-transit-encryption-evaluation.md`
  - `shared/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
  - `docs/obsidian/debugging/2026-04-03-secret-001-vault-transit-evaluation.md`
- Constraint: do not modify sections 1, 2, 4, 5, 6, 7, 8, or 9.

> [!warning] Root Cause
> The original section 3.4 sample was directionally correct but not compatible with the actual [[OpenIG]] Groovy patterns in this repo. It used `globals.get('vaultToken')`, hardcoded `app1-key`, omitted timeout handling, and did not describe the dual-format rollout read path.

## What Changed

- Added a warning callout at the top of section 3.4 stating the snippet is a reference template only.
- Kept the section 3.4 `Current Code` block unchanged.
- Replaced the section 3.4 `Proposed Code` block with a template that aligns to the repo pattern:
  - `globals.compute('vault_token_' + configuredAppRoleName)` for Vault token caching
  - per-route `transitKeyName`
  - AppRole login from role/secret ID files
  - `connectTimeout` and `readTimeout`
  - `403` cache eviction and single retry
  - fail-closed behavior for encrypt/decrypt
  - explicit UTF-8 base64 encode/decode
- Added rollout and bootstrap notes below the code block.

> [!success] Confirmed Result
> Section 3.4 now reflects the same Vault token cache model used elsewhere in the repo and documents the rollout compatibility requirement for legacy plaintext [[Redis]] entries.

## Decisions

- Keep [[Vault]] Transit as the production recommendation.
- Treat section 3.4 as a reference template, not a drop-in patch.
- Document dual-format read explicitly because existing [[Redis]] values are plaintext today.

> [!tip]
> If this pattern is later implemented in `TokenReferenceFilter.groovy`, keep the final Groovy code aligned with the exact helper and error-handling conventions already used in `VaultCredentialFilter.groovy`.

## Current State

- Audit document fixed and committed.
- Obsidian note written for project continuity.

## Files Changed

- `docs/audit/2026-04-02-vault-transit-encryption-evaluation.md`
- `docs/obsidian/debugging/2026-04-03-vault-transit-section-3-4-doc-fix.md`
