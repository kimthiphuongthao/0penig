# Architecture

## ⚠️ NGUYÊN TẮC ĐỘC LẬP — BẮT BUỘC TUÂN THỦ MỌI LÚC

Mỗi stack là một **bộ hoàn chỉnh độc lập**. Khi triển khai/debug/fix bất kỳ stack nào:

- **KHÔNG** dùng chung credential, secret, key, config với stack khác
- Mỗi stack có OpenIG riêng, Vault riêng, Redis riêng, nginx riêng
- `sharedSecret` trong `config.json` phải **khác nhau** giữa các stacks
- Backchannel logout URL phải tự đăng ký độc lập trong Keycloak — không phụ thuộc stack khác
- Keycloak là **shared IdP duy nhất** được phép dùng chung — mọi thứ khác phải độc lập

Vi phạm nguyên tắc này = lỗi thiết kế nghiêm trọng, **dừng lại và sửa ngay**.

## Stack overview

| Stack | Port | Apps | Auth mechanism |
|-------|------|------|----------------|
| stack-a | 80 | WordPress, WhoAmI | Form login injection |
| stack-b | 9080 | Redmine, Jellyfin | Form login injection, Token injection |
| stack-c | 18080 | Grafana, phpMyAdmin | Header injection, HTTP Basic Auth |

Keycloak shared: `http://auth.sso.local:8080`, realm `sso-realm`.

## clientEndpoint namespace (MỖI app trong cùng OpenIG instance PHẢI unique)

| Stack | App | clientEndpoint | Keycloak client |
|-------|-----|----------------|-----------------|
| A | WordPress | `/openid/app1` | `openig-client` |
| A | WhoAmI | `/openid/app2` | `openig-client` |
| B | Redmine | `/openid/app3` | `openig-client-b` |
| B | Jellyfin | `/openid/app4` | `openig-client-b-app4` |
| C | Grafana | `/openid/app5` | `openig-client-c-app5` |
| C | phpMyAdmin | `/openid/app6` | `openig-client-c-app6` |

## HA pattern (tất cả stacks)
- nginx `ip_hash` → sticky routing (cùng IP → cùng OpenIG node)
- Production target: OpenIG `JwtSession` heap object phải được khai báo dưới tên heap `Session` → session mã hóa trong cookie, stateless, mọi node đọc được
- Nếu heap object bị đặt tên `"JwtSession"` thay vì `"Session"`, OpenIG sẽ fall back sang Tomcat `HttpSession` (`JSESSIONID`) dù `type` vẫn là `JwtSession`
- Legacy app cookies phải nằm ở browser; OpenIG session chỉ giữ OIDC tokens và marker nhỏ (`*_user_sub`, cookie names), không giữ raw upstream cookies như `wp_session_cookies` / `redmine_session_cookies`
- Vault credentials shared mount → cả 2 node dùng chung `role_id`/`secret_id`

## Pinned canonical origins
- Stack A: `CANONICAL_ORIGIN_APP1`, `CANONICAL_ORIGIN_APP2` trên `sso-openig-1` và `sso-openig-2`
- Stack B: `CANONICAL_ORIGIN_APP3`, `CANONICAL_ORIGIN_APP4` trên `sso-b-openig-1` và `sso-b-openig-2`
- Stack C: `CANONICAL_ORIGIN_APP5`, `CANONICAL_ORIGIN_APP6` trên `stack-c-openig-c1-1` và `stack-c-openig-c2-1`
- Tất cả logic redirect/logout/session re-entry PHẢI dùng pinned origin từ env var này, không dùng inbound `Host`
- Stack B Redmine không còn public host port `3000`; browser chỉ truy cập qua `http://redmine-b.sso.local:9080`

## Runtime secret + image pattern
- Secret runtime của từng stack nằm trong file `.env` cục bộ (gitignored); chỉ commit `.env.example`
- OIDC client secrets đi qua `OAuth2ClientFilter` PHẢI dùng strong random alphanumeric-only values; không dùng Base64 có `+`, `/`, `=` vì OpenIG 6 không URL-encode `client_secret` trong POST body
- Với các secret Base64 khác (không đi qua `OAuth2ClientFilter`), phải copy nguyên vẹn. Dấu `=` cuối chuỗi là dữ liệu hợp lệ, không phải ký tự thừa
- Tất cả OpenIG containers PHẢI pin `openidentityplatform/openig:6.0.1`
- KHÔNG dùng `openidentityplatform/openig:latest` vì `latest=6.0.2` chuyển sang Tomcat 11 và làm OpenIG 6 không khởi động được

