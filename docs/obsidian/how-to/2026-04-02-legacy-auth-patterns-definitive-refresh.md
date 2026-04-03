---
title: Legacy auth patterns definitive refresh
tags:
  - openig
  - documentation
  - deliverables
  - architecture
  - sso
  - slo
date: 2026-04-02
status: completed
---

# Legacy auth patterns definitive refresh

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Redis]] [[2026-04-02-standard-gateway-pattern-en-v1-4-update]]

## Context

Task: refresh `docs/deliverables/legacy-auth-patterns-definitive.md` with the current shared-infra baseline from `.claude/rules/architecture.md` and the validated control language from `docs/deliverables/standard-gateway-pattern.md`.

Scope:

- qualify LDAP as a future-only pattern for the validated 6-app baseline
- replace the outdated Jellyfin logout wording that implied hardcoded values
- replace the older token-storage and auto-refresh wording with the Redis token-reference baseline
- add the missing shared-infra routing matrix, `tokenRefKey` requirements, `VaultCredentialFilter.groovy`, and 2026-03-31 implementation corrections

## What Done

- Added the LDAP future-pattern qualifier in the `Executive Summary` and `Mechanism 5: LDAP Authentication` sections.
- Inserted `## Current Shared-Infra Baseline` after the executive summary with the 6-app routing matrix: hostname, `clientEndpoint`, Keycloak client, session heap, and cookie name.
- Added the shared-infra controls paragraph covering unique `clientEndpoint`, per-app `tokenRefKey` (`token_ref_id_app1..6`), host-only cookies `IG_SSO_APP1..APP6`, Redis ACL users `openig-app1..6`, Vault AppRoles `openig-app1..6`, `CANONICAL_ORIGIN_APP1..6`, and `StripGatewaySessionCookies.groovy`.
- Replaced the old token-based SSO sentence with the current `TokenReferenceFilter.groovy` Redis offload model and explicitly removed the earlier refresh-token auto-renewal model from the validated baseline.
- Updated the `Template-Based Integration` section so `TokenReferenceFilter`, `SloHandler`, and `SloHandlerJellyfin` all call out the required unique per-app `tokenRefKey`.
- Replaced the Jellyfin template wording with the current parameterized route-args description and added `VaultCredentialFilter.groovy` to the available-template list.
- Added `### Implementation Corrections (2026-03-31)` with `BUG-002`, `AUD-003`, `DOC-007`, and `AUD-009`.
- Staged and committed only the deliverable file with commit `49e7648`.

> [!success]
> `docs/deliverables/legacy-auth-patterns-definitive.md` now reflects the active 6-app shared runtime instead of the older generalized wording around LDAP, token refresh, and Jellyfin logout handling.

> [!tip]
> The new baseline section is intentionally short and placed near the top so reviewers can reconcile the rest of the document against the current hostname-routed shared runtime before reading mechanism-by-mechanism details.

> [!warning]
> The Obsidian note was created after the requested docs commit so the Git commit stayed limited to `docs/deliverables/legacy-auth-patterns-definitive.md`.

## Decisions

- Keep the new shared-infra baseline as a standalone top-level section because the rest of the document still compares general legacy mechanisms, while the active lab baseline is an implementation-specific constraint set.
- Keep the LDAP rows in place and qualify them with an explicit note instead of rewriting the verdict table, so the document still preserves the cross-source research consensus while clarifying the current lab scope.
- Put the 2026-03-31 fixes inside `Template-Based Integration` because they alter how callback, logout, and token-reference templates behave in the active runtime.

## Current State

- `docs/deliverables/legacy-auth-patterns-definitive.md` now aligns with the shared-runtime routing, isolation, and token-reference model used across all 6 apps.
- The document explicitly distinguishes current validated patterns from future LDAP work and older refresh-token assumptions.
- Git commit for the deliverable update: `49e7648`.

## Next Steps

- Mirror the same shared-infra baseline clarifications into any presentation or translated legacy-auth deliverables that still describe LDAP or token refresh as part of the active validated baseline.
- If more shared-runtime corrections land, append them as dated implementation notes instead of silently changing the template descriptions.

## Files Changed

- `docs/deliverables/legacy-auth-patterns-definitive.md`
- `docs/obsidian/how-to/2026-04-02-legacy-auth-patterns-definitive-refresh.md`
