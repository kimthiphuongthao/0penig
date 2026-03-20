---
title: Translate Standard Gateway Pattern to Vietnamese
tags:
  - openig
  - documentation
  - gateway-pattern
  - vi
date: 2026-03-14
status: done
---

# Translate Standard Gateway Pattern to Vietnamese

## Context

Mục tiêu là tạo bản dịch tiếng Việt cho tài liệu chuẩn gateway SSO/SLO để đội vận hành và tích hợp đọc trực tiếp bằng tiếng Việt, nhưng vẫn giữ nguyên hợp đồng kỹ thuật cho [[OpenIG]], [[Keycloak]], [[Vault]], Redis và các cơ chế adapter ở [[Stack C]].

## What Done

- Đọc tài liệu gốc `docs/standard-gateway-pattern.md`.
- Dịch đầy đủ sang file mới `docs/standard-gateway-pattern-vi.md`.
- Giữ nguyên cấu trúc Markdown, bảng, checklist và luồng SLO.
- Giữ thuật ngữ kỹ thuật quan trọng (`JwtSession`, `OAuth2ClientFilter`, `BackchannelLogoutHandler`, `SessionBlacklistFilter`, `sid`, `id_token_hint`, `requireHttps`, `httpOnly`, `Secure`).

## Decisions

- Dùng tiêu đề song ngữ theo dạng `Tiếng Việt (English)` cho section chính để dễ đối chiếu.
- Giữ nguyên tên field kỹ thuật, key cấu hình, code id và status code HTTP để tránh sai nghĩa triển khai.
- Không thay đổi nội dung chuẩn kiểm soát (MUST/SHOULD/MUST NOT), chỉ dịch ngữ nghĩa.

> [!success] Confirmed
> Bản dịch đã bao phủ toàn bộ các phần yêu cầu: Overview, Login Mechanism Coverage, Pattern Architecture, Required Controls, Recommended Controls, SLO Flow, Anti-Patterns, Checklist.

> [!tip] Best Practice
> Khi cập nhật bản tiếng Anh, nên cập nhật bản tiếng Việt cùng commit để tránh lệch hợp đồng kỹ thuật.

> [!warning] Gotcha
> Không dịch các identifier cấu hình hoặc tên filter sang tiếng Việt vì có thể gây nhầm khi đối chiếu route/Groovy thật.

## Current State

- File gốc: `docs/standard-gateway-pattern.md` (không chỉnh sửa).
- File mới: `docs/standard-gateway-pattern-vi.md` (đã tạo).
- Không thay đổi route, script hoặc gateway config runtime.

## Next Steps

- Thêm liên kết chéo giữa hai tài liệu EN/VI ở đầu mỗi file.
- Nếu tài liệu chuẩn nâng version, cập nhật đồng bộ EN/VI và ghi chênh lệch thay đổi ở note riêng.

## Files Changed

- `docs/standard-gateway-pattern-vi.md`
