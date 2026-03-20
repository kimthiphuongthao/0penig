---
title: Docs Audit Second Pass After Pattern Consolidation
tags:
  - debugging
  - docs
  - openig
  - sso
date: 2026-03-17
status: done
---

# Docs Audit Second Pass After Pattern Consolidation

## Context

Second-pass end-of-session docs audit after Pattern Consolidation Steps 3 and 4 completed on 2026-03-17.

- Step 1+2: `SessionBlacklistFilter` `6 -> 1` (`a76e194`, `832bbae`)
- Step 3: `BackchannelLogoutHandler` `3 -> 1` (`4d8f065`)
- Step 4: `SloHandler` `5 -> 2` (`3b8a6d8`)

Related systems: [[OpenIG]], [[Keycloak]], [[Vault]], [[Stack C]]

## What Was Checked

- All requested markdown files under `docs/deliverables/`, `docs/testing/`, `docs/reference/`, `docs/fix-phase/`, `docs/audit/2026-03-16-pre-packaging-audit/`, plus root `docs/progress.md`
- Target terms: `BackchannelLogout`, `SloHandler`, `SessionBlacklist`, `JWKS`, `try.catch`, `C-1`, `H-1`, `H-2`, `H-3`, `H-6`, `H-9`, `M-2`, `M-14`, `pattern consolidation`, `Step 3`, `Step 4`
- Requested recursive grep cross-check over `docs/**/*.md`

## Stale Items Fixed

> [!success]
> Current-state docs were updated where they still described pre-consolidation or pre-timeout-reduction behavior.

- `docs/deliverables/audit-auth-patterns.md`
  - Replaced stale `EX 3600` blacklist TTL references with current `EX 1800`
  - Marked Jellyfin logout coherence as resolved after FIX-01 and Step 4
- `docs/deliverables/standalone-legacy-app-integration-guide.md`
  - Corrected Stack B `app3`/`app4` backchannel and OIDC namespace mapping
  - Updated Redis blacklist example from `EX 3600` to `EX 1800`
- `docs/deliverables/sso-workflow-security.md`
  - Replaced stale H9 TTL/durability narrative with current aligned TTL + AOF persistence state
  - Removed outdated fail-open orphan-session wording for `SessionBlacklistFilter`
- `docs/reference/why-redis-slo.md`
  - Updated explainer examples from `EX 3600` to `EX 1800`
- `docs/fix-phase/checklist.md`
  - Clarified that FIX-02 originally raised TTL to `28800`, but current live state is `1800` after `9cbf71a`
- Audit docs updated to reflect completed consolidation:
  - `00-executive-summary.md`
  - `03-custom-groovy-gap-analysis.md`
  - `05-code-quality-review.md`
  - `07-consolidated-action-items.md`

## Evidence Chain

- `a76e194`, `832bbae`: `SessionBlacklistFilter` consolidation
- `4d8f065`: `BackchannelLogoutHandler` consolidation + JWKS race / TTL-unit fixes
- `3b8a6d8`: `SloHandler` consolidation + try-catch fix
- `9cbf71a`: session timeout reduced to `30 minutes`, blacklist TTL aligned to `1800`

## Current State

> [!tip]
> The most important documentation distinction after this pass is between:
> 1. historical audit snapshots, and
> 2. current live state after Steps 1-4.

- Historical audit numbers remain preserved where the file is explicitly an audit snapshot
- Current-state operational docs now describe:
  - Stack B as Redmine `/openid/app3` and Jellyfin `/openid/app4`
  - blacklist TTL as `1800`, aligned to `JwtSession.sessionTimeout`
  - `BackchannelLogoutHandler`, `SessionBlacklistFilter`, and `SloHandler` consolidation work as completed

## Files Changed

- `docs/deliverables/audit-auth-patterns.md`
- `docs/deliverables/standalone-legacy-app-integration-guide.md`
- `docs/deliverables/sso-workflow-security.md`
- `docs/reference/why-redis-slo.md`
- `docs/fix-phase/checklist.md`
- `docs/audit/2026-03-16-pre-packaging-audit/00-executive-summary.md`
- `docs/audit/2026-03-16-pre-packaging-audit/03-custom-groovy-gap-analysis.md`
- `docs/audit/2026-03-16-pre-packaging-audit/05-code-quality-review.md`
- `docs/audit/2026-03-16-pre-packaging-audit/07-consolidated-action-items.md`

## Next Steps

> [!warning]
> The recursive grep over `docs/` also surfaced matches in `docs/historical/`, `docs/reviews/`, and existing `docs/obsidian/` notes. They were outside the explicit file scope for this pass.

- If needed, run a follow-up audit pass over `docs/historical/`, `docs/reviews/`, and `docs/obsidian/`
- Keep any future session-timeout changes coupled with blacklist TTL updates