## Keycloak URL + compose baseline
- Cả 3 stacks giờ dùng env-driven Keycloak URLs trong route JSON: `KEYCLOAK_BROWSER_URL` cho browser-facing `issuer`/authorize/logout semantics, `KEYCLOAK_INTERNAL_URL` cho OpenIG -> Keycloak `token`/`userinfo`/`jwks`
- Stack C `docker-compose.yml` giờ parity-aligned với Stack B baseline về `container_name`, `restart`, `platform`, healthcheck, và OpenIG node naming; nếu lệch nữa phải là chủ ý và documented
- Tất cả 6 OpenIG services đã thêm `extra_hosts: host.docker.internal:host-gateway` để Linux portability không phụ thuộc Docker Desktop magic hostname

## Cookie session
- Stack A: `IG_SSO`, `cookieDomain: ".sso.local"`
- Stack B: `IG_SSO_B`, `cookieDomain: ".sso.local"`
- Stack C: `IG_SSO_C`, `cookieDomain: ".sso.local"`
- Current lab state on `fix/jwtsession-production-pattern`: cả 3 stacks vẫn đang chạy `HttpSession` fallback cho đến khi heap object được rename lại thành `Session` và payload size được xác nhận < 4KB

## SLO mechanism
- Keycloak → backchannel logout → `BackchannelLogoutHandler.groovy` → Redis blacklist
- `SessionBlacklistFilter.groovy` check Redis mỗi request → kick nếu blacklisted
- Redis riêng mỗi stack: `sso-redis-a`, `sso-redis-b`, `stack-c-redis-c-1`

## Vault — file storage
- Config: `vault/config/vault.hcl` (multi-line HCL, KHÔNG semicolon)
- docker-compose: `command: server` (KHÔNG explicit `-config` path)
- Keys: `vault/keys/.vault-keys.unseal`, `vault/keys/.vault-keys.admin`
- Bootstrap flag: `vault/data/.bootstrap-done`
- Sau Docker restart: sealed → bootstrap → regenerate `secret_id` → restart OpenIG

## Container names

| Component | Stack A | Stack B | Stack C |
|-----------|---------|---------|---------|
| nginx | `sso-nginx` | `sso-b-nginx` | `stack-c-nginx-c-1` |
| openig-1 | `sso-openig-1` | `sso-b-openig-1` | `stack-c-openig-c1-1` |
| openig-2 | `sso-openig-2` | `sso-b-openig-2` | `stack-c-openig-c2-1` |
| vault | `sso-vault` | `sso-b-vault` | `stack-c-vault-c-1` |
| redis | `sso-redis-a` | `sso-redis-b` | `stack-c-redis-c-1` |
| app1 | `sso-wordpress` | `sso-b-redmine` | `stack-c-grafana-1` |
| app2 | `sso-whoami` | `sso-b-jellyfin` | `stack-c-phpmyadmin-1` |
| db | `sso-mysql` | `sso-b-mysql-redmine` | `stack-c-mariadb-1` |
| keycloak | `sso-keycloak` (shared) | — | — |

## URLs và credentials

| App | URL |
|-----|-----|
| Keycloak | `http://auth.sso.local:8080` (admin/admin) |
| WordPress | `http://wp-a.sso.local` |
| WhoAmI | `http://whoami-a.sso.local` |
| Redmine | `http://redmine-b.sso.local:9080` |
| Jellyfin | `http://jellyfin-b.sso.local:9080` |
| Grafana | `http://grafana-c.sso.local:18080` |
| phpMyAdmin | `http://phpmyadmin-c.sso.local:18080` |

Keycloak test users: `alice`/`alice123`, `bob`/`bob123`

App credentials (injected by OpenIG via Vault):
- WordPress: `alice_wp`, `bob_wp`
- Redmine: login `alice@lab.local`, `bob@lab.local`
- Jellyfin: `alice`/`AliceJelly2026`, `bob`/`BobJelly2026` (**set thủ công, không trong bootstrap**)
- Grafana: auto-provisioned từ `preferred_username`
- phpMyAdmin: `alice` confirmed working via Vault -> MariaDB; `bob` vẫn là infra gap vì live Stack C MariaDB chưa provision user này
