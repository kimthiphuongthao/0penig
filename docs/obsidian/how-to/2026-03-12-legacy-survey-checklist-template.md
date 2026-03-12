---
title: Legacy Survey Checklist Template Before SSO Integration
tags:
  - how-to
  - sso
  - legacy-app
  - openig
  - checklist
date: 2026-03-12
status: done
---

# Context

Task: tạo template khảo sát kỹ thuật dùng trước khi tích hợp legacy app vào SSO.

Nguồn đối chiếu: route JSON của [[OpenIG]] ở Stack A/B/C, tài liệu pattern xác thực, và hướng dẫn tích hợp standalone với [[Keycloak]] + [[Vault]].

Liên quan: [[Stack C]]

# What Was Done

- Đọc và tổng hợp:
  - `docs/legacy-app-code-changes.md`
  - `docs/standalone-legacy-app-integration-guide.md`
  - `docs/legacy-auth-patterns.md`
  - toàn bộ route JSON tại:
    - `stack-a/openig_home/config/routes/`
    - `stack-b/openig_home/config/routes/`
    - `stack-c/openig_home/config/routes/`
  - danh sách file Groovy tại:
    - `stack-a/openig_home/scripts/groovy/`
    - `stack-b/openig_home/scripts/groovy/`
    - `stack-c/openig_home/scripts/groovy/`
- Tạo file checklist mới: `docs/legacy-survey-checklist.md`.
- Checklist cover đủ 8 nhóm khảo sát:
  - app info
  - auth mechanism
  - session management
  - trusted header/proxy auth
  - credential structure
  - deployment/subpath
  - SLO capability
  - pattern recommendation + complexity.
- Thêm bảng reference điền sẵn cho 6 app đã tích hợp: WordPress, WhoAmI, Redmine, Jellyfin, Grafana, phpMyAdmin.

> [!success] Confirmed Working
> Template đã phản ánh đúng 3 nhóm pattern chính đang dùng trong lab: Form Inject, Token Inject (+ response rewrite), Header/Basic Inject và Native OIDC.

# Key Decisions

1. Dùng checklist dạng Markdown với checkbox + bảng để copy/paste nhanh khi onboard app mới.
2. Tách rõ phần "khảo sát thực tế" và phần "kết luận pattern" để tránh chọn giải pháp theo cảm tính.
3. Giữ thêm section "Kết luận nhanh theo dấu hiệu" để giảm thời gian triage ban đầu.

> [!warning] Gotcha
> Câu hỏi "app có hỗ trợ subpath natively không" là điểm loại trừ sớm; nếu không hỏi ngay từ đầu sẽ phát sinh nhiều thay đổi infra về sau.

> [!tip] Best Practice
> Khi điền checklist, luôn thu thập đồng thời login endpoint, CSRF behavior, session cookie name, logout endpoint/method trong cùng 1 phiên test để tránh mismatch dữ liệu.

# Current State

- File checklist mới đã có trong docs và sẵn sàng dùng làm template chuẩn cho khảo sát pre-integration.
- Có thể dùng trực tiếp cho app mới trước khi viết route/filter Groovy.

# Files Changed

- `docs/legacy-survey-checklist.md`
- `docs/obsidian/how-to/2026-03-12-legacy-survey-checklist-template.md`
