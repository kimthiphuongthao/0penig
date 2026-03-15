# Gotchas & Decision Log

## Known bugs (chưa fix)
- Không ghi nhận bug SSO/SLO mở nào trong phạm vi file này.

## Gotchas đã biết

| Vấn đề | Nguyên nhân | Fix |
|--------|-------------|-----|
| "SSO authentication failed" | openig-2 có file `role_id`/`secret_id` rỗng | Regenerate AppRole credentials → ghi vào shared mount |
| Vault "bind: address already in use" | `command: server -config=...` + entrypoint tự thêm `-config` → 2 listeners | Dùng `command: server` only |
| Bootstrap script abort silently | `vault status` exit 2 khi sealed + `set -euo pipefail` | `code=$(vault status >/dev/null 2>&1; echo $?)` + `|| true` |
| phpMyAdmin không nhận Basic Auth từ env | `PMA_AUTH_TYPE=http` env var không hoạt động với Docker image | Mount `config.user.inc.php` với `$cfg['Servers'][1]['auth_type'] = 'http'` |
| Jellyfin passwords không trong bootstrap | Jellyfin lưu passwords trong SQLite volume, bootstrap script không set được | Set thủ công qua Jellyfin API sau lần đầu. Nếu volume bị xóa phải set lại: alice/AliceJelly2026, bob/BobJelly2026 |
| TC-1201 false fail | Test check cookie trên unauthenticated request → chỉ thấy JSESSIONID | IG_SSO chỉ xuất hiện sau login. Không phải bug. |
| Keycloak hiện "Do you want to log out?" | `defaultLogoutGoto` tĩnh thiếu `id_token_hint` — Keycloak 18+ bắt buộc khi có `post_logout_redirect_uri` | Dùng custom SloHandler Groovy đọc `id_token` từ session, append `id_token_hint` động |
| Stack C backchannel logout không trigger | Route có Host condition match `grafana-c.sso.local` nhưng Keycloak POST tới `host.docker.internal` | Bỏ Host condition, chỉ match path `/openid/appX/backchannel_logout` |
| SloHandler URL `/null/realms/...` | `KEYCLOAK_BROWSER_URL` env var không set trong docker-compose Stack C | Thêm `KEYCLOAK_BROWSER_URL: "http://auth.sso.local:8080"` vào openig-c1 và openig-c2 |
| Grafana logout chỉ reload trang | Grafana `/logout` trả 302 (không phải 401) → không trigger failureHandler → không SLO | Thêm route `00-grafana-logout.json` intercept `GET /logout` → SloHandlerGrafana |
| Keycloak "Invalid parameter: id_token_hint" | SloHandler dùng `OIDC_CLIENT_ID` (shared) nhưng id_token được issue cho dedicated client (e.g. `openig-client-b-app4`) → client_id mismatch | Dùng env var riêng cho mỗi client (`OIDC_CLIENT_ID_APP4`), đảm bảo SloHandler dùng đúng client_id khớp với OAuth2ClientFilter |
| Keycloak "Invalid redirect uri" khi logout | `post.logout.redirect.uris` chưa configured trong Keycloak client dedicated (chỉ có trong shared client) | Thêm `post.logout.redirect.uris` vào Keycloak client dedicated qua admin API hoặc realm-export.json |

## Decision log

| Decision | Lý do |
|----------|-------|
| HA dùng `ip_hash` + JwtSession, không dùng Redis session store | Đã test và confirmed hoạt động. Redis session store phức tạp hơn không cần thiết cho lab. |
| Vault file storage thay vì dev mode | Dev mode mất data khi restart. File storage persist, production-grade. |
| Mỗi stack Redis riêng, không share | Isolation — SLO 1 stack không ảnh hưởng stack khác. Cross-stack SLO qua Keycloak backchannel. |
| Stack B đã thêm `cookieDomain: ".sso.local"` | Đồng bộ với trạng thái hiện tại của dự án, không còn theo dõi như bug. |
| Custom SloHandler thay vì `defaultLogoutGoto` | OpenIG 6.0.2 không có `openIdEndSessionOnLogout` (feature không tồn tại — max version là 6.0.2, không có 6.5+). `defaultLogoutGoto` tĩnh không thể mang `id_token_hint`. | Dùng ScriptableHandler đọc `id_token` từ session key `oauth2:...` |
| phpMyAdmin SLO hoạt động qua 401 | phpMyAdmin `auth_type=http` logout gửi 401 để clear browser Basic Auth → HttpBasicAuthFilter failureHandler trigger → redirect `/openid/app6/logout` → Keycloak end_session | Đây là by design, không cần dedicated logout intercept |
| Jellyfin dùng dedicated Keycloak client | Jellyfin cần `openig-client-b-app4` riêng (không share `openig-client-b` với Redmine) vì namespace OIDC khác nhau | Đảm bảo SloHandler, OAuth2ClientFilter, backchannel logout, post_logout_redirect_uris đều dùng cùng client_id |
