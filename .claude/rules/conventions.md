# Conventions

## Nginx — F5 BIG-IP alignment
- Upstream pool naming: `<app>_pool` (ví dụ: `grafana_pool`, `wordpress_pool`)
- Luôn có `proxy_set_header Host $host` và `X-Real-IP $remote_addr`
- Strip trusted headers từ client trước khi inject: `proxy_set_header X-WEBAUTH-USER ""`
- Dùng `ip_hash` cho sticky routing, `keepalive` cho OneConnect pattern

## Vault bootstrap
- HCL multi-line (KHÔNG semicolon)
- `command: server` trong docker-compose (KHÔNG explicit `-config` path)
- Fix exit code: `code=$(vault status >/dev/null 2>&1; echo $?)` + `|| true` cho pipelines

## Docs
- Mọi docs nằm trong `docs/` với tên lowercase
- `docs/test-cases.md` — 28 test cases SSO/SLO
- `docs/test-report.md` — kết quả test gần nhất
- `docs/vault-hardening-gaps.md` — 9 gaps Vault hardening

## /etc/hosts cần có
```
127.0.0.1  auth.sso.local
127.0.0.1  wp-a.sso.local
127.0.0.1  whoami-a.sso.local
127.0.0.1  openiga.sso.local
127.0.0.1  redmine-b.sso.local
127.0.0.1  jellyfin-b.sso.local
127.0.0.1  openigb.sso.local
127.0.0.1  grafana-c.sso.local
127.0.0.1  phpmyadmin-c.sso.local
127.0.0.1  openig-c.sso.local
```
