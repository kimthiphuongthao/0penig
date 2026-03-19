---
title: Current State — SSO Lab
updated: 2026-03-19
tags: [state, status]
status: active
---

# Current State

Last updated: **2026-03-19**

---

## Live Status

| Stack | Apps | SSO | SLO |
|-------|------|-----|-----|
| **A** | WordPress, WhoAmI | ✅ login PASS | ✅ logout PASS |
| **B** | Redmine, Jellyfin | ✅ login PASS | ✅ logout PASS |
| **C** | Grafana, phpMyAdmin | ✅ login PASS | ✅ logout PASS |

> [!success]
> Phase 1 + Phase 2 `JwtSession` production pattern is now VALIDATED on `fix/jwtsession-production-pattern`. All three stacks passed full login+logout validation on 2026-03-19, and `BackchannelLogoutHandler` now supports `ES256` with EC JWKS reconstruction and `SHA256withECDSA`.

> [!success]
> Branch `fix/jwtsession-production-pattern` is ready to merge into `main`.

> [!warning]
> Remaining infra gap: Stack C phpMyAdmin is still effectively `alice`-only until MariaDB user `bob` is provisioned or that limitation is documented explicitly.

## Program State

- Pattern Consolidation: COMPLETE
- Phase 1 `JwtSession` restore: VALIDATED
- Phase 2 Redis Token Reference Pattern: VALIDATED
- BackchannelLogoutHandler ES256/EC support: COMPLETE
- Branch status: ready to merge `fix/jwtsession-production-pattern` -> `main`
- Remaining infra gap: Stack C phpMyAdmin `bob` user not provisioned in live MariaDB
- Gap report: [2026-03-17-production-readiness-gap-report.md](../../audit/2026-03-17-production-readiness-gap-report.md)
- Master backlog: [master-backlog.md](../../fix-tracking/master-backlog.md)
- Phase 2b final steps: COMPLETE — STEP-13+14 finished on 2026-03-18

## Recent Commits

- `d2eb8e9` — BackchannelLogoutHandler support EC/ES256 keys for JWKS lookup and signature verification
- `646a45a` — BackchannelLogoutHandler accept `ES256` in addition to `RS256`
- `47cbab9` — TokenReferenceFilter dynamic oauth2 session key discovery; `IG_SSO_C` shrunk to `849` chars
- `9b2d109` — Redis Token Reference Pattern
- `0454796` — Phase 1 restore `Session` heap + `ES256` + disable `refresh_token`
- `6cc3fc9` — Stack C recovery: Vault/AppRole refresh + phpMyAdmin secret realigned

## Last Verified

**2026-03-19** — Full login+logout validation PASS on Stack A, Stack B, and Stack C. `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C` were all present; TokenRef Store/Restore and backchannel Redis blacklist flows were validated end-to-end.

## Related Notes

- [[2026-03-19-jwtsession-phase-1-2-full-validation-pass]]
- [[2026-03-19-all-stacks-backchannel-logout-es256-acceptance]]
- [[2026-03-19-all-stacks-backchannel-logout-ec-es256-jwks-support]]
- [[Stack A]]
- [[Stack B]]
- [[Stack C]]
- [[OpenIG]]
- [[Keycloak]]
- [[Vault]]
- [[CLAUDE.md]] — full roadmap
