# OpenIG OIDC Compliance Report — SSO Lab

**Ngày kiểm tra:** 2026-02-27T10:35:00+07:00  
**Skill:** `openig-oidc-checker` (14 official OpenIdentityPlatform docs)  
**Files reviewed:**
- `openig_home/config/config.json`
- `openig_home/config/admin.json`
- `openig_home/config/routes/01-wordpress.json`
- `openig_home/scripts/groovy/CredentialInjector.groovy`
- `docker-compose.yml`

---

## Summary

| | |
|---|---|
| **Overall Status** | ⚠️ WARNINGS — không có lỗi Critical, cấu hình hoạt động |
| **Critical Issues** | 0 |
| **Warnings** | 5 |
| **Info / Best Practice** | 4 |
| **Passed Checks** | 18 |

---

## ⚠️ Warnings

### W1 — `clientSecret` hardcode plaintext
**File:** `01-wordpress.json` · `OpenIGClientReg`

`"clientSecret": "openig-client-secret"` hardcode trực tiếp trong JSON.  
Chấp nhận được cho lab, **KHÔNG ổn cho production**.

```diff
- "clientSecret": "openig-client-secret",
+ "clientSecret": "${env['OPENIG_CLIENT_SECRET']}",
```

### W2 — `WordPressProxy` nên dùng `ReverseProxyHandler`
**File:** `01-wordpress.json` · `WordPressProxy`

Theo docs, terminal handler proxying đến backend app nên là `ReverseProxyHandler`, không phải `ClientHandler`.

```diff
- "type": "ClientHandler",
+ "type": "ReverseProxyHandler",
```

### W3 — `JwtSession.sharedSecret` không khớp giữa 2 file ⚠️ QUAN TRỌNG
**File:** `config.json` vs `docker-compose.yml`

| Source | Value (base64) |
|--------|----------------|
| `config.json` | `U1NPLUxhYi1Kd3RTZWNyZXQtMzJieXRlcy1LRVkhISE=` |
| `docker-compose.yml` (`JWT_SHARED_SECRET`) | `U1NPLUxhYi1Kd3RTZWNyZXQtMzJieXRlcy1LRVlcIVwh` |

Giải mã: khác nhau ở ký tự `!`. Hiện tại vẫn hoạt động vì cả 2 node dùng cùng `config.json`. Nhưng nếu dùng env var thì sẽ sai. Cần đồng bộ.

> **Note:** `JwtSession.sharedSecret` KHÔNG hỗ trợ EL expression `${env['...']}` — phải hardcode hoặc dùng KeyStore (theo SKILL.md Common Pitfall #7).

### W4 — `admin.json` mode `EVALUATION`
Phù hợp lab. Đổi sang `DEPLOYMENT` hoặc configure authentication trước production.

### W5 — Route `condition` pattern đúng
`"${not(matches(request.uri.path, '^/openig'))}"` — thiết kế đúng để tránh loop.

---

## ℹ️ Info / Best Practice

| # | Mục | Ghi chú |
|---|-----|---------|
| I1 | Thiếu `jwksUri` trong Issuer | Không validate JWT signature locally. Có thể thêm để tăng bảo mật |
| I2 | `FileAttributesFilter.fields` không khai báo | Dùng column index — dễ vỡ nếu CSV thay đổi thứ tự cột |
| I3 | Không có `CookieFilter` | WP cookies relay toàn bộ về browser — cân nhắc suppress bớt |
| I4 | Groovy timeout | ✅ `connectTimeout = 5000`, `readTimeout = 5000` đã có |

---

## ✅ Passed Checks (18 mục)

### Route Structure
- ✅ Route có `handler` field (Chain)
- ✅ `condition` dùng valid EL expression
- ✅ `baseURI` → `http://wordpress` (Docker internal DNS)
- ✅ Tất cả heap objects typed và named đúng
- ✅ Tất cả references trong filters/handlers tồn tại

### OAuth2ClientFilter
- ✅ `clientEndpoint: "/openid"` — valid
- ✅ `registrations` references `OpenIGClientReg`
- ✅ `requireHttps: false` — phù hợp dev/lab
- ✅ `failureHandler` defined (StaticResponseHandler 500)
- ✅ `target: "${attributes.openid}"` — đúng chuẩn
- ✅ `scopes` có `openid`, `profile`, `email`

### Issuer / ClientRegistration
- ✅ `authorizeEndpoint` dùng `localhost:8080` (browser-facing) — đúng split-endpoint
- ✅ `tokenEndpoint` / `userInfoEndpoint` dùng `keycloak:8080` (Docker DNS)
- ✅ `clientId`, `clientSecret`, `issuer` ref, `scopes` đầy đủ

### Groovy Script (CredentialInjector)
- ✅ Type `application/x-groovy` đúng
- ✅ `attributes.openid['user_info']['preferred_username']` — **đúng** (`user_info` có gạch dưới)
- ✅ `return next.handle(context, request)` ở cuối
- ✅ Trả về `Response` object khi error
- ✅ Null checks đầy đủ

### Docker/Deployment
- ✅ OpenIG mount `./openig_home:/opt/openig`
- ✅ `OPENIG_BASE: /opt/openig`
- ✅ Keycloak healthcheck + `depends_on: condition: service_healthy`
- ✅ WordPress không expose ra host
- ✅ Cả 2 OpenIG node dùng chung volume → config nhất quán

---

## Keycloak-Specific Notes

1. **Split-endpoint pattern ✅** — `authorizeEndpoint` dùng `localhost`, `token/userinfo` dùng Docker DNS.
2. **`iss` claim match** — Keycloak `iss` = `http://localhost:8080/realms/sso-realm`. Khớp với Issuer config.
3. **`user_info` underscore ✅** — Script đúng cú pháp OpenIG 5.x/6.x.
4. **JwtSession sharedSecret** — Không hỗ trợ EL, phải đồng bộ thủ công.

---

## Action Items (theo priority)

| Priority | Action |
|----------|--------|
| 🔴 Fix ngay | Sync `JwtSession.sharedSecret` giữa `config.json` và `docker-compose.yml` (W3) |
| 🟡 Pre-production | Move `clientSecret` sang env var (W1) |
| 🟡 Best practice | Đổi `WordPressProxy` → `ReverseProxyHandler` (W2) |
| 🟢 Nice-to-have | Thêm `fields` vào `FileAttributesFilter` (I2) |
| 🟢 Nice-to-have | Thêm `jwksUri` vào Issuer (I1) |
