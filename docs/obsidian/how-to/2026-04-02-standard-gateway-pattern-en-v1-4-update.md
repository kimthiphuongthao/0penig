---
title: Standard gateway pattern EN v1.4 update
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

# Standard gateway pattern EN v1.4 update

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Redis]] [[Stack C]] [[2026-04-02-standard-gateway-pattern-en-vi-gap-analysis]]

## Context

Task: update `docs/deliverables/standard-gateway-pattern.md` from v1.3 to v1.4 using `.claude/rules/architecture.md` as the routing and isolation source of truth, plus the existing EN/VI gap-analysis note.

Scope:

- add the missing per-app routing and session matrix
- add the missing per-app Vault AppRole and Redis ACL mapping tables
- document the recent implementation corrections from 2026-03-31
- keep the existing document structure intact

## What Done

- Bumped the deliverable header to version `1.4` and date `2026-04-02`.
- Inserted the per-app routing and isolation matrix into the existing `1. Session isolation` section.
- Inserted the per-app Redis ACL mapping into `2. Redis revocation and token-reference contract`.
- Inserted the per-app Vault AppRole mapping into `3. Vault secret model`.
- Added `Implementation corrections (2026-03-31)` as a new end-of-document section covering `BUG-002`, `AUD-003`, `DOC-007`, and `AUD-009`.
- Staged and committed only the deliverable file with commit `9a8b3ce`.

> [!success]
> The English deliverable now includes the concrete per-app routing, cookie, Redis, and Vault isolation data that was previously present only in `.claude/rules/architecture.md`.

> [!tip]
> This update keeps the document presentation-friendly by inserting tables into existing control sections instead of splitting the doc into a new appendix.

> [!warning]
> The Obsidian note was created after the docs commit so the requested Git commit stayed limited to `docs/deliverables/standard-gateway-pattern.md`.

## Decisions

- Keep the routing matrix under `Session isolation` because `clientEndpoint`, session heap, and cookie name are part of the shared-runtime isolation contract, not just deployment inventory.
- Keep Redis and Vault mappings in their existing control sections so the document remains readable as an implementation contract.
- Record the 2026-03-31 fixes in the deliverable itself because they change the effective behavior of callback, logout, and token-reference handling in the active runtime.

## Current State

- `docs/deliverables/standard-gateway-pattern.md` is now aligned with the active shared-runtime routing and isolation model from `.claude/rules/architecture.md`.
- The document still reads as an engineering reference, but it now exposes the app-by-app mappings that were previously missing for reviewers and operators.
- Git commit for the deliverable update: `9a8b3ce`.

## Next Steps

- Mirror the same missing shared-runtime details into `docs/deliverables/standard-gateway-pattern-vi.md` instead of continuing the older 3-stack framing.
- If a presentation variant is needed, derive it from the updated EN v1.4 doc rather than the older VI document.
- If more implementation fixes land, keep appending them as dated correction notes instead of silently changing control text.

## Files Changed

- `docs/deliverables/standard-gateway-pattern.md`
- `docs/obsidian/how-to/2026-04-02-standard-gateway-pattern-en-v1-4-update.md`
