# Hướng dẫn tích hợp Legacy App vào hệ thống SSO (OpenIG + Keycloak)

Tài liệu này dành cho team ứng dụng legacy cần tích hợp vào kiến trúc SSO hiện tại. Nội dung bám sát cấu hình đang chạy ở 2 stack (`sso-lab` và `sso-lab-dotnet`), tập trung vào các điểm thường gây lỗi khi triển khai thực tế.

## 1) Tổng quan kiến trúc

### 1.1 Mô hình tổng thể (2 stack, dùng chung Keycloak)

```text
                                      +-----------------------------+
                                      | Keycloak (shared IdP)       |
                                      | Browser URL:                |
                                      |   http://auth.sso.local:8080|
                                      | Internal URL (from OpenIG): |
                                      |   http://host.docker.internal:8080 |
                                      +---------------+-------------+
                                                      |
                           OIDC Auth/Token/UserInfo  |
                                                      v
+------------------------------+          +------------------------------+
| Stack A (sso-lab)            |          | Stack B (sso-lab-dotnet)     |
| Public: openiga.sso.local    |          | Public: openigb.sso.local:9080|
| Port: 80                     |          | Port: 9080                    |
|                              |          |                               |
| Browser -> nginx-a:80        |          | Browser -> nginx-b:9080       |
|            -> openig-1/2     |          |            -> openig-b1/b2    |
|                -> /app1 WP   |          |                -> /app3 .NET  |
|                -> /app2 whoami|         |                               |
| Redis: redis-a               |          | Redis: redis-b                |
| Vault: vault (wp-creds/*)    |          | Vault: vault-b (dotnet-creds/*)|
+------------------------------+          +------------------------------+

Back-channel logout flow:
Keycloak -> POST /openid/app1/backchannel_logout (Stack A)
Keycloak -> POST /openid/app3/backchannel_logout (Stack B)
Mỗi stack tự ghi blacklist SID vào Redis của chính stack đó.
```

### 1.2 Thành phần chính

- `OpenIG`: reverse proxy + OIDC gateway + filter chain + credential injection.
- `Keycloak`: IdP dùng chung cho cả Stack A và Stack B.
- `Vault`: lưu credential legacy app theo user (KV v2).
- `Redis`: blacklist SID để enforce Single Logout kiểu back-channel.

## 2) URL convention (subdomain ưu tiên, subpath fallback)

### 2.1 Hai mô hình triển khai

- **Subdomain (khuyến nghị)**:
  - Mỗi app có public domain riêng, app chạy tại root `/`.
  - Ví dụ: `http://wp-a.sso.local/...`, `http://dotnet-b.sso.local:9080/...`.
  - Đây là mô hình ưu tiên cho app mới vì ít phải sửa code/app config nhất.
- **Subpath (fallback khi không cấp được subdomain)**:
  - Nhiều app dùng chung một domain OpenIG và tách bằng prefix path.
  - Ví dụ: `http://openiga.sso.local/app1/...`, `http://openiga.sso.local/app2/...`.
  - Chỉ dùng khi ràng buộc hạ tầng DNS/certificate/policy không cho cấp subdomain riêng.

### 2.2 Quy ước route và callback theo từng mô hình

- Subdomain: route app match theo `Host` app tương ứng (path app thường là `/`).
- Subpath: route app match theo `Host` + prefix path `/appX`.
- Callback OIDC vẫn đi theo prefix riêng `/openid/{app-id}` cho từng app ở cả hai mô hình.
- Nginx nên forward `Host` bằng `$http_host` để giữ đúng host + optional port:

```nginx
proxy_set_header Host $http_host;
```

Nếu dùng `$host`, bạn có thể mất `:80`/`:9080`, dẫn tới mismatch `redirect_uri` và lookup session key.

## 3) App có cần tự biết Public URL không?

### 3.1 Với mô hình Subdomain (khuyến nghị)

- App **không cần biết public URL đặc biệt**.
- App chạy tại `/` như triển khai thông thường, không cần base-path config riêng.
- Hầu như không cần can thiệp vào code hoặc DB chỉ để “gắn prefix URL”.

### 3.2 Với mô hình Subpath (fallback)

- App **phải biết và tự cấu hình base path** (ví dụ `/appX`).
- Các kiểu cấu hình thường gặp:
  - .NET: `app.UsePathBase("/app3")`
  - Rails: `RAILS_RELATIVE_URL_ROOT=/appX`
  - Apache/PHP: `Alias /appX /var/www/html`
  - CMS như WordPress: cập nhật `siteurl`/`home` trong DB hoặc cấu hình tương đương về `http://host/appX`
