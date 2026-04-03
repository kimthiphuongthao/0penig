---
title: Production hardening backlog
tags:
  - documentation
  - backlog
  - hardening
  - openig
  - vault
  - redis
  - tls
date: 2026-04-03
status: completed
---

# Production hardening backlog

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Redis]] [[Stack A]] [[Stack B]] [[Stack C]] [[2026-04-03-secret-001-vault-transit-evaluation]]

## Context

Task: create `docs/fix-tracking/production-hardening-backlog.md` from four repo sources:

- `docs/audit/2026-04-02-openig-best-practices-compliance-evaluation.md`
- `docs/obsidian/debugging/2026-04-03-secret-001-vault-transit-evaluation.md`
- `.claude/rules/architecture.md`
- `CLAUDE.md`

The goal was to turn the remaining shared-runtime production hardening gaps into one implementation-ordered backlog with explicit scope, acceptance criteria, and blockers.

> [!warning]
> This task changed tracking/docs only. No [[OpenIG]] route JSON, Groovy runtime logic, nginx config, Vault bootstrap logic, Redis ACL, or [[Keycloak]] client configuration was modified.

## What Done

- Created `docs/fix-tracking/production-hardening-backlog.md`.
- Added a top summary table showing `OPEN = 5`, `TOTAL = 5`.
- Recorded five tasks in the requested implementation order:
  - `VAULT-TRANSIT-001`
  - `PKCE-001`
  - `AUDIT-LOG-001`
  - `SECURE-COOKIE-001`
  - `TLS-001`
- Added task-specific acceptance criteria grounded in the current `shared/` runtime:
  - Transit must reuse `globals.compute()` cache and support dual-format Redis reads during rollout.
  - PKCE applies to the six active `OAuth2ClientFilter` routes only.
  - Audit logging must avoid token, cookie, Vault, and Redis payload leakage.
  - Secure cookie hardening remains gated on TLS.
  - TLS remains Phase `7b` deferred work for the shared runtime.
- Staged and committed only the backlog file with commit `7aea5f0`.

> [!success]
> The repo now has a dedicated production-hardening backlog for the active shared deployment instead of leaving these items split across audit notes, architecture rules, and roadmap text.

## Decisions

- Keep `VAULT-TRANSIT-001` first because `SECRET-001` is the only remaining `P1` item and the Transit evaluation already narrowed the viable implementation pattern.
- Keep `PKCE-001` and `AUDIT-LOG-001` as `P2` items because both are real hardening gaps, but neither blocks the current confidential-client lab flow.
- Keep `SECURE-COOKIE-001` after `TLS-001` dependency analysis, even though the requested order lists the cookie task first, because the backlog explicitly records that it cannot close before Phase `7b`.

> [!tip]
> When implementing `VAULT-TRANSIT-001`, treat the Obsidian note [[2026-04-03-secret-001-vault-transit-evaluation]] as the detailed compatibility checklist. The backlog entry should remain concise and execution-oriented.

## Current State

- `docs/fix-tracking/production-hardening-backlog.md` exists and is committed in `7aea5f0`.
- The backlog captures the current shared-runtime hardening sequence without reopening already accepted lab exceptions.
- This Obsidian note was created after the docs commit so the requested Git commit stayed limited to the backlog file.

## Next Steps

- Use `docs/fix-tracking/production-hardening-backlog.md` as the source of truth for the next production-hardening implementation work.
- When a backlog item is implemented, update the task row, add verification evidence, and write the corresponding [[OpenIG]] / [[Vault]] / [[Redis]] technical note under `docs/obsidian/`.
- Revisit `SECURE-COOKIE-001` only after the shared TLS plan is approved for Phase `7b`.

## Files Changed

- `docs/fix-tracking/production-hardening-backlog.md`
- `docs/obsidian/how-to/2026-04-03-production-hardening-backlog.md`
