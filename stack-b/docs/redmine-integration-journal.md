# Redmine SSO Integration Journal

**App**: Redmine 5.1 (Ruby on Rails)
**Target**: `http://openigb.sso.local:9080/app4/`
**Stack**: Stack B (sso-lab-dotnet)
**Started**: 2026-03-04

Tài liệu này ghi lại từng bước tích hợp Redmine vào SSO theo thứ tự thực hiện:
- Claude hướng dẫn làm gì
- Bạn thực hiện như thế nào
- Thu thập được thông tin gì
- Kết quả dùng để làm gì

---

## Phase 1: Dựng Redmine (HOÀN THÀNH)

### Bước 1.1 — Thêm Redmine vào docker-compose.yml

**Claude hướng dẫn**: Thêm 2 services `mysql-redmine` và `redmine` vào `sso-lab-dotnet/docker-compose.yml`

**Kết quả**: ✅ Done
- mysql-redmine: MySQL 8.0, database `redmine`, user `redmine/redmine_pass`
- redmine: image `redmine:5.1`, port 3000 exposed, chạy tại root `/` (không dùng subpath vì gây crash)

**Lưu ý phát sinh**: `REDMINE_RELATIVE_URL_ROOT=/app4` + mount `configuration.yml:ro` gây crash do `chown: Read-only file system`. Giải pháp: chạy tại root, cấu hình subpath ở bước sau.

---

### Bước 1.2 — Tạo Redmine users

**Claude hướng dẫn**: Tạo users trong Redmine với email làm identifier (vì Vault-b sẽ lookup theo email từ Keycloak)

**Bạn thực hiện**: Đăng nhập Redmine admin tại `http://localhost:3000`, vào Administration → Users → New user

**Kết quả**: ✅ Users đã tạo

| Redmine Login | Email | Password | Keycloak user tương ứng |
|---------------|-------|----------|------------------------|
| alice_rm | alice@lab.local | AliceRm2026! | alice |
| bob_rm | bob@lab.local | BobRm2026! | bob |
| duykk1 | duykk1@bank.com | duy123 | duykk1 |

---

### Bước 1.3 — Thêm duykk1 vào các hệ thống

**Claude hướng dẫn**: Tạo user duykk1 đồng bộ trên Keycloak, WordPress, Vault

**Kết quả**: ✅ Done
- Keycloak: user `duykk1`, email `duykk1@bank.com`, pass `duy123`
- WordPress: user `duykk1` với role phù hợp
- Vault: credentials cho WordPress

---

### Bước 1.4 — Test đăng nhập Redmine local

**Claude hướng dẫn**: Đăng nhập thủ công tại `http://localhost:3000` để xác nhận Redmine hoạt động trước khi tích hợp SSO

**Bạn thực hiện**: Mở browser, login với alice_rm / AliceRm2026!

**Kết quả**: ✅ Đăng nhập thành công

---

## Phase 2: Thu thập thông tin kỹ thuật (HOÀN THÀNH)

**Mục đích**: Trước khi viết `RedmineCredentialInjector.groovy`, cần biết CHÍNH XÁC:
- URL endpoint nhận POST login
- Tên các input field (name attribute trong HTML)
- Có CSRF token không? Tên field? Cách lấy?
- Tên cookie session sau login thành công
- HTTP status code khi login thành công
- Redirect về đâu sau login?

---

### Bước 2.1 — Xem HTML form login (HOÀN THÀNH)

**Claude hướng dẫn**:
1. Mở `http://localhost:3000/login`
2. F12 → Elements (hoặc View Page Source)
3. Tìm thẻ `<form>`, ghi lại:
   - `action` attribute của form
   - `name` của input username
   - `name` của input password
   - Có `input type="hidden"` nào không? `name="?"` và `value="?"` (vài ký tự đầu)

