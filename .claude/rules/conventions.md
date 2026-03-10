# Conventions

## ⚠️ NGUYÊN TẮC TỐI THƯỢNG — KHÔNG BAO GIỜ VI PHẠM

**TUYỆT ĐỐI KHÔNG sửa code/config của bất kỳ ứng dụng đích nào** — áp dụng cho 100% ứng dụng, không ngoại lệ.

"Ứng dụng đích" = mọi thứ không phải OpenIG gateway: app server, database, reverse proxy của app, identity provider, container image, docker-compose của app, v.v.

Mọi giải pháp SSO/SLO **CHỈ được thực hiện ở phía OpenIG gateway**:
- `openig_home/config/routes/*.json`
- `openig_home/scripts/groovy/*.groovy`
- `openig_home/config/config.json`
- `nginx/nginx.conf` (gateway nginx)
- `vault/` (bootstrap, policy)

Khi phát hiện issue cần sửa, **BẮT BUỘC báo cáo theo format**:
- Sửa phần nào? (tên file, section cụ thể)
- Có tuân thủ nguyên tắc không sửa app đích không? (Có / Không — nếu Không thì dừng lại)
- Nội dung thay đổi đề xuất là gì?

→ Chờ user confirm trước khi thực hiện.

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
