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

## 2026-03-24 Documentation sync update

Context: shared-infra runtime is the active deployment, but the main rules and deliverables still described the old three-stack model. This task aligned the active documentation with the source of truth in `shared/docker-compose.yml`, `shared/nginx/nginx.conf`, `shared/openig_home/config/routes/`, `shared/vault/init/vault-bootstrap.sh`, and `shared/redis/acl.conf`.

## What Was Done

- Rewrote [[OpenIG]] architecture rules to describe the active `shared/` runtime instead of Stack A/B/C as the deployment model.
- Replaced the old multi-section restart runbook with the single shared-infra restart sequence using `shared-vault`, `shared-openig-1`, and `shared-openig-2`.
- Updated `CLAUDE.md` roadmap with completed shared-infra consolidation, per-app Redis ACL, per-app [[Vault]] AppRole isolation, and completed security-audit items.
- Rewrote the legacy app manager checklist so app teams only see the information they must provide, not gateway-internal stack details.
- Replaced the standard gateway pattern deliverable with the shared-infra baseline: per-app route-local cookies, per-app Redis ACL, per-app AppRoles, and production transport gaps.
- Updated the definitive auth reference with shared-runtime session and isolation notes.

> [!success] Shared-doc baseline aligned
> The active rules and primary deliverables now describe `shared/` as the deployment model, with hostname routing on port 80 and app-level isolation controls.

## Decisions

- Keep `shared/docker-compose.yml` as the naming source of truth, including the actual MariaDB container name `shared-mariadb`.
- Document app isolation as the active boundary: `SessionApp1..6`, `IG_SSO_APP1..APP6`, `openig-app1..6`, and `app1:*..app6:*`.
- Leave unrelated pre-existing deliverables untouched if they were not part of the requested edit set, but record them when the final stale-reference sweep still flags them.

> [!warning] Remaining stale references
> The final grep sweep still finds legacy port and cookie references in `docs/deliverables/standalone-legacy-app-integration-guide.md` and `docs/deliverables/audit-auth-patterns.md`. Those files were outside the requested edit list for this task.

## Current State

- Primary shared-infra docs are updated and internally consistent with the `shared/` runtime files.
- The requested stale-reference grep is clean for the edited rules, `CLAUDE.md`, and the targeted deliverables.
- Additional stale references remain in two non-targeted deliverables and should be handled in a follow-up docs sweep.

## Next Steps

- Decide whether to fold `standalone-legacy-app-integration-guide.md` into the shared-infra doc set or archive it as historical.
- Update `audit-auth-patterns.md` if it is still intended to be an active deliverable instead of a historical audit snapshot.
- Keep future doc changes anchored to the `shared/` runtime files first, then propagate outward to secondary deliverables.

## 2026-03-26 Monthly Report Snapshot

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

Context: created the March 2026 monthly report at `docs/progress/2026-03-monthly-report.md` based on March git history, `CLAUDE.md` roadmap completion state, `.memory/MEMORY.md` lab status, and the 2026-03-25 production-readiness audit.

## What Was Done

- Summarized March outcomes against the 3 planned workstreams: legacy-system technical audit, distributed gateway gap analysis, and HA/failover/least-privilege validation.
- Recorded that the shared runtime is functionally validated for end-to-end SSO/SLO across all 6 apps, but the 2026-03-25 audit still leaves multiple production-blocking findings open.
- Captured April priorities around audit closure, deployable packaging, rerun evidence on the packaged topology, and presentation deliverables.

> [!success] Monthly summary captured
> The workspace now has a concise month-end status report for external reporting, while detailed technical evidence remains in the audit and stack notes.

> [!warning] Production gap still open
> The lab is validated as a reference and test environment, not yet as a production-ready deployment. Open items from the 2026-03-25 audit remain the main gating factor.

## Current State

- Monthly report file created: `docs/progress/2026-03-monthly-report.md`
- Shared stack remains the active runtime baseline for reporting and packaging.
- April focus is now clearly scoped to open findings closure, packaging, and solution-report artifacts.

## Next Steps

- Fix the highest-priority open audit items before promoting the shared runtime as a transfer-ready bundle.
- Produce the `OVA` or Docker Compose packaging path with repeatable bootstrap and validation steps.
- Complete the slide/report set so the technical solution can be presented with evidence, risks, and production recommendations.

## Files Changed (2026-03-26 Monthly Report)

- `docs/progress/2026-03-monthly-report.md`
- `docs/obsidian/stacks/stack-shared.md`

## Files Changed (2026-03-24 Documentation Sync)

- `.claude/rules/architecture.md`
- `.claude/rules/restart.md`
- `.claude/rules/gotchas.md`
- `.claude/rules/conventions.md`
- `CLAUDE.md`
- `docs/deliverables/legacy-app-team-checklist.md`
- `docs/deliverables/standard-gateway-pattern.md`
- `docs/deliverables/legacy-auth-patterns-definitive.md`
- `docs/obsidian/stacks/stack-shared.md`

## 2026-04-02 Progress report sync

Context: `docs/progress.md` ended with the week `30/03/2026 - 03/04/2026` plan only. The weekly report needed the realized outcome summary for that week and the next planning block for the packaging handover phase.

## What Was Done

- Added `### Kết quả` to the terminal `## Tuần 30/03/2026 - 03/04/2026` block in `docs/progress.md`.
- Appended `## Tuần 07/04/2026 - 11/04/2026` after the last `---`.
- Verified the closing sequence remains `Kế hoạch -> Kết quả -> --- -> tuần kế tiếp`.

> [!success] Weekly status synchronized
> `docs/progress.md` now reflects the late-March audit closure work and the immediate packaging/documentation plan for the week starting 2026-04-07.

## Current State

- The week ending 2026-04-03 now records high-priority audit finding closure, Gateway error-handling cleanup, shared-runtime SSO/SLO regression re-validation, K8s multi-tenancy mapping, and roadmap/audit synchronization.
- The next planning block now captures the Docker Compose bundle target, K8s multi-tenancy architecture write-up, stakeholder slide refresh, and Quick Start Guide work.

## Files Changed (2026-04-02 Progress Sync)

- `docs/progress.md`
- `docs/obsidian/stacks/stack-shared.md`
