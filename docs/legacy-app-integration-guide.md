# Hướng dẫn Tích hợp Ứng dụng Legacy vào Hệ thống SSO

Tài liệu này cung cấp hướng dẫn kỹ thuật tổng quan dành cho Quản lý Ứng dụng (App Owner) và Quản trị hệ thống (IT Manager) mong muốn tích hợp ứng dụng hiện có (Legacy) vào giải pháp Đăng nhập một lần (SSO) tập trung.

## 1. Tổng quan giải pháp

Giải pháp SSO được thiết kế để cung cấp trải nghiệm đăng nhập hiện đại, bảo mật mà **KHÔNG yêu cầu thay đổi mã nguồn (Source Code)** hoặc cấu trúc cơ sở dữ liệu của ứng dụng gốc.

### Thành phần cốt lõi:
*   **OpenIG (Open Identity Gateway):** Đóng vai trò Reverse Proxy đứng trước ứng dụng, xử lý các luồng xác thực OIDC và tự động điền thông tin đăng nhập (Credential Injection).
*   **Keycloak:** Hệ thống Quản lý Danh tính (IdP) tập trung, nơi người dùng thực hiện đăng nhập SSO.
*   **HashiCorp Vault:** Kho lưu trữ bảo mật dùng để quản lý thông tin tài khoản (username/password) của từng người dùng trên từng ứng dụng cụ thể.

### Lợi ích chính:
*   **Zero Code Change:** Ứng dụng không cần chỉnh sửa bất kỳ dòng code nào.
*   **Bảo mật nâng cao:** Loại bỏ việc lưu trữ mật khẩu plaintext tại máy trạm; hỗ trợ MFA (Multi-Factor Authentication) thông qua Keycloak.
*   **Quản lý tập trung:** Thu hồi quyền truy cập ứng dụng ngay lập tức khi user rời tổ chức.

---

## 2. Điều kiện tiên quyết về kỹ thuật

Để ứng dụng có thể tích hợp qua cơ chế Proxy/Credential Injection, cần đáp ứng các điều kiện sau:

1.  **Giao diện đăng nhập:** Ứng dụng có form đăng nhập HTML chuẩn (sử dụng phương thức HTTP POST).
2.  **Quản lý phiên (Session):** Phiên làm việc của người dùng được quản lý thông qua HTTP Cookie.
3.  **Phản hồi sau đăng nhập:** Ứng dụng trả về mã trạng thái HTTP (thường là 200 OK hoặc 302 Redirect) sau khi xác thực thành công.
4.  **Kết nối mạng:** Ứng dụng có thể truy cập được từ hệ thống OpenIG thông qua giao thức HTTP/HTTPS.

---

## 3. Thông tin App Owner cần cung cấp

Để triển khai tích hợp, phía đơn vị quản lý ứng dụng cần cung cấp các nhóm thông tin sau:

### Nhóm A — Thông tin luồng đăng nhập (Authentication Flow)

| Thông tin | Mô tả cụ thể | Ví dụ |
| :--- | :--- | :--- |
| **URL nội bộ** | Địa chỉ IP/Domain thực của ứng dụng (OpenIG sẽ proxy tới đây) | `http://10.0.1.50:8080` |
| **Login Endpoint** | Đường dẫn nhận dữ liệu POST login | `/auth/login` hoặc `/wp-login.php` |
| **Username Field** | Tên thuộc tính `name` của ô nhập tài khoản | `txtUsername` hoặc `user_login` |
| **Password Field** | Tên thuộc tính `name` của ô nhập mật khẩu | `txtPassword` hoặc `user_pass` |
| **Hidden Fields** | Các trường ẩn bắt buộc gửi kèm trong form (nếu có) | `__VIEWSTATE`, `csrf_token` |
| **Success URL** | Trang người dùng sẽ thấy sau khi đăng nhập thành công | `/dashboard` hoặc `/home` |
| **Logout URL** | Đường dẫn để xóa session ứng dụng | `/logout` hoặc `/exit` |

### Nhóm B — Thông tin phiên làm việc (Session)

*   **Tên Cookie Session:** Tên cookie mà ứng dụng cấp phát (ví dụ: `PHPSESSID`, `JSESSIONID`, `.AspNetCore.Session`).
*   **Thời gian hết hạn (Timeout):** Thời gian session ứng dụng tồn tại nếu không hoạt động.
*   **CSRF Protection:** Ứng dụng có yêu cầu gửi kèm Token chống giả mạo khi login không?

### Nhóm C — Danh mục người dùng (User Mapping)

Để hệ thống có thể đăng nhập thay mặt người dùng, cần có bảng ánh xạ danh tính:

| SSO Username (Keycloak) | App Username (Nội bộ) | Ghi chú |
| :--- | :--- | :--- |
| `nguyen_van_a` | `user001` | Tài khoản SSO ánh xạ vào User nội bộ |

*Lưu ý: Mật khẩu ứng dụng sẽ được lưu trữ trực tiếp vào Vault dưới dạng mã hóa, không bàn giao qua văn bản plaintext.*

### Nhóm D — Hạ tầng và Mạng lưới

