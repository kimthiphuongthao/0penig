---
title: Legacy app team checklist presentation audit
tags:
  - openig
  - documentation
  - presentation
  - audit
  - sso
  - slo
date: 2026-04-02
status: done
---

# Legacy app team checklist presentation audit

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Redis]]

## Context

Task: audit `docs/deliverables/legacy-app-team-checklist.md` for stakeholder-presentation readiness against the current shared runtime baseline.

Reference sources used:

- `.claude/rules/architecture.md`
- `CLAUDE.md` Roadmap -> `Đã hoàn thành`
- `docs/deliverables/standard-gateway-pattern.md` v1.4 dated 2026-04-02

## What Done

- Read the checklist in full.
- Compared shared-infra framing, login mechanism coverage, handoff fields, and production-gap wording against the active shared runtime.
- Separated findings into outdated statements, missing current-state details, and sections that remain presentation-safe.

> [!success]
> Verdict: `NEEDS_UPDATE`

> [!warning]
> The checklist is still structurally strong and mostly current, but it now understates production transport requirements and presents LDAP too broadly relative to the validated 6-app baseline.

## Key Findings

- Shared-infra framing is directionally correct: one shared runtime, hostname routing on port 80, and legacy `stack-a` / `stack-b` / `stack-c` no longer presented as the active model.
- LDAP is listed as a normal handoff option without the qualifier now used in the gateway baseline: `Future pattern` and `Requires app-specific assessment; not part of the validated 6-app baseline`.
- The production/lab section does not yet include browser-facing HTTPS, `requireHttps: true`, `JwtSession` cookies marked `Secure`, or the explicit note that the current lab remains HTTP-only.
- The checklist does not surface the strongest current-state proof points for stakeholders: the shared runtime already fronts 6 apps and isolates them with route-local session heaps, host-only cookies, per-app Redis ACLs, and per-app Vault AppRoles.

## Decisions

- Keep `docs/deliverables/legacy-app-team-checklist.md` as the primary app-team intake document.
- Do not present it as-is as a current-state readiness summary until the LDAP qualifier and transport language are updated.

## Current State

- The responsibility split between app team and gateway team remains accurate.
- The requested app-team inputs for login, cookie, logout, and deployment constraints are still fit for gateway handoff.
- The browser validation checklist still matches the validated SSO/SLO behavior on the shared runtime.

## Next Steps

- Add one intro/shared-infra sentence that says the active shared runtime already serves 6 apps and relies on hostname routing on port 80.
- Add one isolation sentence covering `SessionApp1..6`, `IG_SSO_APP1..APP6`, per-app Redis ACL, and per-app Vault AppRole.
- Reword LDAP as `requires assessment; not part of the validated 6-app baseline`.
- Expand the production section with browser TLS, `requireHttps: true`, `JwtSession` `Secure` cookies, and the explicit HTTP-only lab exception wording from v1.4.

## Files Changed

- `docs/obsidian/how-to/2026-04-02-legacy-app-team-checklist-presentation-audit.md`
