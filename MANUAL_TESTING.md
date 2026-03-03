# Hướng dẫn Kiểm thử Manual (E2E) SSO Lab

Tài liệu này hướng dẫn chi tiết từng bước kiểm thử luồng Single Sign-On (SSO) trên trình duyệt (Browser), đảm bảo kiến trúc `Nginx → OpenIG → WordPress ← Keycloak` hoạt động đúng.

---

## 🧭 Tổng quan bài toán
- **Mục tiêu:** Truy cập ứng dụng WordPress cũ bằng tài khoản định danh chung quản lý bởi Keycloak, thông qua Gateway OpenIG.
- **Vai trò:**
  - **Trình duyệt (Nginx):** Trỏ đến `http://localhost`, chia tải sang OpenIG.
  - **Bảo vệ (OpenIG):** Phân tích token, nếu chưa login thì chặn và đẩy sang Keycloak.
  - **Identity (Keycloak):** Cung cấp màn hình đăng nhập, xác thực người dùng.
  - **Kho lưu trữ (Vault):** Lưu trữ thông tin đăng nhập (Username/Password) của WordPress một cách bảo mật.
  - **Kế thừa (WordPress):** Chỉ nhận user hợp lệ và cookies đã được OpenIG "bơm" (inject) tự động.

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

### Bước 2: Khởi tạo yêu cầu truy cập Resource
1. Gõ vào thanh địa chỉ: `http://localhost/wp-admin/`
2. **Kỳ vọng:**
   - Trình duyệt sẽ gửi request tới Nginx `http://localhost`.
   - Nginx chuyển cho OpenIG. OpenIG phát hiện bạn chưa đăng nhập (chưa có session từ Keycloak).
   - Trình duyệt tự động chuyển hướng cực nhanh (HTTP 302) sang màn hình của Keycloak.

### Bước 3: Đăng nhập tại Keycloak
1. Lúc này, thanh địa chỉ của bạn sẽ hiển thị URL dạng `http://localhost:8080/realms/sso-realm/...` (Đây là trang đăng nhập của Keycloak).
2. Tại form đăng nhập, nhập thông tin tài khoản Test số 1:
   - **Username:** `alice`
   - **Password:** `alice123`
3. Bấm **Sign In**.

### Bước 4: Chuyển hướng về Gateway (Callback)
1. **Kỳ vọng ngầm (Bạn không cần làm gì):**
   - Keycloak xác thực thành công, cấp OIDC authorization code.
   - Trình duyệt chuyển hướng (redirect) về lại OpenIG tại URL callback (VD: `http://localhost/openid/callback?...`).
   - OpenIG nhận mã code, gọi server-to-server đổi lấy Token. Đọc Token thấy user là `alice`.
   - Script `VaultCredentialFilter` của OpenIG kết nối tới Vault, tự động lấy "tài khoản WP ảo" của alice (Username/Password), gọi API đăng nhập WordPress ngầm, lấy ra Cookie `wordpress_logged_in`.
   - OpenIG nhét cookie này vào HTTP Request và điều hướng bạn trở lại `http://localhost/wp-admin/`.

### Bước 5: Xác minh kết quả cuối cùng
1. Màn hình cuối cùng sẽ dừng lại ở giao diện Admin của WordPress.
2. URL trở về lại là: `http://localhost/wp-admin/`
3. **Tiêu chuẩn đạt (Pass Criteria):**
   - Bạn nhìn thấy màn hình Bảng điều khiển (Dashboard) của WordPress.
   - Góc trên bên phải (hoặc trong Profile) hiển thị tên là `alice` (vai trò Editor của WP).
   - Bạn **KHÔNG PHẢI** gõ bất kỳ mật khẩu WordPress nào cả (OpenIG đã làm hộ bạn).

---

## 🧪 Kịch bản bổ sung: Sticky Session và Failover

Sau khi Pass **Bước 5** (bạn đang ở Dashboard WordPress):

1. **Test Stickyness:** Hãy thử click vòng quanh các menu trong WordPress (VD: Posts, Pages). Mọi thứ phản hồi nhanh chóng (HTTP 200). Bạn vẫn luôn kết nối với cùng 1 node OpenIG nhờ thiết lập `hash $cookie_JSESSIONID` trên Nginx.
   - Mở thêm tab `http://localhost/app2/` để xem header `X-OpenIG-Node` hiển thị node nào (openig-1 hoặc openig-2) đang phục vụ.
