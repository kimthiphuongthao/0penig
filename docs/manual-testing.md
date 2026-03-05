# Hướng dẫn Kiểm thử Manual (E2E) SSO Lab

Tài liệu này hướng dẫn chi tiết từng bước kiểm thử luồng Single Sign-On (SSO) trên trình duyệt (Browser), đảm bảo kiến trúc `Nginx → OpenIG → WordPress ← Keycloak, OpenIG → Vault` hoạt động đúng trên môi trường đa Stack (Multi-stack).

---

## 🧭 Tổng quan bài toán
- **Mục tiêu:** Truy cập các ứng dụng di sản (Legacy) bằng tài khoản định danh chung quản lý bởi Keycloak, thông qua Gateway OpenIG.
- **Vai trò:**
  - **Trình duyệt (Nginx):** Trỏ đến `http://openiga.sso.local` (Stack A) hoặc `http://openigb.sso.local:9080` (Stack B).
  - **Bảo vệ (OpenIG):** Phân tích token, nếu chưa login thì chặn và đẩy sang Keycloak.
  - **Identity (Keycloak):** Cung cấp màn hình đăng nhập, xác thực người dùng tại `http://auth.sso.local:8080`.
  - **Kho lưu trữ (Vault):** Lưu trữ thông tin đăng nhập (Username/Password) của ứng dụng một cách bảo mật.
  - **Kế thừa (WordPress/Dotnet):** Chỉ nhận user hợp lệ và cookies/headers đã được OpenIG "bơm" (inject) tự động.

### Thông tin kỹ thuật hiện tại
- **Stack A (openiga.sso.local):** WordPress (App1) + whoami (App2). Dùng Redis-A.
- **Stack B (openigb.sso.local:9080):** Dotnet-app (App3). Dùng Redis-B.

---

## 🔑 Chuẩn bị Vault (Bắt buộc)
Trước khi kiểm thử, bạn cần khởi tạo dữ liệu trong Vault:
1. Copy script vào container: `docker cp vault/init/vault-bootstrap.sh sso-vault:/tmp/`
2. Chạy bootstrap: `docker exec sso-vault bash /tmp/vault-bootstrap.sh`

---

## 🧪 Kịch bản: Đăng nhập End-to-End (Alice)

### Bước 1: Chuẩn bị môi trường sạch
1. Mở trình duyệt Web (Chrome, Firefox, Safari).
2. Lời khuyên: Hãy mở **Cửa sổ Ẩn danh (Incognito / Private Window)** để đảm bảo không dính cookie cũ. Mở Network Tab (F12) để xem quá trình Redirect nếu muốn.

### Bước 2: Khởi tạo yêu cầu truy cập Resource (Stack A)
1. Gõ vào thanh địa chỉ: `http://openiga.sso.local/app1/wp-admin/`
2. **Kỳ vọng:** Trình duyệt tự động chuyển hướng sang màn hình của Keycloak.

### Bước 3: Đăng nhập tại Keycloak
1. Tại form đăng nhập, nhập thông tin tài khoản Test số 1:
   - **Username:** `alice`
   - **Password:** `alice123`
2. Bấm **Sign In**.

### Bước 4: Chuyển hướng về Gateway (Callback)
1. **Kỳ vọng ngầm:** OpenIG nhận code, lấy credentials từ Vault, login ngầm vào WordPress và trả về session.

### Bước 5: Xác minh kết quả cuối cùng
1. Màn hình cuối cùng sẽ dừng lại ở giao diện Admin của WordPress.
2. URL trở về lại là: `http://openiga.sso.local/app1/wp-admin/`
3. **Tiêu chuẩn đạt (Pass Criteria):** Bạn nhìn thấy màn hình Dashboard của WordPress hiển thị tên là `alice`.

---

## 🧪 Kịch bản: Kiểm tra SSO sang Stack B (App3)

1. Trong cùng trình duyệt đã đăng nhập Alice ở trên, mở tab mới.
2. Truy cập: `http://openigb.sso.local:9080/app3/`
3. **Kỳ vọng:** Bạn được truy cập vào App3 **ngay lập tức** mà không cần qua màn hình login Keycloak.
   → Chứng minh SSO cross-stack hoạt động.

