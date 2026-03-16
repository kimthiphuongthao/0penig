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
| Fail-closed redirect loop khi Redis down | Option B (clear session + redirect) gây infinite loop: Keycloak SSO session tự re-auth → tạo session mới → Redis vẫn down → clear → redirect → loop | Dùng Option A: trả 500 INTERNAL_SERVER_ERROR trực tiếp, KHÔNG clear session. Session preserved → hồi phục tự động khi Redis về |
| `request.uri.toString()` trả URL nội bộ Docker | Trong OpenIG ScriptableFilter, `request.uri` đã bị rewrite thành backend URL (e.g. `http://wordpress/...`) → redirect gửi browser tới hostname Docker internal | ~~Dùng `request.headers.getFirst('Host')`~~ → FIX-08: dùng pinned `CANONICAL_ORIGIN_APPx` constants (env var + hardcoded fallback). Host header cũng không an toàn vì attacker-controlled |
| Backchannel logout lost khi Redis down | BackchannelLogoutHandler trả 500 (FIX-05) nhưng blacklist key không được ghi. Khi Redis recovery, session vẫn valid (chưa bị revoke) cho đến khi JwtSession hết hạn tự nhiên (8h) | Production: Redis HA (Sentinel/Cluster) + Redis persistence (appendonly yes) + Keycloak retry config. Lab: accepted limitation — session hết hạn tự nhiên sau 8h |
| OpenIG 6 config.json không support `${env['']}` | Heap parser gọi `.required()` trước EL evaluation → `${env['VAR']}` trả null → error. Chỉ route files (JSON) mới support EL natively | Dùng `docker-entrypoint.sh` + `sed` thay `__PLACEHOLDER__` trong config.json trước khi OpenIG start. Route files dùng `${env['VAR']}` bình thường |
| Stack B clientEndpoint collision (app4) | `02-redmine.json` và `01-jellyfin.json` cùng dùng `clientEndpoint: "/openid/app4"` → OpenIG register callback handler globally, route load sau chiếm handler → Redmine callback dùng Jellyfin client | Đổi Redmine sang `/openid/app3` (xóa dotnet routes không dùng), dùng `SessionBlacklistFilterApp3.groovy` |
| ~~Entrypoint `cp -r` không overwrite~~ | ~~`docker-entrypoint.sh` dùng `cp -r /opt/openig /tmp/openig` — khi `/tmp/openig` đã tồn tại (restart), cp tạo subdirectory thay vì overwrite → file cũ vẫn được dùng~~ | **FIXED**: thêm `rm -rf "$DST"` trước `cp -r` trong entrypoint (all 3 stacks). Container restart giờ luôn fresh copy + sed substitution. Production: dùng K8s init container pattern |
| Jellyfin SLO → re-login spinner | Sau SLO, re-login Jellyfin: OAuth2 flow OK nhưng Jellyfin API `/DisplayPreferences/usersettings` trả 403 → web client spinner. Pre-existing, do đặc thù app Jellyfin | `post_logout_redirect_uri` đổi sang `/web/index.html` (skip redirect hop). 403 là Jellyfin app permission, không phải SSO issue |

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
| Fail-closed dùng 500 thay vì redirect | Option B (redirect) gây infinite loop do Keycloak SSO re-auth. Option A (500) đơn giản, session preserved, compatible với future K8s + Redis HA. |
| FIX-05: 500 thay vì 400 cho infra errors | Keycloak spec: 400 = bad request (don't retry), 500 = server error (may retry). Trả 500 cho Redis/network errors cho phép Keycloak retry backchannel logout khi infra recovery. |
| FIX-06: docker-entrypoint.sh + sed cho config.json secrets | OpenIG 6 heap parser không support EL trong config.json. Route files hỗ trợ `${env['VAR']}` native. Config.json dùng `__PLACEHOLDER__` + sed substitution tại container startup. sharedSecret + PKCS12 rotated, clientSecret externalized only (must match Keycloak). |
| Stack B Redmine clientEndpoint `/openid/app3` | Redmine cần namespace riêng, dotnet không dùng nữa → dùng `/openid/app3`. Mỗi app trong cùng OpenIG instance PHẢI có clientEndpoint riêng — collision gây OAuth2 callback bị route sai xử lý |
| Stack B xóa dotnet routes | `01-dotnet.json` + `00-dotnet-logout.json` không dùng nữa, chiếm `/openid/app3` namespace | Xóa để Redmine dùng `/openid/app3` |
| Stack B nginx `hash $cookie_JSESSIONID` broken | OpenIG JwtSession không tạo JSESSIONID → hash empty string → routing không deterministic → OAuth2 state mismatch. Stack A/C dùng `ip_hash` đúng | Đổi sang `ip_hash` cho Stack B (commit 93efa92). Luôn dùng `ip_hash` cho tất cả stacks |
| PhpMyAdminCookieFilter gây Token mismatch | phpMyAdmin `auth_type=http` dùng CSRF token protection liên kết với session cookie. Khi filter manipulate cookie (expire/replace) → CSRF token mismatch → lỗi "Token mismatch" | WONT_FIX — không wire filter vào route chain. User switch handled bởi `cacheHeader: false` + fresh `attributes` per request (FIX-09) |
| OpenIG 6 `HttpBasicAuthFilter` hỗ trợ `${attributes.key}` | Trước đây giả định sai rằng HttpBasicAuthFilter chỉ support `${session['...']}` EL. Source code confirm: `username`/`password` là `Expression<String>` — chấp nhận mọi EL hợp lệ kể cả `${attributes.key}` | Dùng `attributes` (transient per-request) thay `session` (persist vào JwtSession cookie) cho credential injection |
