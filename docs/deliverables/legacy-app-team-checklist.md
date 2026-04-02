# Checklist tích hợp SSO cho App Team

**Dành cho:** App owner, manager, technical SME của ứng dụng legacy.
**Mục tiêu:** Điền checklist này và handoff cho gateway team. Gateway team sẽ tích hợp app của bạn vào hệ thống SSO/SLO dùng chung mà không yêu cầu app phải tự implement OIDC hoặc SAML.

> Update 2026-03-24: Lab hiện chạy trên một shared infrastructure duy nhất trong `shared/`. Tất cả app browser-facing đều publish qua hostname trên port 80.

---

## 1. Shared-infra model

- Gateway team vận hành một nền tảng dùng chung: nginx, OpenIG, Keycloak integration, Redis, Vault
- App của bạn sẽ được publish qua hostname riêng trên port 80, ví dụ `http://myapp.sso.local`
- App team không cần biết hoặc vận hành mô hình cũ `stack-a`, `stack-b`, `stack-c`
- Gateway team xử lý toàn bộ SSO, SLO, session gateway, logout propagation và secret backend

The active lab runtime is shared/: one nginx, two OpenIG nodes (shared-openig-1/2), one Redis, and one Vault serving all 6 apps on port 80 via hostname routing.
Each app is isolated with a route-local JwtSession heap (SessionApp1..6), a host-only browser cookie (IG_SSO_APP1..APP6), a per-app Redis ACL user (openig-app1..6), and a per-app Vault AppRole (openig-app1..6).

Điều quan trọng nhất cho app team:

- Bạn cung cấp thông tin kỹ thuật chính xác về login, logout, cookie, và deployment constraints
- Bạn hỗ trợ test browser flow sau khi gateway team cấu hình xong
- Bạn không cần sửa business logic của app để dùng SSO pattern này

---

## 2. App team phải cung cấp gì

### 2.1 Thông tin cơ bản

- [ ] Tên ứng dụng
- [ ] Môi trường đang tích hợp: `DEV` / `UAT` / `PROD`
- [ ] Platform / framework / version
- [ ] Business owner
- [ ] Technical POC
- [ ] Operations POC
- [ ] Hostname mong muốn trên shared infra, ví dụ `http://myapp.sso.local`
- [ ] Tài liệu vận hành hoặc runbook hiện có

### 2.2 Loại cơ chế login hiện tại

Chọn một cơ chế chính:

- [ ] Form login
- [ ] HTTP Basic Auth
- [ ] Header-based / trusted proxy
- [ ] Token / API login
- [ ] LDAP (future pattern — requires app-specific assessment; not part of the validated 6-app baseline)
- [ ] Khác

### 2.3 Chi tiết login mà gateway team cần

| Nếu app dùng | Bắt buộc cung cấp |
|--------------|-------------------|
| Form login | URL login page, login endpoint, HTTP method, field `username`, field `password`, field bổ sung nếu có, CSRF behavior, success/failure signal |
| HTTP Basic Auth | App có dùng browser Basic popup hay mode cấu hình riêng, username format, password format, app có cần bật chế độ `auth_type=http` hoặc tương đương không |
| Header-based | Tên header app tin tưởng, app có mode `auth proxy` / `trusted header` sẵn không, app có cần thêm role/group headers không |
| Token / API login | Token endpoint, HTTP method, request body format, response fields, refresh/revoke endpoint nếu có, token đang được lưu ở đâu phía client |
| LDAP (future pattern — requires app-specific assessment; not part of the validated 6-app baseline) | Cách app bind vào LDAP, team nào đang sở hữu LDAP config, logout limitation nếu có |

### 2.4 Credential format và Vault requirement

Gateway team cần biết app có cần credentials lưu trong Vault hay không.

- [ ] App này cần Vault-stored credentials để gateway inject login
- [ ] App này không cần Vault credentials

Nếu chọn **có**, hãy cung cấp:

- [ ] Credential format app chấp nhận: `username/password`, `Authorization: Basic`, bearer token, API key, hoặc format khác
- [ ] Key dùng để map từ user SSO sang credential app: `preferred_username`, `email`, `employee_id`, hoặc field khác
- [ ] Team nào chịu trách nhiệm reset / rotate credential phía app khi cần
- [ ] Có cần credential riêng cho từng user hay dùng technical/service credential

Thông thường:

- Form / Basic / Token integrations thường cần Vault-stored credentials
- Header-based integrations thường không cần Vault credentials

### 2.5 Session và cookie

Gateway team cần biết artifact nào của app phải được giữ nguyên khi proxy.

- [ ] Session type: cookie / token / stateless / khác
- [ ] Tên cookie session chính của app
- [ ] Tên các cookie bổ sung quan trọng mà gateway phải pass through
- [ ] Cookie domain / path đặc biệt nếu có
- [ ] Session timeout mặc định
- [ ] App có endpoint kiểm tra session còn valid không

> Chỉ liệt kê cookie do chính app phát hành. Cookie gateway là nội bộ của OpenIG và do gateway team xử lý.

### 2.6 Logout

- [ ] App có nút hoặc endpoint logout không
- [ ] Logout endpoint
- [ ] HTTP method logout
- [ ] Logout có cần CSRF token không
- [ ] Sau logout app redirect về đâu
- [ ] Post-logout redirect URL mà business muốn người dùng quay về

### 2.7 Deployment constraints

