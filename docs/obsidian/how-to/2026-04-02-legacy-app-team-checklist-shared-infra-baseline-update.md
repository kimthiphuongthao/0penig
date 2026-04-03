---
title: Legacy app team checklist shared infra baseline update
tags:
  - openig
  - documentation
  - shared-infra
  - transport
  - ldap
  - sso
  - slo
date: 2026-04-02
status: done
---

# Legacy app team checklist shared infra baseline update

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Redis]]

## Context

Task: apply a targeted update to `docs/deliverables/legacy-app-team-checklist.md` so the app-team handoff doc matches the current shared-infra baseline described in `docs/deliverables/standard-gateway-pattern.md` v1.4.

## What Done

- Added the active shared runtime statement to Section 1: one nginx, two OpenIG nodes, one Redis, and one Vault serving all 6 apps on port 80 via hostname routing.
- Added the per-app isolation statement to Section 1: route-local `JwtSession` heaps, host-only `IG_SSO_APP1..APP6` cookies, per-app Redis ACL users, and per-app Vault AppRoles.
- Requalified LDAP in Sections 2.2, 2.3, and 4 as a future pattern that requires app-specific assessment and is not part of the validated 6-app baseline.
- Expanded Section 5 with production transport controls: browser-facing HTTPS, `Secure` session cookies, and `requireHttps: true`, plus the explicit HTTP-only lab exception.
- Committed the checklist change as `c308071`.

> [!success]
> The checklist now reflects the active shared runtime and no longer presents LDAP or production transport hardening too loosely.

> [!warning]
> The lab remains HTTP-only. The document now states clearly that this is a lab exception and must be remediated before production use.

## Decision Rationale

- Keep the checklist structure unchanged because the task required a targeted content correction, not a rewrite.
- Mirror the validated language from the gateway baseline so app-team documentation and gateway-team reference documentation do not drift.

## Current State

- `docs/deliverables/legacy-app-team-checklist.md` is aligned with the current shared runtime summary used for stakeholder-facing deliverables.
- LDAP remains visible as an option, but now with the correct future-pattern qualifier.
- Production transport expectations are explicit for browser traffic and OpenIG runtime behavior.

## Files Changed

- `docs/deliverables/legacy-app-team-checklist.md`
- `docs/obsidian/how-to/2026-04-02-legacy-app-team-checklist-shared-infra-baseline-update.md`