*   **Môi trường:** Ứng dụng chạy trên VM, Container hay Cloud?
*   **Firewall:** Cần cho phép IP của OpenIG truy cập tới Port của ứng dụng.
*   **Load Balancer:** Nếu ứng dụng chạy nhiều node, có yêu cầu Sticky Session không?

### Nhóm E — Các rào cản bảo mật đặc thù

*   **CAPTCHA:** Form login có yêu cầu nhập mã hình ảnh không? (Nếu có, cần tắt đối với IP của Proxy).
*   **Rate Limiting:** Ứng dụng có chặn IP nếu có nhiều lượt login cùng lúc không?
*   **MFA nội bộ:** Ứng dụng có yêu cầu OTP/SMS riêng sau khi nhập pass không?

---

## 4. Quy trình tích hợp

| Bước | Hành động | Trách nhiệm chính |
| :--- | :--- | :--- |
| 1 | Khảo sát kỹ thuật & Cung cấp thông tin (Nhóm A-E) | App Owner |
| 2 | Cấu hình Route & Script trên OpenIG | SSO Team |
| 3 | Khởi tạo tài khoản & Phân quyền trên Keycloak | SSO Team |
| 4 | Nhập thông tin mật khẩu ứng dụng vào Vault | App Owner / Admin |
| 5 | Kiểm thử tích hợp (UAT) trên môi trường Staging | Cả hai bên |
| 6 | Chuyển đổi DNS/Load Balancer sang URL của SSO | IT Manager |

---

## 5. Cam kết về tính toàn vẹn ứng dụng

Chúng tôi cam kết việc tích hợp này **KHÔNG thay đổi**:
1.  **Source Code:** Không sửa mã nguồn ứng dụng.
2.  **Database:** Không thay đổi cấu trúc dữ liệu người dùng hiện có.
3.  **Business Logic:** Các chức năng nghiệp vụ bên trong ứng dụng giữ nguyên 100%.
4.  **Dữ liệu người dùng:** Thông tin hồ sơ trong app không bị ảnh hưởng.

---

## 6. Giới hạn và Trường hợp đặc thù

Một số trường hợp sau đây cần được SSO Team đánh giá riêng:
*   Ứng dụng sử dụng xác thực qua Certificate (Thẻ từ, HSM).
*   Ứng dụng có CAPTCHA bắt buộc không thể tắt cho Proxy.
*   Ứng dụng có cơ chế MFA (xác thực 2 lớp) nội bộ cứng nhắc.
*   Ứng dụng chạy trên các giao thức không phải HTTP (như Desktop App, Thick Client).

---

## 7. FAQ - Câu hỏi thường gặp

1.  **Việc dùng Proxy có làm chậm ứng dụng không?**
    *   Độ trễ (latency) của OpenIG rất thấp (< 10ms), hầu như người dùng không cảm nhận được sự khác biệt.
2.  **Nếu hệ thống SSO gặp sự cố, tôi có truy cập app được không?**
    *   Thông thường người dùng vẫn có thể truy cập app qua URL nội bộ hoặc qua cơ chế dự phòng (Break-glass) nếu được thiết lập.
3.  **OpenIG có lưu mật khẩu của tôi không?**
    *   Không. Mật khẩu được lưu trong Vault. OpenIG chỉ lấy ra và sử dụng trong bộ nhớ đệm (RAM) tại thời điểm login, sau đó xóa ngay lập tức.
4.  **Tôi muốn đổi mật khẩu ứng dụng thì làm thế nào?**
    *   Người dùng có thể đổi mật khẩu SSO trên Keycloak. Mật khẩu ứng dụng legacy sẽ được quản trị viên cập nhật vào Vault hoặc thông qua công cụ đồng bộ.
5.  **Tại sao lại phải dùng OpenIG thay vì sửa code app sang SAML/OIDC?**
    *   Vì các ứng dụng legacy thường đã cũ, khó bảo trì hoặc nhà thầu không còn hỗ trợ, việc sửa code tốn kém và rủi ro cao.
6.  **Hệ thống có hỗ trợ Single Logout (đăng xuất mọi nơi) không?**
    *   Có. Khi đăng xuất khỏi SSO, OpenIG sẽ tự động gọi lệnh logout tới ứng dụng legacy để xóa session.

---

## 8. Checklist bàn giao tổng hợp

Vui lòng kiểm tra kỹ các hạng mục trước khi bàn giao cho SSO Team:

- [ ] Xác định rõ URL Login và các tham số POST.
- [ ] Xác định tên Cookie session chính xác.
- [ ] Đảm bảo đã whitelist IP của OpenIG trên Firewall/Application.
- [ ] Tắt CAPTCHA cho luồng đăng nhập từ Proxy (nếu có).
- [ ] Cung cấp bảng ánh xạ Username SSO và Username App.
- [ ] Xác định URL Redirect sau khi đăng nhập thành công.
- [ ] Kiểm tra ứng dụng hoạt động bình thường qua IP nội bộ.

---
*Tài liệu được soạn thảo bởi SSO Implementation Team - 2026*