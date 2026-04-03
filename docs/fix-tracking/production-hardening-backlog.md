# Production Hardening Backlog

This backlog tracks the remaining production-hardening work for the active `shared/` runtime, based on:

- `docs/audit/2026-04-02-openig-best-practices-compliance-evaluation.md`
- `docs/obsidian/debugging/2026-04-03-secret-001-vault-transit-evaluation.md`
- `.claude/rules/architecture.md`
- `CLAUDE.md`

## Summary

| Status | Count |
|--------|-------|
| OPEN | 5 |
| TOTAL | 5 |

## 1. VAULT-TRANSIT-001

| Field | Value |
|-------|-------|
| ID | VAULT-TRANSIT-001 |
| Title | Vault Transit encryption for Redis token payloads |
| Status | OPEN |
| Priority | P1 |
| Scope (files to change) | `shared/vault/init/vault-bootstrap.sh`<br>`shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy` |
| Acceptance Criteria | 1. `shared/vault/init/vault-bootstrap.sh` enables Vault Transit and provisions the policies/capabilities needed for shared-runtime bootstrap/admin flow and the relevant OpenIG AppRoles.<br>2. `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy` uses `globals.compute()` for Vault token caching and takes per-route args for AppRole files and Transit key name instead of hardcoded app-specific values.<br>3. Redis writes encrypt token payloads before `SET`, and Redis reads support both Transit ciphertext and legacy plaintext JSON during rollout.<br>4. Transit/Vault failures stay fail-closed with explicit timeout handling, `403` cache eviction, controlled disconnect/error handling, and no plaintext fallback on encrypt/decrypt failure. |
| Blockers/Notes | Must use `globals.compute()` cache, per-route args, fail-closed, dual-format read for rollout. Existing Redis entries are plaintext today, so rollout needs dual-read compatibility or a coordinated Redis flush/reseed window. |

## 2. PKCE-001

| Field | Value |
|-------|-------|
| ID | PKCE-001 |
| Title | Enable PKCE on all 6 OAuth2ClientFilter routes |
| Status | OPEN |
| Priority | P2 |
| Scope (files to change) | `shared/openig_home/config/routes/*.json` (all 6 auth routes) |
| Acceptance Criteria | 1. All six shared auth routes using `OAuth2ClientFilter` enable PKCE with a consistent configuration.<br>2. Existing confidential-client wiring remains intact: same `clientId`, `clientSecret`, `clientEndpoint`, scopes, and per-app isolation model.<br>3. The active shared routes `01-wordpress.json`, `02-app2.json`, `01-jellyfin.json`, `02-redmine.json`, `10-grafana.json`, and `11-phpmyadmin.json` all reflect the hardening change. |
| Blockers/Notes | Confidential clients — recommended hardening per OAuth 2.1, not blocking. |

## 3. AUDIT-LOG-001

| Field | Value |
|-------|-------|
| ID | AUDIT-LOG-001 |
| Title | Structured audit logging via OpenIG audit handler |
| Status | OPEN |
| Priority | P2 |
| Scope (files to change) | `shared/openig_home/config/config.json` (add AuditService handler) |
| Acceptance Criteria | 1. `shared/openig_home/config/config.json` defines the shared OpenIG audit handler/service needed for structured audit events.<br>2. Audit output is structured and limited to: timestamp, app, event type, user sub, and result.<br>3. No token values, session cookies, Vault data, Redis payloads, or downstream credentials are written to the audit logs.<br>4. Structured audit logging becomes the canonical shared-runtime audit path instead of relying only on ad-hoc Groovy logger lines. |
| Blockers/Notes | No token values in logs; log only: timestamp, app, event type, user sub, result. |

## 4. SECURE-COOKIE-001

| Field | Value |
|-------|-------|
| ID | SECURE-COOKIE-001 |
| Title | JwtSession Secure cookie flag |
| Status | OPEN |
| Priority | P3 (depends on TLS) |
| Scope (files to change) | `shared/openig_home/config/config.json` + all 6 route JwtSession configs |
| Acceptance Criteria | 1. The fallback global `Session` heap in `shared/openig_home/config/config.json` and all six route-local `JwtSession` heaps explicitly set the Secure cookie flag.<br>2. Existing cookie names, per-app route-local heaps, and host-only cookie behavior remain unchanged apart from Secure hardening.<br>3. This task is only considered complete once browser-facing traffic is served over TLS and OpenIG is no longer issuing session cookies over plain HTTP. |
| Blockers/Notes | Only meaningful after TLS is enabled (Phase 7b). |

## 5. TLS-001

| Field | Value |
|-------|-------|
| ID | TLS-001 |
| Title | Enable TLS between nginx, OpenIG, Vault, Redis, Keycloak |
| Status | OPEN |
| Priority | P3 |
| Scope (files to change) | `shared/nginx/nginx.conf`, `shared/docker-compose.yml`, all groovy scripts (requireHttps) |
| Acceptance Criteria | 1. `shared/nginx/nginx.conf` terminates TLS for browser-facing entrypoints and routes traffic using the production TLS plan rather than the current HTTP-only lab exception.<br>2. `shared/docker-compose.yml` provides the certificate, trust, port, and environment wiring required for nginx, OpenIG, Vault, Redis, and Keycloak.<br>3. Shared HTTPS enforcement is moved from lab mode to production mode, including the route/Groovy handling needed to stop treating plain HTTP as acceptable.<br>4. Completion of this task unblocks `SECURE-COOKIE-001`. |
| Blockers/Notes | Lab exception accepted; Phase 7b deferred. |
