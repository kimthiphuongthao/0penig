# GEMINI.md — SSO Lab Research Context

Gemini là research-only agent trong workflow này. Đọc file này trước khi thực hiện bất kỳ task nào.

## Vai trò của Gemini

- ✅ Research, web search, đọc source code thư viện
- ✅ Phân tích log, trace lỗi, feasibility validation
- ✅ Viết và cập nhật file `.md` (docs, reports, checklists)
- ❌ KHÔNG viết code, config, scripts dù bất kỳ lý do gì
- ❌ KHÔNG sửa file .groovy, .json, .sh, .yml, .conf, .xml, .hcl

Mọi implementation giao cho **Codex**. Gemini chỉ báo cáo findings cho Claude.

## Project overview

SSO lab: tích hợp legacy apps vào SSO qua OpenIG 6 + Keycloak 24 + Vault + Redis.

- **stack-a** (port 80): WordPress, WhoAmI — form login injection
- **stack-b** (port 9080): Redmine, Jellyfin — form login + token injection
- **stack-c** (port 18080): Grafana, phpMyAdmin — header injection + HTTP Basic Auth
- **Keycloak** (port 8080): shared IDP, realm `sso-realm`

Đọc `CLAUDE.md` ở root repo để biết đầy đủ architecture, container names, URLs, credentials.

## Codebase structure

```
sso-lab/
├── CLAUDE.md                    ← project instructions (đọc trước)
├── keycloak/                    ← Keycloak compose + realm export
├── stack-a/
│   ├── docker-compose.yml
│   ├── nginx/nginx.conf
│   ├── openig_home/
│   │   ├── config/config.json   ← JwtSession, cookie IG_SSO (A), IG_SSO_B (B), IG_SSO_C (C)
│   │   ├── config/routes/       ← OpenIG route definitions
│   │   └── scripts/groovy/      ← Groovy filter scripts
│   └── vault/
│       ├── config/vault.hcl
│       └── init/vault-bootstrap.sh
├── stack-b/                     ← tương tự stack-a
├── stack-c/                     ← tương tự stack-a
└── docs/
    ├── deliverables/            ← legacy-app-team-checklist.md, standard-gateway-pattern.md, ...
    ├── testing/                 ← test-cases.md (28 cases), test-report.md
    ├── reference/               ← vault-hardening-gaps.md
    └── audit/                   ← 2026-03-16-pre-packaging-audit/ (8 docs)
```

## Decisions đã được đưa ra (không reinvent)

- **HA**: nginx `ip_hash` + JwtSession (stateless cookie) — đã test, không dùng Redis session store
- **Vault**: file storage mode, `command: server` only trong docker-compose
- **SLO**: backchannel logout → Redis blacklist, mỗi stack có Redis riêng
- **Cookie**: Stack B đã cấu hình `cookieDomain` đầy đủ — issue này đã resolved
- **phpMyAdmin**: dùng `config.user.inc.php` mount, không dùng `PMA_AUTH_TYPE` env var

## Trạng thái hiện tại

- Pattern Consolidation đang thực hiện: Step 1+2 DONE (SessionBlacklistFilter 6→1), Step 3 in progress (BackchannelLogoutHandler Stack A done, chưa test)
- Tất cả known bugs đã fix — xem CLAUDE.md roadmap để biết trạng thái đầy đủ
