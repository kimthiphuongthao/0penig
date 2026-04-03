---
title: Standard Gateway Pattern Doc Audit
tags:
  - docs
  - audit
  - deliverables
  - openig
  - shared-infra
date: 2026-04-02
status: completed
---

# Standard Gateway Pattern Doc Audit

## Context

Reviewed the current deliverables against the active [[OpenIG]] shared-infra solution state:

- `docs/deliverables/standard-gateway-pattern.md` (EN v1.3, 2026-03-24)
- `docs/deliverables/standard-gateway-pattern-vi.md` (VI v1.2, 2026-03-19)

Reference state used:

- `.claude/rules/architecture.md`
- `CLAUDE.md` roadmap section `Đã hoàn thành`
- `docs/audit/2026-03-25-production-readiness-audit.md`

## What Was Checked

- Shared-infra baseline: `shared/`, 6 apps, hostname routing on port 80
- Per-app isolation: `SessionApp1..6`, `IG_SSO_APP1..APP6`, Redis ACL, Vault AppRole
- 2026-04-02 hardening fixes:
  - BUG-002 callback-path `proxy_next_upstream off`
  - AUD-003 JWKS null-cache fix + 60s failure backoff
  - DOC-007 callback-only fail-closed behavior in `TokenReferenceFilter.groovy`
  - AUD-009 standard `SloHandler.groovy` fail-closed behavior when `OPENIG_PUBLIC_URL` is missing

> [!success]
> EN v1.3 is mostly aligned with the active shared-infra architecture and already captures the major shared-runtime shape.

> [!warning]
> VI v1.2 is still anchored to the older 3-stack narrative and is not accurate enough to present as the current solution state.

## Findings

### EN doc

- Missing: explicit callback retry protection requirement for `/openid/app*/callback` on all 6 nginx vhosts. Current state fixed by BUG-002, but EN v1.3 does not document it.
- Missing: explicit JWKS cache hardening rule that only successful JWKS fetches are cached and repeated failures back off for 60s.
- Missing: explicit scope for DOC-007 that fail-closed on missing OAuth2 session keys is callback-path-only, not a rule for all authenticated requests.
- Missing: explicit fail-closed rule for standard `SloHandler.groovy` when `OPENIG_PUBLIC_URL` is absent.
- Repo-state inconsistency: `CLAUDE.md` marks AUD-009 complete, but active Jellyfin logout route `shared/openig_home/config/routes/00-jellyfin-logout.json` still uses `SloHandlerJellyfin.groovy`, and that helper still falls back to legacy `CANONICAL_ORIGIN_APP4` / `OPENIG_PUBLIC_URL` hostnames.
- Missing: current-state readiness framing. The solution is suitable for documented lab use, but the current audit still says not production-ready.

### VI doc

- Outdated baseline: derived-from metadata and narrative still describe the pre-shared-infra 3-stack evaluation model, not the active `shared/` 6-app runtime.
- Missing: EN v1.3 shared-infra deployment contract section and the per-app isolation model (`SessionApp1..6`, `IG_SSO_APP1..APP6`, per-app Redis ACL, per-app Vault AppRole, `CANONICAL_ORIGIN_APP1..6`).
- Missing: EN v1.3 security control status table and the shared-runtime control summary.
- Missing: all 2026-04-02 fix details now reflected in the audit state: BUG-002, AUD-003, DOC-007 callback-only scope, and the partially implemented AUD-009 logout-origin handling.
- Missing: EN v1.3 parameterized-template architecture section and the OpenIG 6.0.1 `args` binding rule.

## Current State

- Active runtime is `shared/`, not legacy `stack-a/`, `stack-b/`, `stack-c/`.
- Current architecture is lab-valid but not production-ready because transport, cookie hardening, session-race limits, and Redis payload protection remain open in the production-readiness audit.

> [!tip]
> If these docs will be shown to stakeholders as "current solution state", EN needs a small sync pass and VI needs a substantive rewrite from EN v1.3 plus the 2026-04-02 fixes.

## Files Changed

- Added this note only: `docs/obsidian/debugging/2026-04-02-standard-gateway-pattern-doc-audit.md`
- No gateway routes, Groovy scripts, nginx config, or runtime files were modified
