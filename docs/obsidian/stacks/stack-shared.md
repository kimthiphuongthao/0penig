---
title: Shared Stack
tags:
  - sso
  - stack-shared
  - openig
  - planning
date: 2026-03-23
status: ready
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

## Next Steps

- Review `.omc/plans/shared-infra.md` for any final scope corrections before implementation starts.
- Execute the plan in order, beginning with shared foundation scaffolding and secret/bootstrap contracts.
- Keep implementation limited to allowed gateway-side assets: OpenIG routes, Groovy scripts, nginx, and Vault/bootstrap wiring.

> [!tip] Implementation guardrail
> Preserve zero blast radius by keeping Redis ACL, Vault AppRole, and per-route session isolation aligned per app from the first shared-stack commit.

## Files Changed

- `.omc/plans/shared-infra.md`
- `docs/obsidian/stacks/stack-shared.md`