**Bạn thu thập được** (qua F12 → Elements, xem HTML source của form):
```
action = /login
username field name = username
password field name = password
Hidden field 1: name="utf8", value="✓" (&#x2713;)
Hidden field 2: name="authenticity_token", value="lqOhYH9p82VG0ICBgq+u3hyoS9MHD9eAup6x1Hp3pnIYVSj2RGcRCbdI/tsTZDrm7DRe3AWhmFUnBpEqzdFcxg=="
```

**Kết luận**:
- Login endpoint: `POST /login`
- Redmine dùng Rails CSRF protection → `authenticity_token` thay đổi mỗi lần GET
- Field `utf8=✓` là Rails convention, cần gửi kèm
- CredentialInjector PHẢI thực hiện 2 bước: GET /login trước để lấy token → sau đó POST

**Dùng để làm gì**: Xác định endpoint POST và tên fields trong form body của CredentialInjector

---

### Bước 2.2 — DevTools Network: quan sát POST login (ĐANG CHỜ)

**Claude hướng dẫn**:
1. F12 → tab Network → tick "Preserve log"
2. Nhập credentials (alice_rm / AliceRm2026!) và nhấn Login
3. Tìm request POST trong danh sách, click vào
4. Ghi lại từ các tab:
   - **Payload/Form Data**: các key=value trong request body
   - **Response Status**: code HTTP (302? 200?)
   - **Response Headers → Set-Cookie**: tên và value của cookie session
   - **Response Headers → Location**: redirect về đâu?

**Bạn thu thập được** (qua DevTools Network tab, quan sát POST /login):

Form Data (Payload):
- authenticity_token = cltCiRfKTgNwoIAVBvkZYhbbDvPG34MwqWsxDlee2FqO3VWWHQJO99J7OXdxGupE12T04ci/ivbP/g+inaACuA==
- username = duykk1 @bank.com  (Redmine chấp nhận email trong field username)
- password = 1234567890
- login = Login  (giá trị submit button)

Response Headers:
- Status: 302 Found (login thành công)
- Set-Cookie: _redmine_session=MDhkNmRB... path=/; HttpOnly; SameSite=Lax
- Location: http://localhost:3000/my/page

**Kết luận**:
- Session cookie tên là: _redmine_session
- Login thành công = HTTP 302 + Set-Cookie _redmine_session mới
- Login thất bại = HTTP 200 (form trả về với lỗi)
- Redirect sau login: /my/page
- Field username chấp nhận cả email (quan trọng: Vault lookup theo email)
- Cần gửi field login=Login (submit button value) trong POST body

**Dùng để làm gì**: Xác nhận field names, biết tên cookie session (`_redmine_session`?), biết flow login thành công

---

### Bước 2.3 — curl test 2-step login (SAU KHI có thông tin từ 2.1 + 2.2)

**Claude hướng dẫn**: Dùng curl để test tự động 2-step login (GET lấy CSRF → POST với credentials)

*(Sẽ điền sau khi có kết quả từ Bước 2.1 và 2.2)*

---

### Bước 2.4 — Xác nhận tên cookie session

**Claude hướng dẫn**: Sau khi login thành công, mở DevTools → Application → Cookies, ghi lại tất cả cookies Redmine set

**Bạn thu thập được**:
```
Cookie 1: name = ___________, value (vài ký tự đầu) = ___________
Cookie 2: name = ___________, value = ___________
```

**Dùng để làm gì**: CredentialInjector sẽ cache và inject đúng cookie này vào mọi request

---

### Bước 2.5 — Tìm logout URL

**Claude hướng dẫn**: Sau khi login, inspect link Logout (chuột phải → Inspect), ghi lại:
- href = ?
- Method (GET hay POST)?

**Bạn thu thập được** (chuột phải vào nút Logout → Inspect):
HTML: <a class='logout' rel='nofollow' data-method='post' href='/logout'>Sign out</a>

Logout URL: POST /logout
(data-method='post' = Rails UJS gửi POST qua JavaScript, không phải GET thông thường)

