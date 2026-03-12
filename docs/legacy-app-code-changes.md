# Thực tế phải sửa gì khi tích hợp legacy app vào SSO

## Câu hỏi 1: Sau khi lab 4 app, thực tế phải sửa code/config ở những phần nào?

### Nguyên tắc gốc vs thực tế

Nguyên tắc của OpenIG: app legacy không cần sửa code. Thực tế: đúng với business logic, nhưng phải sửa ở tầng infrastructure để app biết subpath của mình.

---

### App1 — WordPress

**Sửa trong app (database):**
```sql
UPDATE wp_options SET option_value = 'http://openiga.sso.local/app1'
WHERE option_name IN ('siteurl', 'home');
```

**Sửa infrastructure:**
```apache
# docker/wordpress/app1.conf — Apache Alias để WP nhận request tại /app1
Alias /app1 /var/www/html
```
```dockerfile
# Dockerfile entrypoint — phải enable mod_alias trước khi start Apache
entrypoint: ["bash", "-c", "a2enmod alias && exec docker-entrypoint.sh apache2-foreground"]
```

**Sửa data (one-time setup):**
```sql
-- Tạo user alice_wp/bob_wp — WP fresh install không có sẵn
INSERT INTO wp_users (user_login, user_pass, user_email, user_registered, user_status, display_name)
VALUES
  ('alice_wp', MD5('alice123'), 'alice@lab.local', NOW(), 0, 'Alice'),
  ('bob_wp',   MD5('bob123'),   'bob@lab.local',   NOW(), 0, 'Bob');
```

Không sửa: business logic, theme, plugin, PHP code.

---

### App2 — whoami

Không sửa gì cả. whoami respond với bất kỳ path nào. Đây là app lý tưởng — đúng với nguyên tắc zero-touch.

---

### App3 — ASP.NET Core

**Sửa trong app (1 dòng):**
```csharp
// Program.cs
app.UsePathBase("/app3");
```

Không sửa: authentication, business logic, controllers.

Lưu ý: app dùng CSRF token (`__RequestVerificationToken`) nên OpenIG phải làm 2-step login (GET lấy CSRF token → POST login kèm token).

---

### App4 — Redmine

**Redmine với subpath (`openigb.sso.local/app4`):**

**Sửa infrastructure (nhiều nhất — Docker image official không hỗ trợ subpath):**
```ruby
# config.ru — Rack::URLMap wrapper
require 'rack/urlmap'
map '/app4' do
  run Redmine::Application
end
```

**Sửa data (one-time setup):**
```ruby
# Rails console bên trong container
User.create!(login: "alice", firstname: "Alice", lastname: "Lab",
  mail: "alice@lab.local", password: "alice123", password_confirmation: "alice123",
  admin: false, status: 1)
User.create!(login: "bob", firstname: "Bob", lastname: "Lab",
  mail: "bob@lab.local", password: "bob12345", password_confirmation: "bob12345",
  admin: false, status: 1)
# Lưu ý: Redmine enforce min 8 ký tự — bob dùng bob12345
```

Không sửa: business logic, plugin, Ruby code.

**Redmine với subdomain (`app4.sso.local`):**

- Dùng image `redmine:5.1` standard (không cần build custom image).
- Không cần `RAILS_RELATIVE_URL_ROOT`, không cần `Rack::URLMap`.
- Không sửa gì trong app.

---

### Tổng kết

| App | Subpath — Sửa app | Subpath — Sửa infra | Subdomain — Sửa app | Subdomain — Sửa infra |
|-----|---|---|---|---|
| WordPress | DB update `siteurl/home` | Apache `Alias` + Dockerfile entrypoint | Không | Không (cài fresh → tự detect domain) |
| whoami | Không | Không | Không | Không |
| .NET | 1 dòng `UsePathBase` | Không | Không | Không |
| Redmine | Không | Custom Dockerfile (`Rack::URLMap`) | Không | Không (image standard `redmine:5.1`) |

**Pattern chung:** App không hỗ trợ subpath natively → xử lý ở tầng infrastructure (Dockerfile/config), không phải business logic. Câu hỏi đầu tiên khi onboard app mới: *"App này có hỗ trợ chạy tại subpath không, và bằng cách nào?"* Với subdomain deployment, toàn bộ cột Sửa subpath config và Sửa Dockerfile đều không cần thiết — app chạy tại `/` như môi trường native.

---

## Câu hỏi 2: Nếu app không hỗ trợ subpath thì xử lý thế nào?

Có 3 hướng, theo thứ tự ưu tiên (ưu tiên cách ít can thiệp app nhất):

### Hướng 1: Dùng subdomain thay vì subpath (ưu tiên nhất)

Thay vì `openigb.sso.local/app4`, dùng `app4.sso.local`.

App chạy tại `/` như bình thường (zero-touch), không cần biết prefix subpath. OpenIG proxy toàn bộ domain về app đó.

```nginx
server {
    server_name app4.sso.local;
    location / {
        proxy_pass http://openig_pool;
    }
}
```

Ưu điểm: không cần sửa app, không cần Dockerfile/config/code chỉ để xử lý subpath.
Nhược điểm: tốn thêm DNS entry, cần thêm Keycloak client redirect URI cho subdomain.

Chỉ khi không thể dùng subdomain mới đi xuống các hướng subpath bên dưới. Với subpath, app phải biết prefix (ví dụ `/app4`) nên có thể cần sửa Dockerfile/config/code.

### Hướng 2: Nginx path stripping

```nginx
location /app4/ {
    proxy_pass http://legacy-app:8080/;  # trailing slash = strip /app4
}
```

