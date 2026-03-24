---
title: Tracking docs audit after hasPendingState fix
tags:
  - debugging
  - docs
  - openig
  - stack-c
date: 2026-03-24
status: done
---

# Tracking docs audit after hasPendingState fix

## Context

Session focus: `Fix Grafana SSO after SLO — shared infra`.

The shared runtime on [[OpenIG]] had already received commit `5fb549d`, which adds a `hasPendingState` guard in `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`. The goal of this follow-up was to align the tracking docs with the verified post-fix state instead of leaving the repo in the pre-verification "investigation still open" state.

## What was updated

- `.omc/plans/shared-infra.md`
  - Replaced the stale BUG-SSO2 prerequisite text with the confirmed mixed-state root cause and the shipped shared-infra fix.
  - Marked Step 1 and Step 2 items that are directly backed by repo/runtime evidence.
  - Left Step 4/5 items open where Redis ACL CLI checks, [[Vault]] isolation, or full validation replay were not re-confirmed in this audit.
- `CLAUDE.md`
  - Added completed roadmap milestones for the active shared runtime and the `hasPendingState` fix verification.
- `.memory/MEMORY.md`
  - Updated active branch to `feat/shared-infra`.
  - Marked the Grafana-after-SLO task as `DONE`.
  - Replaced the deferred BUG-SSO2 design note with the actual shared-infra fix outcome.
  - Moved the finished task into Completed and promoted packaging to the next queued task.

> [!success]
> Tracking docs now reflect that the shared fix is shipped and verified, while still leaving unverified shared-infra acceptance items open.

## Key decision

The shared-infra plan was updated as **partial**, not fully complete.

Reason:
- `cd shared && docker compose config` passes.
- Shared runtime artifacts are present and loading.
- `shared-openig-2` stayed clean after post-fix user SSO/SLO testing.
- But the audit did not rerun every Step 4 CLI isolation check, and committed seeded credentials still keep one Step 1 secret-hygiene item open.

> [!warning]
> Do not mark Shared Infrastructure Consolidation fully done until Redis ACL cross-access tests, [[Vault]] AppRole isolation checks, packaging, and final doc migration are re-verified.

## Current state

- Shared infra serves traffic through `shared-openig-1/2` with `shared/openig_home`.
- `stack-c-openig-c1-1` and `stack-c-openig-c2-1` are confirmed orphaned and stopped.
- The `TokenReferenceFilter.then()` mixed-state bug is documented as resolved in shared infra.
- Next queued task is packaging: OVA / Docker Compose bundle.

## Files changed

- `.omc/plans/shared-infra.md`
- `CLAUDE.md`
- `.memory/MEMORY.md`
- `docs/obsidian/debugging/2026-03-24-tracking-docs-post-haspendingstate-fix.md`

## Related notes

- [[OpenIG]]
- [[Keycloak]]
- [[Vault]]
- [[Stack C]]
- [[2026-03-24-session-state-before-compact]]