- App phải tự render URL đúng theo subpath thực; không dùng “URL surgery” ở gateway để vá response HTML.

### 3.3 Vì sao ưu tiên Subdomain

- Subdomain giảm tối đa can thiệp vào ứng dụng legacy.
- Subpath chỉ nên dùng khi không khả thi về DNS/certificate/chính sách cấp domain riêng.

## 4) OIDC Client setup trên Keycloak

### 4.1 Quy tắc bắt buộc

- Mỗi app phải có `clientEndpoint` riêng:
  - App1: `/openid/app1`
  - App2: `/openid/app2`
  - App3: `/openid/app3`
- Scopes chuẩn đang dùng: `openid`, `profile`, `email`.
- Mỗi stack có thể dùng client riêng:
  - Stack A: `openig-client`
  - Stack B: `openig-client-b`

### 4.2 Redirect URIs (bắt buộc có bản with-port và no-port)

Ví dụ cho Stack A:

- `http://openiga.sso.local/openid/app1/*`
- `http://openiga.sso.local:80/openid/app1/*`
- `http://openiga.sso.local/openid/app2/*`
- `http://openiga.sso.local:80/openid/app2/*`

Ví dụ cho Stack B:

- `http://openigb.sso.local:9080/openid/app3/*`
- `http://openigb.sso.local/openid/app3/*` (dự phòng khi đi qua reverse proxy chuẩn hóa port)

### 4.3 `post_logout_redirect_uris` (separator `##`)

Trong Keycloak, thuộc tính client dùng key `post.logout.redirect.uris` và phân tách nhiều URL bằng `##`.

Ví dụ Stack A:

```text
http://openiga.sso.local/app1/##http://openiga.sso.local:80/app1/##http://openiga.sso.local/app2/##http://openiga.sso.local:80/app2/
```

Ví dụ Stack B:

```text
http://openigb.sso.local:9080/app3/##http://openigb.sso.local/app3/
```

### 4.4 Back-channel logout config trên Keycloak

- Mỗi client phải có `backchannelLogoutUrl` riêng trỏ về endpoint back-channel của app/client đó.
- Bật `backchannel.logout.session.required=true`.
- Khi user logout, Keycloak gửi back-channel đến tất cả client trong realm có cấu hình back-channel.

## 5) Session key format (QUAN TRỌNG)

Format đúng:

```text
oauth2:{FULL_URL_ORIGIN}/clientEndpoint
```

Trong đó `{FULL_URL_ORIGIN}` là `scheme://host[:port]` theo request public thực tế. Không dùng path-only kiểu `oauth2:/openid/appX` cho app mới.

Ví dụ đúng:

- Subdomain App1: `oauth2:http://wp-a.sso.local/openid/app1`
- Subpath App1: `oauth2:http://openiga.sso.local:80/openid/app1`
- Subpath App2: `oauth2:http://openiga.sso.local:80/openid/app2`

Ví dụ sai (dễ gây mất `id_token`):

- `oauth2:/openid/app1`
- `oauth2:/openid/app3`

Lý do: OpenIG lưu token theo key gắn với public URL đầy đủ (bao gồm scheme + host + optional port + clientEndpoint). Sai key => script không đọc được `id_token` => logout/blacklist SID hỏng.

Với mô hình subdomain, Groovy script nên lookup host-aware theo thứ tự:

1. `hostWithPort` (lấy từ `Host` header nếu có port)
2. `hostWithoutPort` (strip port)
3. Fallback `OPENIG_PUBLIC_URL`

Lý do: qua nhiều lớp proxy, `Host` header có thể có hoặc không có port. Thử nhiều variant giúp đọc đúng key ổn định hơn.

Lưu ý hiện trạng: một số script cũ của Stack B vẫn còn dùng key path-only (`oauth2:/openid/app3`). Khi tích hợp app mới hoặc harden hệ thống, hãy chuẩn hóa theo format full URL origin + `clientEndpoint`.

## 6) Filter chain chuẩn

### 6.1 App1 (WordPress)

Thứ tự đang chạy:

1. `OidcFilter`
2. `SessionBlacklistFilter`
3. `VaultCredentialFilter`
4. `WpSessionInjector` (`CredentialInjector.groovy`)

