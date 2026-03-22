# Checklist tích hợp SSO cho App Team

**Dành cho:** Team phát triển và vận hành ứng dụng legacy cần tích hợp vào hệ thống SSO.
**Không dành cho:** Gateway team — xem `standalone-legacy-app-integration-guide.md`.

> Update 2026-03-17: Pattern Consolidation Steps 1-6 are complete. STEP-01 deleted `PhpMyAdminCookieFilter.groovy`, STEP-02 rotated Stack C OIDC secrets, and STEP-03 moved compose secrets into gitignored `.env` files while pinning OpenIG to `6.0.1`. Validation follow-up 2026-03-19: the Phase 1+2 `JwtSession` production pattern is fully validated on all three stacks, including Redis token-reference offload and `BackchannelLogoutHandler` support for `RS256` plus `ES256`. Follow-up 2026-03-20: multi-app OpenIG instances now bind per-app token reference keys (`token_ref_id_appN`) to avoid shared-cookie cross-app contamination, and Redis port / blacklist TTL are externalized through env-backed route args rather than hardcoded literals.

> [!warning]
> Gateway-team handoff notes:
> - Pin `openidentityplatform/openig:6.0.1`; do not use `openidentityplatform/openig:latest` because `latest=6.0.2` is currently broken in this lab while `6.0.1` is the verified good OpenIG 6 tag.
> - If an OIDC client secret is consumed by OpenIG `OAuth2ClientFilter`, use a strong random alphanumeric-only value. Avoid `+`, `/`, and `=` because OpenIG does not URL-encode `client_secret` in the token request body.

---

## 1. Cách SSO/SLO hoạt động (30 giây)

```
Browser ──► Gateway ──► App của bạn
              │
              │ (xử lý đăng nhập, quản lý token,
              │  blacklist session khi logout)
              │
           Keycloak (IdP)
```

**Gateway xử lý đăng nhập thay bạn.** Khi người dùng truy cập app, gateway kiểm tra xem họ đã đăng nhập vào hệ thống SSO chưa. Nếu chưa, gateway redirect sang trang đăng nhập tập trung. Sau khi đăng nhập xong, gateway tự động inject credentials vào app — người dùng không cần nhập lại mật khẩu của app.

**App của bạn giữ nguyên cơ chế login hiện tại.** Bạn không cần thêm OIDC, SAML, hay bất kỳ chuẩn nào khác vào code app.

**SLO (Single Logout):** Khi người dùng đăng xuất ở bất kỳ app nào, gateway thông báo cho hệ thống SSO. Các app khác sẽ yêu cầu đăng nhập lại khi người dùng truy cập lần tiếp theo (refresh/navigate).

**Action item cho bạn:** Đọc tiếp Section 3 để biết thông tin nào gateway team cần từ bạn.

---

## 2. Gateway team xử lý gì (không phải việc của bạn)

Bạn **không cần** quan tâm đến những phần sau — gateway team xử lý toàn bộ:

- Thiết lập Keycloak: tạo realm, client OIDC, cấu hình redirect URI, cơ chế nhận lệnh logout tự động từ hệ thống SSO
- OIDC flow: toàn bộ quy trình xác thực giữa gateway và Keycloak
- Session management: duy trì phiên đăng nhập ổn định cho người dùng
- Vault: lưu trữ và rotate credentials của từng user
- Hệ thống đảm bảo logout có hiệu lực: khi người dùng logout ở 1 app, các app khác sẽ yêu cầu đăng nhập lại ở lần truy cập tiếp theo
- Toàn bộ cấu hình và logic xử lý nội bộ của gateway

> **Gateway Integration Model (2026-03-17):** Gateway team configures parameterized Groovy templates via route JSON args — no Groovy code changes needed for standard integrations. App team provides: (1) app URL + login mechanism type, (2) Keycloak client ID (or request gateway team to create), (3) post-logout redirect URL.

