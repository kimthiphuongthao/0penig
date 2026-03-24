---
title: Shared Stack
tags:
  - sso
  - stack-shared
  - openig
  - planning
  - validation
date: 2026-03-24
status: active
---

# Shared Stack

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

Context: captured the shared-infrastructure consolidation plan from the task transcript and saved the extracted markdown as `.omc/plans/shared-infra.md`.

Primary output: `.omc/plans/shared-infra.md`

## What Was Done

- Extracted only the markdown plan content starting at `# Shared Infrastructure Consolidation Plan` from the task output transcript.
- Wrote the extracted content to `.omc/plans/shared-infra.md`.
- Verified the extracted file matches the decoded source payload byte-for-byte.

> [!success] Confirmed artifact
> The workspace now contains the shared-infrastructure plan as a standalone markdown file, ready for execution or review without transcript noise.

## Key Decisions Captured

- The plan is structured into 5 phases: foundation, Groovy adaptation, Stack A validation, Stack B+C validation, and cleanup.
- [[Vault]] isolation is per-app via 6 AppRoles with scoped secret paths.
- Redis isolation is per-app via 6 ACL users with prefixes `app1:*` through `app6:*`.
- [[OpenIG]] remains a shared HA cluster, with the plan explicitly preserving per-app token reference isolation and isolated validation criteria across all 6 apps.

> [!warning] Scope boundary
> This task only captured the plan artifact. It did not implement any gateway, Vault, Redis, nginx, or Keycloak changes.

## Current State

- The plan file exists at `.omc/plans/shared-infra.md`.
- The plan includes 5 execution steps, acceptance criteria, open questions, estimated complexity, and risk mitigation.
- Existing per-stack implementations in [[Stack A]], [[Stack B]], and [[Stack C]] remain unchanged.

## 2026-03-24 Acceptance Update

User-confirmed testing on `shared-openig-2` closed the main Step 3 and Step 4 SSO/SLO acceptance items in `.omc/plans/shared-infra.md`.

Confirmed done:

- Step 3: WordPress SSO works for `alice` and `bob`.
- Step 3: [[WhoAmI]] SSO works.
- Step 3: WordPress SLO triggers backchannel logout and writes the blacklist entry.
- Step 3: Cross-app SLO works between WordPress and [[WhoAmI]].
- Step 4: All 6 apps complete SSO login successfully, with `alice` across all apps and `bob` where applicable.
- Step 4: All 6 apps complete SLO logout successfully, with backchannel firing and blacklist writes confirmed.
- Step 4: Cross-app SLO works across the full 6-app shared runtime.
- Step 4: Testing session stayed at zero OpenIG `ERROR` lines on `shared-openig-2`.

Still open by design:

- Redis key-prefix verification via `redis-cli`.
- Redis ACL cross-app denial verification via `redis-cli`.
- [[Vault]] per-app AppRole scope verification via Vault CLI.
- Explicit `JWT session is too large` log check.
- Jellyfin `deviceId` stability check across sessions.
- phpMyAdmin `bob` login verification.
- Step 5 cleanup and documentation items.
- Step 1 committed-secret hygiene item.

> [!success]
> The shared stack is now documented as functionally passing user-confirmed end-to-end SSO/SLO coverage for Step 3 and the major Step 4 flow checks.

> [!warning]
> Isolation guarantees are not fully closed until the Redis and [[Vault]] CLI checks are executed and recorded.

## Next Steps

- Run the remaining Redis CLI checks for key prefixes and ACL denial.
- Run the remaining [[Vault]] AppRole isolation checks.
- Decide when to close Step 5 packaging and final documentation migration.

> [!tip] Implementation guardrail
> Preserve zero blast radius by keeping Redis ACL, Vault AppRole, and per-route session isolation aligned per app from the first shared-stack commit.

## Files Changed

- `.omc/plans/shared-infra.md`
- `docs/obsidian/stacks/stack-shared.md`
