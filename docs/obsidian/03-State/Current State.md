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
| **C** | Grafana, phpMyAdmin | ✅ Grafana + phpMyAdmin OK | ✅ Grafana + phpMyAdmin OK |

> [!success]
> Pattern Consolidation is COMPLETE across all 6 steps. Post-audit fixes and Phase 2 STEP-01/02/03 are also applied: `PhpMyAdminCookieFilter.groovy` deleted (`20d523f`), Stack C OIDC secrets rotated (`37672ed`), and compose secrets moved into gitignored `.env` files while OpenIG stays pinned to `6.0.1` (`b738577`).

> [!warning]
> Production-readiness audit is COMPLETE, but the lab is NOT READY yet: `37 STILL OPEN`, `6 PARTIAL`, `38 RESOLVED`.

> [!warning]
> Update 2026-03-18: Stack C Grafana SSO/SLO re-validation passed. The prior APP5 padding theory was superseded; OpenIG `OAuth2ClientFilter` does not URL-encode `client_secret`, so APP5 now uses a strong alphanumeric-only secret and both Stack C OpenIG containers were recreated.

## Program State

- Pattern Consolidation: COMPLETE
- Production readiness audit: COMPLETE — NOT READY
- Gap report: [2026-03-17-production-readiness-gap-report.md](../../audit/2026-03-17-production-readiness-gap-report.md)
- Master backlog: [master-backlog.md](../../fix-tracking/master-backlog.md)
- Active follow-up: P1 production-readiness backlog (`H-4/S-2`, `H-7/A-1`, `A-6/A-7/M-13/S-17`)

## Recent Commits

- `b738577` — STEP-03 secrets externalized to `.env` + OpenIG pinned to `6.0.1`
- `37672ed` — STEP-02 Stack C OIDC secret rotation
- `20d523f` — STEP-01 dead `PhpMyAdminCookieFilter.groovy` deleted

## Last Verified

**2026-03-18** — Stack C Grafana SSO/SLO re-validation PASS after rotating APP5 to a strong alphanumeric-only secret and recreating both Stack C OpenIG containers

## Related Notes

- [[Stack A]]
- [[Stack B]]
- [[Stack C]]
- [[OpenIG]]
- [[Keycloak]]
- [[Vault]]
- [[CLAUDE.md]] — full roadmap
