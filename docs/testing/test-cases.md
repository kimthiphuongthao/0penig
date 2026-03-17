## 0. Test Environment
Thông tin cấu hình môi trường phục vụ việc kiểm thử hệ thống SSO Lab.

> Update 2026-03-17: Pattern Consolidation Steps 1-5 are complete. The Step 5 validation run passed for all 5 backchannel logout clients, all 5 logout-capable apps, and the phpMyAdmin inline `failureHandler` path.

### URLs truy cập từ Browser (Host Machine)
Cần cấu hình file `/etc/hosts` trên máy host trỏ các domain sau về `127.0.0.1`.

| Thành phần | URL | Port | Hosts Entry |
|:---|:---|:---|:---|
| **Keycloak IDP** | `http://auth.sso.local:8080` | 8080 | `auth.sso.local` |
| **Stack A WordPress** | `http://wp-a.sso.local` | 80 | `wp-a.sso.local`, `openiga.sso.local` |
| **Stack A WhoAmI** | `http://whoami-a.sso.local` | 80 | `whoami-a.sso.local` |
| **Stack B Redmine** | `http://redmine-b.sso.local:9080` | 9080 | `redmine-b.sso.local`, `openigb.sso.local` |
| **Stack B Jellyfin** | `http://jellyfin-b.sso.local:9080` | 9080 | `jellyfin-b.sso.local` |
| **Stack C App5 Grafana** | `http://grafana-c.sso.local:18080` | 18080 | `grafana-c.sso.local`, `openig-c.sso.local` |
| **Stack C App6 phpMyAdmin** | `http://phpmyadmin-c.sso.local:18080` | 18080 | `phpmyadmin-c.sso.local` |

*Ghi chú: Keycloak Admin Console tại `http://auth.sso.local:8080/admin` (admin/admin).*

### Keycloak Test Users (SSO Login)
Dùng để đăng nhập vào giao diện tập trung của Keycloak.

| Username | Password | Email | Full Name |
|:---|:---|:---|:---|
| **alice** | `alice123` | `alice@lab.local` | Alice Lab |
| **bob** | `bob123` | `bob@lab.local` | Bob Lab |

### App-specific Credentials (Injected by OpenIG)
Thông tin tài khoản thực tế trong ứng dụng legacy (được Vault quản lý và OpenIG tự động inject).

- **WordPress (Stack A):** `alice_wp` / `bob_wp` (Username trong WordPress).
- **Redmine (Stack B):** `alice@lab.local` / `bob@lab.local` (Login tự động qua OIDC attribute mapping).
- **Jellyfin (Stack B):** `alice` / `bob` (Jellyfin internal usernames).
- **Grafana (Stack C):** `alice` / `bob` (Tự động tạo dựa trên `preferred_username` header).
- **phpMyAdmin (Stack C):** `alice` / `bob` (MariaDB users, password lấy từ Vault).

### Container Names (For Debugging)
Sử dụng `docker logs -f <container_name>` để kiểm tra luồng xử lý.

- **Global:** `sso-keycloak`, `kc-mysql`.
- **Stack A:** `sso-nginx`, `sso-openig-1/2`, `sso-vault`, `sso-redis-a`, `sso-wordpress`, `sso-whoami`, `sso-mysql`.
- **Stack B:** `sso-b-nginx`, `sso-b-openig-1/2`, `sso-b-vault`, `sso-redis-b`, `sso-b-jellyfin`, `sso-b-redmine`, `sso-b-mysql-redmine`.
- **Stack C:** `stack-c-nginx-c-1`, `stack-c-openig-c1-1`, `stack-c-openig-c2-1`, `stack-c-vault-c-1`, `stack-c-redis-c-1`, `stack-c-grafana-1`, `stack-c-phpmyadmin-1`, `stack-c-mariadb-1`.

---

# Danh sách Test Cases SSO + SLO (OpenIG, Keycloak, Vault, Redis)

Tài liệu này định nghĩa các test cases bắt buộc để kiểm chứng tính đúng đắn của hệ thống SSO lab, bao gồm khả năng xác thực tập trung, tiêm thông tin đăng nhập tự động, đăng xuất đồng bộ và tính sẵn sàng cao (HA).

## 1. Infrastructure Health
Kiểm tra trạng thái sẵn sàng của các thành phần hạ tầng cốt lõi.

| ID | Tên | Stack | Mô tả | Expected Result | Lý giải |
|:---|:---|:---|:---|:---|:---|
| TC-101 | Vault Connectivity | All | Kiểm tra OpenIG có thể login vào Vault qua AppRole. | Log của OpenIG (`openig-1/2`) không báo lỗi `Vault AppRole login failed`. | Chứng minh cơ chế bảo mật AppRole hoạt động, OpenIG có quyền truy cập Vault. |
| TC-102 | Redis Connectivity | All | Kiểm tra các script Groovy có thể kết nối đến Redis (`redis-a`, `redis-b`). | Log không có cảnh báo `Redis check failed` trong `SessionBlacklistFilter`. | Đảm bảo kênh blacklist cho SLO hoạt động thông suốt. |
| TC-103 | Route Loading | All | Kiểm tra OpenIG đã load đủ các route cấu hình. | Truy cập `/admin/default/heap` trên OpenIG admin port (8080) thấy đủ các route `01-wordpress`, `02-redmine`, v.v. | Đảm bảo gateway đã sẵn sàng phục vụ các ứng dụng legacy. |