> **Gateway shared-instance rule (2026-03-20):** If one OpenIG instance fronts multiple apps, gateway team MUST allocate both a unique `clientEndpoint` and a unique `tokenRefKey` per app (for example `token_ref_id_app3`, `token_ref_id_app4`). Redis host/port and blacklist TTL must stay env/route-arg driven; do not hardcode `6379` or `28800`.

> **Gateway shared-cookie guardrail (2026-03-22):** Khi nhiều app trong cùng tenant share một OpenIG `JwtSession` cookie, `TokenReferenceFilter` phải luôn app-scoped: unique `clientEndpoint` + unique `tokenRefKey` per app, skip Redis restore trên `<clientEndpoint>/callback`, skip Redis offload khi OAuth2 state chưa có token thật, và chỉ strip đúng `oauth2:*` namespace của app hiện tại. Nếu không, App A có thể làm App B mất pending nonce/state và callback fail với `no authorization in progress`.

**Action item cho bạn:** Không có. Phần này chỉ để bạn biết gateway team đang làm gì.

---

## 3. Chúng tôi cần gì từ bạn

Điền checklist này và gửi cho gateway team trước buổi kickoff. Càng điền chi tiết, thời gian tích hợp càng ngắn.

---

### (a) Thông tin app cơ bản

> **Tại sao cần:** Gateway team cần biết platform và version để chọn đúng kỹ thuật inject credentials.

- [ ] Tên ứng dụng: _______________
- [ ] Platform / Framework (PHP, Java, .NET, Rails, Node.js, Go, ...): _______________
- [ ] Version: _______________
- [ ] Môi trường đang khảo sát (DEV / UAT / PROD): _______________
- [ ] URL hiện tại của app: _______________
- [ ] POC kỹ thuật (tên + email): _______________
- [ ] POC vận hành (tên + email): _______________
- [ ] Tài liệu kỹ thuật hiện có (link / runbook nếu có): _______________

---

### (b) Login flow

> **Tại sao cần:** Gateway cần biết chính xác cách app xử lý đăng nhập để inject credentials đúng cách. Sai một field name hoặc thiếu một bước là toàn bộ flow sẽ thất bại.

**Loại xác thực hiện tại** (chọn một):

- [ ] Form HTML — app có trang login với `<form>` POST username/password
- [ ] HTTP Basic Auth — app hiển thị popup browser hoặc yêu cầu `Authorization: Basic` header
- [ ] Header-based — app đọc username từ HTTP header do proxy inject (ví dụ: Grafana Auth Proxy mode)
- [ ] Token API — app có REST API login, trả về token (ví dụ: SPA, Jellyfin)
- [ ] LDAP — app xác thực qua LDAP/Active Directory trực tiếp
- [ ] Khác: _______________

**Câu hỏi bổ sung về login flow:**

- [ ] Login có nhiều bước không? (multi-step): Có / Không
- [ ] Login form có field nào ngoài username/password không? (domain, OTP, tenant...): _______________

**Nếu là Form HTML, điền bảng sau:**

Bạn có thể tìm thông tin này bằng cách: mở DevTools (F12) > tab Network > tick "Preserve log" > đăng nhập thủ công > tìm request POST đến login endpoint.

| Thông tin | Giá trị |
|-----------|---------|
| Login endpoint (URL) | |
| HTTP method | POST |
| Field name: username | |
| Field name: password | |
| Có CSRF token không? (Có / Không) | |
| Nếu có CSRF: tên field | |
| Nếu có CSRF: cần GET trang nào trước để lấy token? | |
| HTTP status khi đăng nhập thành công | 302 / 200 |
| HTTP status khi sai mật khẩu | |
| URL redirect sau khi đăng nhập thành công | |

**Nếu là Token API (SPA):**