2. **Test Failover:** Bạn có thể qua terminal, stop 1 node OpenIG hiện tại đang chạy (VD: `docker compose stop openig-1`). Sau đó quay lại Browser, nhấn F5 / Refresh lại trang WordPress:
   - Request sẽ được đẩy sang node OpenIG còn lại.
   - Vì OpenIG dùng chung `JWT_SHARED_SECRET` và token Keycloak có hiệu lực, bạn vẫn sẽ duy trì session và nhìn thấy Dashboard WordPress mà không bị "văng" ra bắt đăng nhập lại.

---

## 🧪 Kịch bản: Test User thứ hai (Bob)

1. Đóng hẳn cửa sổ ẩn danh hiện tại (để clear toàn bộ Cookie, mô phỏng người dùng mới).
2. Lặp lại từ **Bước 1** đến **Bước 5** nhưng ở Form Keycloak nhập:
   - **Username:** `bob`
   - **Password:** `bob123`
3. **Kỳ vọng:** Bạn sẽ truy cập vào WordPress thành công, nhưng với tư cách là `bob` (vai trò Author — sẽ thấy ít menu admin hơn alice).

---

## 🧪 Kịch bản: Single Logout (SLO) — Đăng xuất đồng thời 2 ứng dụng

Kịch bản này chứng minh rằng khi đăng xuất khỏi **một ứng dụng**, tất cả các ứng dụng còn lại trong hệ thống **đều bị đăng xuất theo** — đây là tính năng Single Logout (SLO).

### Bước 1: Truy cập App2 (ứng dụng thứ hai)
1. Mở cửa sổ ẩn danh mới.
2. Vào địa chỉ: `http://localhost/app2/`
3. **Kỳ vọng:** Trình duyệt redirect sang Keycloak login. Đăng nhập với `alice/alice123`.
4. Sau khi đăng nhập, trang hiển thị thông tin request headers, trong đó có dòng:
   - `X-Authenticated-User: alice`
   - `X-OpenIG-Node: openig-1` *(hoặc `openig-2` tùy node đang phục vụ)*
   → Xác nhận App2 đã nhận diện được user qua SSO và node đang xử lý request.

### Bước 2: Truy cập WordPress trong cùng phiên (SSO)
1. Trong **cùng cửa sổ trình duyệt đó**, mở tab mới, vào: `http://localhost/wp-admin/`
2. **Kỳ vọng:** WordPress Dashboard hiển thị **ngay lập tức, không cần đăng nhập lại**.
   → Chứng minh SSO: cùng một phiên Keycloak, cả 2 app đều được xác thực.

### Bước 3: Đăng xuất khỏi WordPress
1. Trong tab WordPress, click vào tên `alice` góc trên phải → chọn **Log Out**.
2. **Kỳ vọng ngầm (tự động):**
   - OpenIG chặn request logout (`/wp-login.php?action=logout`).
   - Redirect sang `/openid/logout` → OpenIG xóa session JSESSIONID.
   - Tiếp tục redirect sang Keycloak end_session endpoint → Keycloak hủy phiên SSO.

### Bước 4: Kiểm tra SLO trên App2
1. Quay lại tab đang mở `http://localhost/app2/`, nhấn **F5** (Refresh).
2. **Tiêu chuẩn đạt (Pass Criteria):**
   - Trình duyệt **redirect về trang đăng nhập Keycloak** — không hiển thị nội dung App2.
   - URL trên thanh địa chỉ chuyển sang dạng `http://localhost:8080/realms/sso-realm/...`
   → **SLO thành công**: dù chưa logout trực tiếp trên App2, App2 đã bị đăng xuất theo.

### Bước 5: Kiểm tra lại WordPress
1. Vào lại `http://localhost/wp-admin/`.
2. **Tiêu chuẩn đạt:** Redirect về Keycloak login — không còn truy cập được Dashboard.

> **Tóm tắt SLO:** Một lần đăng xuất trên WordPress → cả WordPress lẫn App2 đều yêu cầu đăng nhập lại. Keycloak đóng vai trò trung tâm xóa phiên SSO cho toàn hệ thống.

Chúc bạn test thành công!
