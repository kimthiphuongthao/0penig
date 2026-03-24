---
title: Shared infra test coverage correction for 2026-03-24 partial validation
tags:
  - debugging
  - documentation
  - openig
  - sso-lab
date: 2026-03-24
status: completed
---

# Shared infra test coverage correction

## Context

The shared infra plan for [[OpenIG]] had Step 3 and Step 4 acceptance items marked complete beyond the evidence captured on 2026-03-24.

Actual evidence from that session was limited to:

- One random app tested on `shared-openig-2`
- `shared-openig-2` showed `0` `ERROR`
- The `hasPendingState` fix from commit `5fb549d` did not introduce the earlier mixed-state failure

## Root cause

> [!warning]
> Runtime cleanliness and one-app smoke testing were recorded as if they proved the full per-app SSO/SLO matrix for [[Stack A]], [[Stack B]], and [[Stack C]].

- Log evidence can confirm route load and absence of known runtime failures.
- Log evidence cannot by itself confirm each app's login flow, logout flow, or cross-app logout behavior.

## What was changed

- Added the same partial-test note to Step 3 and Step 4 in `.omc/plans/shared-infra.md`
- Reverted Step 3 app-behavior claims to unchecked:
  - WordPress SSO login
  - WhoAmI SSO login
  - WordPress SLO logout
  - Cross-app SLO
- Reverted Step 4 full-matrix claims to unchecked:
  - All 6 apps SSO login
  - All 6 apps SLO logout
  - Cross-app SLO
- Kept only the items that are supported by logs/runtime evidence:
  - `docker compose up -d` starts without errors
  - All routes loaded
  - TokenReferenceFilter store/restore works for all 6 apps

## Decision

> [!tip]
> Treat partial runtime checks as regression evidence only. Do not mark app-matrix acceptance complete until the exact app and outcome were observed and recorded.

- Use logs to support stability claims.
- Use explicit app-by-app validation to support SSO/SLO completion claims.

## Current state

> [!success]
> The shared infra plan now matches the actual 2026-03-24 evidence. `5fb549d` remains validated on `shared-openig-2`, while the full per-app SSO/SLO matrix is still pending.

## Next steps

- Re-run Step 3 validation explicitly for [[Stack A]]
- Re-run Step 4 validation explicitly for [[Stack B]] and [[Stack C]]
- Record which app was tested, which user was used, and whether login, logout, and cross-app logout were observed

## Files changed

- `.omc/plans/shared-infra.md`
- `docs/obsidian/debugging/2026-03-24-shared-infra-test-coverage-correction.md`