App nhận request tại `/` nhưng không biết mình đang chạy tại `/app4`.

**Vấn đề:** nếu app generate link nội bộ (`href="/login"`) sẽ bị sai — browser resolve thành `openigb.sso.local/login` thay vì `/app4/login` → link chết.

Chỉ dùng được nếu app không generate absolute URL nội bộ (app thuần API/JSON thì OK).

### Hướng 3: X-Forwarded-Prefix header

```nginx
proxy_set_header X-Forwarded-Prefix /app4;
```

Một số framework đọc header này để tự biết prefix: Spring Boot, Django, Laravel. Redmine thì không — đó là lý do phải dùng Rack::URLMap.

### Cây quyết định

```
Có dùng subdomain cho app này được không?
    ↓ Có → dùng subdomain (ưu tiên nhất, app chạy tại /, zero-touch)
    ↓ Không → buộc dùng subpath
               ↓ App có hỗ trợ prefix không?
                   ↓ Có → config theo framework (UsePathBase, RAILS_RELATIVE_URL_ROOT...)
                   ↓ Không → nginx path stripping / X-Forwarded-Prefix (chỉ hợp một số app)
                              ↓ Vẫn broken links → dùng F5 (xem Câu hỏi 3)
```

---

## Câu hỏi 3: Trong thực tế dùng F5 thay nginx thì xử lý được không?

Xem chi tiết tại: [f5-subpath-proxy.md](./f5-subpath-proxy.md)

**Tóm tắt:** F5 BIG-IP giải quyết được vấn đề broken links mà nginx không thể, thông qua Stream Profile và Rewrite Profile. Điều kiện bắt buộc: SSL Offload trên F5 và tắt Gzip phía backend.

| Tính năng | Nginx | F5 BIG-IP |
|------------|-------|-----------|
| Path stripping | Có | Có (iRules / LTP) |
| Fix broken links (href) | Không | Có (Stream/Rewrite Profile) |
| Fix JS dynamic URL | Không | Một phần (iRules / APM) |
| SSL bắt buộc | Không | Có (để dùng Stream/Rewrite) |
| Gzip backend | OK | Phải tắt hoặc decompress |

## Câu hỏi 4: Stack C — Grafana và phpMyAdmin có thực sự "zero-touch app" không?

**Câu trả lời ngắn: Không.** Stack C yêu cầu cấu hình app-side để kích hoạt chế độ xác thực đặc biệt.

### Grafana — Header-based Auth (X-WEBAUTH-USER)

**Phải bật trong app (env vars):**
- `GF_AUTH_PROXY_ENABLED=true` — bật proxy auth mode
- `GF_AUTH_PROXY_HEADER_NAME=X-WEBAUTH-USER` — tên header được tin tưởng
- `GF_AUTH_PROXY_HEADER_PROPERTY=username` — mapping header → thuộc tính user
- `GF_AUTH_PROXY_AUTO_SIGN_UP=true` — tự tạo user nếu chưa tồn tại
- `GF_AUTH_DISABLE_LOGIN_FORM=true` — tắt login form native

Không bật → Grafana bỏ qua hoàn toàn header `X-WEBAUTH-USER`, hiện login form native.

### phpMyAdmin — HTTP Basic Auth

**Phải sửa trong app (mount config PHP):**

```php
// openig_home/phpmyadmin/config.user.inc.php — mount vào container
$cfg["Servers"][$i]["auth_type"] = "http";
```

Không mount → phpMyAdmin dùng cookie auth mặc định → OpenIG inject `Authorization: Basic` nhưng phpMyAdmin không đọc.

Lưu ý: `PMA_AUTH_TYPE=http` env var không hoạt động với Docker image phpmyadmin:latest — phải mount file PHP trực tiếp.

### Tại sao Stack C khác Stack A/B?

| | Stack A/B (Form/Token Inject) | Stack C (Header/Basic Auth Inject) |
|--|--|--|
| App biết về proxy? | Không — nhận credentials như người dùng thật | Có — app phải được cấu hình để tin tưởng proxy |
| App-side config cần thiết? | Không | Có — bật proxy auth mode |
| Nếu app không hỗ trợ chế độ này? | Không ảnh hưởng | Không thể dùng pattern này |
| Tên gọi | Transparent SSO | Cooperative SSO |

### Khi nào dùng Cooperative SSO (Stack C pattern)?

Chỉ áp dụng được khi đủ cả 3 điều kiện:
1. App **đã có sẵn** tính năng proxy auth / trusted header / Basic Auth mode
2. Admin **được phép** cấu hình app (quyền sửa env vars hoặc mount config)
3. App không thể bị form-inject (không có login form HTML, SPA với CSRF phức tạp)

Nếu không đủ → quay về Transparent SSO (Stack A/B pattern) hoặc thương lượng với vendor để app expose API login.

### Tổng kết Stack C

| App | Sửa app | Nội dung |
|-----|---------|---------|
| Grafana | Có (env vars) | Bật `GF_AUTH_PROXY_ENABLED` + cấu hình header name |
| phpMyAdmin | Có (mount config PHP) | Set `auth_type=http` |
| MariaDB | Không | Chỉ tạo user/schema (one-time setup, không phải SSO config) |

**Pattern chung:** Cooperative SSO ít xâm lấn nhất khi app có sẵn proxy auth mode — chỉ config, không sửa code. Nhưng khác với Transparent SSO, app phải **nhận thức** rằng mình đứng sau proxy và được cấu hình để tin tưởng proxy đó.
