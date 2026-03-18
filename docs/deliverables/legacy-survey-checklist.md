# Legacy App Survey Checklist (Pre-Integration SSO)

Mục tiêu: dùng checklist này để khảo sát kỹ thuật **trước khi** chọn pattern tích hợp SSO cho một legacy app.

## 1) Thông tin cơ bản về app

- [ ] Tên ứng dụng:
- [ ] Platform/Framework (PHP, Java, .NET, Node.js, Rails, Go, ...):
- [ ] Version hiện tại:
- [ ] Môi trường khảo sát (DEV/UAT/PROD):
- [ ] Public URL hiện tại:
- [ ] Team owner (POC kỹ thuật + vận hành):
- [ ] Tài liệu kỹ thuật hiện có (link/runbook):

## 2) Cơ chế xác thực hiện tại

### 2.1 Loại xác thực

- [ ] Form HTML
- [ ] HTTP Basic Auth
- [ ] NTLM / Kerberos
- [ ] API Key
- [ ] Native OIDC
- [ ] Native SAML
- [ ] Khác: `____________________`

### 2.2 Login flow chi tiết

| Hạng mục | Giá trị khảo sát |
|---|---|
| Login endpoint | |
| HTTP method | |
| Username field | |
| Password field | |
| Extra field (tenant/domain/otp/...) | |
| Success status code (vd: 302/200) | |
| Fail status code (vd: 200/401) | |

### 2.3 CSRF và multi-step login

- [ ] Có CSRF token
  - Cách lấy token (GET trang nào, parse field nào):
  - Tên field token:
- [ ] Không có CSRF token
- [ ] Login nhiều bước (multi-step)
  - Mô tả các bước:

## 3) Session management

| Hạng mục | Giá trị khảo sát |
|---|---|
| Loại session | `cookie` / `JWT` / `server-side` / `stateless` |
| Tên cookie session chính | |
| Cookie Domain | |
| Cookie Path | |
| HttpOnly | Có / Không |
| Secure | Có / Không |
| SameSite | Lax / Strict / None / Không rõ |
| Session timeout mặc định | |
| Endpoint logout cục bộ | |

## 4) Hỗ trợ Proxy Auth / Trusted Header

- [ ] Có hỗ trợ trusted header
  - Header được hỗ trợ: `X-WEBAUTH-USER` / `X-Remote-User` / `REMOTE_USER` / khác:
  - Có cơ chế giới hạn trusted proxy IP/network không?
- [ ] Không hỗ trợ trusted header
- [ ] Có hỗ trợ HTTP Basic Auth mode
- [ ] Không hỗ trợ HTTP Basic Auth mode
- [ ] Có native OIDC/SAML support
  - Loại: OIDC / SAML
  - Phiên bản/tính năng (đủ cho SSO/SLO hay không):
- [ ] Không có native OIDC/SAML

## 5) Cấu trúc credential

| Hạng mục | Giá trị khảo sát |
|---|---|
| Username field name thực tế | |
| Password field name thực tế | |
| Credential store | `database` / `LDAP` / `file` / `external` |
| Có API/CLI tạo user không | Có / Không |
| Có thể tạo user thủ công không (phục vụ Vault mapping) | Có / Không |
| Yêu cầu policy password (min length, complexity, expiry) | |

## 6) Deployment

- [ ] App đang chạy ở root domain (`/`)
- [ ] App đang chạy dưới subpath (`/appX`)
- [ ] App hỗ trợ subpath natively (UsePathBase / Relative URL Root / siteurl/home / ...):
- [ ] App không hỗ trợ subpath natively
- [ ] Container image có thể customize (Dockerfile/entrypoint/config mount):
- [ ] Không thể customize image (ràng buộc vendor/platform):
- [ ] Gateway image tags sẽ được pin explicit version (không dùng `:latest`)
- [ ] Nếu app đi qua OpenIG `OAuth2ClientFilter`, OIDC client secret dùng giá trị strong random alphanumeric-only (không chứa `+`, `/`, `=`)

Ghi chú triển khai:
- Public host/port chuẩn hóa:
- Yêu cầu rewrite/path stripping hiện tại (nếu có):

## 7) SLO (Single Logout)

| Hạng mục | Giá trị khảo sát |
|---|---|
| Có logout endpoint không | Có / Không |
| Logout method | GET / POST / API |
| Logout chỉ clear client hay cần invalidate server session | |
| Có yêu cầu CSRF khi logout không | |
| Có backchannel logout support không | Có / Không |
| Có endpoint API logout riêng cho token/session không | |