**Kết luận**:
- Logout endpoint: POST /logout
- OpenIG route sẽ intercept: path matches '^/logout' AND method == 'POST'
- SloHandlerRedmine.groovy sẽ: clear session + redirect Keycloak logout

**Dùng để làm gì**: SloHandlerRedmine.groovy sẽ intercept URL này

---

### Tổng kết Phase 2 — Toàn bộ thông tin kỹ thuật đã thu thập

| Thông tin | Giá trị | Dùng trong file |
|-----------|---------|-----------------|
| Login endpoint | POST /login | RedmineCredentialInjector.groovy |
| Field username | username | RedmineCredentialInjector.groovy |
| Field password | password | RedmineCredentialInjector.groovy |
| Field CSRF | authenticity_token | RedmineCredentialInjector.groovy |
| Field submit | login=Login | RedmineCredentialInjector.groovy |
| CSRF flow | GET /login trước để lấy token | RedmineCredentialInjector.groovy |
| Session cookie | _redmine_session | RedmineCredentialInjector.groovy |
| Login success | HTTP 302 + Set-Cookie | RedmineCredentialInjector.groovy |
| Login failure | HTTP 200 | RedmineCredentialInjector.groovy |
| Redirect sau login | /my/page | RedmineCredentialInjector.groovy |
| Logout endpoint | POST /logout | SloHandlerRedmine.groovy + route JSON |
| Session expiry | Redirect về /login | RedmineCredentialInjector.groovy |

Phase 2 status: HOÀN THÀNH

---

## Phase 3: Cấu hình Vault-b (CHƯA LÀM)

**Kế hoạch**: Chạy `vault-bootstrap-redmine.sh` để thêm credentials vào Vault-b

```bash
docker exec sso-b-vault sh /vault/file/vault-bootstrap-redmine.sh
```

**Credentials sẽ được thêm**:
- `secret/redmine-creds/alice@lab.local` → `login=alice_rm, password=AliceRm2026!`
- `secret/redmine-creds/bob@lab.local` → `login=bob_rm, password=BobRm2026!`
- `secret/redmine-creds/duykk1@bank.com` → `login=duykk1, password=duy123`

---

## Phase 4: Cấu hình Keycloak (CHƯA LÀM)

**Kế hoạch**: Cập nhật client `openig-client-b` thêm redirect URIs và backchannel URL cho app4

---

## Phase 5: Viết OpenIG Routes + Groovy Scripts (CHƯA LÀM)

**Files sẽ tạo** (dựa trên thông tin thu thập ở Phase 2):

| File | Mục đích |
|------|----------|
| `routes/00-backchannel-logout-app4.json` | Nhận back-channel logout từ Keycloak |
| `routes/00-redmine-logout.json` | Intercept logout request từ user |
| `routes/02-redmine.json` | Route chính: OIDC → Vault → Inject |
| `groovy/VaultCredentialFilterRedmine.groovy` | Lookup Vault theo email |
| `groovy/RedmineCredentialInjector.groovy` | 2-step login: GET CSRF → POST credentials |
| `groovy/SessionBlacklistFilterApp4.groovy` | Check Redis blacklist |
| `groovy/SloHandlerRedmine.groovy` | Thực hiện Keycloak SLO |

---

## Phase 6: Test E2E (CHƯA LÀM)

**Checklist**:
- [ ] `http://openigb.sso.local:9080/app4/` → redirect Keycloak login
- [ ] Login alice → vào Redmine dashboard với user alice_rm
- [ ] Login duykk1 → vào Redmine dashboard với user duykk1
- [ ] Logout từ Redmine → Keycloak SLO → app3 bị kick
- [ ] Logout từ app3 → Redmine bị kick
- [ ] Check Redis: `docker exec sso-redis-b redis-cli keys "blacklist:*"`

---

*Journal cập nhật liên tục trong quá trình tích hợp.*
