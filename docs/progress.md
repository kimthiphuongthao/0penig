# Tiến độ dự án — SSO/SLO Gateway cho Legacy App

## Tuần 23/02/2026 - 27/02/2026

### Kết quả
- [x] Thiết lập cụm Gateway vận hành Active-Active, giả lập Load Balancer bằng Nginx
- [x] Kiểm chứng Stateless Session Management (JwtSession): failover transparent, không gián đoạn dịch vụ
- [x] Xác nhận bộ Vault API: config, rotate, creds — mỗi Gateway cấu hình riêng endpoint Vault
- [x] Viết và kiểm thử Vault plugin (Go): sinh hash (bcrypt/sha/pbkdf2), cập nhật DB — test end-to-end PostgreSQL, MySQL, MSSQL

---

## Tuần 02/03/2026 - 06/03/2026

### Kết quả
- [x] Chuẩn hóa cấu trúc triển khai đa đơn vị: mỗi bên quản lý ứng dụng legacy vận hành độc lập (Gateway + Credential Store + Session Store riêng), chỉ dùng chung Identity Provider trung tâm
- [x] Bổ sung và xác nhận cơ chế SLO: tích hợp Session Store phân tán, backchannel logout từ IdP lan đến toàn bộ ứng dụng trong realm kể cả cross-unit
- [x] Cấu hình và kiểm chứng AppRole authentication: Gateway lấy token ngắn hạn, policy least-privilege theo từng vai trò
- [x] Xác định Health Check baseline (fall/rise threshold, probe interval, fail timeout) + ghi chú mapping F5 → [healthcheck-baseline.md](./healthcheck-baseline.md)
- [x] Chuẩn hóa Access Log format đồng bộ giữa các Gateway node (real client IP, duration, node ID) → [log-format-standard.md](./log-format-standard.md)
- [x] Xác nhận tính khả thi triển khai theo mô hình subdomain: SSO/SLO hoạt động đầy đủ, app legacy không cần thay đổi cấu hình nội bộ

---

## Tuần 09/03/2026 - 13/03/2026

### Kế hoạch
**Kỹ thuật — tiếp tục hoàn thiện giải pháp:**
- [ ] Kiểm thử các case chưa cover: token refresh, session timeout, node failover trong khi đang xác thực, concurrent logout từ nhiều thiết bị
- [ ] Kiểm thử subdomain deployment cho các ứng dụng còn lại trong lab

**Khảo sát — chuẩn bị cho hệ thống đích:**
- [ ] Xây dựng checklist/template khảo sát kỹ thuật hệ thống legacy (cơ chế xác thực, session management, cấu trúc credential, đặc thù nền tảng)
- [ ] Thu thập đặc tả ban đầu của các hệ thống trong phạm vi triển khai thực tế

### Kết quả
_(cập nhật cuối tuần)_

---
