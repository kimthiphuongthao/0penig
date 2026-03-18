---
title: Current State — SSO Lab
updated: 2026-03-18
tags: [state, status]
status: active
---

# Current State

Last updated: **2026-03-18**

---

## Live Status

| Stack | Apps | SSO | SLO |
|-------|------|-----|-----|
| **A** | WordPress, WhoAmI | ✅ | ✅ |
| **B** | Redmine, Jellyfin | ✅ | ✅ |
| **C** | Grafana, phpMyAdmin | ✅ Grafana + phpMyAdmin OK | ✅ Grafana + phpMyAdmin OK |

> [!success]
> Pattern Consolidation is COMPLETE across all 6 steps. Post-audit fixes are now synced through Phase 2 STEP-14: STEP-01 dead-code cleanup (`20d523f`), STEP-02 Stack C OIDC secret rotation (`37672ed`), STEP-03 secret externalization + OpenIG pin (`b738577`), STEP-04 Redis auth (`8c11916`), STEP-05..12 Phase 2b hardening (`ecbca5d`), STEP-13 nginx cookie `SameSite=Lax` flags, and STEP-14 OpenIG non-root macOS exception comments.

> [!warning]
> Production-readiness audit is COMPLETE, but the lab is NOT READY yet: `18 STILL OPEN`, `7 PARTIAL`, `56 RESOLVED`.

> [!warning]
> Update 2026-03-18: Stack C Grafana SSO/SLO re-validation passed. The prior APP5 padding theory was superseded; OpenIG `OAuth2ClientFilter` does not URL-encode `client_secret`, so APP5 now uses a strong alphanumeric-only secret and both Stack C OpenIG containers were recreated.

## Program State

- Pattern Consolidation: COMPLETE
- Production readiness audit: COMPLETE — NOT READY
- Gap report: [2026-03-17-production-readiness-gap-report.md](../../audit/2026-03-17-production-readiness-gap-report.md)
- Master backlog: [master-backlog.md](../../fix-tracking/master-backlog.md)
- Phase 2b final steps: COMPLETE — STEP-13+14 finished on 2026-03-18

## Recent Commits

- `ecbca5d` — STEP-05..12 Phase 2b hardening batch
- `8c11916` — STEP-04 Redis authentication across all 3 stacks
- `a403b3d` — Stack C Grafana SSO fix: alphanumeric-only APP5 secret + container recreate
- `b738577` — STEP-03 secrets externalized to `.env` + OpenIG pinned to `6.0.1`
- `37672ed` — STEP-02 Stack C OIDC secret rotation
- `20d523f` — STEP-01 dead `PhpMyAdminCookieFilter.groovy` deleted

## Last Verified

**2026-03-18** — STEP-13+14 complete, Stack C Grafana SSO/SLO re-validation remains PASS, and the live scorecard is now `56 RESOLVED / 7 PARTIAL / 18 STILL OPEN`

## Related Notes

- [[Stack A]]
- [[Stack B]]
- [[Stack C]]
- [[OpenIG]]
- [[Keycloak]]
- [[Vault]]
- [[CLAUDE.md]] — full roadmap
