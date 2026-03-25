---
title: Shared Infra Redis Offload and Vault Token TTL Fixes
tags:
  - debugging
  - shared-infra
  - security
  - openig
  - vault
  - redis
date: 2026-03-24
status: complete
---

# Shared Infra Redis Offload and Vault Token TTL Fixes

Applied two code-level security fixes in the consolidated `shared/` stack used by [[OpenIG]], [[Vault]], and [[Redis]].

> [!success] Confirmed fixes
> `AUD-005`: `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy` now fails closed in the `.then()` offload hook. If `setInRedis(...)` throws, the filter returns `new Response(Status.INTERNAL_SERVER_ERROR)` instead of logging and continuing with a partially persisted session.
>
> Vault admin token TTL in `shared/vault/init/vault-bootstrap.sh` is reduced from `8760h` to `720h`, with an inline renewal comment above the token creation line.

## Files Changed

- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `shared/vault/init/vault-bootstrap.sh`

## Verification

- Confirmed the new fail-closed return exists at `TokenReferenceFilter.groovy:357`.
- Confirmed the new Vault comment and `-period=720h` token creation line exist at `vault-bootstrap.sh:201-203`.
- Ran `bash -n shared/vault/init/vault-bootstrap.sh` successfully.

> [!warning] Current workspace state
> `shared/vault/init/vault-bootstrap.sh` still has unrelated local modifications outside this fix. The security commit was created with partial staging so only the TTL/comment hunk was included.

> [!tip] Operations note
> The admin token is still renewable. Renew it with `vault token renew <token>` before the 30-day period expires, or re-run bootstrap to mint a new admin token if it has already expired.

## Commit

- `d7b4f3b` `security: fail-closed on Redis offload, reduce Vault admin token TTL`
