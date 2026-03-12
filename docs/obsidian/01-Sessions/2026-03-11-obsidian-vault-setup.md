---
title: "2026-03-11 — Obsidian Vault Setup"
date: 2026-03-12
tags: [session, setup, obsidian]
agents: [Claude, Codex]
related: [[Current State]]
---

# Obsidian Vault Setup

## Context

User muốn biến Obsidian thành "Internet cá nhân" + "Agent Memory" cho SSO Lab project.

Mục tiêu:
- Bất kỳ agent nào đọc cũng hiểu context
- Không mất thông tin giữa các sessions
- Lưu decisions, session history, current state

---

## What was done

### Claude
- Phân tích workflow hiện tại (CLAUDE.md + rules)
- Xác định giá trị Obsidian mang lại
- Đề xuất Phase 1: Sessions + Decisions

### Codex
- Tạo directory structure `docs/obsidian/`
- Tạo MOC (Map of Content)
- Tạo templates: Session Note, Decision Record
- Tạo note: Current State
- Cập nhật `~/.codex/AGENTS.md` với Obsidian Auto-Note Rule

---

## Decisions made

1. **Song song CLAUDE.md + Obsidian**
   - CLAUDE.md: project overview, roadmap
   - Obsidian: session history, decisions, state

2. **Auto-note sau mỗi task**
   - Codex tự động nhắc user tạo note
   - Default: YES (user có thể opt-out)

3. **Phase 1 tối giản**
   - Chỉ Sessions + Decisions
   - Không Canvas, Bases (chưa cần)

---

## Current state

| Status | Item |
|--------|------|
| ✅ | Vault structure created |
| ✅ | Templates created |
| ✅ | Auto-note rule added to AGENTS.md |
| ⏳ | First real task note — pending |

---

## Next steps

1. [ ] Test với task thật tiếp theo
2. [ ] Tạo session note sau task
3. [ ] Review sau 1 tuần

---

## Files changed

- `docs/obsidian/00-MOC.md`
- `docs/obsidian/templates/Session Note.md`
- `docs/obsidian/templates/Decision Record.md`
- `docs/obsidian/03-State/Current State.md`
- `docs/obsidian/01-Sessions/2026-03-11-obsidian-vault-setup.md`
- `~/.codex/AGENTS.md` (Auto-Note Rule)

---

## Agent handoff

**Recommended next agent:** Claude

**Pick up from here:**
- User có task mới → làm bình thường
- Task xong → Codex nhắc tạo Obsidian note
