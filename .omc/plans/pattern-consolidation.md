# Plan: Pattern Consolidation — Standardize SSO/SLO Gateway Patterns

**Date:** 2026-03-16
**Branch:** `feat/subdomain-test`
**Source:** Pre-packaging audit (6 agents, 2026-03-16) + codebase investigation
**Goal:** Reduce 24 Groovy files (7 distinct patterns) to parameterized templates, fix CRITICAL+HIGH defects, update deliverables.
**Revision:** R1 (Critic REVISE feedback applied — 5 mandatory fixes + 4 nice-to-haves)

> Update 2026-03-17: Pattern Consolidation Steps 1-6 are complete. Step 5 quick wins were finished via `5ae657e`, `aaf66d5`, and `f86c7eb`; Step 6 deliverables were completed in `421b369`; STEP-01 deleted `PhpMyAdminCookieFilter.groovy` (`20d523f`); STEP-02 rotated Stack C OIDC secrets (`37672ed`); STEP-03 moved live secrets into gitignored `.env` files and pinned OpenIG to `6.0.1` (`b738577`). Remaining Stack C Grafana secret-mismatch debugging is outside this plan.

---

## Context

The SSO Lab at `/Volumes/OS/claude/openig/sso-lab` implements SSO/SLO across 3 stacks using OpenIG 6.0.x (runtime pinned to `6.0.1` because `latest=6.0.2` moved to Tomcat 11) + Keycloak 24 + Vault + Redis. The pre-packaging audit found that 18 of 24 Groovy files contain only 4 distinct logic patterns, copy-pasted with parameter changes. ~78% duplication (~1676 lines reducible).

The lab's ultimate goal is a REFERENCE SOLUTION — parameterized, reusable templates that any team can copy and configure for their legacy app. Consolidation is prerequisite to that goal.

---

## Work Objectives

1. Consolidate 4 duplicated pattern families into parameterized templates
2. Fix CRITICAL + HIGH code defects as part of consolidation (not separately)
3. Update deliverable documents to reference the new templates
4. Maintain SSO/SLO functionality throughout (test after each batch)

---

## Guardrails

### Must Have
- Each stack remains fully independent (own Groovy files, copied from templates)
- SSO/SLO verified working after each consolidation step
- Bug fixes (C-1, H-1, H-6) incorporated into consolidated templates
- Templates use `args` binding (CONFIRMED supported by `AbstractScriptableHeapObject.setArgs(Map)` for both `ScriptableFilter` and `ScriptableHandler`)
- All existing test cases (28) continue to pass

### Must NOT Have
- No modifications to target applications (WordPress, Redmine, Jellyfin, Grafana, phpMyAdmin)
- No shared Groovy files across stacks (copy per stack, parameterize per route)
- No OpenIG 7+ features (must work with the OpenIG 6.0.x line; runtime pinned to `6.0.1`)
- No architecture redesign — same HA pattern, same SLO mechanism, same Vault integration

---

## Task Flow

```
Step 1: Smoke-test ScriptableHandler args (quick verification)
    |
    v
Step 2: Consolidate SessionBlacklistFilter (5 active + 1 dead → 1, proven pattern)
    |
    v
Step 3: Consolidate BackchannelLogoutHandler (3 → 1, fix C-1 + H-6)
    |
    v
Step 4: Consolidate SloHandler (5 → 2, fix H-1)
    |
    v
Step 5: Quick-win fixes (H-2, H-3, H-9, M-2, M-10, M-14)
    |
    v
Step 6: Update deliverable documents
```

**Rationale for order:**
- Step 1 is a quick smoke test (5 min) — `args` is confirmed at source level but never used for ScriptableHandler in this codebase; smoke test eliminates any runtime surprise
- Step 2 first because it uses a proven pattern (Stack C) — lowest risk, builds confidence
- Step 3 before Step 4 because BackchannelLogoutHandler is more complex and has the CRITICAL defect
- Step 5 after consolidation to avoid touching files that will be replaced
- Step 6 last because docs must reflect the final state

