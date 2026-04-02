# CLAUDE.md — SSO Lab

Lab SSO tích hợp legacy apps vào SSO/SLO qua OpenIG 6 + Keycloak 24 + Vault + Redis.
Mục tiêu tối thượng: REFERENCE SOLUTION cho 5 login mechanisms (Form/Basic/Header/Token/LDAP) — app cụ thể chỉ là ví dụ, pattern phải generalize được. Xem `docs/deliverables/legacy-auth-patterns-definitive.md`.
Path: `/Volumes/OS/claude/openig/sso-lab`

## Rules (auto-loaded)
@.claude/rules/workflow.md
@.claude/rules/architecture.md
@.claude/rules/conventions.md
@.claude/rules/gotchas.md
@.claude/rules/restart.md

## Architecture snapshot

| Runtime | Public access | Shared components | App isolation |
|---------|---------------|-------------------|---------------|
| `shared/` | Hostname routing on port 80 | `shared-nginx`, `shared-openig-1/2`, `shared-redis`, `shared-vault` | `SessionApp1..6`, `IG_SSO_APP1..APP6`, per-app Redis ACL, per-app Vault AppRole |

## Roadmap

### Đã hoàn thành
- [x] Stack A: WordPress + WhoAmI SSO/SLO
- [x] Stack B: Redmine + Jellyfin SSO/SLO
- [x] Stack C: Grafana + phpMyAdmin SSO/SLO
- [x] Fix 4 SSO/SLO logic bugs: BUG-002 nginx callback retry, AUD-003 JWKS null-cache, DOC-007 TokenRef fail-closed, AUD-009 SloHandler legacy fallbacks
- [x] Vault migration: dev mode → file storage (all stacks)
- [x] Test cases + test report (`docs/`)
- [x] CLAUDE.md, .gemini/GEMINI.md, .claude/rules/ setup
- [x] SLO Stack C: backchannel routes, id_token_hint, SloHandlerGrafana/PhpMyAdmin, KEYCLOAK_BROWSER_URL
- [x] Cross-stack SLO: Jellyfin backchannel fix (openig-client-b-app4), tất cả 5 endpoints đã đăng ký
- [x] Stack B cookieDomain: thêm ".sso.local" vào JwtSession config.json
- [x] H8: Backchannel Logout JWT Validation — RS256 signature, claims validation, JWKS cache, audience fix
- [x] Jellyfin WebSocket: không liên quan SSO/SLO, đã loại khỏi scope
- [x] Docs sync: .claude/rules/ + CLAUDE.md alignment (2026-03-14)
- [x] Code+security review: Stack A/B/C — evidence files tại `docs/reviews/2026-03-14-*.md`
- [x] Cross-stack review summary: `docs/reviews/2026-03-14-cross-stack-review-summary.md`
- [x] Standard Gateway Pattern v1.1: `docs/deliverables/standard-gateway-pattern.md` (priority fix, confidence metadata, Confirmed Strengths section)
- [x] Legacy auth patterns definitive reference: `docs/deliverables/legacy-auth-patterns-definitive.md` (5 mechanisms, 5 logout types, security checklist)
- [x] Workflow evaluation: A/B test Planner vs Codex (2 rounds, 2026-03-15) → Planner wins, workflow.md updated
- [x] FIX-01: Jellyfin logout — namespace app3→app4, client_id OIDC_CLIENT_ID_APP4, post_logout_redirect_uri, Keycloak client openig-client-b-app4
- [x] FIX-02: Redis TTL 3600→28800 + RESP prefix fix (3 stacks)
- [x] FIX-03+04: Fail-closed 500 + socket timeout 200/500ms (3 stacks, 9 files)
- [x] FIX-05: Backchannel error code 400→500 for infra errors (3 stacks)
- [x] FIX-06: Externalize secrets to env vars — docker-entrypoint.sh + ${env['VAR']} (3 stacks, 16 files)
- [x] FIX-07: HTTPS Enforcement Phase 7a — Lab Exception notes in standard-gateway-pattern.md (docs only, Phase 7b deferred)
- [x] FIX-08: Pin redirect base URLs — CANONICAL_ORIGIN_APPx env vars + fallbacks (3 stacks, 11 Groovy files)
- [x] Stack B PKCS12 keystore — JwtSession encryption pattern consistency (all 3 stacks now identical)
- [x] Stack B nginx ip_hash — replace broken hash $cookie_JSESSIONID with ip_hash (match Stack A/C)
- [x] FIX-09: Remove sensitive material from JwtSession (B+C) — Vault tokens (76b648a), phpMyAdmin creds + grafana_username (c0c491d). attributes EL + globals Vault token cache
- [x] Stack B clientEndpoint collision fix: Redmine `/openid/app4` → `/openid/app3`, xóa dotnet routes, Keycloak updated
- [x] Stack B Jellyfin SLO re-login: `post_logout_redirect_uri` → `/web/index.html`, spinner remaining (app permission, not SSO)
- [x] Docs reorganize: `docs/` → `deliverables/`, `testing/`, `reference/`, `historical/` (8 deleted, 22 moved)
- [x] Docs update: sso-workflow-security H8 IMPLEMENTED (Tier 3→2), integration guide +Stack C, audit scores, gateway pattern B F5 RESOLVED
- [x] FIX-10: WONT_FIX — PhpMyAdminCookieFilter Token mismatch (phpMyAdmin CSRF incompatible)
- [x] FIX-11: WONT_FIX — Token injection pattern constraint (SPA requires localStorage)
- [x] FIX-12: sid/sub consistency verified — no mismatch across all stacks
- [x] FIX-13: SloHandler id_token_hint log redaction (Stack A)
- [x] FIX-14+15: Unsafe method reauth 409 + WordPress adapter fail-closed (Stack A)
- [x] Entrypoint cp -r stale config fix — rm -rf before cp (all 3 stacks)
- [x] **Legacy App Team Checklist**: `docs/deliverables/legacy-app-team-checklist.md` — file tối thượng, 3-reviewer QA (Critic+Gemini+Codex)
- [x] **Code review + security review round 2**: 25 fixes (3 rounds, 6 subagent + 2 Codex). Gateway cookie hardening, atomic `globals.compute()`, RESP parsing, admin PRODUCTION, nginx hardening, dead code cleanup, Vault policy fix, etc.
- [x] **Pre-packaging comprehensive audit**: 6 agents, 8 docs at `docs/audit/2026-03-16-pre-packaging-audit/`. Findings: 0/24 Groovy replaceable by built-in, 78% duplication (7 patterns), JWKS race (CRITICAL). ScriptableHandler `args` confirmed YES.
- [x] Post-Stack C docs: OpenIG built-in filter selection guide — covered by audit Task 1A/1B docs
- [x] OpenIG built-in gap analysis: verified 0/14 Groovy scripts replaceable by built-ins; 12 capability gaps documented (`docs/deliverables/openig-builtin-gap-analysis.md`, 2026-03-25)
- [x] Workaround: admin "Logout all sessions" — session timeout 30min + access token 5min (max 5min delay)
- [x] Pattern Consolidation Step 3: BackchannelLogoutHandler 3→1, `globals.compute()` JWKS cache fix (C-1), TTL seconds fix (H-6), args binding (`4d8f065`)
- [x] Pattern Consolidation Step 4: SloHandler 5→2, try-catch fix (H-1), phpMyAdmin inline failureHandler update (`3b8a6d8`)
- [x] Pattern Consolidation Step 5: Quick-win fixes complete - H-2 `.gitignore` for `vault/keys/` (`5ae657e`), H-3 Redmine port 3000 removed, H-9 Stack C proxy buffers aligned, M-2 `CANONICAL_ORIGIN_APP*` added to Stack A/B docker-compose, M-14 dead code deleted; validation PASS for all 5 backchannel clients, all 5 logout redirects, phpMyAdmin inline failureHandler (`aaf66d5`, `f86c7eb`)
- [x] Vault keys purged from git history and branch force-pushed after cleanup
- [x] STEP-01 (L-5): deleted `PhpMyAdminCookieFilter.groovy` dead code (`20d523f`)
- [x] STEP-02 (M-5/S-9): rotated Stack C OIDC client secrets to strong values (`37672ed`)
- [x] STEP-03 (H-5/S-3): externalized secrets to gitignored `.env` files and pinned all OpenIG images to `openidentityplatform/openig:6.0.1` (`b738577`)
- [x] Phase 1 prep: Stack A `CredentialInjector.groovy` refactored to browser cookie pass-through on `fix/jwtsession-production-pattern` (`78e2128`)
- [x] Phase 1 prep: Stack B `RedmineCredentialInjector.groovy` refactored to browser cookie pass-through on `fix/jwtsession-production-pattern` (`895e401`)
- [x] Keycloak token-size trim: removed `realm_access` and `resource_access` from `access_token` for `openig-client`, `openig-client-b`, `openig-client-c-app5`, and `openig-client-c-app6` (persistent runtime/admin API change)
- [x] Stack C recovery: Vault unsealed, AppRole refreshed, `secret/phpmyadmin/alice` realigned to live MariaDB password `AlicePass123`, Grafana + phpMyAdmin login/logout reconfirmed (`6cc3fc9` + 2026-03-19 log evidence)
- [x] Phase 1: restore `JwtSession` production pattern — rename heap `JwtSession` -> `Session`, switch 4 OpenIG clients to `ES256`, disable `refresh_token` (`0454796`)
- [x] Phase 2: Redis Token Reference Pattern — `TokenReferenceFilter.groovy`, dynamic oauth2 session key discovery, browser-bound session cookies reduced to production-safe size (`9b2d109`, `47cbab9`)
- [x] BackchannelLogoutHandler ES256/EC fix — accept `ES256` alg + EC key reconstruction + `SHA256withECDSA` (`646a45a`, `d2eb8e9`)
- [x] Full validation login+logout all 3 stacks on `fix/jwtsession-production-pattern` — PASS (2026-03-19)
- [x] L-1 + L-3: Redis port externalized from hardcoded `6379` and Groovy log prefixes standardized across stacks (`8f17e7b`)
- [x] L-2: Backchannel Redis blacklist TTL externalized from hardcoded `28800` via route args + env-backed defaults (`d2a0411`)
- [x] L-4 + L-6: `SloHandlerJellyfin.groovy` now proceeds without `id_token` and Jellyfin `deviceId` derives from stable `sub` hash (`e4485f1`)
- [x] Code-M3: Stack B `VaultCredentialFilter.groovy` consolidated into a single parameterized script; Redmine/Jellyfin copies deleted (`e22a855`)
- [x] Regression fix: `TokenReferenceFilter.groovy` now binds per-app `tokenRefKey` (`token_ref_id_app1` .. `token_ref_id_app6`) to prevent cross-app same-cookie contamination (`8e9f729`)
- [x] Shared infra consolidation: single `shared-nginx`, `shared-openig-1/2`, `shared-redis`, and `shared-vault` now serve all 6 apps from `shared/` on port 80 via hostname routing
- [x] Shared infra validation: all 6 apps are SSO/SLO PASS on the shared runtime
- [x] Per-app Redis ACL isolation: `openig-app1..6` with minimal command set and `appN:*` key prefixes
- [x] Per-app Vault AppRole isolation: `openig-app1..6` with path-scoped policies and per-app `role_id` / `secret_id` files
- [x] Security fixes: `AUD-001`, `AUD-004`, `AUD-008` hardcoded secrets removed; `AUD-005` fail-closed offload implemented; Vault admin token TTL set with periodic renewal
- [x] Full security audit: OpenIG / Vault / Redis communication audit completed
- [x] Shared infra SSO-after-SLO fix: `TokenReferenceFilter.groovy` preserves pending OAuth2 state while removing stale real-token entries via `hasPendingState` (`5fb549d`); verified after restart `2026-03-24T02:29:40Z` with no `invalid_token`, `no authorization in progress`, or `Missing Redis` on `shared-openig-2` during user SSO/SLO testing

