# Task 3: Code Quality + Simplification Audit

**Agent:** code-reviewer (Opus, READ-ONLY)
**Date:** 2026-03-16
**Files Reviewed:** 24 Groovy scripts across 3 stacks

> Update 2026-03-17: C-1 was resolved in Pattern Consolidation Step 3 (`4d8f065`), H-1 was resolved in Step 4 (`3b8a6d8`), H-3 was resolved in Step 3 (`4d8f065`), M-1/M-2 were resolved by the completed BackchannelLogoutHandler / SessionBlacklistFilter consolidation tracks (`4d8f065`, `a76e194`, `832bbae`), M-9 was resolved in Step 5 (`aaf66d5`), and L-7 was resolved in Step 4 (`3b8a6d8`). Step 6 is the current document-sync pass.

---

## Summary

**Total Issues:** 22
**Verdict:** REQUEST CHANGES

| Severity | Count |
|----------|-------|
| CRITICAL | 1 |
| HIGH | 4 |
| MEDIUM | 10 |
| LOW | 7 |

---

## CRITICAL

### C-1: JWKS cache race condition (3 files)
**Files:** `stack-{a,b,c}/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
**Issue:** Two separate `@Field static volatile` variables (`cachedJwks` + `jwksCacheExpiry`) read/written independently. Classic check-then-act race. Thread A reads non-null cache, Thread B invalidates, Thread A returns null. Unlike Vault token cache which correctly uses `globals.compute()`.
**Fix:** Use `globals.compute()` for JWKS caching, or combine into single immutable object.
**Status:** RESOLVED 2026-03-17 in Pattern Consolidation Step 3 (`4d8f065`).

---

## HIGH

### H-1: SloHandler missing try-catch (3 files)
**Files:** `stack-a/SloHandler.groovy`, `stack-c/SloHandlerGrafana.groovy`, `stack-c/SloHandlerPhpMyAdmin.groovy`
**Issue:** Zero exception handling. Any runtime exception → stack trace to browser. Contrast with `SloHandlerRedmine.groovy` (Stack B) which has proper try-catch.
**Fix:** Wrap in try-catch, return HTML error page on failure.
**Status:** RESOLVED 2026-03-17 in Pattern Consolidation Step 4 (`3b8a6d8`).

### H-2: SessionBlacklistFilterApp2 divergent Base64 decode
**File:** `stack-a/SessionBlacklistFilterApp2.groovy:46-51`
**Issue:** Uses manual character replacement + `Base64.decoder` instead of `Base64.getUrlDecoder().decode()` used everywhere else.
**Fix:** Align with standard pattern.

### H-3: JWKS cache TTL unit inconsistency
**Files:** Stack A/B use seconds (600), Stack C uses milliseconds (600000L)
**Issue:** Functionally equivalent but maintenance-hazardous. Copy-paste between stacks could introduce 600ms or 600000s TTL.
**Fix:** Standardize to seconds (matching A/B).
**Status:** RESOLVED 2026-03-17 in Pattern Consolidation Step 3 (`4d8f065`).

### H-4: SessionBlacklistFilterApp2 manual Socket management
**File:** `stack-a/SessionBlacklistFilterApp2.groovy:94-141`
**Issue:** Uses manual `try/finally` instead of `withCloseable`. Different `readLine`/`readFully` implementation from all other files.
**Fix:** Refactor to use `withCloseable` and shared `readRespLine` pattern.

---

## MEDIUM (10 items)

| # | Finding | Files |
|---|---------|-------|
| M-1 | BackchannelLogoutHandler duplication (~95%, 3 copies) | RESOLVED in Step 3 (`4d8f065`) |
| M-2 | SessionBlacklistFilter duplication (~85%, 6 copies) | RESOLVED in Steps 1+2 (`a76e194`, `832bbae`) |
| M-3 | VaultCredentialFilter duplication (~75%, 4 copies) | 4 files, ~546 lines |
| M-4 | SloHandler Stack A hardcoded Keycloak URL | SloHandler.groovy:26 |
| M-5 | readRespLine doesn't throw on EOF | 8 files |
| M-6 | Vault 403 status inconsistency (502 vs 500) | 4 VaultCredentialFilter files |
| M-7 | base64UrlDecode unnecessary manual padding | 3 BackchannelLogoutHandler files |
| M-8 | Stack B SessionBlacklistFilter shared oidc_sid key | SessionBlacklistFilter.groovy:40 |
| M-9 | App1ResponseRewriter.groovy empty dead code | RESOLVED in Step 5 (`aaf66d5`) |
| M-10 | Socket per request for Redis (no connection pooling) | 9 files |

---

## LOW (7 items)

| # | Finding |
|---|---------|
| L-1 | Magic number: Redis port 6379 hardcoded in 9 files |
| L-2 | Magic number: Redis TTL 1800 hardcoded in 3 files |
| L-3 | Inconsistent log prefix naming across files |
| L-4 | SloHandlerJellyfin skips Keycloak logout when no id_token_hint |
| L-5 | PhpMyAdminCookieFilter exists but dead code (WONT_FIX) |
| L-6 | JellyfinResponseRewriter `buildDeviceId` uses session hashCode |
| L-7 | SloHandler duplication (~70%, 5 copies) — RESOLVED in Step 4 (`3b8a6d8`) |

---

## Duplication Quantification

Historical audit snapshot at review time. Current live state after consolidation:
- BackchannelLogoutHandler: `3 -> 1`
- SessionBlacklistFilter: `6 -> 1`
- SloHandler: `5 -> 2`

| Pattern | Files | Duplicated Lines |
|---------|-------|-----------------|
| BackchannelLogoutHandler | 3 | ~900 |
| SessionBlacklistFilter | 6 | ~500 |
| VaultCredentialFilter | 4 | ~400 |
| readResponseBody helper | 6 | ~100 |
| SloHandler | 5 | ~200 |
| **Total** | **24** | **~2100 of ~2700 (~78%)** |

---

## Positive Observations

1. Consistent fail-closed pattern (500 on Redis error)
2. Atomic Vault token caching (`globals.compute()`)
3. Proper Redis RESP response parsing (post-fix)
4. Good XSS prevention in JellyfinResponseRewriter (`escapeForJs`)
5. Pinned canonical origins (CANONICAL_ORIGIN_APPx)
6. Well-structured error responses with meaningful HTML
7. Defensive null checking (Groovy `?.` and `?:`)
8. Good timeout discipline (200ms-5000ms, no unbounded waits)
9. Consolidated SessionBlacklistFilter template now uses the same `args`-based pattern across all stacks
