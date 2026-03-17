---
title: Current State — SSO Lab
updated: 2026-03-17
tags: [state, status]
status: active
---

# Current State

Last updated: **2026-03-17**

---

## Live Status

| Stack | Apps | SSO | SLO |
|-------|------|-----|-----|
| **A** | WordPress, WhoAmI | ✅ | ✅ |
| **B** | Redmine, Jellyfin | ✅ | ✅ |
| **C** | Grafana, phpMyAdmin | ⚠️ Grafana pending fix; phpMyAdmin OK | ⚠️ Grafana login re-test pending |

> [!success]
> Pattern Consolidation is COMPLETE across all 6 steps. Post-audit fixes and Phase 2 STEP-01/02/03 are also applied: `PhpMyAdminCookieFilter.groovy` deleted (`20d523f`), Stack C OIDC secrets rotated (`37672ed`), and compose secrets moved into gitignored `.env` files while OpenIG stays pinned to `6.0.1` (`b738577`).

> [!warning]
> Production-readiness audit is COMPLETE, but the lab is NOT READY yet: `37 STILL OPEN`, `6 PARTIAL`, `38 RESOLVED`.

> [!warning]
> Current blocker: Stack C Grafana SSO is under investigation. Working session finding: `OIDC_CLIENT_SECRET_APP5` must match exactly across `stack-c/.env`, Keycloak, and the running OpenIG containers; trailing `=` padding is significant for Base64 secrets.

## Program State

- Pattern Consolidation: COMPLETE
- Production readiness audit: COMPLETE — NOT READY
- Gap report: [2026-03-17-production-readiness-gap-report.md](../../audit/2026-03-17-production-readiness-gap-report.md)
- Master backlog: [master-backlog.md](../../fix-tracking/master-backlog.md)
- Active follow-up: Stack C Grafana secret-sync fix + P1 production-readiness backlog

## Recent Commits

- `b738577` — STEP-03 secrets externalized to `.env` + OpenIG pinned to `6.0.1`
- `37672ed` — STEP-02 Stack C OIDC secret rotation
- `20d523f` — STEP-01 dead `PhpMyAdminCookieFilter.groovy` deleted

## Last Verified

**2026-03-17** — Stacks A and B verified working for SSO/SLO; Stack C phpMyAdmin verified; Grafana SSO currently pending re-validation after APP5 secret-sync debugging

## Related Notes

- [[Stack A]]
- [[Stack B]]
- [[Stack C]]
- [[OpenIG]]
- [[Keycloak]]
- [[Vault]]
- [[CLAUDE.md]] — full roadmap
