---
name: transparent-vs-cooperative-sso
description: Phân biệt Transparent SSO (Stack A/B) và Cooperative SSO (Stack C) — khi nào app cần được cấu hình để tin tưởng proxy
type: decision
date: 2026-03-12
---

# Transparent SSO vs Cooperative SSO

## Quyết định

Stack C (Grafana + phpMyAdmin) không phải "zero-touch app" như Stack A/B. Cả 2 app đều yêu cầu cấu hình app-side để kích hoạt chế độ proxy auth. Đây là đặc thù của 2 cơ chế Header-based Auth và HTTP Basic Auth — không thể tránh.

## Định nghĩa

**Transparent SSO** (Stack A/B):
- OpenIG giả lập hành vi người dùng (inject form credentials hoặc token)
- App không biết mình đứng sau proxy
- App không cần thay đổi gì
- Ví dụ: WordPress (form inject), Redmine (form inject), Jellyfin (token inject)

**Cooperative SSO** (Stack C):
- OpenIG phối hợp với app — inject header hoặc Basic Auth header
- App phải được cấu hình để tin tưởng và đọc input từ proxy
- App-side config bắt buộc
- Ví dụ: Grafana (`GF_AUTH_PROXY_ENABLED`), phpMyAdmin (`auth_type=http`)

## Hệ quả kỹ thuật

| Tiêu chí | Transparent | Cooperative |
|----------|-------------|-------------|
| App-side config | Không | Có (env vars hoặc config file) |
| App biết về proxy? | Không | Có |
| Điều kiện áp dụng | App có login form HTML | App có sẵn proxy auth mode |
| Nếu app không hỗ trợ | Form inject vẫn chạy được | Không thể dùng pattern này |
| Rủi ro security | Thấp (OpenIG kiểm soát hoàn toàn) | Cần strip header từ client (đã làm ở nginx) |

## Liên kết

- [[stack-a]] — Transparent SSO examples
- [[stack-b]] — Transparent SSO examples  
- Xem chi tiết: `docs/legacy-app-code-changes.md` Câu hỏi 4
