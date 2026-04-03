---
title: Legacy Auth Patterns Presentation Audit
tags:
  - audit
  - docs
  - deliverables
  - openig
  - keycloak
  - vault
date: 2026-04-02
status: completed
---

# Legacy Auth Patterns Presentation Audit

## Context

Reviewed `docs/deliverables/legacy-auth-patterns-definitive.md` for stakeholder presentation readiness against the current shared-infra baseline documented in [[OpenIG]] deliverables and project architecture notes.

Files reviewed:

- `docs/deliverables/legacy-auth-patterns-definitive.md`
- `.claude/rules/architecture.md`
- `CLAUDE.md`
- `docs/deliverables/standard-gateway-pattern.md`

## What Was Verified

- Current active runtime is `shared/` with hostname routing on port 80 for 6 apps.
- Active topology is `shared-nginx` + `shared-openig-1/2` + `shared-redis` + `shared-vault`.
- Current validated login mechanisms in the 6-app baseline are form, trusted header, token, and HTTP Basic.
- [[Vault]] and Redis isolation are per app via AppRoles and ACL users.
- [[Keycloak]] SLO baseline is RP-initiated logout plus backchannel logout with JWT validation.

> [!warning] Presentation blockers
> The legacy-auth document is mostly accurate as a mechanism taxonomy, but it still overstates LDAP as part of the validated baseline and it underspecifies the current shared-runtime contract that stakeholders will expect to see.

## Findings

### Outdated

- LDAP is presented as a verified current mechanism even though the validated 6-app baseline does not include LDAP; current state keeps LDAP as a future pattern only.
- The current-state banner is too loose for presentation use because it does not spell out the active shared topology and HA shape.
- The token-based section still says the gateway stores app token state and auto-refreshes it; the current baseline uses Redis token-reference offload and the earlier refresh-token model is no longer the pattern to present.
- The template section still anchors on the 2026-03-17 consolidation snapshot and includes a mutable-tag note (`latest=6.0.2`) that should be replaced by the stable rule: pin OpenIG to `6.0.1`.
- `SloHandlerJellyfin.groovy` is described as hardcoded even though the current script is parameterized with route args and per-app Redis/token-ref settings.

### Missing

- A 6-app current-state mapping table that ties each live app to hostname, auth pattern, Keycloak client, session heap, and cookie.
- A shared-infra control summary covering unique `clientEndpoint`, per-app `tokenRefKey`, host-only `IG_SSO_APP1..APP6`, Redis ACL isolation, Vault AppRole isolation, and pinned canonical origins.
- `VaultCredentialFilter.groovy` in the available template list.
- The `tokenRefKey` requirement in the template args/checklist for `TokenReferenceFilter`, `SloHandler`, and `SloHandlerJellyfin`.
- The validated 2026-03-31 implementation corrections from the current gateway pattern (`BUG-002`, `AUD-003`, `DOC-007`, `AUD-009`).

> [!success] Still solid
> The generic mechanism definitions for form, HTTP Basic, trusted-header auth, logout taxonomy, and the core security risk checklist remain usable with only targeted current-state corrections.

## Current State

- [[OpenIG]] shared runtime fronts all 6 apps through hostname routing on port 80.
- [[Vault]] and Redis are shared services with per-app isolation, not per-stack isolation.
- Current validated baseline is a shared-infra reference architecture, not just a research synthesis.

## Next Steps

1. Update the executive summary so it distinguishes generic mechanism coverage from the validated 6-app baseline.
2. Add one shared-infra crosswalk section near the top using the same terminology as `standard-gateway-pattern.md`.
3. Refresh the template section so it matches the current route-arg and token-ref contract before presenting to stakeholders.