- [ ] API login endpoint (URL): _______________
- [ ] HTTP method: POST / khác
- [ ] Request body format: JSON / form-urlencoded
- [ ] Response format: JSON token / khác
- [ ] Token được lưu ở đâu phía client? (localStorage / cookie / sessionStorage): _______________

**Nếu là HTTP Basic Auth:**

- [ ] App hiện đang dùng `auth_type=http` hay popup browser mặc định?
- [ ] Admin có quyền mount file config vào container không?

**Nếu là Header-based (ví dụ: Grafana):**

- [ ] Tên header app đọc username từ: _______________
- [ ] App có sẵn chế độ "Auth Proxy" / "Trusted Header" chưa? (Có / Chưa bật / Không có)

**Nếu là LDAP:**

- [ ] Ghi chú: LDAP cần đánh giá riêng — liên hệ gateway team để thảo luận phương án.

---

### (c) Session và cookie

> **Tại sao cần:** Gateway cần biết session của app hoạt động như thế nào để cache và inject đúng cookies, tránh người dùng bị login loop.

Bạn có thể tìm thông tin này bằng cách: mở DevTools (F12) > tab Application > Cookies, sau khi đăng nhập thủ công.

> **Lưu ý:** Cookie gateway `IG_SSO`, `IG_SSO_B`, `IG_SSO_C` (và cookie tenant-equivalent sau này) là session nội bộ của OpenIG. Gateway team strip các cookie này khỏi upstream request trước khi forward vào app, nên ở bảng dưới bạn chỉ cần liệt kê cookie do chính app của bạn phát hành.

| Thông tin | Giá trị |
|-----------|---------|
| Loại session | `cookie` / `token localStorage` / `stateless` |
| Tên cookie session chính (sau khi đăng nhập) | |
| Cookie Domain (nếu có) | |
| Session timeout mặc định | |
| App có endpoint kiểm tra session còn hợp lệ không? | |

---

### (d) Logout

> **Tại sao cần:** SLO yêu cầu gateway biết cách đăng xuất khỏi app. Nếu app không có logout endpoint, gateway team sẽ xử lý phía gateway.

| Thông tin | Giá trị |
|-----------|---------|
| App có nút logout không? (Có / Không) | |
| Logout endpoint (URL) | |
| HTTP method khi logout | GET / POST |
| Logout có cần CSRF token không? | |
| Sau logout, app redirect về đâu? | |

---

### (e) Deployment

> **Tại sao cần:** Quyết định subdomain vs subpath ảnh hưởng trực tiếp đến việc bạn có cần config thêm trong app không (xem Section 5).

- [ ] App hiện đang chạy ở root domain (`/`)
- [ ] App hiện đang chạy dưới subpath (`/appX`)
- [ ] App có thể được deploy ở subdomain riêng (ví dụ: `myapp.sso.local`) không?
- [ ] Container image có thể customize được (Dockerfile, entrypoint, config mount)?
- [ ] Tạo file `.env` local từ `.env.example` trước lần deploy đầu tiên.
- [ ] Không commit file `.env` vào Git.
- [ ] Pin container image bằng version cụ thể; với OpenIG 6, không dùng `:latest`.
- [ ] Nếu app dùng OIDC client secret qua OpenIG, secret phải là strong random alphanumeric-only (không chứa `+`, `/`, `=`).
- [ ] Nếu deploy trên Linux Docker (không phải Docker Desktop), thêm `extra_hosts: host.docker.internal:host-gateway` vào tất cả service OpenIG trong `docker-compose.yml`; Docker Desktop tự resolve `host.docker.internal`, còn Linux Docker thì không.

> **Ghi chú multi-tenancy (chuẩn bị K8s):** Mỗi tenant phải có namespace riêng ở 3 lớp: tên cookie gateway riêng, Redis key prefix riêng (thường encode qua naming `tokenRefKey`/blacklist keys), và Vault path namespace riêng. Các app trong cùng tenant vẫn share một cookie gateway theo design SSO; isolation giữa app trong cùng tenant nằm ở `clientEndpoint` + `tokenRefKey`, không phải tách cookie per app.

