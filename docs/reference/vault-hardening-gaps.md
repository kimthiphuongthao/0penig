# Vault Security Hardening — Gap Analysis

## Context
- Vault stores plaintext credentials of legacy apps (WordPress, Redmine, Jellyfin, phpMyAdmin/MariaDB)
- OpenIG calls Vault via AppRole on every SSO request
- Internal Docker network only, not internet-facing
- All 3 stacks (stack-a, stack-b, stack-c) share identical config patterns

## Current State vs Production Requirements

| # | Criterion | Current State | Severity | Type | Production Fix |
|---|---|---|---|---|---|
| 1 | Root token revocation | Root token not revoked after bootstrap | CRITICAL | Real Risk | Revoke root token immediately after bootstrap |
| 2 | Unseal key storage | stored on same filesystem as Vault data | HIGH | Real Risk | Separate unseal key from Vault data filesystem (or use auto-unseal) |
| 3 | AppRole hardening | missing: secret_id_num_uses, secret_id_ttl, CIDR binding | HIGH | Real Risk | Harden AppRole: secret_id_num_uses=1, secret_id_ttl short, CIDR binding |
| 4 | Audit logging | Not enabled | HIGH | Real Risk | Enable audit logging (at least one durable audit device) |
| 5 | Storage backend | File storage (no HA, no hot backup) | HIGH | Lab Convenience | Migrate storage to Raft integrated storage for HA |
| 6 | TLS security | TLS disabled (tls_disable=true) | MEDIUM | Real Risk | Enable TLS (if network isolation is not guaranteed) |
| 7 | KV v2 versions | max_versions not limited (default 10) | MEDIUM | Lab Convenience | Limit KV v2 max_versions to 3-5 |
| 8 | Operational config | api_addr not set in vault.hcl | LOW | Operational Gap | Set api_addr |
| 9 | Memory locking | disable_mlock not set | LOW | Operational Gap | Set disable_mlock |

## Priority Fix Order (for production-grade)
1. Revoke root token immediately after bootstrap
2. Separate unseal key from Vault data filesystem (or use auto-unseal with KMS/HSM)
3. Harden AppRole: secret_id_num_uses=1, secret_id_ttl short, CIDR binding
4. Enable audit logging (at least one durable audit device)
5. Enable TLS (if network isolation is not guaranteed)
6. Migrate storage to Raft integrated storage for HA
7. Limit KV v2 max_versions to 3-5
8. Set api_addr and disable_mlock

## Phase: Production Hardening (Deferred)
These items are tracked as pending work after lab validation is complete. See main plan file.

## References
- https://developer.hashicorp.com/vault/docs/concepts/seal
- https://developer.hashicorp.com/vault/docs/auth/approle
- https://developer.hashicorp.com/vault/docs/audit
- https://developer.hashicorp.com/vault/docs/configuration/storage
- CIS HashiCorp Vault Benchmark