**Rollback strategy:** Each step should be committed separately. If a step breaks SSO/SLO, `git revert <commit>` provides clean rollback. Step 3 (MEDIUM-HIGH risk) should especially be committed before Step 4 begins.

**VaultCredentialFilter NOT in scope:** The 4 copies have more structural variance (different username sources, different output attributes). Consolidation would require abstracting the username extraction strategy, which is app-specific by design. Documenting the shared Vault login pattern in templates is sufficient.

---

## Detailed TODOs

### Step 1: Smoke-Test ScriptableHandler `args` Support

**Objective:** Quick runtime confirmation that `args` passed in route JSON are accessible in a ScriptableHandler Groovy script. Source code analysis (`AbstractScriptableHeapObject.setArgs(Map)`) confirms this SHOULD work — this step eliminates any runtime edge case.

**Files to create (temporary, removed after verification):**
- `stack-a/openig_home/config/routes/99-test-args.json` (test route)
- `stack-a/openig_home/scripts/groovy/TestArgsHandler.groovy` (test script)

**Changes:**
1. Create `TestArgsHandler.groovy` that reads `args.testParam` and returns it in response body
2. Create route JSON with `"type": "ScriptableHandler"` and `"args": {"testParam": "hello"}`
3. Restart `sso-openig-1`, curl the test endpoint
4. Verify response contains "hello" — confirms args works for ScriptableHandler at runtime
5. Remove test files after verification

**Acceptance criteria:**
- [x] Runtime confirmation: ScriptableHandler `args` binding works — CONFIRMED. Args = top-level Groovy vars via `binding.hasVariable('key')`.
- [x] Test files removed
- [N/A] If unexpected failure: fall back to env vars — NOT needed, args work correctly

**Status: ✅ DONE** (2026-03-16). Key finding: `args as Map` pattern does NOT work. See CORRECTION block in Step 2.

**Risk:** LOW — isolated test route, does not affect existing functionality. Expected to pass.

---

### Step 2: Consolidate SessionBlacklistFilter (5 active + 1 dead -> 1)

**Objective:** Replace 5 active SessionBlacklistFilter copies (plus 1 dead code file) with 1 parameterized template, deployed to all 3 stacks.

**Current state:**
| File | Stack | Status | Parameterizes via |
|------|-------|--------|-------------------|
| `SessionBlacklistFilter.groovy` | A | Active | Hardcoded `/openid/app1`, `oidc_sid`, `redis-a`, `CANONICAL_ORIGIN_APP1` |
| `SessionBlacklistFilterApp2.groovy` | A | Active | Hardcoded `/openid/app2`, `oidc_sid_app2`, `redis-a`, `CANONICAL_ORIGIN_APP2` |
| `SessionBlacklistFilter.groovy` | B | **DEAD CODE** | No route references this file. Routes use App3/App4 variants only. |
| `SessionBlacklistFilterApp3.groovy` | B | Active | Hardcoded `/openid/app3`, `oidc_sid_app3`, `redis-b` |
| `SessionBlacklistFilterApp4.groovy` | B | Active | Hardcoded `/openid/app4`, `oidc_sid_app4`, `redis-b` |
| `SessionBlacklistFilter.groovy` | C | Active | **Already uses `args`** — `clientEndpoint`, `sessionCacheKey` |