Thứ tự này không được đảo:

- OIDC phải chạy trước để có `attributes.openid`.
- Blacklist phải chạy sớm để chặn session đã logout.
- Vault lấy credential sau khi biết user.
- Injector chạy cuối để login ngầm vào app legacy rồi inject cookie.

### 6.2 App3 (.NET)

Stack B dùng chuỗi tương đương:

1. `OAuth2ClientFilter`
2. `SessionBlacklistFilter`
3. `DotnetCredentialInjectorFilter`

## 7) Route priority (lexicographic)

OpenIG load route theo thứ tự tên file/route có prefix số, nên dùng convention:

1. `00-backchannel-...` (ưu tiên cao nhất)
2. `00-...-logout-intercept`
3. `01-...` route app chính
4. `02-...` route app tiếp theo

Trong stack hiện tại:

- Stack A: `00-backchannel-logout-app1.json` -> `00-wp-logout.json` -> `01-wordpress.json` -> `02-app2.json`
- Stack B: `00-backchannel-logout-app3.json` -> `00-dotnet-logout.json` -> `01-dotnet.json`

## 8) Credential Injection (Vault AppRole + KV v2)

### 8.1 Luồng WordPress (Stack A)

1. `VaultCredentialFilter` đọc `preferred_username` từ OIDC user info.
2. Nếu chưa có `vault_token` hoặc token hết hạn:
   - Đọc `role_id` và `secret_id` từ file (`VAULT_ROLE_ID_FILE`, `VAULT_SECRET_ID_FILE`).
   - Login AppRole: `POST /v1/auth/approle/login`.
   - Cache token và expiry vào session OpenIG.
3. Đọc secret KV v2: `GET /v1/secret/data/wp-creds/{username}`.
4. Gắn kết quả vào `attributes.wp_credentials`.
5. `CredentialInjector.groovy`:
   - POST ngầm vào `http://wordpress/wp-login.php`.
   - Thu thập `Set-Cookie` từ WordPress.
   - Cache `wp_session_cookies` vào session OpenIG.
   - Inject cookie vào request upstream.

### 8.2 Luồng .NET (Stack B)

- Tương tự, nhưng secret path là `secret/data/dotnet-creds/{username}`.
- Injector xử lý anti-forgery token/cookie trước khi POST login.

## 9) Cross-stack SLO bằng Redis Blacklist (quan trọng nhất)

### 9.1 `BackchannelLogoutHandler`

- Nhận `POST` từ Keycloak tại `/openid/{app}/backchannel_logout`.
- Parse body `application/x-www-form-urlencoded`, lấy `logout_token`.
- Decode JWT payload (base64url), extract `sid` (fallback `sub`).
- Ghi Redis key:

```text
SET blacklist:{sid} 1 EX 3600
```

- Việc giao tiếp Redis làm thủ công bằng raw RESP protocol qua `Socket`.

### 9.2 `SessionBlacklistFilter`

- Mỗi request app đi qua filter sẽ:
  1. Resolve SID từ session cache (`oidc_sid` / `oidc_sid_app2`).
  2. Nếu chưa có cache, đọc `id_token` từ session key `oauth2:{FULL_URL_ORIGIN}/clientEndpoint`.
  3. Decode JWT lấy `sid` và cache lại.
  4. `GET blacklist:{sid}` trên Redis.
- Nếu bị blacklist:
  - `session.clear()`
  - Redirect về public URL hiện tại bằng `Host` header (không dùng internal URI).

### 9.3 Tại sao cross-stack hoạt động dù Redis tách riêng?

- Stack A dùng `redis-a`, Stack B dùng `redis-b`.
- Keycloak gửi back-channel tới tất cả client có `backchannelLogoutUrl` trong realm.
- Mỗi stack tự nhận lệnh logout và tự ghi SID vào Redis local của stack đó.

## 10) Nginx sticky session: dùng `ip_hash` (Stack A)

Stack A (`nginx/nginx.conf`) đang dùng:

```nginx
upstream openig_pool {
    ip_hash;
    server openig-1:8080;
    server openig-2:8080;
}
```

Lý do thực tế để ưu tiên `ip_hash` thay vì `hash $cookie_JSESSIONID` trong mô hình này:

