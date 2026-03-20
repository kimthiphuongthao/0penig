---
title: Step 6 Deliverables Sync Parameterized Templates
tags:
  - debugging
  - docs
  - step-6
  - openig
date: 2026-03-17
status: done
---

# Step 6 Deliverables Sync Parameterized Templates

## Context

Pattern Consolidation Steps 1-5 completed the live `[[OpenIG]]` script consolidation. Step 6 updated the deliverable set so the written architecture now matches the parameterized Groovy template model used across the stacks with `[[Keycloak]]`, `[[Vault]]`, and Redis-backed logout enforcement.

## What Done

- Added a new `Template-Based Integration` section to the legacy auth reference with the template catalogue, selection tree, and OpenIG 6.0.2 args-binding pattern.
- Updated the standard gateway pattern so Required Control 1 and Control 7 now point to the parameterized implementations used in the live stacks.
- Added an end-of-file architecture note in the standard gateway pattern linking readers back to the definitive template catalogue.
- Added an app-team-facing note clarifying that standard gateway integrations now use route JSON args instead of per-app Groovy edits.

> [!success]
> The three deliverable docs now describe the same integration model: per-stack template copies, per-route args configuration, and a separate `SloHandlerJellyfin.groovy` for the Jellyfin logout API case.

## Decisions

- Kept the new architecture summary in deliverables only; no historical sections were rewritten beyond the requested insertions.
- Preserved the explicit OpenIG 6.0.2 warning that route `args` bind as top-level Groovy variables, not as an `args` map.
- Left app-specific behavior limited to the existing Jellyfin variant instead of broadening the narrative to other hypothetical custom handlers.

> [!warning]
> Older documents may still describe pre-consolidation script names or copy-paste integration patterns unless they were part of this Step 6 sync scope.

## Current State

- `docs/deliverables/legacy-auth-patterns-definitive.md` now carries the authoritative template catalogue.
- `docs/deliverables/standard-gateway-pattern.md` now ties the control language directly to the parameterized implementation.
- `docs/deliverables/legacy-app-team-checklist.md` now tells app teams exactly which inputs they must provide for a standard integration.

> [!tip]
> For future integrations, start with the deliverable cross-reference first: template catalogue in the legacy auth reference, control rationale in the standard gateway pattern, and required app inputs in the app-team checklist.

## Next Steps

- Keep future deliverable updates anchored to the same date-stamped architecture milestones to reduce drift.
- If more app-specific handler variants appear, extend the template catalogue first so downstream guides stay aligned.

## Files Changed

- [[docs/deliverables/legacy-auth-patterns-definitive.md]]
- [[docs/deliverables/standard-gateway-pattern.md]]
- [[docs/deliverables/legacy-app-team-checklist.md]]
