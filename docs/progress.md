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
- [x] Kiểm chứng và hoàn thiện 2 cơ chế xác thực mới: Trusted Header Injection (proxy auth) và HTTP Basic Auth Injection — Gateway inject credentials mà không sửa code ứng dụng đích
- [x] Xác nhận cross-stack SLO: mỗi ứng dụng trong cùng realm dùng OIDC client độc lập, backchannel logout phủ đủ toàn bộ endpoints across stacks
- [x] Kiểm thử token refresh: Gateway tự động gia hạn access token silently, không gián đoạn session người dùng
- [x] Kiểm thử session timeout: JwtSession expire → re-authentication transparent qua IdP SSO session
- [x] Kiểm thử node failover: sticky routing + passive health check đảm bảo continuity khi 1 Gateway node down
- [x] Kiểm thử concurrent logout: admin-initiated per-session logout trigger backchannel đúng; xác nhận "force logout all" không propagate qua backchannel — documented as known IdP limitation
- [x] Xây dựng checklist khảo sát kỹ thuật legacy app: template chuẩn hóa thu thập thông tin trước khi tích hợp SSO

---

## Tuần 16/03/2026 - 20/03/2026

### Kế hoạch
**Kỹ thuật — hoàn thiện trước khi đóng gói:**
- [ ] Investigate 2 lỗi tồn đọng trong logs: `BackchannelLogoutHandler: logout_token is missing` và `Authorization call-back failed: no authorization in progress`
- [x] Bật Redis persistence (`appendonly yes`) — đảm bảo blacklist SLO không mất khi Redis restart (commit 00f5558)
- [ ] Bật Vault audit logging — bắt buộc trước khi đưa vào production/OVA
- [ ] Workaround admin "Logout all sessions" không trigger backchannel — đánh giá giải pháp via Keycloak REST API
- [ ] Kiểm thử failover trong khi OAuth2 flow đang chạy (interrupt giữa chừng)
- [ ] Phase 3: Vault Production Hardening — theo gap list tại `docs/reference/vault-hardening-gaps.md`

**Đóng gói & triển khai:**
- [ ] Đánh giá và chọn phương án đóng gói: OVA / Docker Compose bundle / Vagrant box
- [ ] Thiết kế cấu trúc package: single-command deploy, cấu hình tối thiểu cho đơn vị nhận
- [ ] Viết Quick Start Guide cho đơn vị nhận package

**Tài liệu & báo cáo:**
- [ ] Viết slide báo cáo phương án giải pháp SSO/SLO (kiến trúc, luồng xác thực, kết quả lab)
- [ ] Viết tài liệu tổng quan giải pháp cho stakeholder
- [ ] Cập nhật integration guide: section OpenIG built-in filter selection guide

**Khảo sát:**
- [ ] Thu thập đặc tả ban đầu của các hệ thống trong phạm vi triển khai thực tế

### Kết quả
- [x] Pattern Consolidation Step 5 hoàn tất: H-2 `vault/keys/` trong `.gitignore` (`5ae657e`); H-3 bỏ Redmine host port `3000`; H-9 thêm `proxy_buffer_size 128k` + `proxy_buffers 4 256k` cho Stack C; M-2 bổ sung `CANONICAL_ORIGIN_APP1..APP4` cho Stack A/B; M-14 xóa `App1ResponseRewriter.groovy`
- [x] Kiểm thử xác nhận sau Step 5: BackchannelLogout trên đủ 5 OIDC clients đều ghi Redis blacklist; SloHandler trên 5 app đều redirect với `id_token_hint=PRESENT`; phpMyAdmin inline `failureHandler` vẫn hoạt động đúng
- [x] Pattern Consolidation Steps 1-6: COMPLETE
- [x] Post-audit fixes: Stack A `BackchannelLogoutHandler` consolidated (`f85a3f2`), 3 leftover `SloHandler*` files deleted
- [x] `.memory` symlink setup cho Codex write access (`15174f6`)
- [x] Production readiness audit: `38 RESOLVED`, `6 PARTIAL`, `37 STILL OPEN`
- [x] Gap report written: `docs/audit/2026-03-17-production-readiness-gap-report.md` (`26e8e69`)
- [x] 64-file maintenance inventory completed
- [x] STEP-01 (L-5): xóa `PhpMyAdminCookieFilter.groovy` dead code (`20d523f`)
- [x] STEP-02 (M-5/S-9): rotate Stack C OIDC client secrets sang giá trị mạnh 44 ký tự (`37672ed`)
- [x] STEP-03 (H-5/S-3): externalize secret sang `.env` + pin toàn bộ OpenIG images về `openidentityplatform/openig:6.0.1` (`b738577`)
- [x] End-of-session doc audit sync: roadmap, audit docs, test docs, rules, và state notes đã phản ánh STEP-01/02/03
- [ ] Current blocker: Grafana SSO re-validation pending — `OIDC_CLIENT_SECRET_APP5` cần được verify end-to-end, bao gồm trailing `=` padding
- [ ] Next: xử lý Grafana SSO blocker rồi tiếp tục P1 backlog (`H-4/S-2`, `H-7/A-1`, `A-6/A-7/M-13/S-17`)

---
