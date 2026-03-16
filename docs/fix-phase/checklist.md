# Fix Phase Checklist — OpenIG Gateway GAPs

> ⚠️ **SUPERSEDED** — This file is a high-level tracking reference only.
> Primary implementation plan (file paths, code changes, acceptance criteria, execution batches):
> **`.omc/plans/fix-phase-openig-gaps.md`**
> Maintain this file for progress tracking (Status column + Fix Log). Do not use as implementation guide.



**Based on:** `docs/deliverables/standard-gateway-pattern.md` v1.1
**Source findings:** `docs/reviews/2026-03-14-cross-stack-review-summary.md` → Next Steps 1–7
**Started:** 2026-03-14
**Status legend:** `[ ]` pending · `[~]` in progress · `[x]` done · `[!]` blocked

---

## Group 1 — Revocation Contract 🔴 HIGHEST PRIORITY
> Stacks: A, B, C | Fixes: A F2/F3, B F2/F3/F9/F10/F11, C F2/F3/F7/F8

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 1a | BackchannelLogoutHandler.groovy — TTL 3600s → 28800s | A, B, C | [x] | ✅ Done 2026-03-15. Also fixed RESP prefix $4→$5. Verified TTL≈28800 all 3 stacks. Commit 792760f |
| 1b | SessionBlacklistFilter + variants — catch block fail-open → fail-closed (503/redirect login) | A, B, C | [x] | ✅ Done 2026-03-15. Return 500 on Redis error (fail-closed). Session preserved for recovery. Tested A+B+C. Commit 278a29c |
| 1c | Redis socket timeouts — connectTimeout=200ms, soTimeout=500ms (BackchannelLogoutHandler + SessionBlacklistFilter) | A, B, C | [x] | ✅ Done 2026-03-15. connect=200ms, read=500ms via InetSocketAddress. 9 files across 3 stacks. Commit 278a29c |
| 1d | BackchannelLogoutHandler — catch Exception → 500 (không phải 400) cho infra faults | A, B, C | [x] | ✅ Done 2026-03-15. All 3 stacks (A also had same pattern). Tested Redis down → 500. Commit 9b770cd |
| 1e | Verify sid/sub consistency — BackchannelLogoutHandler write vs SessionBlacklistFilter* read | B | [ ] | B F11 — 1/4 reviewers, investigate first |

---

## Group 2 — Secret Externalization 🟠 HIGH
> Stacks: A, B, C | Fixes: A F1, B F1, C F1

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 2a | config.json — sharedSecret + PKCS12 password → Vault/env | A | [x] | ✅ Done 2026-03-15. Env vars via docker-compose + docker-entrypoint.sh sed substitution. Commit f677d9f |
| 2b | config.json + 01-jellyfin.json + 02-redmine.json — sharedSecret + clientSecrets → Vault/env | B | [x] | ✅ Done 2026-03-15. Route files use native ${env['VAR']}. Commit f677d9f |
| 2c | config.json + 10-grafana.json + 11-phpmyadmin.json — sharedSecret + clientSecrets → Vault/env | C | [x] | ✅ Done 2026-03-15. Same pattern as A+B. Commit f677d9f |
| 2d | Rotate tất cả exposed secrets + invalidate existing sessions | A, B, C | [x] | ✅ Done 2026-03-15. sharedSecret + PKCS12 rotated; clientSecret externalized only (must match Keycloak). Commit f677d9f |

---

## Group 3 — Transport + Origin Integrity 🟠 HIGH
> Stacks: A, B, C | Fixes: A §6, A F5, B F4/F7, C F4/F9

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 3a | SloHandler*.groovy + SessionBlacklistFilter*.groovy — pin redirect base URLs, không dùng inbound Host | A, B, C | [x] | ✅ Done 2026-03-15. CANONICAL_ORIGIN_APPx env vars + hardcoded fallbacks, 11 Groovy files. Also fixed CredentialInjector.groovy (Stack A) + RedmineCredentialInjector.groovy (Stack B) request.uri redirect |
| 3b | requireHttps: false → true (config.json + route files) | B | [ ] | B F4. Phase 7a docs done (Lab Exception note). Phase 7b (actual change) deferred to Vault Hardening |
| 3c | requireHttps: false → true (config.json + route files) | C | [ ] | C F4. Phase 7a docs done. Phase 7b deferred |
| 3d | Validate Codex-only HTTP findings: VaultCredentialFilter, BackchannelLogoutHandler, SloHandler, CredentialInjector | A | [ ] | A §6 Codex-only — confirm scope trước khi fix |

---

## Group 4 — Session Storage Boundaries 🟡 MEDIUM
> Stacks: B, C | Fixes: B F6/F8, C F5

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 4a | Remove Vault token + downstream session material khỏi JwtSession → server-side store | B | [x] | ✅ Done 2026-03-16. Vault tokens → fresh fetch per request. Commit 76b648a |
| 4b | Jellyfin access token — localStorage → httpOnly Secure cookie | B | [ ] | B F8 |
| 4c | Remove vault_token + phpmyadmin_username/password khỏi JwtSession → server-side store | C | [x] | ✅ Done 2026-03-16. Vault tokens (76b648a) + phpMyAdmin creds + grafana_username (c0c491d). attributes EL + globals Vault token cache. Code reviewed. Commit c0c491d |

