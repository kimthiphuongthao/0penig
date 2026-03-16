---
title: Vault Hardening Audit Across Three Stacks
tags:
  - vault
  - security
  - audit
  - debugging
date: 2026-03-16
status: completed
---

# Vault Hardening Audit Across Three Stacks

Context: read-only audit of [[Vault]] hardening across [[Stack A]], [[Stack B]], and [[Stack C]] for the [[OpenIG]] lab stacks that share [[Keycloak]].

## Scope

- `stack-{a,b,c}/vault/config/vault.hcl`
- `stack-{a,b,c}/vault/init/vault-bootstrap.sh`
- `stack-{a,b,c}/docker-compose.yml`
- persisted Vault state under `stack-*/vault/data/` and `stack-*/vault/file/`

## Findings

> [!warning] Root token still persists
> All three bootstrap scripts write the initial root token to `/vault/data/.vault-keys.root`, export `VAULT_TOKEN` from that file, and do not revoke it. The files also exist on disk in all three stacks.

> [!warning] Unseal key is co-located with Vault data
> All three bootstrap scripts write `/vault/data/.vault-keys.unseal` while `vault.hcl` uses file storage at `/vault/data`. This keeps seal material on the same filesystem as the Vault storage backend.

> [!warning] AppRole hardening is incomplete
> AppRole roles are created with `token_ttl` and `token_max_ttl`, but there is no `secret_id_num_uses`, no `secret_id_ttl`, and no CIDR binding such as `bound_cidr_list`.

> [!success] Audit logging is enabled in practice
> Each bootstrap script enables a file audit device if missing, and all three stacks contain `vault/file/audit.log`. The logs record `sys/audit/file` enablement on `2026-03-16`.

> [!tip] Storage and listener patterns are identical
> All three `vault.hcl` files use `storage "file"` with `path = "/vault/data"` and `listener "tcp"` with `tls_disable = true`. None set `api_addr` or `disable_mlock`.

## Custom Policies

- `stack-a`: policy `openig-readonly`
  - `path "secret/data/wp-creds/*"` -> `["read"]`
- `stack-b`: policy `openig-readonly-b`
  - `path "secret/data/redmine-creds/*"` -> `["read"]`
  - `path "secret/data/jellyfin-creds/*"` -> `["read"]`
- `stack-c`: policy `openig-policy-c`
  - `path "secret/data/phpmyadmin/*"` -> `["read"]`

## Persisted Policy Names Seen In Vault Storage

- [[Stack A]]: `default`, `control-group`, `response-wrapping`, `openig-readonly`
- [[Stack B]]: `default`, `control-group`, `response-wrapping`, `openig-readonly-b`
- [[Stack C]]: `default`, `control-group`, `response-wrapping`, `openig-policy-c`

> [!warning] Policy breadth note
> The custom AppRole policies are narrow and read-only. The active root token is the only clearly over-broad policy exposure evidenced directly from the repo, because the audit logs show operations executing under policy `root`. The built-in policies exist in encrypted storage blobs, so their exact ACL bodies are not directly readable from the repository alone.

## Current State Summary

- Root token revocation: not implemented
- Unseal key separation: not implemented
- AppRole SecretID hardening: not implemented
- Audit device: implemented
- Storage backend migration to Raft: not implemented
- TLS enablement: not implemented
- KV v2 `max_versions` tuning: not implemented
- `api_addr`: not implemented
- `disable_mlock`: not implemented

## Next Steps

1. Revoke and stop persisting the root token after bootstrap.
2. Move unseal material off the Vault data filesystem or adopt auto-unseal.
3. Harden AppRole SecretIDs with one-time use, short TTL, and CIDR restriction.
4. Add explicit KV v2 tuning, `api_addr`, and `disable_mlock`.
5. Plan migration from file storage to integrated storage if this lab is promoted beyond isolated demo use.