Ghi chú về deployment hiện tại (port, reverse proxy đang dùng, v.v.):

_______________

---

## 4. Bạn cần config gì phía app

Điều này phụ thuộc vào loại xác thực của app bạn.

### Form-based (ví dụ: WordPress, Redmine)

**Thường không cần sửa code app.** Gateway tự POST credentials vào login form của bạn, thu cookie session, và inject vào các request tiếp theo — giống như người dùng đăng nhập thủ công.

Ngoại lệ duy nhất: nếu app chạy dưới **subpath** (ví dụ `/app1`), bạn cần cho app biết base path của nó:

| App | Config cần thiết |
|-----|-----------------|
| WordPress | Cập nhật `siteurl` và `home` trong database hoặc wp-config |
| Rails (Redmine) | Thêm `Rack::URLMap` trong `config.ru` hoặc set `RAILS_RELATIVE_URL_ROOT` |
| ASP.NET Core | Thêm `app.UsePathBase("/appX")` trong `Program.cs` |
| Các app khác | Hỏi gateway team — subpath config tùy từng framework |

Nếu app chạy ở **subdomain riêng** (khuyến nghị), không cần config gì thêm.

---

### Token API + SPA (ví dụ: Jellyfin)

**Không cần config app.** Gateway xử lý toàn bộ phía proxy.

---

### Header-based (ví dụ: Grafana)

**Cần bật Auth Proxy mode trong app.** Đây là config sẵn có của app — bạn chỉ cần bật, không cần sửa code.

Ví dụ với Grafana (env vars):

```
GF_AUTH_PROXY_ENABLED=true
GF_AUTH_PROXY_HEADER_NAME=X-WEBAUTH-USER
GF_AUTH_PROXY_HEADER_PROPERTY=username
GF_AUTH_PROXY_AUTO_SIGN_UP=true
GF_AUTH_DISABLE_LOGIN_FORM=true
```

Không bật → app bỏ qua header gateway inject, hiện login form native như bình thường.

---

### HTTP Basic Auth (ví dụ: phpMyAdmin)

**Cần bật HTTP auth mode.** Thường là mount một file config nhỏ vào container.

Ví dụ với phpMyAdmin (mount file PHP):

```php
$cfg['Servers'][$i]['auth_type'] = 'http';
```

Lưu ý: với phpMyAdmin Docker image, env var `PMA_AUTH_TYPE=http` không hoạt động — phải mount file config trực tiếp.

---

### LDAP

Cần đánh giá riêng — liên hệ gateway team.

---

**Action item cho bạn:** Xác định loại xác thực của app (Section 3b), đối chiếu với bảng trên, thực hiện config nếu cần trước buổi test chung.

---

## 5. Subdomain vs Subpath

Đây là quyết định quan trọng ảnh hưởng đến khối lượng config phía bạn.

```
App của bạn có thể chạy ở subdomain riêng không?
(ví dụ: myapp.sso.local thay vì gateway.sso.local/myapp)

    Có ──► Dùng subdomain (khuyến nghị)
           - App chạy ở root /
           - KHÔNG cần config base path
           - Image Docker standard, không cần custom

    Không ──► Dùng subpath
              - App phải biết base path của nó
              - Có thể cần sửa config app (xem Section 4)
              - Một số app/framework không hỗ trợ tốt
```

**Subdomain** là cách đơn giản nhất cho cả hai phía. Gateway team cần thêm DNS entry và Keycloak redirect URI cho subdomain mới — đó là công việc của họ, không phải của bạn.

**Subpath** phức tạp hơn vì app phải tự biết prefix của mình. Nếu app generate link nội bộ mà không biết prefix (ví dụ `href="/login"` thay vì `href="/myapp/login"`), các link đó sẽ bị broken.

