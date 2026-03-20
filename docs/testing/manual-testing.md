# Hướng dẫn Kiểm thử Manual (E2E) SSO Lab — Toàn diện 3 Stack

Tài liệu này cung cấp kịch bản kiểm thử Single Sign-On (SSO) và Single Logout (SLO) trên toàn bộ hệ thống SSO Lab (Stack A, B, C).

---

## 🧭 1. Tổng quan hệ thống

Hệ thống bao gồm 3 Stack OpenIG độc lập chia sẻ chung một Identity Provider (Keycloak).

| Stack | Domain chính | Ứng dụng | Cơ chế Auth | Cookie Session |
|:---|:---|:---|:---|:---|
| **Stack A** | `sso.local` | WordPress, WhoAmI | Form Login Injection | `IG_SSO` |
| **Stack B** | `sso.local:9080` | Redmine, Jellyfin | Form Login / Token Injection | `IG_SSO_B` |
| **Stack C** | `sso.local:18080` | Grafana, phpMyAdmin | Header / HTTP Basic Auth | `IG_SSO_C` |

### Tài khoản kiểm thử (Keycloak)
- **Alice:** `alice` / `alice123`
- **Bob:** `bob` / `bob123`

---

## 🔑 2. Chuẩn bị môi trường (Bắt buộc)

Trước khi test, đảm bảo các Stack đã được Unseal và Bootstrap dữ liệu trong Vault:

```bash
# Stack A
docker exec sso-vault sh /tmp/vault-bootstrap.sh
# Stack B
docker exec sso-b-vault sh /tmp/vault-bootstrap.sh
# Stack C
docker exec stack-c-vault-c-1 sh /tmp/vault-bootstrap.sh
```

*Lưu ý: Jellyfin yêu cầu set password thủ công `AliceJelly2026` / `BobJelly2026` trong lần chạy đầu tiên.*

*Lưu ý Stack C (2026-03-18): APP5 đã được fix bằng secret strong random alphanumeric-only. Nếu Grafana lại báo `invalid_client_credentials`, verify `OIDC_CLIENT_SECRET_APP5` giống hệt nhau giữa `stack-c/.env`, Keycloak client `openig-client-c-app5`, và env runtime của các container OpenIG; không dùng secret chứa `+`, `/`, `=` vì OpenIG không URL-encode `client_secret`. Sau khi đổi secret phải recreate cả 2 container OpenIG Stack C.*

---

## 🧪 3. Stack A SSO Test — WordPress + WhoAmI

1. **Truy cập WordPress:** Vào `http://wp-a.sso.local/wp-admin/`.
2. **Login:** Nhập `alice` / `alice123` tại màn hình Keycloak.
3. **Verify WP:** Bạn phải vào thẳng Dashboard WordPress với tư cách user `alice_wp`.
4. **Truy cập WhoAmI:** Mở tab mới vào `http://whoami-a.sso.local/`.
5. **Verify WhoAmI:** Trang hiển thị thông tin user `alice` mà không yêu cầu login lại.

---

## 🧪 4. Stack B SSO Test — Redmine + Jellyfin

1. **Truy cập Redmine:** Vào `http://redmine-b.sso.local:9080/`.
2. **Login:** Nếu chưa login ở Stack A, nhập `alice` / `alice123`. Nếu đã login Stack A, Keycloak sẽ tự động redirect về Redmine.
3. **Verify Redmine:** Bạn phải thấy mình đã đăng nhập với account `alice@lab.local`.
4. **Truy cập Jellyfin:** Vào `http://jellyfin-b.sso.local:9080/`.
5. **Verify Jellyfin:** OpenIG tự động inject token, bạn vào thẳng màn hình Home của Jellyfin.

---

## 🧪 5. Stack C SSO Test — Grafana + phpMyAdmin

1. **Truy cập Grafana:** Vào `http://grafana-c.sso.local:18080/`.
2. **Verify Grafana:** Trang hiển thị Dashboard Grafana. Kiểm tra icon Profile (góc dưới trái) phải thấy user `alice`. (Cơ chế: Header Injection `X-WEBAUTH-USER`).
3. **Truy cập phpMyAdmin:** Vào `http://phpmyadmin-c.sso.local:18080/`.
4. **Verify phpMyAdmin:** Bạn vào thẳng danh sách Database. Kiểm tra tag "User:" ở trên cùng phải là `alice@sso-mariadb`. (Cơ chế: HTTP Basic Auth Injection).

---

## 🔄 6. Single Logout (SLO) Test

### Kịch bản 1: SLO nội bộ Stack A
1. Đang ở WordPress (`wp-a.sso.local`), nhấn **Log Out**.
2. Refresh tab WhoAmI (`whoami-a.sso.local`).
3. **Kỳ vọng:** Cả 2 app đều bị đẩy về màn hình Login của Keycloak.

### Kịch bản 2: SLO nội bộ Stack B (Redis Blacklist)
1. Đang ở Redmine (`redmine-b.sso.local:9080`), nhấn **Sign out**.
2. Refresh tab Jellyfin (`jellyfin-b.sso.local:9080`).
3. **Kỳ vọng:** Jellyfin bị logout. Kiểm tra Redis: `docker exec sso-redis-b redis-cli keys "blacklist:*"` phải thấy sid của Alice.

### Kịch bản 3: SLO nội bộ Stack C
1. Truy cập `http://phpmyadmin-c.sso.local:18080/openid/app6/logout`.
2. Refresh tab Grafana.
3. **Kỳ vọng:** Grafana bị logout.

---

## 🌍 7. Cross-Stack SLO Test (Đăng xuất toàn cục)

Đây là bài test quan trọng nhất kiểm chứng sự phối hợp giữa 3 Gateway qua Back-channel.

1. **Đăng nhập cả 3 Stack:** Mở 3 tab: WordPress (A), Redmine (B), Grafana (C). Đảm bảo cả 3 đều đang ở trạng thái Login.
2. **Thực hiện Logout tại 1 Stack:** Ví dụ logout từ WordPress (Stack A).
3. **Kiểm tra Stack B & C:** Refresh tab Redmine và Grafana.
4. **Kỳ vọng:** Cả Redmine và Grafana đều bị đăng xuất ngay lập tức.
5. **Giải thích:** Keycloak gửi Back-channel Logout tới endpoint `/backchannel_logout` của từng Stack. OpenIG nhận được sẽ ghi Session ID vào Redis blacklist riêng của Stack đó.

---

## 🛠️ 8. Ghi chú Kỹ thuật cho Tester

- **Sticky Sessions:** Hệ thống dùng `ip_hash` trên Nginx. Nếu bạn stop 1 node OpenIG, bạn có thể bị redirect về Keycloak do session local (`JSESSIONID`) không được đồng bộ, nhưng sẽ không phải nhập lại password (do Keycloak SSO session vẫn còn).
- **Cookie Domains:** 
  - Stack A & C dùng chung `cookieDomain: ".sso.local"`.
  - Stack B hiện tại dùng cookie riêng biệt cho từng subdomain (optimization gap).
- **Redis Blacklist:** Nếu SLO không hoạt động, hãy kiểm tra log của `BackchannelLogoutHandler.groovy` để xem logout token có hợp lệ không và Redis có nhận được key không.
- **Header Stripping:** Thử giả mạo header `X-WEBAUTH-USER: admin` khi truy cập Grafana. OpenIG phải loại bỏ header này trước khi xử lý, đảm bảo không bị bypass.

Chúc bạn kiểm thử thành công!