- Affinity có ngay từ request đầu tiên (kể cả trước khi có cookie ổn định).
- Giảm rủi ro callback OIDC rơi sang node khác trong giai đoạn bootstrap session.
- Cấu hình đơn giản, ít phụ thuộc vào tình trạng cookie phía browser/proxy trung gian.

Ghi chú: Stack B hiện vẫn dùng `hash $cookie_JSESSIONID consistent`.

## 11) Dual-layer session

Hệ thống dùng 2 lớp session:

1. `JwtSession` cookie (`IG_SSO` ở Stack A, `IG_SSO_B` ở Stack B): giữ state pre-auth.
2. `JSESSIONID` server-side: giữ OAuth tokens sau auth.

Vì sao cần `JSESSIONID`:

- Bộ token (`access_token`, `id_token`, `refresh_token`) trong lab xấp xỉ ~4.8KB.
- Vượt ngưỡng cookie phổ biến 4KB nếu nhồi trực tiếp vào client cookie.

## 12) Environment variables quan trọng

| Biến môi trường | Mục đích | Ghi chú |
|---|---|---|
| `OPENIG_PUBLIC_URL` | Dùng để build session key OIDC đúng format | Stack A đang dùng trong `SloHandler` và `SessionBlacklistFilter*` |
| `REDIS_HOST` | Host Redis blacklist | Stack A: `redis-a`, Stack B: `redis-b` |
| `VAULT_ADDR` | Endpoint Vault | Stack A: `http://vault:8200`, Stack B: `http://vault-b:8200` |
| `KEYCLOAK_BROWSER_URL` | URL browser redirect đến Keycloak | Ví dụ `http://auth.sso.local:8080` |
| `KEYCLOAK_INTERNAL_URL` | URL OpenIG gọi token/userinfo/jwks | Ví dụ `http://host.docker.internal:8080` |

## 13) Checklist tích hợp (theo vai trò)

| Bước | Việc cần làm | Trách nhiệm chính | Done khi |
|---|---|---|---|
| 1 | Chốt public domain/port/path cho app | App team + Platform | URL convention được phê duyệt |
| 2 | Chọn mô hình triển khai: **Subdomain** = cấp subdomain + DNS/hosts (app không cần sửa) / **Subpath** = app team cấu hình base path (`siteurl`, `UsePathBase`, `RAILS_RELATIVE_URL_ROOT`, `Alias`) | App team + Platform | Subdomain chạy ở `/` hoặc subpath render link đúng dưới `/appX` |
| 3 | Tạo route OpenIG (`00-backchannel`, `00-logout-intercept`, `01-app`) | IAM/Gateway team | Route match đúng Host + path |
| 4 | Khai báo `clientEndpoint` riêng cho app | IAM/Gateway team | Callback path không trùng app khác |
| 5 | Tạo/đăng ký Keycloak client + scopes | IAM team | Login OIDC nhận đủ claims |
| 6 | Điền `redirectUris` cả with-port/no-port variants | IAM team | Không còn lỗi `Invalid redirect_uri` |
| 7 | Điền `post.logout.redirect.uris` bằng `##` | IAM team | Logout redirect đúng |
| 8 | Cấu hình `backchannelLogoutUrl` + `backchannel.logout.session.required=true` | IAM team | Back-channel POST tới app thành công |
| 9 | Cấu hình Vault AppRole + secret path + policy read-only | Security/Platform | OpenIG đọc được cred theo username |
| 10 | Implement `VaultCredentialFilter` + `CredentialInjector` | IAM/Gateway team | Login ngầm vào legacy app thành công |
| 11 | Verify session key format `oauth2:{FULL_URL_ORIGIN}/clientEndpoint` | IAM/Gateway team | Script đọc được `id_token` ổn định |
| 12 | Test SSO + SLO nội stack + cross-stack | QA + App team | Logout một app làm mất phiên đúng kỳ vọng |

## 14) Troubleshooting thực tế

### 14.1 `Invalid redirect_uri`

Nguyên nhân thường gặp:

- Thiếu variant `:80` hoặc thiếu variant no-port trong `redirectUris`.
- Nhập sai định dạng `post.logout.redirect.uris` (không dùng `##`).
- Nginx forward `Host` sai (`$host` làm rơi port).

### 14.2 Không tìm được `id_token` trong session

Triệu chứng:

- Script logout/blacklist log ra `id_token` null.

Nguyên nhân:

- Dùng session key path-only (`oauth2:/openid/appX`) thay vì full URL origin (`scheme://host[:port]`).

Fix:

