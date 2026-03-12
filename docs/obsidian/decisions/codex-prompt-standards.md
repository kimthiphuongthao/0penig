---
title: Codex Prompt Standards
tags:
  - codex
  - workflow
  - standards
date: 2026-03-12
status: verified
---

# Codex Prompt Standards

Liên quan hệ thống: [[OpenIG]]

## Verified Facts

- Default exec timeout là 10 giây (`DEFAULT_EXEC_COMMAND_TIMEOUT_MS = 10_000`), không phải 10 phút.
- Ví dụ `timeout -k ...` không phù hợp làm chuẩn chung cho macOS vì không có `timeout` built-in.
- `model_reasoning_effort` values xác nhận: `none`, `minimal`, `low`, `medium`, `high`, `xhigh`.
- `model_reasoning_summary` values xác nhận: `auto`, `concise`, `detailed`, `none`.

> [!success]
> `model_reasoning_effort` và `model_reasoning_summary` values đã được xác nhận.

## Unverified Claims (Giữ nhãn UNVERIFIED)

- Context window 400K/1M+ chưa có nguồn chính thức đủ thẩm quyền trong bộ tài liệu đã kiểm tra.
- JSON-RPC rate limit endpoint `account/rateLimits/read` chưa xác minh chính thức.
- Pattern `env -i bash --noprofile --norc ...` là best practice chưa xác minh chính thức.
- `timeoutMs` trong payload `command/exec` chưa xác minh là field support ổn định.

> [!warning]
> `--search` không tồn tại trong `codex exec` (exit code 2). Cần dùng Gemini (hoặc web tool riêng) khi cần web search.

> [!warning]
> Default timeout của exec là 10 giây, không phải 10 phút.

## Decision

- Tiêu chuẩn tài liệu prompt Codex phải tách rõ `Verified` và `UNVERIFIED`.
- Các claim chưa có nguồn authoritative phải dán nhãn `UNVERIFIED` ngay trong section liên quan.