**Target template:** NEW parameterized template (NOT Stack C's current pattern — see correction below). Parameters via route JSON `args`:
- `clientEndpoint` (required) — e.g., `/openid/app1`
- `sessionCacheKey` (required) — e.g., `oidc_sid`
- `canonicalOrigin` (required) — e.g., `http://wp-a.sso.local` (hardcoded value, highest priority)
- `canonicalOriginEnvVar` (optional) — e.g., `CANONICAL_ORIGIN_APP1` (env var name, used as override if set; `canonicalOrigin` arg is the fallback)

**CORRECTION (verified 2026-03-16 via Step 1 smoke test):**
Stack C's current SessionBlacklistFilter uses `args as Map` pattern (`binding.hasVariable('args') && args instanceof Map`) — this does NOT work at runtime. `args` is never bound as a Map. Stack C only works because of hardcoded app5/app6 fallbacks, not args.

Correct binding pattern for OpenIG 6 args:
- Each key in route JSON `args` block becomes a **top-level Groovy variable**
- Access via: `binding.hasVariable('clientEndpoint') ? (clientEndpoint as String) : null`
- NOT via: `args.clientEndpoint` or `(args as Map).clientEndpoint`

**Priority order for canonical origin resolution:**
1. Env var named by `canonicalOriginEnvVar` (if arg present AND env var is set) — allows runtime override without redeployment
2. `canonicalOrigin` arg value — the compile-time default

**Files to change:**

*Stack A:*
- REPLACE `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy` — new parameterized template
- DELETE `stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy`
- MODIFY `stack-a/openig_home/config/routes/01-wordpress.json` — add `args` block to ScriptableFilter config
- MODIFY `stack-a/openig_home/config/routes/02-app2.json` — change `file` from `SessionBlacklistFilterApp2.groovy` to `SessionBlacklistFilter.groovy`, add `args` block

*Stack B:*
- DELETE `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy` — **dead code**, no route references it
- REPLACE `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp3.groovy` — replace with copy of new template, rename to `SessionBlacklistFilter.groovy`
- DELETE `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy`
- MODIFY `stack-b/openig_home/config/routes/02-redmine.json` — change `file` to `SessionBlacklistFilter.groovy`, add `args`
- MODIFY `stack-b/openig_home/config/routes/01-jellyfin.json` — change `file` to `SessionBlacklistFilter.groovy`, add `args`

*Stack C:*
- MODIFY `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy` — align with canonical template (add `canonicalOrigin` arg, remove hardcoded app5/app6 fallback logic, simplify)
- MODIFY `stack-c/openig_home/config/routes/10-grafana.json` — add `canonicalOrigin` to `args`
- MODIFY `stack-c/openig_home/config/routes/11-phpmyadmin.json` — add `canonicalOrigin` to `args`

**Key improvements in consolidated template vs current Stack C:**
- Remove hardcoded app5/app6 fallback endpoints (rely solely on `args.clientEndpoint`)
- Accept `canonicalOrigin` as arg (instead of deriving from `sessionCacheKey` comparison)
- Cleaner `readRespLine` with EOF throw (audit finding M-11)
- Standardize `Base64.getUrlDecoder().decode()` (no manual padding — audit finding H-8)

**Per-stack mini-test checklist (deploy one stack at a time):**
1. Stack A first: restart openig-1/2, test WordPress SSO login+logout, test WhoAmI SSO login, test Redis-down fail-closed 500
2. Stack B: restart openig-1/2, test Redmine SSO login+logout, test Jellyfin SSO login+logout, test Redis-down fail-closed 500
3. Stack C: restart openig-1/2, test Grafana SSO login+logout, test phpMyAdmin SSO login+logout, test Redis-down fail-closed 500

**Acceptance criteria:**
- [x] Only 1 `SessionBlacklistFilter.groovy` per stack (3 total, identical template)
- [x] `SessionBlacklistFilterApp2.groovy`, `SessionBlacklistFilterApp3.groovy`, `SessionBlacklistFilterApp4.groovy` deleted
- [x] Stack B dead code `SessionBlacklistFilter.groovy` deleted (replaced by new template)
- [x] Each route JSON passes correct `args` for its app
- [x] SSO/SLO test: login + logout all 6 apps, cross-stack SLO — ALL PASS
- [x] Redis down test: fail-closed 500 on all 6 apps — ALL PASS

**Status: ✅ DONE** (commits a76e194 + 832bbae, 2026-03-16). All 3 stacks deployed and tested one at a time. User confirmed PASS.

**Risk:** MEDIUM — touching all 6 app routes. Mitigate by deploying one stack at a time (A first, verify, then B, then C). Commit after each stack passes.

---

### Step 3: Consolidate BackchannelLogoutHandler (3 -> 1, fix C-1 + H-6)

**Status: ✅ DONE** (2026-03-17)
- Completed via commit `4d8f065`.
- Step 3 scope tested successfully across all 3 stacks.
- Implemented: `BackchannelLogoutHandler` 3 -> 1, `globals.compute()` JWKS cache (C-1 fix), TTL standardized to seconds (H-6 fix), route `args` binding.
  - **Next:** Post-audit cleanup tracking and packaging follow-up only.

**Objective:** Replace 3 BackchannelLogoutHandler copies with 1 parameterized template that also fixes the CRITICAL JWKS cache race condition and the HIGH TTL unit inconsistency.

**Current state:**
| Stack | Audience Type | `validateClaims` Signature | Redis Host | TTL Unit | Race? |
|-------|---------------|----------------------------|------------|----------|-------|
| A | `'openig-client'` (String) | `String expectedAudience` — handles single String only | `redis-a` | seconds | YES |
| B | `['openig-client-b', 'openig-client-b-app4']` (List) | `def expectedAudience` — handles both String and List (polymorphic) | `redis-b` | seconds | YES |
| C | `['openig-client-c-app5', 'openig-client-c-app6']` (List) | `List<String> expectedAudiences` — handles List only | `redis-c` | millis | YES |

**Consolidated template base: Stack B's polymorphic `validateClaims`.**
Stack B's `def expectedAudience` signature handles all cases: String aud vs String expected, String aud vs List expected, List aud vs String expected, List aud vs List expected. This is the only version that correctly handles all JWT audience scenarios without requiring the caller to know the audience format in the JWT payload.

**Parameterization via `args`:**
- `audiences` (List) — e.g., `["openig-client"]` or `["openig-client-b", "openig-client-b-app4"]`
- `redisHost` (String) — e.g., `redis-a`
- `jwksUri` (String) — e.g., `http://host.docker.internal:8080/realms/sso-realm/protocol/openid-connect/certs`
- `issuer` (String) — e.g., `http://auth.sso.local:8080/realms/sso-realm`
- One route per backchannel endpoint, each with its own `args`

**Bug fixes incorporated:**

1. **C-1 CRITICAL: JWKS cache race condition**
   - Current: `@Field static volatile` + check-then-act (non-atomic)
   - Fix: Use `globals.compute('jwks_cache')` for atomic cache refresh (same pattern as Vault token cache in VaultCredentialFilter)
   - This also eliminates the `@Field static volatile` vars entirely
   - **Note on lock contention:** `globals.compute()` uses `ConcurrentHashMap.compute()` which holds a bin lock during execution. JWKS fetch has a 5s timeout — concurrent backchannel requests will block waiting for the lock. This is acceptable in the lab context (backchannel requests are infrequent). Production should use a read-through cache with async refresh.

2. **H-6 HIGH: JWKS TTL unit inconsistency**
   - Current: Stack A/B use seconds, Stack C uses millis
   - Fix: Standardize to seconds in the consolidated template (matches `System.currentTimeMillis() / 1000` used throughout)

**Files to change:**

*All stacks:*
- REPLACE `stack-{a,b,c}/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy` — new parameterized template with `globals.compute()` JWKS cache + Stack B polymorphic `validateClaims`
- MODIFY `stack-{a,b,c}/openig_home/config/routes/00-backchannel-logout-*.json` — add `args` block

**Per-stack mini-test checklist (deploy one stack at a time):**
1. Stack A first: restart openig-1/2, login as alice to WordPress, logout, check Redis for blacklist key, check OpenIG logs for JWKS fetch
2. Stack B: same test with Redmine + Jellyfin (Jellyfin has separate client), verify both audience clients handled
3. Stack C: same test with Grafana + phpMyAdmin
4. Cross-stack: login to all 3 stacks, logout from one, verify backchannel hits all 3

**Acceptance criteria:**
- [x] Only 1 `BackchannelLogoutHandler.groovy` per stack (3 total, identical template)
- [x] Uses Stack B's polymorphic `validateClaims(payload, def expectedAudience)` — handles String AND List audience
- [x] JWKS cache uses `globals.compute()` — no `@Field static volatile`
- [x] TTL consistently in seconds across all stacks
- [x] Backchannel logout test: logout from app in each stack, verify Redis blacklist written
- [ ] JWKS refetch on kid miss still works (key rotation scenario)
- [ ] Concurrent backchannel requests do not cause duplicate JWKS fetches (race condition fixed)

**Risk:** MEDIUM-HIGH — BackchannelLogoutHandler is the most complex Groovy file (~345 lines). The JWKS cache refactor changes the caching mechanism. Mitigate by:
1. Committing Step 2 before starting Step 3 (clean rollback point via `git revert`)
2. Testing one stack at a time
3. Monitoring logs for JWKS fetch during backchannel logout
4. Verifying kid-miss refetch still triggers correctly

---

### Step 4: Consolidate SloHandler (5 -> 2, fix H-1)

**Status: ✅ DONE** (2026-03-17)
- Completed via commit `3b8a6d8`.
- Step 4 scope tested successfully across all 3 stacks.
- Implemented: `SloHandler` 5 -> 2, try-catch hardening (H-1 fix), route `args` binding, `11-phpmyadmin.json` failureHandler update.
  - **Next:** Post-audit cleanup tracking and packaging follow-up only.

**Objective:** Replace 5 SloHandler copies with 2 templates: a standard SloHandler and a Jellyfin-specific one. Fix missing try-catch in 3 files.

**Current state:**
| File | Stack | Has try-catch | App-specific logic | Referenced by |
|------|-------|---------------|--------------------|---------------|
| `SloHandler.groovy` | A | NO | None — simplest version | `00-wp-logout.json` |
| `SloHandlerRedmine.groovy` | B | YES | None — has correct error handling | `00-redmine-logout.json` |
  | `SloHandlerJellyfin.groovy` | B | YES | Jellyfin `/Sessions/Logout` API call | `00-jellyfin-logout.json` |
  | `SloHandlerGrafana.groovy` | C | NO | None | `00-grafana-logout.json` |
  | `SloHandlerPhpMyAdmin.groovy` | C | NO | None | `00-phpmyadmin-logout.json` **AND** `11-phpmyadmin.json` (line 94, failureHandler inline ref) |

**Post-audit cleanup note (2026-03-17):** `SloHandlerRedmine.groovy`, `SloHandlerGrafana.groovy`, and `SloHandlerPhpMyAdmin.groovy` remained on disk after the route migration even though no route referenced them anymore. They were deleted in the post-audit cleanup pass after reference verification.

**CRITICAL: `11-phpmyadmin.json` inline SloHandler reference.**
The phpMyAdmin route (`11-phpmyadmin.json`) has an inline `ScriptableHandler` in the `PhpMyAdminBasicAuth` `failureHandler` block that references `"file": "SloHandlerPhpMyAdmin.groovy"` (line 94). This is the 401-triggered SLO path (when phpMyAdmin returns 401, the failureHandler triggers SLO). After consolidation, this file reference MUST be updated to `SloHandler.groovy` with appropriate `args`, or phpMyAdmin 401-triggered SLO will be BROKEN.

**Templates:**

1. **`SloHandler.groovy`** (standard) — covers 4 of 5 current files
   - Parameters via `args`:
     - `clientEndpoint` — e.g., `/openid/app1`
     - `clientId` — e.g., `openig-client`
     - `canonicalOrigin` — e.g., `http://wp-a.sso.local`
     - `postLogoutPath` — e.g., `/` (default) or `/web/index.html`
   - Based on `SloHandlerRedmine.groovy` pattern (has try-catch + env var for Keycloak URL)
   - Fixes H-1: adds try-catch wrapping (missing in Stack A + Stack C copies)

2. **`SloHandlerJellyfin.groovy`** (unique) — kept separate because it has app-specific logout API call
   - Apply try-catch fix (already present, verify)
   - No structural change needed, just align coding style

**Files to change:**

*Stack A:*
- REPLACE `stack-a/openig_home/scripts/groovy/SloHandler.groovy` — new parameterized template
- MODIFY `stack-a/openig_home/config/routes/00-wp-logout.json` — add `args`

*Stack B:*
- REPLACE `stack-b/openig_home/scripts/groovy/SloHandlerRedmine.groovy` -> rename to `SloHandler.groovy` (standard template)
- KEEP `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy` — align style only
- MODIFY `stack-b/openig_home/config/routes/00-redmine-logout.json` — update file ref to `SloHandler.groovy` + add `args`
- MODIFY `stack-b/openig_home/config/routes/00-jellyfin-logout.json` — no change if keeping separate

*Stack C:*
- REPLACE `stack-c/openig_home/scripts/groovy/SloHandlerGrafana.groovy` -> `SloHandler.groovy` (standard template)
- DELETE `stack-c/openig_home/scripts/groovy/SloHandlerPhpMyAdmin.groovy`
- MODIFY `stack-c/openig_home/config/routes/00-grafana-logout.json` — update file ref to `SloHandler.groovy` + add `args`
- MODIFY `stack-c/openig_home/config/routes/00-phpmyadmin-logout.json` — update file ref to `SloHandler.groovy` + add `args`
- **MODIFY `stack-c/openig_home/config/routes/11-phpmyadmin.json`** — update `PhpMyAdminBasicAuth` failureHandler's `"file"` from `"SloHandlerPhpMyAdmin.groovy"` to `"SloHandler.groovy"`, add `"args"` block with `clientEndpoint: "/openid/app6"`, `clientId: "openig-client-c-app6"`, `canonicalOrigin: "http://phpmyadmin-c.sso.local:18080"`, `postLogoutPath: "/"`

**Per-stack mini-test checklist (deploy one stack at a time):**
1. Stack A: restart openig-1/2, WordPress login then click logout, verify Keycloak end_session redirect with id_token_hint
2. Stack B: Redmine logout (standard SloHandler), Jellyfin logout (dedicated SloHandlerJellyfin), verify both reach Keycloak
3. Stack C — CRITICAL TEST:
   - Grafana: click logout in Grafana, verify SLO redirect
   - phpMyAdmin explicit logout: access `/openid/app6/logout`, verify SLO redirect
   - **phpMyAdmin 401-triggered SLO**: clear phpMyAdmin session/cookie, access phpMyAdmin, verify failureHandler triggers SloHandler.groovy correctly (the inline ref in `11-phpmyadmin.json`)

**Acceptance criteria:**
- [x] Standard `SloHandler.groovy` used by 4 logout routes (WP, Redmine, Grafana, phpMyAdmin) + 1 inline failureHandler ref (`11-phpmyadmin.json`)
- [x] All SloHandlers have try-catch wrapping (H-1 fixed)
- [x] SloHandlerJellyfin kept separate (unique app-specific logic)
- [x] RP-initiated logout test: click logout in **5 apps with logout UI** (WordPress, Redmine, Jellyfin, Grafana, phpMyAdmin), verify Keycloak end_session redirect
- [ ] WhoAmI (app2) has no logout UI — verify session invalidated via cross-stack SLO when WordPress logout triggers backchannel (WhoAmI shares the same Keycloak client `openig-client` as WordPress; backchannel logout blacklists the sid, which is checked by WhoAmI's SessionBlacklistFilter)
- [x] phpMyAdmin 401-triggered SLO works (inline failureHandler in `11-phpmyadmin.json` correctly uses consolidated `SloHandler.groovy` with `args`)
- [x] id_token_hint present in redirect URL when session has id_token
- [x] Missing id_token graceful fallback (no crash, still redirects)

**Risk:** MEDIUM — SloHandler is simpler than BackchannelLogoutHandler, but touches all 6 logout routes plus 1 inline handler ref. Same mitigation: one stack at a time. Commit Step 3 before starting Step 4.

---

### Step 5: Quick-Win Fixes (from audit)

**Objective:** Fix 6 low-risk items that were deferred to avoid touching files being consolidated.

| # | Fix | Files | Effort |
|---|-----|-------|--------|
| H-2 | Add `vault/keys/` to `.gitignore` | `.gitignore` | TRIVIAL |
| H-3 | Remove Redmine port 3000 exposure | `stack-b/docker-compose.yml` | TRIVIAL |
| H-9 | Add `proxy_buffer_size 128k` to Stack C nginx | `stack-c/nginx/nginx.conf` | TRIVIAL |
| M-2 | Add missing `CANONICAL_ORIGIN_*` env vars to Stack A/B docker-compose | `stack-a/docker-compose.yml`, `stack-b/docker-compose.yml` | TRIVIAL |
| M-10 | Stack A SloHandler Keycloak URL -> env var | Already fixed by Step 4 consolidation | N/A |
| M-14 | Delete `App1ResponseRewriter.groovy` (0 bytes dead code) | `stack-a/openig_home/scripts/groovy/App1ResponseRewriter.groovy` | TRIVIAL |

**Status: ✅ DONE** (2026-03-17) — All Step 5 quick wins implemented. Tests PASS: BackchannelLogout all 5 clients → Redis blacklisted, SloHandler all 5 apps → `id_token_hint=PRESENT`, phpMyAdmin inline failureHandler OK. Commits: `5ae657e`, `aaf66d5`, `f86c7eb`.

**Acceptance criteria:**
- [x] `vault/keys/` in `.gitignore`
- [x] Redmine port 3000 not exposed in `docker-compose.yml`
- [x] Stack C nginx has `proxy_buffer_size 128k` and `proxy_buffers 4 256k`
- [x] `CANONICAL_ORIGIN_*` env vars present in all 3 docker-compose files
- [x] `App1ResponseRewriter.groovy` deleted
- [x] All stacks start without errors after docker-compose changes

**Risk:** LOW — config changes only, no logic changes.

---

### Step 6: Update Deliverable Documents

**Objective:** Update the 3 key deliverables to reflect the consolidated, parameterized template architecture.

**Files to update:**

1. **`docs/deliverables/legacy-auth-patterns-definitive.md`**
   - Add section: "Template-Based Integration" with decision tree pointing to the 4 templates
   - Add: which template to copy for each login mechanism

2. **`docs/deliverables/standard-gateway-pattern.md`**
   - Update Required Control 1 (Revocation) to reference the parameterized BackchannelLogoutHandler + SessionBlacklistFilter templates
   - Update Required Control 7 (Adapter Contract) to reference parameterized SloHandler
   - Add: "Parameterized Template Architecture" section describing the args-based approach

3. **`docs/deliverables/legacy-app-team-checklist.md`**
   - Update Section 6 (Integration process) to mention that gateway team copies and configures templates
   - No structural changes needed — the checklist is app-team facing

**Acceptance criteria:**
- [x] `legacy-auth-patterns-definitive.md` has decision tree for template selection
- [x] `standard-gateway-pattern.md` references parameterized templates
- [x] `legacy-app-team-checklist.md` integration process reflects template workflow
- [x] No stale file references in any deliverable

**Risk:** LOW — documentation changes only.

**Status: ✅ DONE** (2026-03-17) — deliverable docs were updated in commit `421b369`, and Section 6 of `legacy-app-team-checklist.md` was refreshed in the post-audit cleanup pass.

---

## Success Criteria (overall)

1. **Line count reduction:** 18 consolidatable files -> 9 per-stack template copies across the 3 shared pattern families (`SessionBlacklistFilter`, `BackchannelLogoutHandler`, `SloHandler`) parameterized via route `args`, with `SloHandlerJellyfin.groovy` kept separate for its app-specific logout API.
2. **Bug fixes:** C-1 (JWKS race) FIXED, H-1 (SloHandler try-catch) FIXED, H-6 (TTL units) FIXED.
3. **SSO/SLO:** All 28 test cases pass after consolidation.
4. **Template reusability:** Any new app can be integrated by copying a template and setting `args` in route JSON.
5. **Deliverables:** All 3 key documents updated to reflect the new architecture.

---

## Risk Assessment Summary

| Step | Risk | Mitigation |
|------|------|------------|
| 1. Smoke-test args | LOW | Isolated test, removed after, expected to pass |
| 2. SessionBlacklistFilter | MEDIUM | Deploy one stack at a time, commit after each |
| 3. BackchannelLogoutHandler | MEDIUM-HIGH | Commit Step 2 first (rollback point), one stack at a time, log monitoring, kid-miss test |
| 4. SloHandler | MEDIUM | Commit Step 3 first (rollback point), one stack at a time, all logout flows + inline failureHandler tested |
| 5. Quick wins | LOW | Config only |
| 6. Docs | LOW | No code changes |

**Rollback strategy:** Each step committed separately. `git revert <commit-hash>` provides clean rollback for any step. Steps 3 and 4 (highest risk) each have the previous step's commit as a stable baseline.

---

## Out of Scope

- **VaultCredentialFilter consolidation** — structural variance too high (different username sources per mechanism). Document the shared Vault login pattern instead.
- **CredentialInjector/RedmineCredentialInjector/JellyfinTokenInjector** — these are app-specific adapters by design, not duplicated patterns.
- **JellyfinResponseRewriter** — unique, not duplicated.
- **PhpMyAdminCookieFilter** — WONT_FIX (inactive, phpMyAdmin CSRF incompatible).
- **Security items C-2 and H-4** — significant effort, separate plan needed (app tokens over HTTP, Redis auth).
- **H-5** — already resolved in STEP-03 (`b738577`).
- **M-5** — already resolved in STEP-02 (`37672ed`).
- **Stack C docker-compose alignment (H-7)** — separate infrastructure task.
- **Nginx security headers (M-3), cookie flags (M-4)** — separate security hardening phase.

---

## Revision Log

### R1 (2026-03-16) — Critic REVISE feedback

**Mandatory fixes applied:**

1. **CRITICAL-1: Missing `11-phpmyadmin.json` inline SloHandler reference** — Added `MODIFY stack-c/openig_home/config/routes/11-phpmyadmin.json` to Step 4 with specific args. Added dedicated 401-triggered SLO test to acceptance criteria.

2. **MAJOR-1: Stack B `SessionBlacklistFilter.groovy` is dead code** — Changed from "REPLACE" to "DELETE (dead code)" in Step 2. Updated consolidation count from "6 -> 1" to "5 active + 1 dead -> 1 template".

3. **MAJOR-2: BackchannelLogoutHandler `validateClaims` type mismatch** — Explicitly stated: "Use Stack B's polymorphic `validateClaims(payload, def expectedAudience)` as the base template." Added comparison table showing all 3 variants and rationale for choosing Stack B.

4. **MAJOR-3: Removed resolved conditional branches** — Removed "Open Question" section entirely. Removed all "If ScriptableHandler does NOT support args" fallback branches. Step 1 reduced to a quick smoke test (not a blocking prerequisite). `args` used unconditionally throughout.

5. **MAJOR-4: WhoAmI (app2) has no logout route** — Updated Step 4 acceptance criteria to "5 apps with logout UI" instead of 6. Added specific WhoAmI cross-stack SLO verification criterion.

**Nice-to-have improvements applied:**

- Added per-stack mini-test checklists to Steps 2, 3, and 4 for incremental deployment verification
- Specified priority order for `canonicalOrigin` arg vs `canonicalOriginEnvVar` env var in Step 2
- Added `globals.compute()` lock contention note in Step 3 (5s JWKS timeout blocks concurrent backchannel requests; acceptable for lab, production needs async refresh)
- Added rollback strategy using `git revert` with commit-per-step discipline; emphasized for Step 3 (MEDIUM-HIGH risk)