## 2. SSO — Pre-auth Redirect
Kiểm chứng cơ chế bảo vệ ứng dụng (vào app là phải qua Keycloak).

| ID | Tên | Stack | Mô tả | Expected Result | Lý giải |
|:---|:---|:---|:---|:---|:---|
| TC-201 | Anonymous Access Block | Stack A | Truy cập `http://wp-a.sso.local/` khi chưa đăng nhập. | HTTP 302 redirect về `http://auth.sso.local:8080/realms/sso-realm/protocol/openid-connect/auth`. | Chứng minh `OAuth2ClientFilter` (OidcFilter) đang bảo vệ ứng dụng. |

## 3. SSO — Full Login Flow
Kiểm chứng luồng đăng nhập hoàn chỉnh từ IDP về App.

| ID | Tên | Stack | Mô tả | Expected Result | Lý giải |
|:---|:---|:---|:---|:---|:---|
| TC-301 | OIDC Authentication | All | Nhập user/pass đúng trên form Keycloak. | Redirect về OpenIG callback, sau đó vào thẳng dashboard của ứng dụng mà không cần login lại. | Chứng minh luồng OIDC giữa OpenIG và Keycloak thành công. |

## 4. SSO — Credential Injection
Kiểm chứng khả năng tự động "đóng giả" user để login vào legacy app.

| ID | Tên | Stack | Mô tả | Expected Result | Lý giải |
|:---|:---|:---|:---|:---|:---|
| TC-401 | Vault Creds Retrieval | Stack A | Login Keycloak bằng user `alice`. | `VaultCredentialFilter` log cho thấy lấy được `wp_username: alice_wp` từ Vault. | Chứng minh logic ánh xạ user Keycloak → legacy user thành công. |
| TC-402 | Automatic Form Login | Stack A | User truy cập WordPress qua OpenIG. | User thấy mình đã login vào WordPress (có thanh admin bar, tên user là `alice_wp`). | Chứng minh `CredentialInjector` đã POST form login thành công đằng sau hậu trường. |

## 5. SSO — HA Failover
Kiểm chứng tính ổn định của hệ thống khi có sự cố node.

| ID | Tên | Stack | Mô tả | Expected Result | Lý giải |
|:---|:---|:---|:---|:---|:---|
| TC-501 | OpenIG Node Failover | Stack A | Đang dùng app, stop container `sso-openig-1`. | Refresh trang vẫn giữ được session, không bị logout. | Chứng minh `ip_hash` của Nginx và `JWT_SHARED_SECRET` của OpenIG giúp share session thành công. |

## 6. SLO — Backchannel Logout
Kiểm chứng cơ chế đăng xuất từ phía Server (an toàn hơn Frontchannel).

| ID | Tên | Stack | Mô tả | Expected Result | Lý giải |
|:---|:---|:---|:---|:---|:---|
| TC-601 | Single App Logout | Stack B | Nhấn nút "Logout" trên Redmine. | User bị redirect về Keycloak logout. Quay lại truy cập Redmine phải bị hỏi login lại. | Chứng minh `SloHandler` đã gửi yêu cầu logout lên Keycloak thành công. |
| TC-602 | Redis Blacklisting | All | Keycloak gửi logout POST đến `/backchannel_logout`. | Redis xuất hiện key `blacklist:<sid>` với giá trị `1`. | Chứng minh `BackchannelLogoutHandler` đã nhận và ghi blacklist vào Redis đúng thiết kế. |

## 7. SLO — Cross-stack
Kiểm chứng sức mạnh của hệ thống xác thực tập trung: Logout 1 nơi, mất quyền mọi nơi.

| ID | Tên | Stack | Mô tả | Expected Result | Lý giải |
|:---|:---|:---|:---|:---|:---|
| TC-701 | Multi-stack Invalidation | All | Login vào cả WordPress (Stack A) và Redmine (Stack B). Sau đó logout WordPress. | Truy cập Redmine ngay lập tức bị kick ra (redirect Keycloak). | Chứng minh Keycloak đã bắn signal đến mọi client và các stack đều check chung/riêng Redis thành công. |

## 8. Security — Header Stripping
Kiểm chứng tính bảo mật của gateway.

| ID | Tên | Stack | Mô tả | Expected Result | Lý giải |
|:---|:---|:---|:---|:---|:---|
| TC-801 | Trusted Header Protection | Stack A | Dùng Postman gửi request kèm header `X-Forwarded-User: admin`. | Ứng dụng WhoAmI (App2) nhận được header đã bị OpenIG ghi đè hoặc xóa sạch. | Chứng minh client không thể bypass authentication bằng cách fake headers. |

