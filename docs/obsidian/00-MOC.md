# SSO Lab — Obsidian Vault

**Project:** SSO/SLO integration with OpenIG 6 + Keycloak 24 + Vault + Redis
**Vault created:** 2026-03-11

---

## Entry Points

| Note | Purpose |
|------|---------|
| [[Current State]] | Hệ thống đang ở đâu, gì chạy/gì hỏng |
| [[Session Notes]] | Lịch sử làm việc theo ngày |
| [[Decision Records]] | Tại sao chọn X thay vì Y |

---

## Quick Links

- [[CLAUDE.md]] — Project overview, roadmap
- [[.claude/rules/workflow.md]] — AI agent workflow
- [[.claude/rules/architecture.md]] — Architecture patterns
- [[.claude/rules/gotchas.md]] — Known issues & decisions

---

## Folder Structure

```
docs/obsidian/
├── 00-MOC.md              # This file — entry point
├── 01-Sessions/           # Session notes (per day/task)
├── 02-Decisions/          # Decision records (why X not Y)
├── 03-State/              # Current system state
├── 04-Architecture/       # Diagrams, flows
├── 05-Components/         # OpenIG, Keycloak, Vault, Redis
├── 06-Runbooks/           # Restart, debug checklists
└── 07-External-Docs/      # Extracted docs from web
```

---

## Agent Instructions

**New agent reading this:**
1. Start with [[Current State]] — understand where we are
2. Check [[Session Notes]] — see what was done recently
3. Review [[Decision Records]] — understand why decisions made
4. Continue from there
