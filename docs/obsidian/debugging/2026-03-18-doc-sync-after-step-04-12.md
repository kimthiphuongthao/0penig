---
title: 2026-03-18 doc sync after STEP-04-12
tags:
  - debugging
  - docs
  - obsidian
  - stack-b
  - audit
date: 2026-03-18
status: complete
---

# 2026-03-18 doc sync after STEP-04-12

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack B]] [[Stack C]]

## Context

- After STEP-04 (`8c11916`) and STEP-05..12 (`ecbca5d`), four documentation files still reflected the 2026-03-17 audit snapshot instead of the current post-hardening baseline.
- The stale items were concentrated in three places: [[Stack B]] runtime notes, app-team deployment guidance, and the live state/progress summaries used as the session snapshot.

## What updated

- `docs/obsidian/stacks/stack-b.md`: added missing notes for STEP-07, STEP-08, STEP-11, and STEP-12; corrected `app3 -> Redmine` and `app4 -> Jellyfin`; removed the stale Redmine host port exposure and resolved `cookieDomain` warning.
- `docs/deliverables/legacy-app-team-checklist.md`: documented the Linux Docker `extra_hosts: host.docker.internal:host-gateway` requirement for all [[OpenIG]] services.
- `docs/obsidian/03-State/Current State.md`: updated the date, audit scorecard, active follow-up items, and recent commit list so the live snapshot matches the gap report through STEP-12.
- `docs/progress.md`: added the 2026-03-18 completion line for STEP-04..12 and replaced the stale `Next` pointer with the remaining Phase 2b items.

> [!success]
> The state snapshot and deliverable docs are now aligned with the post-audit hardening baseline through STEP-12.

> [!warning]
> Remaining follow-up should stay limited to the real open backlog only: STEP-13 cookie flags and STEP-14 non-root [[OpenIG]] user.

## Current state

- Audit scorecard now matches the current gap report: `55 RESOLVED`, `6 PARTIAL`, `20 STILL OPEN`.
- [[Stack B]] notes now match runtime config: no Redmine host port publishing, `cookieDomain: ".sso.local"` is present, and Linux portability guidance is explicitly documented.
- The app-team checklist now carries the `host.docker.internal` portability requirement that Docker Desktop masked during local testing.

## Files changed

- `docs/obsidian/stacks/stack-b.md`
- `docs/deliverables/legacy-app-team-checklist.md`
- `docs/obsidian/03-State/Current State.md`
- `docs/progress.md`

> [!tip]
> Keep future doc-sync commits atomic with backlog and state-note updates whenever one STEP closes multiple audit findings in a single batch.
