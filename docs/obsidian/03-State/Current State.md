---
title: Current State — SSO Lab
updated: 2026-03-11
tags: [state, status]
---

# Current State

Last updated: **2026-03-11**

---

## Working ✅

| Stack | Apps | SSO | SLO |
|-------|------|-----|-----|
| **A** | WordPress, WhoAmI | ✅ | ✅ |
| **B** | Redmine, Jellyfin | ✅ | ⚠️ |
| **C** | Grafana, phpMyAdmin | ✅ | ✅ |

---

## Partially Working ⚠️

| Issue | Stack | Impact |
|-------|-------|--------|
| Jellyfin SLO not tested | B | Logout không sync |
| Nginx stickiness bug | B | Session không consistent |

---

## Broken ❌

| Issue | Stack | Fix pending |
|-------|-------|-------------|
| Cross-stack SLO | All | Redis sync |
| Jellyfin WebSocket | B | `http://` → `ws://` |

---

## Pending Tasks

From [[CLAUDE.md]]:

- [ ] SLO test thủ công: stack-b, stack-c
- [ ] Cross-stack SLO test
- [ ] Fix Jellyfin WebSocket
- [ ] Stack B cookieDomain (LOW)

---

## Last Verified

**2026-03-11** — Stack A login/logout tested successfully

---

## Related Notes

- [[Session Notes]] — what was done recently
- [[Decision Records]] — why architecture choices
- [[CLAUDE.md]] — full roadmap
