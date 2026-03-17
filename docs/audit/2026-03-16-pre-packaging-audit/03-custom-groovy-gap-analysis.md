# Task 1C: Gap Analysis — Custom Groovy vs OpenIG Built-in

**Agent:** analyst (Opus, READ-ONLY)
**Date:** 2026-03-16

> Update 2026-03-17: the `ScriptableHandler` `args` prerequisite was confirmed during Step 1; SessionBlacklistFilter consolidation `6 -> 1` is complete (Steps 1+2, `a76e194`, `832bbae`); BackchannelLogoutHandler consolidation `3 -> 1` is complete (Step 3, `4d8f065`); the missing SloHandler try-catch plus SloHandler consolidation `5 -> 2` are complete (Step 4, `3b8a6d8`); and Step 5 deleted `App1ResponseRewriter.groovy` dead code (`aaf66d5`). Step 6 is the current document-sync pass.

---

## Executive Summary

Of 24 Groovy files across 3 stacks, **none can be fully replaced by OpenIG 6.0.2 built-in filters**. The custom code addresses five capability gaps: (1) backchannel logout with JWT validation, (2) session blacklisting via Redis, (3) Vault AppRole credential fetching, (4) Keycloak end_session_endpoint with id_token_hint, and (5) response body rewriting. However, there is **massive cross-stack duplication** — 24 files contain effectively only 7 distinct logical patterns.

---

## Per-File Verdict Table

| Groovy File | Stack | Purpose | Verdict | Rationale |
|---|---|---|---|---|
| BackchannelLogoutHandler.groovy | A | Backchannel JWT validation + Redis blacklist | **KEEP** | No built-in backchannel logout |
| BackchannelLogoutHandler.groovy | B | Same, audience list [openig-client-b, openig-client-b-app4] | **KEEP** | Same — only audience/redis host differ |
| BackchannelLogoutHandler.groovy | C | Same, audience list [openig-client-c-app5, openig-client-c-app6] | **KEEP** | Same — millis vs seconds variance at audit time, resolved in Step 3 (`4d8f065`) |
| SessionBlacklistFilter.groovy | A | Check Redis blacklist, fail-closed 500 | **KEEP** | No built-in session revocation |
| SessionBlacklistFilterApp2.groovy | A | Same for WhoAmI (app2) | **KEEP** | Historical audit verdict only — **RESOLVED/DELETED** by Pattern Consolidation Step 2 (`a76e194`, `832bbae`); replaced by the parameterized `SessionBlacklistFilter.groovy` template |
| SessionBlacklistFilter.groovy | B | Same for Stack B (app3+app4) | **KEEP** | Different redis host |
| SessionBlacklistFilterApp3.groovy | B | Same for Redmine | **KEEP** | Historical audit verdict only — **RESOLVED/DELETED** by Pattern Consolidation Step 2 (`a76e194`, `832bbae`); replaced by the parameterized `SessionBlacklistFilter.groovy` template |
| SessionBlacklistFilterApp4.groovy | B | Same for Jellyfin | **KEEP** | Historical audit verdict only — **RESOLVED/DELETED** by Pattern Consolidation Step 2 (`a76e194`, `832bbae`); replaced by the parameterized `SessionBlacklistFilter.groovy` template |
| SessionBlacklistFilter.groovy | C | Parameterized via `args` (app5+app6) | **KEEP** | Best implementation — uses args |
| VaultCredentialFilter.groovy | A | Vault AppRole → WordPress creds | **KEEP** | No built-in Vault client |
| VaultCredentialFilterRedmine.groovy | B | Vault AppRole → Redmine creds | **KEEP** | Same Vault pattern |
| VaultCredentialFilterJellyfin.groovy | B | Vault AppRole → Jellyfin creds | **KEEP** | Same Vault pattern |
| VaultCredentialFilter.groovy | C | Vault AppRole → phpMyAdmin creds | **KEEP** | Extra id_token decode step |
| CredentialInjector.groovy | A | WordPress multi-step login + cookie cache | **KEEP** | Beyond PasswordReplayFilter |
| RedmineCredentialInjector.groovy | B | Redmine GET+CSRF+POST login | **KEEP** | Beyond PasswordReplayFilter |
| JellyfinTokenInjector.groovy | B | Jellyfin JSON API auth + header injection | **KEEP** | Unique token injection pattern |
| JellyfinResponseRewriter.groovy | B | Inject JS into HTML for localStorage | **KEEP** | No built-in response rewriting |
| SloHandler.groovy | A | Clear session + Keycloak end_session redirect | **KEEP** | No built-in end_session support |
| SloHandlerRedmine.groovy | B | Same for Redmine (with try-catch) | **KEEP** | Same + has error handling |
| SloHandlerJellyfin.groovy | B | Same + Jellyfin /Sessions/Logout API call | **KEEP** | Extra app-specific logout |
| SloHandlerGrafana.groovy | C | Same for Grafana | **KEEP** | Missing try-catch at audit time, resolved in Step 4 (`3b8a6d8`) |
| SloHandlerPhpMyAdmin.groovy | C | Same for phpMyAdmin | **KEEP** | Missing try-catch at audit time, resolved in Step 4 (`3b8a6d8`) |
| PhpMyAdminCookieFilter.groovy | C | Track cookie ownership (INACTIVE) | **KEEP (dead)** | WONT_FIX — phpMyAdmin CSRF incompatible |
| App1ResponseRewriter.groovy | A | Empty file (0 bytes) | **REMOVED** | Dead code deleted in Step 5 (`aaf66d5`) |

