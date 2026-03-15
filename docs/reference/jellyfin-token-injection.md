# Tài liệu Tích hợp Jellyfin SSO: Token-based Injection Pattern

Tài liệu này phân tích phương pháp tích hợp Jellyfin vào hệ thống SSO thông qua OpenIG, tập trung vào sự khác biệt giữa cơ chế Cookie truyền thống và Token-based Auth của các ứng dụng hiện đại.

## 1. So sánh pattern Cookie-based vs Token-based injection

| Đặc tính | Cookie-based (WP/Redmine) | Token-based (Jellyfin) |
| :--- | :--- | :--- |
| **Luồng Login** | Form POST (`application/x-www-form-urlencoded`) | JSON POST (`application/json`) |
| **Cơ chế Duy trì** | Header `Set-Cookie` từ backend | Access Token trong JSON Response body |
| **Injection tại IG** | Inject header `Cookie: session_id=...` | Inject header `Authorization: MediaBrowser ...` |
| **Lưu trữ phía Client** | Browser tự quản lý Cookie | Thường lưu trong `localStorage` hoặc `sessionStorage` |
| **Đặc thù logout** | Xóa Cookie hoặc hết hạn session | Invalidate Token qua API backend |

## 2. Chi tiết luồng Authentication của Jellyfin

Jellyfin sử dụng hệ thống xác thực dựa trên token được thiết kế cho cả Web và App di động.

- **Endpoint xác thực:** `POST /Users/AuthenticateByName`
- **Request format:**
  - Body: JSON `{"Username": "...", "Pw": "..."}`
  - Header bắt buộc: `X-Emby-Authorization` chứa thông tin thiết bị (Client, Device, DeviceId, Version).
- **Response:** Trả về object `AuthenticationResult` chứa:
  - `AccessToken`: Chuỗi token dùng cho các request sau.
  - `User`: Thông tin user bao gồm `Id`.
  - `ServerId`: ID của server Jellyfin.
- **Định dạng request tiếp theo:**
  Mọi request sau đó phải kèm header:
  `Authorization: MediaBrowser Client="...", Device="...", DeviceId="...", Version="...", Token="<access_token>"`
- **Vấn đề localStorage:** 
  Web UI của Jellyfin được thiết kế để đọc token từ `localStorage`. Khi OpenIG đứng giữa inject header, browser JS có thể không thấy token trong storage, dẫn đến UI có trạng thái "chưa đăng nhập" dù backend đã chấp nhận request. Đây là thách thức lớn nhất khi tích hợp "không chạm" (zero-code change).

## 3. Thiết kế tích hợp OpenIG cho Jellyfin

### Route: `01-jellyfin.json`
- **Condition:** Kiểm tra Host header khớp với `jellyfin-b.sso.local`.
- **Filter chain:**
  1. `OAuth2ClientFilter`: Xác thực user qua Keycloak.
  2. `SessionBlacklistFilter`: Kiểm tra trạng thái logout tập trung (Redis).
  3. `VaultCredentialFilter`: Lấy username/password của Jellyfin từ HashiCorp Vault.
  4. `JellyfinTokenInjector`: Filter tùy chỉnh để thực hiện trao đổi token.

### Filter mới: `JellyfinTokenInjector.groovy`
Filter này sẽ thực hiện logic logic sau:
1. **Check Cache:** Kiểm tra trong session cache của OpenIG xem đã có `jellyfin_token` và `jellyfin_user_id` chưa.
2. **Token Exchange:** Nếu chưa có, thực hiện một cuộc gọi "side-request" (POST JSON) đến backend Jellyfin bằng credentials lấy từ Vault.
3. **Parse & Store:** Phân tích JSON response, trích xuất `AccessToken` và `UserId`, sau đó lưu vào session của OpenIG.
4. **Header Injection:** Xây dựng chuỗi Authorization đúng định dạng `MediaBrowser ...` và chèn vào request gửi đến backend.

### Thách thức kỹ thuật
- **Lỗi trạng thái UI:** Nếu Web UI Jellyfin dựa hoàn toàn vào `localStorage`, ta có thể cần một `ResponseRewriter` để inject một đoạn script nhỏ vào trang `index.html` nhằm đẩy token từ server-side vào `localStorage` của browser.
- **Hết hạn Token:** Jellyfin token có thể bị vô hiệu hóa bởi admin hoặc hết hạn ngầm. Filter cần bắt được mã lỗi `401 Unauthorized` từ backend để xóa cache và thực hiện re-authenticate tự động.
- **Định danh thiết bị (DeviceId):** Cần đảm bảo `DeviceId` là duy nhất và nhất quán cho mỗi session của user để tránh việc Jellyfin tạo ra quá nhiều session ảo trong database.

## 4. Checklist triển khai Jellyfin SSO

1. **Docker:** Thêm service `jellyfin` vào `stack-b/docker-compose.yml`.
2. **Nginx:** Cấu hình server block `jellyfin-b.sso.local` trỏ về OpenIG.
3. **Jellyfin Setup:** Truy cập trực tiếp để hoàn tất wizard thiết lập ban đầu (tạo thư viện, admin).
4. **User Sync:** Tạo tài khoản `alice`, `bob` trên Jellyfin (password có thể ngẫu nhiên, IG sẽ quản lý).
5. **Vault:** Lưu credentials của user vào Vault theo đường dẫn `secret/jellyfin/data/<email>`.
6. **Keycloak:** Đăng ký client mới hoặc cập nhật Redirect URI cho Jellyfin endpoint.
7. **OpenIG Route:** Tạo file `01-jellyfin.json` với cấu hình tương ứng.
8. **Groovy Script:** Viết `JellyfinTokenInjector.groovy` xử lý JSON POST và Header injection.
9. **SLO Handler:** Viết `SloHandlerJellyfin.groovy` để gọi API logout của Jellyfin khi user thoát.
10. **Kiểm tra SSO:** Đăng nhập Keycloak → Truy cập Jellyfin → Tự động đăng nhập thành công.
11. **Kiểm tra SLO:** Nhấn logout trên portal → Jellyfin token bị hủy + session bị chặn bởi Redis blacklist.

## 5. Single Logout (SLO) đặc thù cho Jellyfin

Không giống như các app Cookie-based chỉ cần xóa cookie, Jellyfin yêu cầu gọi API để invalidate session trên server:

- **API Logout:** `POST /Sessions/Logout` hoặc `DELETE /Sessions/{sessionId}`.
- **Thực thi:** Khi OpenIG nhận được tín hiệu SLO từ Keycloak hoặc user truy cập `/logout`:
  1. IG lấy `AccessToken` từ session.
  2. Gửi request `POST /Sessions/Logout` kèm token đến Jellyfin backend.
  3. Ghi log và xóa thông tin token khỏi session cache.
  4. Cập nhật Redis blacklist để chặn các request cũ (nếu có).