- Chuẩn hóa về `oauth2:{OPENIG_PUBLIC_URL}/openid/{appX}`.

### 14.3 `SessionBlacklistFilter` redirect về internal URI

Triệu chứng:

- Redirect về URL nội bộ không truy cập được từ browser.

Nguyên nhân:

- Dùng `request.uri.toString()` trực tiếp (thiếu public host/port).

Fix:

- Dựng redirect URL bằng `Host` header: `http://{Host}{path}?{query}`.

### 14.4 Groovy `GString` vs `String` khi set header

Triệu chứng:

- Lỗi kiểu `Cannot parse header value from type ...` khi set `Location`.

Nguyên nhân:

- Đưa `GString` trực tiếp vào header map của OpenIG.

Fix:

- Ép về `String` rõ ràng (string concatenation/cast) trước khi set header.

### 14.5 OpenIG không hot-reload script như kỳ vọng

Triệu chứng:

- Sửa file `.groovy` nhưng runtime vẫn hành vi cũ.

Fix vận hành:

- Restart container OpenIG sau mỗi lần đổi script/filter route quan trọng.

## 15) FAQ

### Q1. Có thể dùng chung 1 OIDC client cho nhiều app không?

Có thể (Stack A hiện dùng 1 client cho App1 + App2), nhưng mỗi app vẫn phải có `clientEndpoint` riêng để callback/session key không đè nhau.

### Q2. Vì sao logout App1 lại làm App2 bị yêu cầu login lại?

Do 2 app cùng nằm sau cùng OpenIG session stack A; khi SLO clear session/token thì cả hai app mất phiên SSO cục bộ.

### Q3. Cross-stack SLO cần Redis chung không?

Không bắt buộc trong mô hình hiện tại. Mỗi stack dùng Redis riêng, Keycloak broadcast back-channel tới từng client để mỗi stack tự blacklist SID local.

### Q4. Có bắt buộc app legacy hỗ trợ OIDC native không?

Không. App chỉ cần có login form/session cookie chuẩn để OpenIG thực hiện credential injection.

### Q5. Khi thêm app mới (`/app4`) cần đổi gì tối thiểu?

- Thêm route `00-backchannel-logout-app4` và `01-app4`.
- Chọn `clientEndpoint` mới `/openid/app4`.
- Đăng ký đầy đủ redirect/logout URIs trên Keycloak.
- Cấu hình secret path trên Vault và injector tương ứng.

### Q6. Vì sao tài liệu nhấn mạnh with-port và no-port variant?

Vì thực tế header Host có thể khác nhau theo proxy chain. Khai báo đủ biến thể giúp tránh lỗi production khó tái hiện.

---

## Phụ lục: Mẫu nhanh cho team tích hợp app mới

### Template A — Subdomain flow (khuyến nghị)

1. Chọn subdomain riêng cho app (ví dụ `appx.sso.local`) và cấu hình DNS/hosts.
2. Tạo route OpenIG match theo `Host` app, app upstream chạy tại `/`.
3. Tạo Keycloak client/endpoint `/openid/appX`.
4. Đăng ký `redirectUris` phù hợp (with-port/no-port nếu có khả năng thay đổi qua proxy).
5. Đăng ký `post.logout.redirect.uris` bằng `##`.
6. Bật back-channel logout và trỏ URL `/openid/appX/backchannel_logout`.
7. Cấu hình Vault + injector cho app.
8. Kiểm thử: login, refresh, logout nội stack, logout cross-stack.

### Template B — Subpath flow (fallback)

1. Chốt public URL app theo prefix path: `http://openiga.sso.local/appX`.
2. Cấu hình app tự biết base path `/appX` (`UsePathBase`, `RAILS_RELATIVE_URL_ROOT`, `Alias`, DB update...).
3. Tạo route OpenIG match theo `Host` + path `/appX` với filter order chuẩn.
4. Tạo Keycloak client/endpoint `/openid/appX`.
5. Đăng ký `redirectUris` with-port/no-port.
6. Đăng ký `post.logout.redirect.uris` bằng `##`.
7. Bật back-channel logout và trỏ URL `/openid/appX/backchannel_logout`.
8. Cấu hình Vault + injector cho app.
9. Kiểm thử: login, refresh, logout nội stack, logout cross-stack.

Nếu bạn làm đủ các bước trên, team ứng dụng có thể go-live mà không cần sửa core business code của legacy app.
