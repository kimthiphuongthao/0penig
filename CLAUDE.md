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

### Pending
- [ ] SLO test thủ công: stack-b (Redmine + Jellyfin), stack-c (Grafana + phpMyAdmin)
- [ ] Cross-stack SLO test
- [ ] Fix Jellyfin WebSocket: `http://` → `ws://` trong `stack-b/openig_home/config/routes/01-jellyfin.json`
- [ ] Stack B cookieDomain (LOW priority)

### Phase tiếp theo
- [ ] Phase 3: Vault Production Hardening — xem `docs/vault-hardening-gaps.md`
- [ ] Post-Stack C docs: OpenIG built-in filter selection guide

## Compact instructions
When using /compact, focus on: pending tasks, recent decisions, bugs found, current stack status.