## 8) Kết luận pattern phù hợp

### 8.1 Đề xuất pattern (chọn 1 chính)

- [ ] Form Inject
- [ ] Token Inject
- [ ] Header Inject
- [ ] Basic Auth Inject
- [ ] Native OIDC
- [ ] Native SAML

### 8.2 Lý do chọn pattern

- Cơ sở kỹ thuật chính:
- Ràng buộc chính (CSRF, SPA localStorage, trusted header, subpath, ...):

### 8.3 App-side config cần thiết

- [ ] Không cần app-side config
- [ ] Có cần app-side config
  - Danh sách config bắt buộc:
  - Mức độ can thiệp: config-only / script / code change

### 8.4 Độ phức tạp tích hợp

- [ ] Thấp
- [ ] Trung bình
- [ ] Cao

Lý do chấm độ phức tạp:
- Auth flow:
- Session model:
- SLO model:
- Mức độ phụ thuộc team app:

---

## Bảng khảo sát mẫu (tham chiếu từ app đã tích hợp)

> Lưu ý: danh sách dưới đây gồm **6 app** tham chiếu: WordPress, WhoAmI, Redmine, Jellyfin, Grafana, phpMyAdmin.

| App | Auth hiện tại | Session | Proxy/Trusted Header | Native OIDC/SAML | Pattern đề xuất | App-side config cần thiết | Độ phức tạp |
|---|---|---|---|---|---|---|---|
| WordPress | Form HTML (`/wp-login.php`) | Cookie (`wordpress_*`, `wordpress_logged_in_*`) | Không | Không | Form Inject | Có: set `siteurl/home`; infra Alias cho subpath (nếu dùng subpath) | Trung bình |
| WhoAmI | Không có login form riêng (được bảo vệ bởi OpenIG) | Không quản lý session app phức tạp | Có thể nhận header user từ gateway | Không | Header Inject (gateway-to-app), hoặc OIDC-only không inject credential | Không bắt buộc phía app (chủ yếu gateway config) | Thấp |
| Redmine | Form HTML + CSRF (`/login`, `authenticity_token`) | Cookie server-side (`_redmine_session`) | Không | Không mặc định | Form Inject (2-step GET token + POST login) | Có nếu chạy subpath: cần cấu hình relative URL root/infrastructure mapping | Cao |
| Jellyfin | API login token (`/Users/AuthenticateByName`) + SPA | Token + state phía client (`localStorage`) | Không dùng trusted header mode mặc định | Không mặc định | Token Inject + Response Rewriter | Không cần sửa code app; cần xử lý inject script đúng thời điểm tại gateway. **Lưu ý:** Jellyfin yêu cầu Keycloak client riêng (`openig-client-b-app4`) tách biệt khỏi client dùng chung của stack — namespace `OAuth2ClientFilter` và client ID trong SloHandler phải khớp với client này (FIX-01, commit a3cb6c3). | Cao |
| Grafana | Proxy auth (header `X-WEBAUTH-USER`) hoặc native auth | Server-side session + cookie | Có (Auth Proxy mode) | Có (OIDC/SAML native) | Header Inject (khi bật Auth Proxy) hoặc Native OIDC | Có: bật `GF_AUTH_PROXY_ENABLED` + config header mapping | Trung bình |
| phpMyAdmin | HTTP mode (khi cấu hình `auth_type=http`) | Auth theo HTTP Basic + session app | Hỗ trợ Basic Auth mode | Không mặc định | Basic Auth Inject | Có: set `$cfg['Servers'][$i]['auth_type']='http'` qua config mount | Trung bình |

## Kết luận nhanh theo dấu hiệu khảo sát

- [ ] Có form login + cookie session + (có/không CSRF) -> ưu tiên **Form Inject**
- [ ] Có API token login + SPA lưu state client-side -> ưu tiên **Token Inject** (thường cần response rewrite)
- [ ] App hỗ trợ trusted header và chỉ nhận traffic từ gateway -> ưu tiên **Header Inject**
- [ ] App hỗ trợ HTTP auth mode -> ưu tiên **Basic Auth Inject**
- [ ] App có OIDC/SAML native ổn định -> ưu tiên **Native OIDC/SAML** (giảm custom injector)
