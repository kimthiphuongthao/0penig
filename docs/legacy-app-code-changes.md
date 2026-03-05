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

---

### Tổng kết

| App | Sửa app code | Sửa subpath config | Sửa Dockerfile | Sửa data |
|-----|-------------|-------------------|----------------|----------|
| WordPress | Không | DB update (siteurl/home) | a2enmod alias + entrypoint fix | Tạo user WP |
| whoami | Không | Không cần | Không | Không |
| .NET | 1 dòng UsePathBase | Không | Không | Không |
| Redmine | Không | Rack::URLMap wrapper | Custom image | Tạo user Rails |

**Pattern chung:** App không hỗ trợ subpath natively → xử lý ở tầng infrastructure (Dockerfile/config), không phải business logic. Câu hỏi đầu tiên khi onboard app mới: *"App này có hỗ trợ chạy tại subpath không, và bằng cách nào?"*

---

## Câu hỏi 2: Nếu app không hỗ trợ subpath thì xử lý thế nào?

Có 3 hướng, theo thứ tự ưu tiên:

### Hướng 1: Dùng subdomain thay vì subpath (ưu tiên nhất)

Thay vì `openigb.sso.local/app4`, dùng `app4.sso.local`.

App chạy tại `/` như bình thường, không cần biết subpath. OpenIG proxy toàn bộ domain về app đó.

```nginx
server {
    server_name app4.sso.local;
    location / {
        proxy_pass http://openig_pool;
    }
}
```

Ưu điểm: không cần sửa app, không cần Dockerfile riêng.
Nhược điểm: tốn thêm DNS entry, cần thêm Keycloak client redirect URI cho subdomain.

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
App có hỗ trợ subpath không?
    ↓ Có → config 1 dòng (UsePathBase, RAILS_RELATIVE_URL_ROOT...)
    ↓ Không → dùng subdomain (sạch nhất)
               ↓ Không thể dùng subdomain → nginx path stripping
                  (chỉ OK nếu app không generate internal absolute URL)
               ↓ App generate internal URL → dùng F5 (xem Câu hỏi 3)
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
