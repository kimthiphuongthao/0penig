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

## Roadmap

### Đã hoàn thành
- [x] Stack A: WordPress + WhoAmI SSO/SLO
- [x] Stack B: Redmine + Jellyfin SSO/SLO
- [x] Stack C: Grafana + phpMyAdmin SSO/SLO
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
- [ ] FIX-09: Remove sensitive material from JwtSession (B+C) — Vault tokens done (76b648a), phpMyAdmin creds still in session → next: direct Auth header injection
- [x] Stack B clientEndpoint collision fix: Redmine `/openid/app4` → `/openid/app3`, xóa dotnet routes, Keycloak updated
- [x] Stack B Jellyfin SLO re-login: `post_logout_redirect_uri` → `/web/index.html`, spinner remaining (app permission, not SSO)
- [x] Docs reorganize: `docs/` → `deliverables/`, `testing/`, `reference/`, `historical/` (8 deleted, 22 moved)
- [x] Docs update: sso-workflow-security H8 IMPLEMENTED (Tier 3→2), integration guide +Stack C, audit scores, gateway pattern B F5 RESOLVED
- [x] **Legacy App Team Checklist**: `docs/deliverables/legacy-app-team-checklist.md` — file tối thượng, 3-reviewer QA (Critic+Gemini+Codex)

### Pending
- [ ] Workaround: admin "Logout all sessions" chưa trigger backchannel logout

### Phase tiếp theo
- [ ] **Fix phase**: implement fixes theo `.omc/plans/fix-phase-openig-gaps.md` (15 fixes, Batch 0→7) — tracking: `docs/fix-phase/checklist.md`
- [ ] Phase 3: Vault Production Hardening — xem `docs/reference/vault-hardening-gaps.md`
- [ ] Post-Stack C docs: OpenIG built-in filter selection guide
- [ ] Redis persistence (appendonly yes) — đảm bảo SLO blacklist survive restart
- [ ] Vault audit logging
- [ ] Đóng gói: OVA / Docker Compose bundle — single-command deploy
- [ ] Slide + tài liệu báo cáo phương án giải pháp

## File tối thượng — Legacy App Team Checklist
- Path: `docs/deliverables/legacy-app-team-checklist.md`
- Quan trọng nhất trong toàn bộ deliverables — mọi tài liệu khác phục vụ đầu vào cho file này
- Xây dựng liên tục xuyên suốt project, check sau mỗi task/thay đổi
- KHÔNG update lung tung — chỉ update khi có evidence từ code/test thực tế

## Compact instructions
When using /compact, focus on: pending tasks, recent decisions, bugs found, current stack status.