---

## 🧪 Kịch bản bổ sung: Sticky Session và Failover

Sau khi Pass **Bước 5** (bạn đang ở Dashboard WordPress):

1. **Test Stickyness:** Click vòng quanh các menu. Bạn luôn kết nối với cùng 1 node OpenIG nhờ thiết lập `hash $cookie_JSESSIONID` trên Nginx.
2. **Test Failover:** Stop node hiện tại (`docker compose stop openig-1`). Refresh trang WordPress.
   - **Kỳ vọng (EXPECTED):** Trình duyệt redirect về Keycloak (HTTP 302). Do phiên làm việc local (`JSESSIONID`) không được đồng bộ, node mới yêu cầu re-auth.

---

## 🧪 Kịch bản: Test User thứ hai (Bob)

1. Đóng hẳn cửa sổ ẩn danh hiện tại.
2. Mở cửa sổ mới, đăng nhập `bob / bob123` vào WordPress.
3. **Kỳ vọng:** Truy cập WordPress thành công với tư cách là `bob` (vai trò Author).

---

## 🧪 Kịch bản: Single Logout (SLO) — Đăng xuất đồng thời trong 1 Stack

### Bước 1: Truy cập App2 (whoami)
1. Đăng nhập `alice` vào `http://openiga.sso.local/app2/`.
2. Mở tab mới vào WordPress `http://openiga.sso.local/app1/wp-admin/` (SSO sẽ tự login).

### Bước 2: Đăng xuất khỏi WordPress
1. Click **Log Out** trong WordPress.
2. **Kỳ vọng:** Keycloak nhận yêu cầu logout và hủy phiên SSO.

### Bước 3: Kiểm tra App2
1. Quay lại tab App2, nhấn **F5**.
2. **Tiêu chuẩn đạt:** Trình duyệt redirect về trang đăng nhập Keycloak.

---

## 🧪 Kịch bản: Cross-stack SLO (Redis Blacklist)

Kịch bản này kiểm chứng tính năng Back-channel Logout đẩy sang Redis.

### Bước 1: Đăng nhập đa Stack
1. Đăng nhập App1 (Stack A) và App3 (Stack B).

### Bước 2: Logout từ App3
1. Truy cập `http://openigb.sso.local:9080/app3/logout`.
2. Keycloak thực hiện back-channel logout tới Stack A.

### Bước 3: Kiểm tra App1
1. Refresh tab WordPress (`http://openiga.sso.local/app1/wp-admin/`).
2. **Tiêu chuẩn đạt:** WordPress bị đăng xuất, redirect về Keycloak.
   → **Giải thích:** `BackchannelLogoutHandler` nhận yêu cầu, ghi `sid` vào Redis. `SessionBlacklistFilter` phát hiện và hủy session local.

---

## 🛠️ Ghi chú Kỹ thuật (Kiến trúc Session & SLO)

1.  **JwtSession (Cookie `IG_SSO`):** Lưu trạng thái pre-auth (nonce/state).
2.  **Server-side Session (`JSESSIONID`):** Lưu OAuth2 Tokens (~4.8KB, vượt giới hạn cookie 4KB).
3.  **Redis Blacklist (Enterprise SLO):**
    - `BackchannelLogoutHandler`: Nhận logout_token, ghi `blacklist:{sid}` vào Redis (TTL 3600s).
    - `SessionBlacklistFilter`: Chặn request, check Redis, nếu có key thì clear session.
    - **Lưu ý:** Filter redirect phải dùng Header `Host` để đảm bảo đúng domain/port công khai.
4.  **Separator ##:** Trong Keycloak, `post_logout_redirect_uris` phải dùng `##` để phân tách nhiều URL.
5.  **Cấu hình Public URL:** WordPress phải được set `siteurl` = `http://openiga.sso.local/app1`. Không dùng rewrite URL tại gateway.

Chúc bạn test thành công!
