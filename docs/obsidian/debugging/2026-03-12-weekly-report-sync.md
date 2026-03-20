---
title: Weekly report sync 2026-03-12
tags:
  - debugging
  - documentation
  - openig
  - keycloak
  - vault
  - stack-c
date: 2026-03-12
status: done
---

# Weekly report sync (2026-03-12)

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

Đồng bộ lại tài liệu tuần trong SSO lab sau vòng test nâng cao ngày 2026-03-12.

## What changed

- Cập nhật `docs/progress.md`:
  - Điền đầy đủ phần `### Kết quả` cho tuần `09/03/2026 - 13/03/2026`.
  - Thêm tuần mới `16/03/2026 - 20/03/2026` với kế hoạch + placeholder kết quả.
- Append `docs/test-report.md`:
  - Thêm section `Bổ sung kiểm thử — 2026-03-12` gồm TC-ADV-01..04.

> [!success]
> Các kết quả test nâng cao đã được phản ánh đầy đủ vào tiến độ tuần và test report tổng.

## Decisions captured

- Phân loại `Admin -> Logout all sessions` là known limitation từ [[Keycloak]], không phải bug của [[OpenIG]].
- Giữ trạng thái khảo sát legacy là deferred để tách khỏi scope test kỹ thuật tuần hiện tại.

> [!warning]
> Khi vận hành thực tế cần dùng sign out từng session hoặc REST API thay cho "Logout all sessions" nếu cần backchannel logout chắc chắn.

## Current state

- Token refresh: pass (silent refresh).
- Session timeout: pass (re-auth transparent khi SSO session Keycloak còn).
- Node failover: pass (xác nhận qua header node đích).
- Concurrent logout: partial do limitation của Keycloak.

> [!tip]
> Sau mỗi vòng test nên cập nhật đồng thời `progress.md` và `test-report.md` để tránh lệch giữa planning và evidence.

## Files changed

- `docs/progress.md`
- `docs/test-report.md`
