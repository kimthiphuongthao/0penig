# Vault Security Hardening — Gap Analysis

## Context
- Vault stores plaintext credentials of legacy apps (WordPress, Redmine, Jellyfin, phpMyAdmin/MariaDB)
- OpenIG calls Vault via AppRole on every SSO request
- Internal Docker network only, not internet-facing
- All 3 stacks (stack-a, stack-b, stack-c) share identical config patterns

## Current State vs Production Requirements

| # | Criterion | Current State | Severity | Status |
|---|---|---|---|---|
| 1 | Root token revocation | Root token revoked after bootstrap. Orphan admin token (vault-admin policy) used for operations. Break-glass via `vault operator generate-root`. | CRITICAL | **RESOLVED** (commit d4920a9) |
| 2 | Unseal key storage | Unseal keys stored at `/vault/keys/` (separate volume from `/vault/data/`). Migration block handles existing installations. | HIGH | **RESOLVED** (commit d4920a9) |
| 3 | AppRole hardening | `secret_id_ttl=72h` enforced via post-bootstrap hardening. CIDR binding deferred — Docker network CIDRs auto-assigned, not stable. Production: use K8s pod CIDR or fixed Docker subnets. | HIGH | **PARTIAL** — TTL done, CIDR deferred |
| 4 | Audit logging | File audit device enabled at `/vault/file/audit.log` (idempotent, runs every bootstrap). | HIGH | **RESOLVED** (commit 494405b) |
| 5 | Storage backend | File storage (no HA). Acceptable for single-node lab. Production: migrate to Raft integrated storage. | HIGH | Lab Convenience — deferred |
| 6 | TLS security | TLS disabled (`tls_disable=true`). Internal Docker network provides isolation. Production: enable mTLS. | MEDIUM | Deferred to Phase 7b |
| 7 | KV v2 versions | `max_versions=5` set via `vault write secret/config` in post-bootstrap hardening. | MEDIUM | **RESOLVED** (commit ca8d0f8) |
| 8 | api_addr | `api_addr = "http://127.0.0.1:8200"` set in vault.hcl. | LOW | **RESOLVED** (commit ca8d0f8) |
| 9 | disable_mlock | `disable_mlock = false` explicitly set in vault.hcl. Container has `IPC_LOCK` capability. | LOW | **RESOLVED** (commit ca8d0f8) |

## Admin Token Architecture
- Policy: `vault-admin` — least-privilege (AppRole management, audit, health, KV config)
- Token type: orphan, periodic (8760h), renewable
- Storage: `/vault/keys/.vault-keys.admin` (chmod 600)
- Root recovery: `vault operator generate-root` using unseal key (break-glass procedure)

## Remaining Production Gaps
1. **AppRole CIDR binding** — requires fixed Docker subnets or K8s pod CIDR
2. **TLS** — enable mTLS when network isolation not guaranteed (Phase 7b)
3. **Raft storage** — migrate from file to Raft for HA (production only)
4. **Audit redundancy** — add second audit device (syslog/socket) for production SPOF prevention
5. **Response wrapping** — wrap SecretID delivery for defense-in-depth
6. **Admin token auto-renewal** — periodic token needs renewal within 8760h period

## References
- https://developer.hashicorp.com/vault/docs/concepts/production-hardening
- https://developer.hashicorp.com/vault/docs/auth/approle
- https://developer.hashicorp.com/vault/docs/audit
- https://developer.hashicorp.com/vault/docs/configuration/storage
- CIS HashiCorp Vault Benchmark