## 9. Stack C — App5 Grafana (Header-based Auth)
Kiểm chứng cơ chế xác thực dựa trên trusted headers.

| ID | Tên | Stack | Mô tả | Expected Result | Lý giải |
|:---|:---|:---|:---|:---|:---|
| TC-901 | Grafana Pre-auth | Stack C | Truy cập `http://grafana-c.sso.local/` khi chưa login. | Redirect về Keycloak login page. | Chứng minh Grafana được bảo vệ bởi OIDC Filter. |
| TC-902 | Header Injection | Stack C | Login thành công và vào Grafana. | Grafana nhận diện đúng user (không hỏi login). Header `X-WEBAUTH-USER` chứa username từ session OpenIG. | Chứng minh `HeaderFilter` đã inject đúng thông tin định danh. |
| TC-903 | Auto-provisioning | Stack C | Login bằng một user mới hoàn toàn trên Keycloak. | Grafana tự tạo account mới cho user này dựa trên header nhận được. | Chứng minh sự phối hợp giữa OpenIG injection và Grafana Auth Proxy. |

## 10. Stack C — App6 phpMyAdmin (HTTP Basic Auth)
Kiểm chứng cơ chế xác thực Basic Auth tự động.

| ID | Tên | Stack | Mô tả | Expected Result | Lý giải |
|:---|:---|:---|:---|:---|:---|
| TC-1001 | phpMyAdmin Pre-auth | Stack C | Truy cập `http://phpmyadmin-c.sso.local/`. | Redirect về Keycloak login page. | Đảm bảo phpMyAdmin không bị lộ giao diện login mặc định. |
| TC-1002 | Basic Auth Injection | Stack C | Login Keycloak và vào phpMyAdmin. | User vào thẳng giao diện quản lý DB mà không thấy form login của phpMyAdmin. | Chứng minh `HttpBasicAuthFilter` đã inject header `Authorization: Basic` thành công. |
| TC-1003 | DB User Mapping | Stack C | Login bằng user Keycloak khác nhau. | phpMyAdmin kết nối vào MariaDB bằng đúng user tương ứng (check phần "User" trong phpMyAdmin). | Chứng minh credentials lấy từ Vault là chính xác theo từng user. |

## 11. Stack B — App3 Jellyfin (Token Injection)
Kiểm chứng cơ chế login qua API và tiêm Token.

| ID | Tên | Stack | Mô tả | Expected Result | Lý giải |
|:---|:---|:---|:---|:---|:---|
| TC-1101 | API Authentication | Stack B | Login Keycloak, truy cập Jellyfin. | OpenIG log báo gọi thành công API `/Users/AuthenticateByName`. | Chứng minh OpenIG có khả năng tự động thực hiện luồng login API. |
| TC-1102 | Token Injection | Stack B | Kiểm tra request từ OpenIG gửi sang Jellyfin. | Header `Authorization` chứa chuỗi `MediaBrowser ... Token="..."`. | Chứng minh `JellyfinTokenInjector` đã duy trì và inject session token đúng định dạng. |
| TC-1103 | Jellyfin Vault Mapping| Stack B | Login bằng user Keycloak. | `VaultCredentialFilterJellyfin` log lấy đúng pass ứng dụng dựa trên email user. | Chứng minh khả năng mapping linh hoạt (theo email thay vì username) khi lấy data từ Vault. |

## 12. Security Deep-check
Kiểm chứng các cấu hình bảo mật nâng cao.

| ID | Tên | Stack | Mô tả | Expected Result | Lý giải |
|:---|:---|:---|:---|:---|:---|
| TC-1201 | Domain Cookie Scope | All | Kiểm tra cookie `IG_SSO` (A/B) và `IG_SSO_C` (C). | Cookie có thuộc tính `Domain=.sso.local`. | Đảm bảo session có thể share qua lại giữa các subdomains trong cùng stack. |
| TC-1202 | Header Anti-spoofing | Stack C | Gửi request thủ công kèm `X-WEBAUTH-USER: admin` đến Grafana route. | OpenIG xóa header cũ và ghi đè bằng username thực tế từ session Keycloak. | Chứng minh cấu hình `remove` trong `HeaderFilter` bảo vệ ứng dụng khỏi tấn công injection. |

---
**Nguồn tham khảo (Confirmed Facts):**
- Cấu hình route: `stack-a/openig_home/config/routes/01-wordpress.json`
- Logic tiêm credentials: `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
- Cơ chế SLO: `docs/reference/why-redis-slo.md` và `BackchannelLogoutHandler.groovy`
- Cấu hình HA: `stack-a/nginx/nginx.conf` và `stack-a/docker-compose.yml`
- Route Grafana & phpMyAdmin: `stack-c/openig_home/config/routes/10-grafana.json`, `11-phpmyadmin.json`
- Logic Token Jellyfin: `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy`
