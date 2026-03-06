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
                                      | Lab sslRequired: NONE       |
                                      +---------------+-------------+
                                                      |
                           OIDC Auth/Token/UserInfo  |
                                                      v
+--------------------------------+        +----------------------------------+
| Stack A (sso-lab)              |        | Stack B (sso-lab-dotnet)         |
| Browser -> nginx-a:80          |        | Browser -> nginx-b:9080          |
|            -> openig-1/2       |        |            -> openig-b1/b2       |
| App1: wp-a.sso.local:80        |        | App3: jellyfin-b.sso.local:9080  |
|       -> WordPress             |        |       -> Jellyfin                |
| App2: whoami-a.sso.local:80    |        | App4: redmine-b.sso.local:9080   |
|       -> whoami                |        |       -> Redmine                 |
| Canonical OpenIG URL:          |        | Canonical OpenIG URL:            |
|   http://openiga.sso.local:80  |        |   http://openigb.sso.local:9080  |
| Redis: redis-a                 |        | Redis: redis-b                   |
| Vault: vault (wp-creds/*)      |        | Vault: vault-b                   |
|                                |        |   (jellyfin-creds/*,             |
|                                |        |    redmine-creds/*)              |
+--------------------------------+        +----------------------------------+

Back-channel logout flow:
Keycloak -> POST http://host.docker.internal/openid/app1/backchannel_logout
Keycloak -> POST http://host.docker.internal:9080/openid/app3/backchannel_logout
Keycloak -> POST http://host.docker.internal:9080/openid/app4/backchannel_logout
Mỗi stack tự ghi blacklist SID vào Redis của chính stack đó.
```

### 1.2 Thành phần chính

- `OpenIG`: reverse proxy + OIDC gateway + filter chain + credential injection.
- `Keycloak`: IdP dùng chung cho cả Stack A và Stack B.
- `Vault`: lưu credential legacy app theo user (KV v2).
- `Redis`: blacklist SID để enforce Single Logout kiểu back-channel.

## 2) URL convention (subdomain app + callback `/openid/{app}`)

### 2.1 Quy ước public URL hiện tại

- Stack A:
  - Canonical OpenIG URL: `http://openiga.sso.local:80`
  - App1 (WordPress): `http://wp-a.sso.local/`
  - App2 (whoami): `http://whoami-a.sso.local/`
- Stack B:
  - Canonical OpenIG URL: `http://openigb.sso.local:9080`
  - App3 (Jellyfin): `http://jellyfin-b.sso.local:9080/`
  - App4 (Redmine): `http://redmine-b.sso.local:9080/`

`OPENIG_PUBLIC_URL` vẫn là canonical URL của từng stack, nhưng browser hiện truy cập app qua subdomain riêng. Đây là lý do một số script không thể chỉ dựa vào duy nhất `OPENIG_PUBLIC_URL` để lookup session key.

### 2.2 Quy ước route điều kiện Host + path

- Route app match theo `Host` public của từng app; path app runtime thường là `/`.
- Callback OIDC và back-channel logout vẫn dùng prefix `/openid/{app-id}` trên chính host app đó.
- Nginx phải forward `Host` bằng `$http_host` để giữ cả port:

```nginx
proxy_set_header Host $http_host;
```

Nếu dùng `$host`, bạn rất dễ mất `:80`/`:9080`, dẫn tới mismatch `redirect_uri` và session key.

## 3) Ứng dụng phải tự biết Public URL của chính nó

Trong môi trường lab hiện tại, các app chính đi theo mô hình subdomain nên app thường chạy ở root `/`. Tuy nhiên nguyên tắc vẫn giữ nguyên: app phải tự render URL đúng theo public host/path mà browser đang thấy; không dùng “URL surgery” ở gateway để vá hành vi ứng dụng.

### 3.1 WordPress (App1)

- Browser truy cập WordPress qua `http://wp-a.sso.local/`.
- `OPENIG_PUBLIC_URL` của Stack A vẫn là `http://openiga.sso.local:80`; hai giá trị này khác nhau.
- Vì host browser và canonical OpenIG URL không trùng nhau, các filter xử lý session/SLO phải dùng lookup host-aware thay vì hard-code một session key duy nhất.

### 3.2 Jellyfin (App3) và Redmine (App4)

- Jellyfin public URL: `http://jellyfin-b.sso.local:9080/`.
- Redmine public URL: `http://redmine-b.sso.local:9080/`.
- Cả hai app hiện được expose ở root `/` trên subdomain riêng; không dùng prefix `/app3` hoặc `/app4` cho luồng browser chính.

### 3.3 Khi nào cần base-path/sub-path config?

- Chỉ cần khi app thật sự được publish dưới `/appX`.
- Khi đó app phải tự biết base path bằng setting native như `siteurl`, `RAILS_RELATIVE_URL_ROOT`, `UsePathBase`, `Alias`...
- App phải render URL tuyệt đối/tương đối theo path public thực, không dựa vào rewrite HTML response ở OpenIG.

## 4) OIDC Client setup trên Keycloak

### 4.1 Quy tắc bắt buộc

- Mỗi app phải có `clientEndpoint` riêng:
  - App1: `/openid/app1`
  - App2: `/openid/app2`
  - App3: `/openid/app3`
  - App4: `/openid/app4`
- Scopes chuẩn đang dùng: `openid`, `profile`, `email`.
- Mỗi stack có thể dùng client riêng:
  - Stack A: `openig-client`
  - Stack B: `openig-client-b`
- Trong môi trường lab, realm Keycloak đang để `sslRequired=NONE` vì toàn bộ luồng chạy trên HTTP nội bộ. Không mang cấu hình này sang môi trường production.

### 4.2 Redirect URIs (bắt buộc có bản with-port và no-port)

Ví dụ cho Stack A:

- `http://wp-a.sso.local/openid/app1/*`
- `http://wp-a.sso.local:80/openid/app1/*`
- `http://whoami-a.sso.local/openid/app2/*`
- `http://whoami-a.sso.local:80/openid/app2/*`

Ví dụ cho Stack B:

- `http://jellyfin-b.sso.local:9080/openid/app3/*`
- `http://jellyfin-b.sso.local/openid/app3/*`
- `http://redmine-b.sso.local:9080/openid/app4/*`
- `http://redmine-b.sso.local/openid/app4/*`

### 4.3 `post_logout_redirect_uris` (separator `##`)

Trong Keycloak, thuộc tính client dùng key `post.logout.redirect.uris` và phân tách nhiều URL bằng `##`.

Ví dụ Stack A:

```text
http://wp-a.sso.local/##http://wp-a.sso.local:80/##http://whoami-a.sso.local/##http://whoami-a.sso.local:80/
```

Ví dụ Stack B:

```text
http://jellyfin-b.sso.local:9080/##http://jellyfin-b.sso.local/##http://redmine-b.sso.local:9080/##http://redmine-b.sso.local/
```

### 4.4 Back-channel logout config trên Keycloak

- Mỗi client phải có `backchannel.logout.url` riêng trỏ về endpoint back-channel của app/client đó.
- Giá trị này trong lab PHẢI dùng `host.docker.internal`, không dùng domain `.sso.local`, vì container Keycloak không resolve được các host public kiểu `wp-a.sso.local`, `jellyfin-b.sso.local`, `redmine-b.sso.local`.
- Ví dụ:
  - App1: `http://host.docker.internal/openid/app1/backchannel_logout`
  - App3: `http://host.docker.internal:9080/openid/app3/backchannel_logout`
  - App4: `http://host.docker.internal:9080/openid/app4/backchannel_logout`
- Bật `backchannel.logout.session.required=true`.
- Khi user logout, Keycloak gửi back-channel đến tất cả client trong realm có cấu hình back-channel.

Để request back-channel không bị retry/chuyển node giữa chừng, Nginx location cho `/openid/{app}/backchannel_logout` phải có tối thiểu:

```nginx
proxy_request_buffering off;
proxy_next_upstream off;
```

## 5) Session key format (QUAN TRỌNG)

Format đúng:

```text
oauth2:{FULL_PUBLIC_ORIGIN}/clientEndpoint
```

Trong đó `FULL_PUBLIC_ORIGIN` có thể là `http://host:port` hoặc `http://host`, tùy đúng giá trị OpenIG đã dùng khi tạo phiên OIDC.

Không dùng path-only kiểu `oauth2:/openid/appX` cho app mới.

Ví dụ đúng trong Stack A:

- App1 theo host browser có thể là `oauth2:http://wp-a.sso.local:80/openid/app1` hoặc `oauth2:http://wp-a.sso.local/openid/app1`.
- App1 canonical fallback là `oauth2:http://openiga.sso.local:80/openid/app1`.
- App2 canonical fallback là `oauth2:http://openiga.sso.local:80/openid/app2`.

Ví dụ đúng trong Stack B:

- App3: `oauth2:http://jellyfin-b.sso.local:9080/openid/app3`
- App4: `oauth2:http://redmine-b.sso.local:9080/openid/app4`
- Canonical fallback của cả Stack B vẫn dựa trên `OPENIG_PUBLIC_URL`: `oauth2:http://openigb.sso.local:9080/openid/appX`.

Ví dụ sai (dễ gây mất `id_token`):

- `oauth2:/openid/app1`
- `oauth2:/openid/app3`

Lý do: OpenIG lưu token theo key gắn with public URL đầy đủ (bao gồm scheme + host + port + clientEndpoint). Sai key => script không đọc được `id_token` => logout/blacklist SID hỏng.

Lưu ý hiện trạng:

- Stack A `SessionBlacklistFilter` không lookup theo 1 key duy nhất. Filter này đang dùng 3-key host-aware lookup cho App1 theo thứ tự: `hostWithPort`, `hostWithoutPort`, rồi fallback về `OPENIG_PUBLIC_URL`.
- Lý do: browser truy cập WordPress bằng `wp-a.sso.local`, nhưng `OPENIG_PUBLIC_URL` lại là `openiga.sso.local:80`.
- Không dùng path-only kiểu `oauth2:/openid/appX` cho app mới; kiểu này rất dễ làm mất `id_token` khi xử lý SLO.

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

### 6.2 App3 (Jellyfin)

Stack B dùng chuỗi:

1. `OAuth2ClientFilter`
2. `SessionBlacklistFilterApp3`
3. `VaultCredentialFilterJellyfin`
4. `JellyfinTokenInjectorFilter`
5. `JellyfinResponseRewriterFilter`

### 6.3 App4 (Redmine)

Stack B dùng chuỗi:

1. `OAuth2ClientFilter`
2. `SessionBlacklistFilterApp4`
3. `VaultCredentialFilterRedmine`
4. `RedmineCredentialInjectorFilter`

## 7) Route priority (lexicographic)

OpenIG load route theo thứ tự tên file/route có prefix số, nên dùng convention:

1. `00-backchannel-...` (ưu tiên cao nhất)
2. `00-...-logout-intercept`
3. `01-...` route app chính
4. `02-...` route app tiếp theo

Trong stack hiện tại:

- Stack A: `00-backchannel-logout-app1.json` -> `00-wp-logout.json` -> `01-wordpress.json` -> `02-app2.json`
- Stack B: `00-backchannel-logout-app3.json` -> `00-backchannel-logout-app4.json` -> `00-jellyfin-logout.json` -> `00-redmine-logout.json` -> `01-jellyfin.json` -> `02-redmine.json`

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

### 8.2 Luồng Jellyfin (Stack B)

1. `VaultCredentialFilterJellyfin` đọc credential từ `secret/data/jellyfin-creds/{username}`.
2. `JellyfinTokenInjector.groovy` gọi `POST /Users/AuthenticateByName` để lấy `AccessToken` dạng JSON.
3. Token được cache vào session OpenIG.
4. `JellyfinResponseRewriter.groovy` inject script vào HTML để set `localStorage` đúng format Jellyfin SPA mong đợi.
5. Các API call tiếp theo được inject `Authorization` header từ token đã cache.

### 8.3 Luồng Redmine (Stack B)

1. `VaultCredentialFilterRedmine` đọc credential từ `secret/data/redmine-creds/{username}`.
2. `RedmineCredentialInjector.groovy` GET `/login` để lấy `authenticity_token` và init cookie.
3. Sau đó script POST credentials + token + init cookies vào Redmine.
4. Session cookie Redmine được cache vào session OpenIG rồi inject vào request upstream.

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
  2. Nếu chưa có cache, đọc `id_token` từ session key `oauth2:{FULL_PUBLIC_ORIGIN}/clientEndpoint`.
  3. Decode JWT lấy `sid` và cache lại.
  4. `GET blacklist:{sid}` trên Redis.
- Riêng Stack A App1 đang dùng 3-key host-aware lookup:
  1. `oauth2:http://{HostWithPort}/openid/app1`
  2. `oauth2:http://{HostWithoutPort}/openid/app1`
  3. `oauth2:{OPENIG_PUBLIC_URL}/openid/app1`
  Đây là bắt buộc vì host browser `wp-a.sso.local` khác với `OPENIG_PUBLIC_URL` là `openiga.sso.local:80`.
- Nếu bị blacklist:
  - `session.clear()`
  - Redirect về public URL hiện tại bằng `Host` header.
  - KHÔNG dùng `request.uri.toString()`, vì trong flow upstream nó có thể trả về internal URI kiểu `http://wordpress/...` hoặc `http://redmine:3000/...` mà browser không truy cập được.

### 9.3 Tại sao cross-stack hoạt động dù Redis tách riêng?

- Stack A dùng `redis-a`, Stack B dùng `redis-b`.
- Keycloak gửi back-channel tới tất cả client có `backchannel.logout.url` trong realm.
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

### 10.1 Back-channel logout location trên Nginx

Với mọi location `/openid/{app}/backchannel_logout`, cấu hình tối thiểu phải giữ nguyên hai dòng sau:

```nginx
proxy_request_buffering off;
proxy_next_upstream off;
```

Lý do:

- Back-channel logout là request 1-shot từ Keycloak; không được buffering/retry như traffic thông thường.
- Nếu Nginx retry sang node khác, SID blacklist có thể ghi lệch node hoặc gây hành vi khó debug.
- Cấu hình này hiện đang áp dụng cho `wp-a.sso.local`, `jellyfin-b.sso.local:9080` và `redmine-b.sso.local:9080`.

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
| `OPENIG_PUBLIC_URL` | Dùng làm canonical origin cho session key OIDC | Stack A còn phải fallback về biến này vì browser host `wp-a.sso.local` khác `openiga.sso.local:80` |
| `REDIS_HOST` | Host Redis blacklist | Stack A: `redis-a`, Stack B: `redis-b` |
| `VAULT_ADDR` | Endpoint Vault | Stack A: `http://vault:8200`, Stack B: `http://vault-b:8200` |
| `KEYCLOAK_BROWSER_URL` | URL browser redirect đến Keycloak | Ví dụ `http://auth.sso.local:8080` |
| `KEYCLOAK_INTERNAL_URL` | URL OpenIG gọi token/userinfo/jwks | Ví dụ `http://host.docker.internal:8080` |

## 13) Phân loại cơ chế xác thực Legacy App — 3 nhóm

### Nhóm 1 — Form-based Login + Server-side Session Cookie

Đại diện: WordPress, Redmine

Cơ chế: App có HTML login form. Browser POST credentials -> server set session cookie -> mọi request sau mang cookie đó.

OpenIG pattern: `CredentialInjector` POST ngầm vào login endpoint -> thu `Set-Cookie` -> cache vào OpenIG session -> inject cookie vào mọi request tiếp theo.

Chi tiết triển khai:

- Simple (WordPress): 1-step POST, không có CSRF.
- CSRF (Redmine/Rails): 2-step — GET login page lấy `authenticity_token` + `Set-Cookie` khởi tạo, rồi POST với token và init cookies.
- Nhận biết thành công: HTTP `302` + new `Set-Cookie` session.
- Nhận biết thất bại: HTTP `200` (form render lại với error).

Độ khó: Trung bình. Cần reverse-engineer login flow (field names, CSRF, cookie names).

Ví dụ trong lab: `CredentialInjector.groovy` (WP), `RedmineCredentialInjector.groovy` (Rails 2-step)

### Nhóm 2 — Token-based API Auth + Client-side State (SPA)

Đại diện: Jellyfin

Cơ chế hoàn toàn khác: KHÔNG có HTML login form. App là SPA (Single Page Application). Auth qua REST API (`POST /Users/AuthenticateByName`) -> server trả `AccessToken` dạng JSON. Token được SPA lưu vào `localStorage` của browser (KHÔNG phải cookie server-side). Mọi API call sau đó browser tự đọc token từ `localStorage` và gửi trong `Authorization` header.

Tại sao inject header không đủ: OpenIG có thể inject `Authorization` header vào HTTP request, nhưng SPA không đọc header từ server — nó đọc `localStorage`. Nên dù header đúng, browser-side JS vẫn thấy "chưa đăng nhập" và redirect về login page.

OpenIG phải xử lý 2 tầng:

- Tầng 1 (`JellyfinTokenInjector`): Gọi `AuthenticateByName` -> lấy `AccessToken` -> cache vào OpenIG session -> inject `Authorization` header cho các API call backend (Jellyfin server cần header này để validate request).
- Tầng 2 (`JellyfinResponseRewriter`): Intercept HTML response -> inject `<script>` vào `</head>` (PHẢI là `</head>`, không phải `</body>`) -> script chạy TRƯỚC Jellyfin JS -> set `localStorage["jellyfin_credentials"]` đúng format mà Jellyfin SPA mong đợi.

Race condition nguy hiểm: Browser fire 10-20 request song song khi load trang lần đầu (JS chunks, CSS, API calls). Nếu tất cả đều gọi `AuthenticateByName` đồng thời -> Jellyfin SQLite `DbUpdateConcurrencyException` -> HTTP `500`. Fix: chỉ gọi `AuthenticateByName` khi request có `Accept: text/html` (HTML page request), các request khác pass-through.

`localStorage` format bắt buộc (Jellyfin yêu cầu đủ các field):

```json
{"Servers":[{"Id":"server-id","AccessToken":"token","UserId":"user-id","DateLastAccessed":timestamp,"LastConnectionMode":0,"LocalAddress":"http://host","ManualAddress":"http://host","RemoteAddress":"http://host"}]}
```

SLO đặc thù: Phải gọi `POST /Sessions/Logout` tới Jellyfin API với token trước khi redirect về Keycloak logout. Nếu bỏ qua bước này, Jellyfin session vẫn còn active dù Keycloak đã logout.

Độ khó: Cao hơn đáng kể so với Nhóm 1. Cần hiểu SPA lifecycle, `localStorage` format, script injection timing, race condition prevention.

Ví dụ trong lab: `JellyfinTokenInjector.groovy`, `JellyfinResponseRewriter.groovy`, `SloHandlerJellyfin.groovy`

### Nhóm 3 — Native OIDC/OAuth2 App

Đại diện: ASP.NET Core với OpenID Connect middleware

Cơ chế: App tự hỗ trợ OIDC natively, tự xử lý auth flow với Keycloak.

OpenIG pattern: Chỉ cần `OAuth2ClientFilter` để enforce authentication + `SessionBlacklistFilter` cho SLO. Không cần `CredentialInjector`.

Độ khó: Thấp nhất. App tự lo auth, OpenIG chỉ đóng vai proxy + SLO enforcer.

| Tiêu chí | Nhóm 1: Form+Cookie | Nhóm 2: API+localStorage (SPA) | Nhóm 3: Native OIDC |
|----------|--------------------|---------------------------------|---------------------|
| Cơ chế auth | HTML form POST | REST API -> JSON token | OIDC flow native |
| Nơi lưu auth state | Server-side session cookie | Browser localStorage | App tự quản lý |
| OpenIG inject gì | Session cookies | Authorization header + localStorage script | Không inject |
| Filters cần viết | VaultCredential + CredentialInjector | TokenInjector + ResponseRewriter | Không cần injector |
| Độ khó | Trung bình | Cao | Thấp |
| CSRF cần xử lý | Tùy app (Rails: có, WP: không) | Không | Không |
| Ví dụ trong lab | WordPress, Redmine | Jellyfin | .NET app |
| Script injection vào response | Không | Có (HTML rewrite) | Không |

## 14) Checklist tích hợp (theo vai trò)

| Bước | Việc cần làm | Trách nhiệm chính | Done khi |
|---|---|---|---|
| 1 | Chốt public domain/port/path cho app | App team + Platform | URL convention được phê duyệt |
| 2 | Cấu hình app theo public URL thật: subdomain thì chạy ở `/`, subpath thì set base URL (`siteurl`, `RAILS_RELATIVE_URL_ROOT`, `UsePathBase`, `Alias`) | App team | App render link đúng theo host/path public |
| 3 | Tạo route OpenIG (`00-backchannel`, `00-logout-intercept`, `01-app`) | IAM/Gateway team | Route match đúng Host + path |
| 4 | Khai báo `clientEndpoint` riêng cho app | IAM/Gateway team | Callback path không trùng app khác |
| 5 | Tạo/đăng ký Keycloak client + scopes | IAM team | Login OIDC nhận đủ claims |
| 6 | Điền `redirectUris` cả with-port/no-port variants | IAM team | Không còn lỗi `Invalid redirect_uri` |
| 7 | Điền `post.logout.redirect.uris` bằng `##` | IAM team | Logout redirect đúng |
| 8 | Cấu hình `backchannel.logout.url` bằng `host.docker.internal` + `backchannel.logout.session.required=true` | IAM team | Back-channel POST tới app thành công |
| 9 | Cấu hình Vault AppRole + secret path + policy read-only | Security/Platform | OpenIG đọc được cred theo username |
| 10 | Chọn đúng pattern theo loại app: `CredentialInjector` hoặc `TokenInjector + ResponseRewriter` hoặc native OIDC | IAM/Gateway team | App login thành công theo đúng cơ chế auth của nó |
| 11 | Verify session key candidates `oauth2:{FULL_PUBLIC_ORIGIN}/clientEndpoint` theo đúng host browser và `OPENIG_PUBLIC_URL` | IAM/Gateway team | Script đọc được `id_token` ổn định |
| 12 | Test SSO + SLO nội stack + cross-stack | QA + App team | Logout một app làm mất phiên đúng kỳ vọng |

## 15) Troubleshooting thực tế

### 14.1 `Invalid redirect_uri`

Nguyên nhân thường gặp:

- Thiếu variant `:80` hoặc thiếu variant no-port trong `redirectUris`.
- Nhập sai định dạng `post.logout.redirect.uris` (không dùng `##`).
- Nginx forward `Host` sai (`$host` làm rơi port).

### 14.2 Không tìm được `id_token` trong session

Triệu chứng:

- Script logout/blacklist log ra `id_token` null.

Nguyên nhân:

- Dùng session key path-only (`oauth2:/openid/appX`) thay vì full URL + port.

Fix:

- Chuẩn hóa về `oauth2:{OPENIG_PUBLIC_URL}/openid/{appX}`.
- Với Stack A App1, dùng 3-key host-aware lookup (`hostWithPort`, `hostWithoutPort`, `OPENIG_PUBLIC_URL`) thay vì chỉ thử 1 key.

### 14.3 `SessionBlacklistFilter` redirect về internal URI

Triệu chứng:

- Redirect về URL nội bộ không truy cập được từ browser.

Nguyên nhân:

- Dùng `request.uri.toString()` trực tiếp; trong upstream flow giá trị này có thể là internal URI kiểu `http://wordpress/...`.

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

## 16) FAQ

### Q1. Có thể dùng chung 1 OIDC client cho nhiều app không?

Có thể (Stack A hiện dùng 1 client cho App1 + App2), nhưng mỗi app vẫn phải có `clientEndpoint` riêng để callback/session key không đè nhau.

### Q2. Vì sao logout App1 lại làm App2 bị yêu cầu login lại?

Do 2 app cùng nằm sau cùng OpenIG session stack A; khi SLO clear session/token thì cả hai app mất phiên SSO cục bộ.

### Q3. Cross-stack SLO cần Redis chung không?

Không bắt buộc trong mô hình hiện tại. Mỗi stack dùng Redis riêng, Keycloak broadcast back-channel tới từng client để mỗi stack tự blacklist SID local.

### Q4. Có bắt buộc app legacy hỗ trợ OIDC native không?

Không. Trong lab hiện có đủ 3 nhóm:

- Form-based + session cookie: WordPress, Redmine.
- Token-based SPA + `localStorage`: Jellyfin.
- Native OIDC: .NET app.

Điều quan trọng là chọn đúng pattern OpenIG theo cơ chế auth thực của app, không ép mọi app vào mô hình form login.

### Q5. Khi thêm app mới (`appX`) cần đổi gì tối thiểu?

- Thêm route `00-backchannel-logout-appX` và route app chính tương ứng.
- Chọn `clientEndpoint` mới `/openid/appX`.
- Đăng ký đầy đủ redirect/logout URIs trên Keycloak.
- Cấu hình secret path trên Vault và injector tương ứng.

### Q6. Vì sao tài liệu nhấn mạnh with-port và no-port variant?

Vì thực tế header Host có thể khác nhau theo proxy chain. Khai báo đủ biến thể giúp tránh lỗi production khó tái hiện.

---

## Phụ lục: Mẫu nhanh cho team tích hợp app mới

1. Chốt public URL app: ưu tiên subdomain riêng như `http://app-x.sso.local[:port]/`; chỉ dùng `http://openig-a/appX` khi buộc phải đi subpath.
2. Nếu dùng subdomain, để app chạy ở `/`; nếu dùng subpath, cấu hình app tự biết `/appX`.
3. Tạo route OpenIG với filter order chuẩn.
4. Tạo Keycloak client/endpoint `/openid/appX`.
5. Đăng ký `redirectUris` with-port/no-port.
6. Đăng ký `post.logout.redirect.uris` bằng `##`.
7. Bật back-channel logout và trỏ `backchannel.logout.url` về `http://host.docker.internal[:port]/openid/appX/backchannel_logout`.
8. Cấu hình Vault + injector cho app.
9. Kiểm thử: login, refresh, logout nội stack, logout cross-stack.

Nếu bạn làm đủ các bước trên, team ứng dụng có thể go-live mà không cần sửa core business code của legacy app.

---

# Phần 2: Thu thập Thông tin Kỹ thuật từ App Legacy

### Tại sao cần thu thập?

Trước khi viết CredentialInjector.groovy, bạn cần biết CHÍNH XÁC:
- URL endpoint nhận POST login
- Tên các input field (name attribute)
- Có CSRF token không? Tên field là gì?
- Tên cookie session sau khi đăng nhập thành công
- HTTP status code khi login thành công
- URL trang sau khi login redirect đến

Nếu đoán sai bất kỳ thông tin nào, CredentialInjector sẽ thất bại im lặng.

### Phương pháp 1: Xem HTML Source

Truy cập trang login của app, nhấn chuột phải > View Page Source.

Tìm thẻ form:
```html
<form method="POST" action="/login">
  <input type="text" name="username" />
  <input type="password" name="password" />
  <input type="hidden" name="authenticity_token" value="abc123..." />
  <button type="submit">Login</button>
</form>
```

Ghi lại:
- `action` = login endpoint (ví dụ `/login`)
- `name` của input username (có thể là `username`, `login`, `user[login]`, `j_username`, `log`)
- `name` của input password (có thể là `password`, `passwd`, `user[password]`, `j_password`, `pwd`)
- `name` của hidden CSRF field (thường là `authenticity_token`, `_token`, `__RequestVerificationToken`, `csrf_token`)

### Phương pháp 2: DevTools Network Tab (chính xác nhất)

Mở DevTools (F12) > tab Network > tick "Preserve log".

Thực hiện login thủ công. Sau đó tìm request POST đến login endpoint:

**Request Headers cần ghi lại:**
- Cookie header (session init cookies nếu có)
- Content-Type (thường là `application/x-www-form-urlencoded`)

**Request Body (Form Data) — đây là thông tin quan trọng nhất:**
```
username=alice
password=AlicePass123
authenticity_token=abc123xyz
```

**Response:**
- Status code: 302 (thành công) hay 200 (thất bại với form lỗi)?
- Set-Cookie headers: tên cookie session là gì? (ví dụ `_redmine_session`, `JSESSIONID`, `.ASPXAUTH`)
- Location header (redirect đến đâu sau login thành công?)

### Phương pháp 3: curl thủ công

Dùng curl để test từng bước:

**Bước 1: GET login page, lấy CSRF token và session init cookie**
```bash
curl -v -c /tmp/app-cookies.txt http://localhost:3000/login 2>&1 | grep -E "(Set-Cookie|authenticity_token|_token)"
```

**Bước 2: POST login với credentials**
```bash
curl -v -b /tmp/app-cookies.txt -c /tmp/app-cookies-after.txt 
  -X POST http://localhost:3000/login 
  -d "username=alice&password=AlicePass123&authenticity_token=TOKEN_FROM_STEP1" 
  -H "Content-Type: application/x-www-form-urlencoded" 
  --max-redirs 0 2>&1 | grep -E "(HTTP/|Set-Cookie|Location)"
```

Kết quả mong đợi:
```
HTTP/1.1 302 Found
Location: /dashboard
Set-Cookie: _app_session=abc123xyz; path=/; HttpOnly
```

**Bước 3: Test session còn hoạt động**
```bash
curl -v -b /tmp/app-cookies-after.txt http://localhost:3000/dashboard 2>&1 | grep "HTTP/"
```
Nếu trả về 200 = session hợp lệ. Nếu trả về 302 về /login = session không hoạt động.

### Checklist kỹ thuật cần điền trước khi viết CredentialInjector

| Thông tin | Giá trị |
|-----------|---------|
| Login endpoint (method + URL) | POST /login |
| Field name: username | `username` hoặc `login` |
| Field name: password | `password` |
| CSRF token field name | `authenticity_token` (nếu có) |
| CSRF: cần GET trước không? | Có / Không |
| Init cookies (từ GET) | Tên cookie cần gửi kèm POST |
| HTTP status thành công | 302 |
| Session cookie name | `_redmine_session` |
| Redirect sau login | `/my/page` hoặc path khác |
| Logout URL + method | GET /logout hoặc DELETE /session |
| Session expiry indicator | Redirect về /login |

### Ví dụ thực tế: Redmine 5.1

**Bước 1: GET /login**
```bash
curl -v -c /tmp/redmine-cookies.txt http://localhost:3000/login 2>&1
```
Kết quả:
- Set-Cookie: `_redmine_session=...; path=/; HttpOnly`
- HTML có: `<input name="authenticity_token" value="LONG_TOKEN" />`

**Bước 2: POST /login**
```bash
curl -v -b /tmp/redmine-cookies.txt -c /tmp/redmine-after.txt 
  -X POST http://localhost:3000/login 
  --data-urlencode "username=alice_rm" 
  --data-urlencode "password=AliceRm2026!" 
  --data-urlencode "authenticity_token=TOKEN" 
  --max-redirs 0 2>&1 | grep -E "(HTTP/|Location|Set-Cookie)"
```
Kết quả khi thành công:
```
HTTP/1.1 302 Found
Location: /my/page
Set-Cookie: _redmine_session=NEW_SESSION_VALUE; path=/; HttpOnly
```

**Kết luận Redmine:**
- Login endpoint: `POST /login`
- Fields: `username`, `password`, `authenticity_token`
- CSRF: Cần GET trước để lấy token + init cookie
- Session cookie: `_redmine_session`
- Success: HTTP 302 + new `_redmine_session` cookie
- Failure: HTTP 200 (trả về form with lỗi)

**Vì Redmine có CSRF**, CredentialInjector phải thực hiện 2 bước:
1. GET /login → extract `authenticity_token` từ HTML + lưu Set-Cookie
2. POST /login với token + credentials + cookie từ bước 1

Xem mẫu code đầy đủ tại: `stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy`