**Action item cho bạn:** Điền câu trả lời vào Section 3e (deployment) và thảo luận với gateway team trong buổi kickoff.

---

## 6. Quy trình tích hợp

| Bước | Mô tả | Ai làm | Thời gian ước tính |
|------|-------|--------|-------------------|
| 1 | Điền checklist này và gửi gateway team | App team | 1–2 ngày |
| 2 | Khảo sát kỹ thuật, chọn pattern, xác nhận deployment model | Gateway team | 1 ngày |
| 3 | Config app phía bạn (nếu cần — xem Section 4) | App team | 0–2 ngày |
| 4 | Reset mật khẩu toàn bộ user trong app, cung cấp danh sách `username:password` cho gateway team nhập vào Vault | App team | 1 ngày |
| 5 | Gateway team build cấu hình và logic xử lý của gateway | Gateway team | 2–5 ngày |
| 6 | Test chung: SSO login, session, logout, cross-app SLO | Cả hai team | 1–2 ngày |
| 7 | Go-live: chuyển DNS, verify production | Cả hai team | 1 ngày |

> **Template workflow note:** Với app tích hợp mới theo pattern chuẩn, gateway team chỉ copy và cấu hình các template tham số hóa `SessionBlacklistFilter`, `BackchannelLogoutHandler`, và `SloHandler` bằng cách set `args` trong route JSON. Không cần sửa code Groovy cho các integration tiêu chuẩn.

**Lưu ý Bước 4 — Credentials:**
- **Bước này CHỈ áp dụng cho app dùng Form login, Token API, hoặc HTTP Basic Auth.** Nếu app của bạn dùng Header-based auth (Auth Proxy mode), không cần reset mật khẩu — gateway inject username trực tiếp từ hệ thống SSO.
- App team chạy script reset mật khẩu cho toàn bộ user cần tích hợp SSO.
- Cung cấp danh sách credentials cho gateway team để nhập vào Vault. Format credentials phụ thuộc vào loại app:

| Loại app | Vault lookup key | Ví dụ |
|----------|-----------------|-------|
| Form login (WordPress) | `preferred_username` từ Keycloak | alice → secret/wp-creds/alice |
| Form login (Redmine) | `email` từ Keycloak | alice@lab.local → secret/redmine-creds/alice@lab.local |
| Token API (Jellyfin) | `email` để lookup, `preferred_username` để login app | alice@lab.local → secret/jellyfin-creds/alice@lab.local |
| HTTP Basic (phpMyAdmin) | `preferred_username` từ Keycloak | alice → secret/phpmyadmin/alice |
| Header-based (Grafana) | Không cần Vault — username từ SSO inject trực tiếp | — |

**Lưu ý:** Gateway team sẽ cho bạn biết chính xác Vault path format và lookup key cho app của bạn. Bạn cần chuẩn bị danh sách credentials theo đúng format đó.
- Gateway team sẽ không bao giờ lộ danh sách này ra ngoài Vault.
- Sau khi go-live, mật khẩu trong app vẫn là mật khẩu reset đó — người dùng đăng nhập qua SSO, không cần nhớ mật khẩu app.

**Action item cho bạn:** Xác nhận lịch với gateway team sau khi điền xong checklist. Bước 3 và Bước 4 có thể thực hiện song song.

---

## 7. Testing Checklist

Sau khi gateway team deploy xong, app team tester có thể tự chạy các test sau trên browser. Không cần công cụ đặc biệt.

### TC-1: SSO Login
1. Mở trình duyệt private/incognito.
2. Truy cập URL app của bạn (ví dụ `http://myapp.sso.local`).
3. **Kết quả mong đợi:** Tự động redirect sang trang đăng nhập tập trung (Keycloak). Sau khi đăng nhập, redirect về app và đang trong trạng thái logged in.
4. **Lỗi thường gặp:** Vẫn thấy login form của app → báo gateway team.

