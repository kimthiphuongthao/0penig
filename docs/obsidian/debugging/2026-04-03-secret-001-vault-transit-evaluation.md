---
title: SECRET-001 Vault Transit Evaluation
tags:
  - debugging
  - security
  - vault
  - redis
  - openig
date: 2026-04-03
status: completed
---

# SECRET-001 Vault Transit Evaluation

Related: [[OpenIG]] [[Vault]] [[Redis]]

## Context

- Task: evaluate whether `docs/audit/2026-04-02-vault-transit-encryption-evaluation.md` proposes the right best-practice fix for SECRET-001 in this repo.
- Scope reviewed:
  - `docs/audit/2026-04-02-vault-transit-encryption-evaluation.md`
  - `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
  - `shared/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
  - `shared/vault/init/vault-bootstrap.sh`
  - `shared/openig_home/config/routes/01-wordpress.json`
  - supporting route/config checks in `shared/openig_home/config/routes/*.json` and `shared/docker-compose.yml`

## Verdict

> [!warning] Architectural verdict
> [[Vault]] Transit is the right **production-grade** pattern for encrypting Redis token payloads before storage, but section 3.4 is **not drop-in compatible** with the current [[OpenIG]] Groovy and route patterns.

> [!success] Important nuance
> The document is directionally correct on the security model: encrypt-before-Redis is the only option reviewed here that actually addresses plaintext token exposure in Redis and backups.

## What The Codebase Actually Does

- `TokenReferenceFilter.groovy` restores token payloads from Redis before the request reaches downstream filters, then re-serializes and stores them again on the response path.
- That means Transit would add:
  - one decrypt operation before `next.handle(...)`
  - one encrypt operation before Redis `SET` on the response path
- For routes that already call `VaultCredentialFilter.groovy`, the marginal dependency cost is acceptable, but the runtime cost is really **two Transit calls per authenticated request**, not one.
- `TokenReferenceFilter.groovy` is reused across multiple app and logout routes, not only WordPress, so a hardcoded `app1-key` is not reusable in this repo.

## Compatibility Gaps

### Vault token caching

- Existing Vault access uses `globals.compute('vault_token_' + configuredAppRoleName)` in `VaultCredentialFilter.groovy`.
- The proposal uses `globals.get('vaultToken')`, but there is no matching writer for that cache key in the current Groovy scripts.
- Because `TokenReferenceFilter.groovy` runs before `VaultCredentialFilter.groovy` in the route chain, it cannot assume another filter already populated a Vault token for the current request.

### Error handling

- Current Vault code is explicit about timeouts, HTTP status handling, and `403` token-expiry cleanup.
- Section 3.4 omits:
  - AppRole login flow
  - `connectTimeout` / `readTimeout`
  - `403` cache invalidation
  - error-stream parsing
  - explicit `disconnect()`
- The result still fails closed in broad terms, but not in the same controlled pattern already used in the repo.

### Groovy encoding conventions

- Current Groovy scripts consistently use explicit UTF-8 conversions such as `getBytes('UTF-8')` and `new String(..., 'UTF-8')`.
- Section 3.4 uses `plaintext.bytes.encodeBase64().toString()` and returns `response.data.plaintext.decodeBase64()` directly.
- The decrypt side should explicitly convert decoded bytes back to a UTF-8 string before JSON parsing.

## Repo-Level Blockers

> [!warning] Required integration work
> Transit is not provisioned in the current bootstrap.

- `shared/vault/init/vault-bootstrap.sh` enables only `secret/` KV v2 and AppRole auth today.
- Existing AppRole policies grant reads on `secret/data/...` only; they do not grant `transit/encrypt/*` or `transit/decrypt/*`.
- The admin policy also needs Transit-related capabilities if bootstrap is expected to create and manage keys.
- Existing Redis data is plaintext JSON today, so rollout needs either:
  - a Redis flush/reseed window, or
  - dual-read logic: plaintext for legacy entries, Transit decrypt only for `vault:` ciphertext entries
- Route definitions that use `TokenReferenceFilter.groovy` will need new per-app Transit config, not a hardcoded key.

## Alternatives

### AES/GCM in Groovy

- Simpler to implement than Transit.
- Avoids two extra Vault API calls per request.
- Still mitigates plaintext Redis storage if the key is stored outside Redis.
- Weaker operationally because key management, rotation, and separation of duties move back into [[OpenIG]].

### Redis password + TLS

- Useful hardening for transport.
- Does **not** solve SECRET-001 because Redis values and backups remain plaintext to Redis admins or post-compromise readers.

> [!tip]
> If the goal is lab-only risk reduction with minimal moving parts, AES/GCM in Groovy is the simpler compromise. If the goal is the best production architecture, [[Vault]] Transit remains the better design.

## Recommendation

- Keep the document's high-level recommendation: use [[Vault]] Transit for production hardening.
- Do **not** implement section 3.4 as written.
- If Transit is implemented here, align it to existing patterns:
  - reuse the `globals.compute(...)` AppRole token cache model
  - add per-route args for AppRole files and transit key name
  - add explicit HTTP timeouts and `403` cache eviction
  - use explicit UTF-8 base64 encode/decode
  - support legacy plaintext Redis entries during rollout

## Files Changed

- `docs/obsidian/debugging/2026-04-03-secret-001-vault-transit-evaluation.md`
