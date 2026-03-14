# CLAUDE.md — SSO Lab

Lab SSO tích hợp legacy apps vào SSO/SLO qua OpenIG 6 + Keycloak 24 + Vault + Redis.
Path: `/Volumes/OS/claude/openig/sso-lab`, branch: `feat/subdomain-test`

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

### Pending
- [ ] Workaround: admin "Logout all sessions" chưa trigger backchannel logout

### Phase tiếp theo
- [ ] Phase 3: Vault Production Hardening — xem `docs/vault-hardening-gaps.md`
- [ ] Post-Stack C docs: OpenIG built-in filter selection guide
- [ ] Redis persistence (appendonly yes) — đảm bảo SLO blacklist survive restart
- [ ] Vault audit logging
- [ ] Security hardening: secrets externalization, Redis fail-closed, TLS — see docs/reviews/2025-03-13-security-review-h8-jwt.md
- [ ] Đóng gói: OVA / Docker Compose bundle — single-command deploy
- [ ] Slide + tài liệu báo cáo phương án giải pháp

## Compact instructions
When using /compact, focus on: pending tasks, recent decisions, bugs found, current stack status.