### TC-2: Session còn hoạt động sau refresh
1. Sau khi đăng nhập thành công (TC-1), nhấn F5 hoặc reload trang.
2. **Kết quả mong đợi:** Vẫn đang đăng nhập, không bị redirect về login.

### TC-3: Logout từ app
1. Đang đăng nhập, click nút logout trong app.
2. **Kết quả mong đợi:** Bị đăng xuất và redirect về trang login (hoặc trang chủ). Nếu truy cập lại app, cần đăng nhập lại.

### TC-4: Cross-app SLO (quan trọng nhất)
> Cần có ít nhất 2 app đã tích hợp SSO để chạy test này.

1. Đăng nhập vào App A và App B (mở cả 2 tab).
2. Logout khỏi App A.
3. Chuyển sang tab App B, refresh trang.
4. **Kết quả mong đợi:** App B cũng bị đăng xuất, cần đăng nhập lại.

### TC-5: Session timeout
1. Đăng nhập thành công.
2. Chờ hết thời gian session (hỏi gateway team thời gian mặc định — thường 30 phút; có thể điều chỉnh tùy môi trường).
3. Truy cập app.
4. **Kết quả mong đợi:** Redirect về trang đăng nhập, sau khi đăng nhập lại tiếp tục truy cập bình thường.

**Action item cho bạn:** Chạy đủ 5 test case trên và báo kết quả cho gateway team. Test TC-4 cần cả hai team phối hợp.

---

## 8. FAQ

**1. Code app có bị thay đổi không?**

Gần như không. Gateway inject credentials từ bên ngoài — app nhận request như từ người dùng thật. Ngoại lệ duy nhất: nếu app cần bật một chế độ đặc biệt (Header Proxy mode, HTTP Basic mode) hoặc cần biết subpath của nó — nhưng đây là config, không phải sửa business logic.

**2. App có CSRF token thì sao?**

Gateway team xử lý. Gateway sẽ GET trang login trước để lấy CSRF token, sau đó POST cùng token đó — giống hệt người dùng thật. Bạn cần cung cấp thông tin CSRF trong Section 3b.

**3. App có nhiều cơ chế login (form và API) thì chọn cái nào?**

Chọn cơ chế nào phù hợp với loại app. Nếu app là web truyền thống có HTML form, dùng Form Inject. Nếu app là SPA không có form, dùng Token API. Gateway team sẽ quyết định sau khi xem thông tin khảo sát.

**4. App không có nút logout thì sao?**

Gateway team cấu hình để tự động xử lý logout khi người dùng click logout trong app. Bạn không cần thêm gì vào app.

**5. Gateway down thì app có bị ảnh hưởng không?**

Khi gateway down, người dùng không thể đăng nhập mới. Người dùng đang có session hợp lệ có thể bị ảnh hưởng tùy cấu hình. Đây là trade-off của mô hình centralized gateway — gateway chạy HA (High Availability) với ít nhất 2 node để giảm thiểu downtime.

**Lưu ý về SLO khi hạ tầng gặp sự cố:** Nếu thành phần lưu trữ trạng thái logout (Redis) bị gián đoạn đúng lúc có lệnh logout, phiên đăng nhập ở một số app có thể chưa bị thu hồi ngay. Phiên đó sẽ hết hạn tự nhiên theo thời gian session timeout (mặc định 30 phút). Trong môi trường production, hạ tầng Redis chạy HA (High Availability) để giảm thiểu khả năng này xảy ra.

**Lưu ý về Keycloak:** Gateway HA không loại bỏ dependency vào Keycloak. Nếu Keycloak unavailable, login mới, frontchannel logout, và backchannel logout delivery đều bị ảnh hưởng. Production cần HA/availability plan cho cả gateway **và** Keycloak, không chỉ riêng gateway.

**6. Có thể truy cập app trực tiếp (không qua gateway) để debug không?**