### Phase tiếp theo
- JwtSession production-pattern work is merged to `main` (`fix/jwtsession-production-pattern` was created from `9a7b855` before rename experiment commit `e37536d`)
- Backup branch: `feat/subdomain-test` at `cdb5425` = working HttpSession fallback snapshot
- [x] **Fix phase COMPLETE**: 15 fixes (11 implemented, 2 WONT_FIX, 2 verified-no-action) — tracking: `docs/fix-phase/checklist.md`
- [x] Phase 3: Vault Production Hardening — 6/9 RESOLVED, 1 PARTIAL (CIDR), 2 deferred (TLS, Raft)
- [x] STEP-03 (H-5/S-3): Secrets externalization to `.env` files + pin OpenIG image to `6.0.1`
- [x] STEP-04 (H-4/S-2): Redis authentication — requirepass + AUTH command (all 3 stacks)
- [x] STEP-05 (A-6/A-7/M-13/S-17): Keycloak URL externalization Stack A + C
- [x] STEP-06 (H-7/A-1): Stack C docker-compose parity + Stack A healthcheck
- [x] STEP-07 (M-9/Code-M6): Vault error 502 consistency Stack B + C
- [x] STEP-08 (M-11): BackchannelLogoutHandler readRespLine EOF fail-closed
- [x] STEP-09 (M-12): base64UrlDecode simplification (all 3 stacks)
- [x] STEP-10 (A-3/S-14): Stack C nginx proxy timeouts alignment
- [x] STEP-11 (A-4): extra_hosts host.docker.internal Linux portability (all 3 stacks)
- [x] STEP-12 (M-3/S-7): nginx security headers (X-Frame-Options, X-Content-Type-Options, Referrer-Policy)
- [x] STEP-13 (M-4/S-8): Cookie SameSite=Lax flags via proxy_cookie_flags (all 3 stacks nginx.conf)
- [x] STEP-14 (M-6/S-10): OpenIG non-root — macOS vault permission constraint, comment added (PARTIAL)
- [x] Post-Stack C docs: OpenIG built-in filter selection guide — covered by audit
- [x] Pattern Consolidation Steps 1-5 complete - Step 5 quick wins (H-2 .gitignore, H-3 Redmine port, H-9 nginx buffers, M-2 CANONICAL_ORIGIN A+B, M-14 dead code) are done and validated
- [x] Pattern Consolidation Step 6: Update deliverable documents. Plan: `.omc/plans/pattern-consolidation.md`
- [x] Redis persistence (appendonly yes) — đảm bảo SLO blacklist survive restart
- [x] Vault audit logging
- [x] Stack C Grafana SSO re-validation/fix — root cause: OpenIG OAuth2ClientFilter không URL-encode client_secret; Base64 secret chứa '+' → Keycloak decode thành space → invalid_client_credentials. Fix: rotate secret alphanumeric-only (commit a403b3d)
- [x] Merge `fix/jwtsession-production-pattern` -> `main`
- [x] Provision MariaDB user `bob` for Stack C phpMyAdmin or document alice-only support explicitly
- [ ] Đóng gói: OVA / Docker Compose bundle — single-command deploy
- [ ] Slide + tài liệu báo cáo phương án giải pháp

## File tối thượng — Legacy App Team Checklist
- Path: `docs/deliverables/legacy-app-team-checklist.md`
- Quan trọng nhất trong toàn bộ deliverables — mọi tài liệu khác phục vụ đầu vào cho file này
- Xây dựng liên tục xuyên suốt project, check sau mỗi task/thay đổi
- KHÔNG update lung tung — chỉ update khi có evidence từ code/test thực tế

## Compact instructions
When using /compact, focus on: pending tasks, recent decisions, bugs found, current stack status.
