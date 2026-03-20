---
title: Cross-Stack OpenIG Gap Implementation Plan
tags:
  - openig
  - security-review
  - implementation-plan
  - cross-stack
date: 2026-03-14
status: done
---

# Cross-Stack OpenIG Gap Implementation Plan

## Context

Built an implementation plan for fixing the reviewed gateway gaps across [[Stack A]], [[Stack B]], and [[Stack C]] using:

- `docs/reviews/2026-03-14-cross-stack-review-summary.md`
- `docs/reviews/2026-03-14-stack-a-openig-code-security-review.md`
- `docs/reviews/2026-03-14-stack-b-openig-code-security-review.md`
- `docs/reviews/2026-03-14-stack-c-openig-code-security-review.md`
- `docs/standard-gateway-pattern.md`

The task was planning only. No OpenIG route, Groovy, or app code changes were made.

> [!success] Output
> Produced a cross-stack remediation plan grouped by priority, with exact file targets, acceptance criteria, dependencies, investigation gates, and dangerous-change callouts.

## What Done

- Read the review summary and all three stack review documents end to end.
- Read the current OpenIG config and Groovy files referenced by those reviews to tighten file targeting and dependency mapping.
- Normalized findings into:
  - cross-stack fixes
  - stack-specific fixes
  - pre-implementation investigations
  - low-confidence findings
- Mapped execution order around [[OpenIG]], [[Keycloak]], [[Vault]], and Redis revocation behavior.

## Decisions

- Treated broken functionality first:
  - revocation correctness
  - fail-closed behavior
  - Stack B Jellyfin logout namespace mismatch
- Kept secret externalization as a separate rollout batch because rotation and session invalidation can disrupt active users across all stacks.
- Flagged single-review findings separately:
  - Stack A validation-only additions
  - Stack B `F6` and `F11`
- Treated Stack C findings as source-backed but without internal reviewer cross-corroboration.

> [!warning] Dangerous changes
> The plan calls out several high-risk rollout areas: fail-closed revocation, `JwtSession` secret rotation, pinned public-origin changes, HTTPS enforcement, and moving adapter state out of browser-bound session storage.

## Current State

- There is now a single implementation plan covering all gaps called out in the March 14 review set.
- The plan distinguishes:
  - must-fix cross-stack security contract issues
  - Stack A adapter edge cases needing validation
  - Stack B session-namespace and token-storage issues
  - Stack C route wiring and browser-session boundary issues

## Next Steps

- Start with design and investigation gates before any code edits:
  - fail-closed UX choice
  - secret source and rotation workflow
  - server-side adapter state design for [[Vault]] and downstream credentials
  - Stack B `app3` versus `app4` route/namespace audit
- Execute revocation fixes before transport and adapter hardening.
- Run logout and revocation regression tests per stack after each batch.

> [!tip] Best practice
> Keep route JSON, SLO handlers, blacklist filters, and adapter filters in the same review batch. Most high-risk bugs here come from drift between config and scripts, not from isolated logic errors.

## Files Changed

- `docs/obsidian/debugging/2026-03-14-cross-stack-openig-gap-implementation-plan.md`
