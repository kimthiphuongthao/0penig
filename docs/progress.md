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

## Tuần 23/03/2026 - 27/03/2026

### Kế hoạch
- Khôi phục và kiểm thử production session pattern trên toàn bộ stack
- Tiếp tục đối chiếu audit hệ thống đích với mô hình Distributed Gateway, cập nhật Gap analysis
- Rà soát và chuẩn hóa tài liệu reference solution theo từng login mechanism
- Cập nhật Quick Start Guide và slide báo cáo

### Kết quả
- [x] Khôi phục và kiểm thử production session pattern trên toàn bộ stack: hoàn tất chuyển sang shared-infra runtime, xác nhận SSO/SLO end-to-end cho toàn bộ 6 ứng dụng trên cùng cụm OpenIG/Vault/Redis dùng chung; đồng thời xử lý lỗi SSO sau SLO do mixed OAuth2 state/token restore và chốt baseline session/cookie isolation cho mô hình production.
- [x] Tiếp tục đối chiếu audit hệ thống đích với mô hình Distributed Gateway, cập nhật Gap analysis: hoàn tất OpenIG built-in gap analysis, lập production-readiness audit ngày 25/03 và cập nhật vòng hiệu chỉnh để chốt danh sách findings mở, mức độ ưu tiên và các blind spots cần xử lý trước đóng gói.
- [ ] Rà soát và chuẩn hóa tài liệu reference solution theo từng login mechanism: đã đồng bộ phần lớn deliverables, rules và integration guide sang kiến trúc shared-infra, bổ sung hướng dẫn bootstrap và cập nhật các tài liệu audit liên quan; tuy nhiên bộ reference solution theo từng mechanism vẫn chưa hoàn tất để sẵn sàng bàn giao.
- [ ] Cập nhật Quick Start Guide và slide báo cáo: đã có nền tảng đóng gói ban đầu với script bootstrap idempotent cho shared runtime, nhưng Quick Start Guide hoàn chỉnh và slide báo cáo vẫn là hạng mục tồn đọng cho tuần kế tiếp.

---

## Tuần 30/03/2026 - 03/04/2026

### Kế hoạch
**Kỹ thuật**
- Ưu tiên triển khai các findings còn mở từ production-readiness audit 2026-03-25, trước hết là BUG-002 (nginx callback retry), AUD-003 (JWKS null-cache), AUD-009 và blind spot BS-001 về Redis plaintext token payload.
- Chốt fix plan cho toàn bộ findings OPEN/PARTIAL, hoàn tất các hardening có thể đóng ngay trên shared runtime và chạy nốt các vòng security/code review còn deferred để khóa baseline readiness.

**Đóng gói & triển khai**
- Hoàn thiện packaging shared-infra theo hướng single-command deploy, chuẩn hóa `shared/bootstrap.sh`, cấu trúc bundle bàn giao và bộ biến cấu hình tối thiểu cho đơn vị triển khai.
- Đánh giá và chốt phương án đóng gói cuối cùng cho lab handover, ưu tiên Docker Compose bundle và giữ OVA như phương án trình diễn nếu cần.

**Tài liệu & báo cáo**
- Hoàn thiện Quick Start Guide, tài liệu vận hành/chuyển giao và cập nhật reference solution theo trạng thái audit mới nhất, bám 5 login mechanisms của bộ giải pháp.
- Soạn slide báo cáo và tài liệu tổng quan cho stakeholder, nhấn mạnh kiến trúc Distributed Gateway, mức sẵn sàng production, các gap còn lại và khuyến nghị triển khai.

### Kết quả
- Xử lý và đóng các findings ưu tiên cao còn mở từ production readiness audit tháng 3
- Chuẩn hóa cơ chế xử lý lỗi tại tầng Gateway — loại bỏ cấu hình fallback legacy (tự đánh giá không còn phù hợp với kiến trúc mục tiêu)
- Kiểm chứng lại toàn diện luồng SSO/SLO trên shared runtime sau thay đổi — không phát sinh regression
- Rà soát và mapping cấu hình shared runtime theo mô hình K8s multi-tenancy — xác định phạm vi, các thành phần cần tách biệt và điều kiện tiên quyết về packaging để sẵn sàng triển khai
- Đồng bộ tài liệu audit và trạng thái roadmap — hoàn thiện hồ sơ kỹ thuật trước giai đoạn đóng gói

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
- [x] Production readiness audit: `55 RESOLVED`, `6 PARTIAL`, `20 STILL OPEN`
- [x] Gap report written: `docs/audit/2026-03-17-production-readiness-gap-report.md` (`26e8e69`)
- [x] 64-file maintenance inventory completed
- [x] STEP-01 (L-5): xóa `PhpMyAdminCookieFilter.groovy` dead code (`20d523f`)
- [x] STEP-02 (M-5/S-9): rotate Stack C OIDC client secrets away from weak literal `secret-c` (`37672ed`)
- [x] STEP-03 (H-5/S-3): externalize secret sang `.env` + pin toàn bộ OpenIG images về `openidentityplatform/openig:6.0.1` (`b738577`)
- [x] 2026-03-18: hoàn thành STEP-04 Redis auth (`8c11916`) + STEP-05..12 Phase 2b hardening batch (`ecbca5d`) (9 P1-MUST + nhiều P2-SHOULD fixes, parallel Codex execution)
- [x] End-of-session doc audit sync: roadmap, audit docs, test docs, rules, và state notes đã phản ánh STEP-01/02/03
- [x] Stack C Grafana SSO/SLO re-validation PASS: confirmed OpenIG `OAuth2ClientFilter` does not URL-encode `client_secret`; APP5 rotated to alphanumeric-only secret, Stack C OpenIG containers recreated, user confirmed end-to-end flow (`a403b3d`)
- [x] 2026-03-18: STEP-13 (proxy_cookie_flags SameSite=Lax, all 3 stacks) + STEP-14 (non-root: macOS vault permission constraint, comment added). Phase 2b complete.
- [x] 2026-03-19: Phase 1+2 `JwtSession` production pattern fully validated on `fix/jwtsession-production-pattern`
- [x] 2026-03-19: BackchannelLogoutHandler ES256/EC fix complete (`646a45a`, `d2eb8e9`)
- [x] 2026-03-19: Full login+logout validation PASS on Stack A, Stack B, and Stack C
- [x] 2026-03-20: hoàn thành L-1/L-3 (`8f17e7b`), L-2 (`d2a0411`), L-4/L-6 (`e4485f1`), và Code-M3 Stack B `VaultCredentialFilter.groovy` consolidation (`e22a855`)
- [x] 2026-03-20: fix regression `BUG-TOKENREFKEY` (`8e9f729`) bằng per-app `tokenRefKey` (`token_ref_id_app1` .. `token_ref_id_app6`) để chặn cross-app same-cookie contamination
- [x] 2026-03-20: end-of-session audit synced backlog, gap report, deliverables, rules, Obsidian stack/debug notes, progress, and `.memory` before compact

