# Gotchas & Decision Log

## Known bugs (chưa fix)
- **Jellyfin WebSocket**: route `01-jellyfin.json` dùng `http://` thay vì `ws://` → pending fix
- **Stack B cookieDomain**: `config.json` thiếu `cookieDomain: ".sso.local"` → LOW priority

## Gotchas đã biết

| Vấn đề | Nguyên nhân | Fix |
|--------|-------------|-----|
| "SSO authentication failed" | openig-2 có file `role_id`/`secret_id` rỗng | Regenerate AppRole credentials → ghi vào shared mount |
| Vault "bind: address already in use" | `command: server -config=...` + entrypoint tự thêm `-config` → 2 listeners | Dùng `command: server` only |
| Bootstrap script abort silently | `vault status` exit 2 khi sealed + `set -euo pipefail` | `code=$(vault status >/dev/null 2>&1; echo $?)` + `|| true` |
| phpMyAdmin không nhận Basic Auth từ env | `PMA_AUTH_TYPE=http` env var không hoạt động với Docker image | Mount `config.user.inc.php` với `$cfg['Servers'][1]['auth_type'] = 'http'` |
| Jellyfin passwords không trong bootstrap | Jellyfin lưu passwords trong SQLite volume, bootstrap script không set được | Set thủ công qua Jellyfin API sau lần đầu. Nếu volume bị xóa phải set lại: alice/AliceJelly2026, bob/BobJelly2026 |
| TC-1201 false fail | Test check cookie trên unauthenticated request → chỉ thấy JSESSIONID | IG_SSO chỉ xuất hiện sau login. Không phải bug. |

## Decision log

| Decision | Lý do |
|----------|-------|
| HA dùng `ip_hash` + JwtSession, không dùng Redis session store | Đã test và confirmed hoạt động. Redis session store phức tạp hơn không cần thiết cho lab. |
| Vault file storage thay vì dev mode | Dev mode mất data khi restart. File storage persist, production-grade. |
| Mỗi stack Redis riêng, không share | Isolation — SLO 1 stack không ảnh hưởng stack khác. Cross-stack SLO qua Keycloak backchannel. |
| `cookieDomain` Stack B thiếu là chấp nhận được | SSO hoạt động qua Keycloak session, không phải cookie sharing. Thiếu cookieDomain chỉ thêm 1 redirect thừa. |