Được, trong môi trường dev/internal network. App vẫn chạy bình thường — gateway là proxy, không sửa app. Chỉ có điều khi truy cập trực tiếp, bạn sẽ thấy login form gốc của app thay vì SSO.

**7. Credentials trong Vault ai quản lý, rotate khi nào?**

Gateway team quản lý Vault. Khi cần rotate mật khẩu (ví dụ: sau sự cố bảo mật), app team reset mật khẩu trong app và cung cấp danh sách mới cho gateway team cập nhật Vault — quy trình tương tự Bước 4 trong Section 6.

**8. App dùng LDAP thì sao?**

LDAP cần đánh giá riêng vì cơ chế xác thực và SLO khác với 4 nhóm pattern đã verify. Liên hệ gateway team để thảo luận phương án phù hợp với kiến trúc LDAP hiện tại của bạn.

**9. Tối đa bao nhiêu app trên 1 gateway?**

Không có giới hạn cứng. Mỗi app có cấu hình riêng độc lập (ví dụ: nhiều app đang chạy song song trên cùng gateway). Khi thêm app mới, gateway team thêm cấu hình mới mà không ảnh hưởng app đang chạy. Khi nhiều app share cùng một OpenIG instance, mỗi app vẫn PHẢI có `clientEndpoint` riêng và `tokenRefKey` riêng để tránh đè OAuth/session state của nhau trong cùng browser session. Trong phase multi-tenancy, ranh giới isolation là tenant: mỗi tenant tách bằng tên cookie riêng, Redis namespace/prefix riêng, và Vault path namespace riêng; còn các app trong cùng tenant vẫn share một cookie gateway theo design SSO.

**10. Liên hệ ai khi có vấn đề?**

Trong quá trình tích hợp: gateway team POC (hỏi tên/email khi bắt đầu dự án). Trong production: team nào phụ trách phần nào thì xử lý phần đó — lỗi đăng nhập SSO/SLO là gateway team, lỗi business logic trong app là app team.

---

## 9. Thuật ngữ

| Thuật ngữ | Nghĩa |
|-----------|-------|
| **SSO** (Single Sign-On) | Đăng nhập một lần, tự động vào tất cả app trong hệ thống |
| **SLO** (Single Logout) | Đăng xuất một lần, tự động đăng xuất khỏi tất cả app |
| **OIDC** (OpenID Connect) | Giao thức xác thực tiêu chuẩn mà gateway dùng để nói chuyện với Keycloak |
| **IdP** (Identity Provider) | Hệ thống quản lý danh tính người dùng tập trung — trong lab này là Keycloak |
| **Gateway** | Reverse proxy đứng trước app, xử lý toàn bộ SSO/SLO thay app |
| **Keycloak** | Phần mềm IdP mã nguồn mở, quản lý tài khoản và phiên đăng nhập |
| **Vault** | Hệ thống lưu trữ bí mật (credentials) mã hóa, gateway đọc mật khẩu app từ đây |

---

## 10. Tài liệu tham khảo

| Tài liệu | Mô tả | Dành cho |
|----------|-------|----------|
| `docs/deliverables/standalone-legacy-app-integration-guide.md` | Hướng dẫn tích hợp đầy đủ: kiến trúc, URL convention, OIDC client setup, cấu hình xử lý nội bộ, troubleshooting | Gateway team và app team muốn hiểu sâu hơn |
| `docs/deliverables/legacy-auth-patterns-definitive.md` | 5 cơ chế xác thực legacy được verify qua 3 nguồn research; bảng so sánh và security checklist | Gateway team, security reviewer |
| `docs/deliverables/legacy-survey-checklist.md` | Survey template kỹ thuật đầy đủ (phiên bản chi tiết hơn file này) | Gateway team khi cần khảo sát chuyên sâu |
| `docs/deliverables/standard-gateway-pattern.md` | Standard Gateway Pattern v1.1: 7 required controls, anti-patterns, enterprise checklist | Gateway team, architect |