---

## Tuần 23/03/2026 - 27/03/2026

### Kế hoạch
**Kỹ thuật:**
- [ ] Khôi phục và kiểm thử production session pattern trên toàn bộ stack
- [ ] Tiếp tục đối chiếu audit hệ thống đích với mô hình Distributed Gateway, cập nhật Gap analysis
- [ ] Rà soát và chuẩn hóa tài liệu reference solution theo từng login mechanism

**Tài liệu & báo cáo:**
- [ ] Cập nhật Quick Start Guide và slide báo cáo

### Kết quả
- [x] Hoàn tất chuyển đổi sang kiến trúc shared-infra: một cụm gateway duy nhất (nginx + OpenIG HA + Redis + Vault) phục vụ toàn bộ 6 ứng dụng qua hostname routing — xác nhận SSO/SLO 6/6 PASS
- [x] Tăng cường bảo mật tầng runtime: per-app Redis ACL isolation, per-app Vault AppRole isolation, xử lý các security findings từ audit nội bộ (AUD-001, AUD-004, AUD-005, AUD-008)
- [x] Hoàn thiện production readiness audit: 30+ findings qua 5 vòng review, phân loại mức độ ưu tiên, tổng hợp thành master audit doc
- [x] Hoàn thiện phân tích khoảng cách OpenIG built-in vs custom Groovy: 0/14 script có thể thay thế bằng built-in, tài liệu hóa 12 capability gaps
- [x] Bổ sung packaging artifact: bootstrap.sh — khởi động Vault và tái tạo AppRole idempotent bằng một lệnh
- [ ] Cập nhật Quick Start Guide và slide báo cáo — chưa hoàn thiện, chuyển sang tuần tiếp

---

## Tuần 30/03/2026 - 03/04/2026

### Kế hoạch
**Kỹ thuật:**
- [ ] Xử lý các findings còn mở từ production readiness audit (ưu tiên BUG và security findings)
- [ ] Hoàn thiện bằng chứng kiểm chứng HA, failover và kiểm soát truy cập tối thiểu

**Đóng gói & triển khai:**
- [ ] Thiết kế và thử nghiệm Docker Compose bundle: single-command deploy
- [ ] Viết Quick Start Guide cho đơn vị nhận package

**Tài liệu & báo cáo:**
- [ ] Hoàn thiện slide báo cáo phương án giải pháp SSO/SLO
- [ ] Cập nhật tài liệu reference solution với kết quả audit và gap analysis
- [ ] Viết tài liệu tổng quan giải pháp cho stakeholder/lãnh đạo

### Kết quả
- Xử lý và đóng các findings ưu tiên cao còn mở từ production readiness audit tháng 3
- Chuẩn hóa cơ chế xử lý lỗi tại tầng Gateway — loại bỏ cấu hình fallback legacy (tự đánh giá không còn phù hợp với kiến trúc mục tiêu)
- Kiểm chứng lại toàn diện luồng SSO/SLO trên shared runtime sau thay đổi — không phát sinh regression
- Rà soát và mapping cấu hình shared runtime theo mô hình K8s multi-tenancy — xác định phạm vi, các thành phần cần tách biệt và điều kiện tiên quyết về packaging để sẵn sàng triển khai
- Đồng bộ tài liệu audit và trạng thái roadmap — hoàn thiện hồ sơ kỹ thuật trước giai đoạn đóng gói

---

## Tuần 07/04/2026 - 11/04/2026

### Kế hoạch
- Triển khai Docker Compose bundle — thực thi single-command deploy, chốt cấu trúc package và bộ biến cấu hình tối thiểu
- Xây dựng tài liệu kiến trúc K8s multi-tenancy từ kết quả mapping tuần trước — chi tiết hóa từng thành phần và lộ trình packaging
- Chuẩn hóa và bổ sung slide báo cáo — cập nhật theo kết quả audit và trạng thái kỹ thuật mới nhất, sẵn sàng trình bày cho stakeholder
- Viết Quick Start Guide cho đơn vị nhận package

---