---

## Group 5 — RP-Initiated Logout + Observability 🔴 URGENT (5a)
> Stacks: A, B | Fixes: A F4, B F5

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 5a | SloHandlerJellyfin.groovy — namespace 'app3' → 'app4' | B | [x] | ✅ Done 2026-03-15. Also fixed: client_id mismatch (OIDC_CLIENT_ID→OIDC_CLIENT_ID_APP4), post_logout_redirect_uri, Keycloak client config. Commit a3cb6c3 |
| 5b | SloHandler.groovy — không log full logout URL chứa id_token_hint | A | [ ] | A F4 |

---

## Group 6 — Adapter Contract 🟡 MEDIUM
> Stacks: A, C | Fixes: A §6, C F6

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 6a | Wire PhpMyAdminCookieFilter vào route chain 11-phpmyadmin.json | C | [!] | WONT_FIX — Token mismatch confirmed (phpMyAdmin CSRF protection incompatible with cookie manipulation). User switch handled by `cacheHeader: false` + fresh attributes per request (FIX-09). |
| 6b | CredentialInjector.groovy — validate synthetic login failure → fail closed (không proxy unauthenticated) | A | [ ] | A §6 Subagent-only |
| 6c | Validate adapter cleanup hooks wired đúng vào route chain | A | [ ] | A §6 Codex-only |

---

## Group 7 — Unsafe Method Reauth 🟡 MEDIUM
> Stack: A | Fix: A §6 Codex-only

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 7a | CredentialInjector.groovy — POST/PUT/PATCH/DELETE expired session → 401 thay vì redirect (mất body) | A | [ ] | A §6 Codex-only |

---

## Progress Summary

| Group | Total | Done | In Progress | Pending |
|-------|-------|------|-------------|---------|
| 1 — Revocation | 5 | 4 | 0 | 1 |
| 2 — Secrets | 4 | 4 | 0 | 0 |
| 3 — Transport/Origin | 4 | 1 | 0 | 3 |
| 4 — Session Storage | 3 | 2 | 0 | 1 |
| 5 — Logout/Observability | 2 | 0 | 0 | 2 |
| 6 — Adapter Contract | 3 | 0 | 0 | 3 |
| 7 — Unsafe Method | 1 | 0 | 0 | 1 |
| **Total** | **22** | **11** | **0** | **11** |

---

## Fix Log

| Date | ID | Action | Result | Commit |
|------|----|--------|--------|--------|
| 2026-03-15 | 1a | FIX-02: Redis TTL 3600→28800 + RESP prefix fix | PASS — TTL≈28800 verified A+B+C | 792760f |
| 2026-03-15 | 1b+1c | FIX-03+04: fail-closed 500 + socket timeout 200/500ms | PASS — tested Redis stop/start A+B+C | 278a29c |
| 2026-03-15 | 1d | FIX-05: backchannel error code 400→500 for infra errors | PASS — tested Redis down→500, normal SLO→200 | 9b770cd |
| 2026-03-15 | 2a+2b+2c+2d | FIX-06: externalize secrets to env vars + rotate sharedSecret/PKCS12 | PASS — 16 files, 3 entrypoint scripts, all routes loaded, SSO/SLO verified | f677d9f |
| 2026-03-15 | — | FIX-07 Phase 7a: Lab Exception notes in standard-gateway-pattern.md | PASS — docs only, no code change | 38a3f7e |
| 2026-03-15 | 3a | FIX-08: pin redirect origins via CANONICAL_ORIGIN_APPx | PASS — 11 Groovy files, all routes loaded, no Host-header redirects remaining | 7fc73ba |
| 2026-03-15 | — | Stack B PKCS12 keystore config (pattern consistency) | PASS — JWT session warning gone, all 3 stacks identical pattern | daa0af0 |
| 2026-03-15 | — | Stack B nginx ip_hash (replace broken JSESSIONID hash) | PASS — match Stack A/C pattern | 93efa92 |
| 2026-03-15 | 4a+4c | FIX-09: remove sensitive material from JwtSession | REVERTED — Executor regressions (phpMyAdmin, Redmine route, cookie invalidation) | 817ab0b |
| 2026-03-16 | — | Stack B clientEndpoint collision fix + Jellyfin SLO re-login | PASS — Redmine /openid/app3, dotnet removed, Jellyfin post_logout /web/index.html | 6727f5a |
| 2026-03-16 | 4a+4c | FIX-09 re-impl: Vault tokens removed from JwtSession (B+C) | PASS — fresh Vault login per request. phpMyAdmin creds stay (EL limitation). MariaDB password synced | 76b648a |
| 2026-03-16 | 4c | FIX-09 complete: phpMyAdmin creds + grafana_username → attributes EL | PASS — attributes transient per-request, globals Vault token cache. Code reviewed. SSO+SLO tested | c0c491d |
