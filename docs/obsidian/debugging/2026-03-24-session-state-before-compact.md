---
title: 2026-03-24 Session State Before Compact
tags:
  - debugging
  - openig
  - stack-c
  - shared-runtime
date: 2026-03-24
status: in-progress
---

# Session state before `/compact`

Ghi chú trạng thái hiện tại để tiếp tục sau `/compact`, tập trung vào [[OpenIG]] shared runtime và hành vi của [[Stack C]].

> [!success] Trạng thái hiện tại
> Đã apply fix `hasPendingState` và đã restart runtime shared. Phần còn lại là xác định các log lỗi hiện tại thuộc giai đoạn pre-fix hay post-fix.

## Trạng thái fix đã apply

- `commit 5fb549d`: `hasPendingState` fix trong `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy` (lines `300-324`)
- Fix: sau `401` check, thêm `hasPendingState` guard — nếu vừa có stale tokens vừa có pending OAuth2 state dưới `2` URL-format khác nhau (cùng endpoint suffix), chỉ xóa stale tokens, giữ pending state
- Containers đã restart: `shared-openig-1`, `shared-openig-2`

## Vấn đề còn chưa rõ (cần check sau compact)

Logs `shared-openig-1` vẫn có:

1. `invalid_token` errors (`I/O dispatcher 1`) — CẦN XÁC ĐỊNH: trước hay sau restart fix?
2. `'Authorization call-back failed because the state parameter contained an unexpected value'`
3. `'Authorization call-back failed because there is no authorization in progress'`
4. `'Missing Redis payload for tokenRefKey=token_ref_id_app5 tokenRefId=59d1d5bd-71aa-4dce-9a11-c3d3728fef90'`

> [!warning] Điểm mấu chốt
> Chưa thể kết luận fix đã đủ hay chưa nếu chưa đối chiếu timestamp của các log lỗi với thời điểm restart runtime.

## Bước tiếp theo sau compact

1. Check timestamps: `docker logs shared-openig-1 --timestamps | grep -E 'invalid_token|authorization in progress|state parameter|Missing Redis' | tail -20`
2. So sánh với thời điểm restart (`docker logs shared-openig-1 --timestamps | grep -E 'Starting|Loaded the route' | tail -5`)
3. Nếu errors là PRE-fix → `hasPendingState` fix có thể đã giải quyết, cần user test lại
4. Nếu errors là POST-fix → bug chưa fix hết, cần điều tra thêm

## Xác nhận runtime đúng

- `shared-nginx` (port `80`) → `shared-openig-1/2` → mount `shared/openig_home` `✅`
- `stack-c-openig-c1-1/c2-1`: ORPHANED, không phục vụ traffic
- Codex KHÔNG thể dùng `docker logs` trong `--full-auto` sandbox → phải dùng `--dangerously-bypass-approvals-and-sandbox`

## App status (từ logs gần nhất)

- `app5` (Grafana): Restore→Store cycling với UUID mới liên tục → HEALTHY (active session)
- `app6` (phpMyAdmin): Restore→Store cycling bình thường → HEALTHY
- `app4` (Jellyfin): Restore→Store cycling bình thường → HEALTHY
- `app1`, `app2`, `app3`: chưa thấy activity gần đây trong logs
