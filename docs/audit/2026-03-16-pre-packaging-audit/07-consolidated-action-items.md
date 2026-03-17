# Consolidated Action Items — Pre-Packaging Audit

**Date:** 2026-03-16
**Source:** 6 audit reports (Tasks 1A through 4)

---

## Priority 1 — CRITICAL (fix before any deployment)

| # | Finding | Source | Files | Effort |
|---|---------|--------|-------|--------|
| C-1 | JWKS cache race condition — non-atomic check-then-act on 2 volatile fields | Code Review | 3x BackchannelLogoutHandler.groovy | RESOLVED 2026-03-17 in Step 3 (`4d8f065`) |
| C-2 | App session tokens in JwtSession over HTTP (WP cookies, Jellyfin token, Redmine cookies) | Security | CredentialInjector.groovy, JellyfinTokenInjector.groovy, RedmineCredentialInjector.groovy | HIGH — requires server-side storage or accept as documented lab limitation |

---

## Priority 2 — HIGH (fix before packaging)

| # | Finding | Source | Files | Effort |
|---|---------|--------|-------|--------|
| H-1 | SloHandler missing try-catch (3 files) | Code+Security+Architecture | SloHandler.groovy (A), SloHandlerGrafana.groovy (C), SloHandlerPhpMyAdmin.groovy (C) | RESOLVED 2026-03-17 in Step 4 (`3b8a6d8`) |
| H-2 | `vault/keys/` not in .gitignore | Security | .gitignore | TRIVIAL — 1 line |
| H-3 | Redmine port 3000 exposed — bypasses SSO | Security | stack-b/docker-compose.yml | RESOLVED 2026-03-17 in Step 5 (`f86c7eb`) |
| H-4 | Redis without authentication (all stacks) | Security | 3x docker-compose.yml + 9 Groovy files | MEDIUM — add requirepass + AUTH commands |
| H-5 | Secrets in docker-compose.yml committed to git | Security | 3x docker-compose.yml | MEDIUM — .env file + .gitignore |
| H-6 | JWKS TTL unit inconsistency (Stack C millis vs A/B seconds) | Code Review | 3x BackchannelLogoutHandler.groovy | RESOLVED 2026-03-17 in Step 3 (`4d8f065`) |
| H-7 | Stack C docker-compose missing platform/user/restart/healthchecks | Architecture | stack-c/docker-compose.yml | LOW — copy patterns from A/B |
| H-8 | SessionBlacklistFilterApp2 divergent Base64 implementation | Code Review | SessionBlacklistFilterApp2.groovy | LOW — align with other files |
| H-9 | Stack C nginx missing proxy_buffer_size 128k | Architecture | stack-c/nginx/nginx.conf | RESOLVED 2026-03-17 in Step 5 (`f86c7eb`) |

---

## Priority 3 — MEDIUM (recommended before packaging)

| # | Finding | Source | Effort |
|---|---------|--------|--------|
| M-1 | Hardcoded Keycloak URLs in BackchannelLogoutHandler (3 files) | Architecture | LOW — System.getenv() |
| M-2 | Missing CANONICAL_ORIGIN_* env vars in A/B docker-compose | Architecture | RESOLVED 2026-03-17 in Step 5 (`f86c7eb`) |
| M-3 | No security response headers on nginx (all stacks) | Security | LOW — add_header directives |
| M-4 | JwtSession cookies lack SameSite flag | Security | LOW — nginx proxy_cookie_flags |
| M-5 | Weak OIDC client secrets Stack C ("secret-c") | Security | TRIVIAL — generate random |
| M-6 | OpenIG containers run as root (Stacks A/B) | Security | MEDIUM — fix volume permissions |
| M-7 | Vault TLS disabled + UI enabled | Security | MEDIUM — deferred to TLS phase |
| M-8 | Hardcoded passwords in vault-bootstrap.sh | Security | MEDIUM — .env sourcing |
| M-9 | Vault 403 error status inconsistency (502 vs 500) | Code Review | LOW — standardize to 500 |
| M-10 | Stack A SloHandler hardcoded Keycloak URL (no env var) | Code+Architecture | TRIVIAL |
| M-11 | readRespLine doesn't throw on EOF | Code Review | LOW |
| M-12 | base64UrlDecode unnecessary manual padding | Code Review | LOW |
| M-13 | Externalize Keycloak URLs in Stack A+C routes (match B pattern) | Architecture | MEDIUM |
| M-14 | App1ResponseRewriter.groovy dead code (0 bytes) | Code+Analyst | RESOLVED 2026-03-17 in Step 5 (`f86c7eb`) |

---

## Priority 4 — Consolidation (historical plan; Steps 1-4 now largely complete)

| # | Task | Lines Saved | Status / prerequisite |
|---|------|-------------|---------------------|
| P-1 | Parameterize BackchannelLogoutHandler via `args` (3 → 1 file) | ~693 | **RESOLVED** in Step 3 (`4d8f065`) |
| P-2 | Parameterize SessionBlacklistFilter (6 → 1 file, Stack C pattern) | ~597 | **RESOLVED** in Steps 1+2 (`a76e194`, `832bbae`) |
| P-3 | Extract shared Vault login utility | ~196 | Still open — verify OpenIG Groovy classpath / `evaluate()` support |
| P-4 | Parameterize SloHandler via `args` (5 → 1-2 files) | ~190 | **RESOLVED** in Step 4 (`3b8a6d8`) |
| **Total** | | **~1676** | |

---

## Open Questions (require investigation before consolidation)

1. OpenIG 6.0.2 `ScriptableHandler` `args` binding? Resolved 2026-03-16 by the Step 1 smoke test; Steps 3 and 4 now depend on it in production code.
2. Can OpenIG 6 load shared Groovy utility scripts from classpath / `evaluate()`? Still open (prerequisite for P-3 only).
3. JWKS cache TTL unit difference (Stack C millis vs A/B seconds)? Resolved 2026-03-17 in Step 3 (`4d8f065`) by standardizing to seconds.

---

## Quick Win Batch (can be done in one Codex session)

Items that can be fixed together with minimal risk:
- H-2: Add `vault/keys/` to .gitignore
- H-3: Remove Redmine port 3000 exposure
- H-9: Add proxy_buffer_size to Stack C nginx
- M-2: Add CANONICAL_ORIGIN env vars to A/B docker-compose
- M-10: SloHandler Stack A → env var for Keycloak URL (already resolved in Step 4, `3b8a6d8`)
- M-14: Delete App1ResponseRewriter.groovy

**Estimated impact:** 6 fixes, ~30 minutes, zero risk of regression.