---

## Cross-Stack Duplication Analysis

Historical audit snapshot below. Current live state after Steps 1-4:
- BackchannelLogoutHandler: **RESOLVED** — `3 -> 1` in Step 3 (`4d8f065`)
- SessionBlacklistFilter: **RESOLVED** — `6 -> 1` in Steps 1+2 (`a76e194`, `832bbae`)
- SloHandler: **RESOLVED** — `5 -> 2` in Step 4 (`3b8a6d8`)

| Pattern | Current | Target | Lines Saved |
|---------|---------|--------|-------------|
| BackchannelLogoutHandler (3 copies, ~95% identical) | 3 files, ~1043 lines | 1 file, ~350 lines | **~693** |
| SessionBlacklistFilter (6 copies, ~85% identical) | 6 files, ~752 lines | 1 file, ~155 lines | **~597** |
| VaultCredentialFilter (4 copies, ~75% identical) | 4 files, ~546 lines | 4+1 shared, ~350 lines | **~196** |
| SloHandler (5 copies, ~70% identical) | 5 files, ~300 lines | 1-2 files, ~110 lines | **~190** |
| **Total** | **18 files, ~2641 lines** | **7-8 files, ~965 lines** | **~1676** |

---

## PasswordReplayFilter Investigation

**Q: Could `PasswordReplayFilterHeaplet` replace CredentialInjector (WordPress)?**

**A: No.** PasswordReplayFilter cannot replace because:
1. No cookie caching in JwtSession (replays form on every qualifying response)
2. No session expiry detection on response (redirect-to-wp-login.php)
3. No FIX-14 unsafe method handling (409 for POST/PUT)
4. WordPress `testcookie` requirement not supported

Same reasoning applies to Redmine (CSRF extraction + GET-then-POST flow).

---

## Open Questions

1. `ScriptableHandler` `args` binding? Resolved 2026-03-16 by the Step 1 smoke test; Steps 3 and 4 now use it.
2. Can OpenIG 6 load shared Groovy utilities from classpath / `evaluate()`? Still open (prerequisite for Vault utility consolidation only).
3. JWKS cache TTL unit difference (Stack C millis vs A/B seconds)? Resolved 2026-03-17 in Step 3 by standardizing to seconds (`4d8f065`).
4. `App1ResponseRewriter.groovy` (0 bytes) should be deleted? Resolved 2026-03-17 in Step 5 (`aaf66d5`).