- [ ] App bắt buộc chạy ở root path hay hỗ trợ subpath
- [ ] App có thể publish bằng hostname riêng trên port 80 không
- [ ] App có mode cấu hình đặc biệt cần bật cho trusted proxy / Basic Auth / header auth không
- [ ] Có dependency nào khiến gateway team phải preserve thêm headers hoặc cookies không
- [ ] Có healthcheck hoặc smoke-test URL nên dùng sau deploy không

---

## 3. Gateway team xử lý gì

Những phần dưới đây **không phải việc của app team**:

- Tạo và cấu hình OIDC client ở Keycloak
- Viết route OpenIG, Groovy logic, logout orchestration
- Quản lý Redis, Vault, AppRole, ACL, session blacklist
- Cấu hình gateway cookie, token reference, revocation, backchannel logout
- Cấu hình nginx hostname routing và HA gateway

App team **không cần**:

- Sửa code ứng dụng để tự implement OIDC hoặc SAML
- Tự cấu hình SSO trực tiếp trong app
- Tự hiểu Redis hoặc Vault internals
- Tự xử lý single logout propagation giữa các app

App team **có thể vẫn cần**:

- Bật một mode cấu hình có sẵn của app, ví dụ trusted header mode hoặc Basic Auth mode
- Cung cấp hoặc rotate credentials của app khi gateway team yêu cầu
- Hỗ trợ xác nhận login/logout behavior trên browser

---

## 4. Deployment handoff checklist

Đây là checklist ngắn gọn có thể gửi trực tiếp cho gateway team:

- [ ] App name, environment, business owner, technical POC
- [ ] Final hostname trên shared infra
- [ ] Login mechanism type: Form / Basic / Header / Token / LDAP (future pattern — requires app-specific assessment; not part of the validated 6-app baseline)
- [ ] Login endpoint hoặc trusted header hoặc token endpoint details
- [ ] Credential format app yêu cầu
- [ ] Có cần Vault-stored credentials không
- [ ] Lookup key để map user SSO sang user app
- [ ] App session cookie names cần gateway pass through
- [ ] Logout endpoint, method, CSRF behavior
- [ ] Post-logout redirect URL
- [ ] App config toggle cần bật cho proxy integration nếu có
- [ ] Test users hoặc validation window để chạy smoke test

Nếu muốn handoff bằng bảng, dùng mẫu sau:

| Field | Giá trị |
|-------|---------|
| App name | |
| Shared-infra hostname | |
| Login mechanism | |
| Login endpoint / header / token endpoint | |
| Credential format | |
| Vault credentials required | |
| User lookup key | |
| App session cookie names | |
| Logout endpoint + method | |
| Post-logout redirect URL | |
| Special config toggles | |
| Test contact | |

---

## 5. Production requirements và lab limits

Tài liệu này mô tả **pattern đã được validate trong lab**. Production cần thêm các control sau:

- TLS giữa các thành phần gateway, đặc biệt là Vault và Redis
- Browser-facing HTTPS is required in production; all session cookies must include the Secure flag
- OpenIG must be configured with requireHttps: true in production
- In the current lab, transport remains HTTP-only (lab exception); this must be remediated before production use
- Vault Transit cho Redis token / blacklist encryption
- OS-level disk encryption hoặc biện pháp tương đương cho volumes và backup
- AppRole `secret_id` rotation workflow rõ ràng

Khuyến nghị production bổ sung:

- Network segmentation giữa browser tier, app tier, admin tier
- HA plan cho Keycloak, Vault, Redis, và gateway
- Credential rotation runbook giữa app team và gateway team

---

## 6. Browser validation checklist

Sau khi gateway team deploy xong, app team nên chạy các test tối thiểu sau:

### TC-1: SSO login

1. Mở browser private/incognito
2. Truy cập app qua hostname mới, ví dụ `http://myapp.sso.local`
3. Kết quả mong đợi: browser được redirect sang login tập trung, sau đó quay lại app trong trạng thái logged in

### TC-2: Refresh vẫn còn session

1. Sau khi login thành công, refresh trang
2. Kết quả mong đợi: không bị đá về login

### TC-3: Logout từ app

1. Logout từ chính app
2. Kết quả mong đợi: session app bị clear và truy cập lại sẽ cần login lại

### TC-4: Cross-app SLO

1. Login vào App A và App B
2. Logout khỏi App A
3. Refresh App B
4. Kết quả mong đợi: App B cũng yêu cầu login lại

### TC-5: Session timeout

1. Login thành công
2. Chờ hết session timeout đã thống nhất
3. Truy cập app lại
4. Kết quả mong đợi: phải login lại bình thường

---

## 7. FAQ ngắn

**App có phải sửa code không?**

Thông thường không. Gateway xử lý đăng nhập ở phía trước app. Nếu cần thay đổi, thường chỉ là bật một mode cấu hình có sẵn của app.

**App team có phải tự làm OIDC hoặc SAML không?**

Không. Đó là trách nhiệm của gateway team.

**Ai quản lý Vault và Redis?**

Gateway team.

**App team cần lo secret rotation không?**

App team vẫn cần sở hữu password reset hoặc credential lifecycle của app. Gateway team cập nhật secret backend dựa trên thông tin đó.

**Có thể truy cập app trực tiếp để debug không?**

Có thể trong môi trường nội bộ nếu team giữ đường truy cập kỹ thuật. Nhưng luồng SSO/SLO chính thức luôn đi qua shared gateway hostname trên port 80.

---

## 8. Tài liệu tham khảo

| Tài liệu | Dành cho |
|----------|----------|
| `docs/deliverables/standard-gateway-pattern.md` | Gateway team, architect, security reviewer |
| `docs/deliverables/legacy-auth-patterns-definitive.md` | Gateway team cần chọn pattern kỹ thuật phù hợp |
