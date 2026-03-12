---
title: Vault AppRole Design
tags:
  - vault
  - approle
  - security
  - decision
date: 2026-03-12
status: decision
---

# Vault AppRole Design

Related: [[Vault]] [[OpenIG]] [[Stack A]] [[Stack B]] [[Stack C]]

## Decision Summary

- Use Vault file storage (not dev mode) because dev mode loses data on restart.
- Use one AppRole per stack.
- Write `role_id` and `secret_id` to `vault/file/` for OpenIG startup reads.

## Bootstrap Pattern

Standard flow:
1. Check Vault status (`sealed`/`unsealed`) and reachability.
2. Unseal when required.
3. Enable `kv-v2` at `secret/`.
4. Write policy.
5. Create AppRole.
6. Export `role_id`/`secret_id` to file mount.
7. Seed application secrets.
8. Mark completion.

## Idempotency

- Flag file: `/vault/data/.bootstrap-done`
- Behavior: prevents destructive re-run after initial bootstrap.

## Phase 3 Hardening Gaps

From `docs/vault-hardening-gaps.md`:
- Root token is not revoked.
- Audit logging is not enabled.
- TLS is disabled.
- `secret_id_ttl` is not set.
- CIDR restriction is not configured.

> [!warning]
> Root token is NOT revoked after bootstrap. This is a critical production gap.
