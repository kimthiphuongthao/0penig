---
title: Production Readiness Gap Report
tags:
  - sso
  - audit
  - production-readiness
  - openig
  - keycloak
date: 2026-03-17
status: complete
---

# Production Readiness Gap Report

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

Context: wrote the 2026-03-17 cross-stack production-readiness gap report for the SSO Lab reference-solution baseline, using the 2026-03-16 pre-packaging audit plus current repo state.

Primary output: `docs/audit/2026-03-17-production-readiness-gap-report.md`

## What Was Done

- Consolidated the current readiness status into one document covering all three stacks.
- Preserved audit IDs so the report can act as both a status artifact and a remediation tracker.
- Grouped the remaining gaps into `Must Fix`, `Should Fix`, `Lab Exceptions`, and `Nice to Have`.
- Captured the resolved Pattern Consolidation work separately so open work is not mixed with already-closed items.

> [!warning]
> Current readiness remains `NOT READY`: 39 open, 6 partial, 36 resolved out of 81 checks.

## Key Decisions Captured

- Blocking issues are the ones that either leave active security exposure in place or prevent one stack from matching the reference pattern.
- Stack C parity was treated as a first-class blocker because the lab goal is a reusable multi-stack reference, not a one-off demo.
- Lab exceptions were retained only where the pattern is still valid but transport or hardening phases are intentionally deferred.

> [!success]
> Pattern Consolidation already removed a large amount of risk and duplication: JWKS race, `SloHandler` hardening, TTL unit consistency, handler consolidation, repo hygiene, port exposure cleanup, and dead-code removal.

## Current State

- The gateway pattern shape is now much stronger than the 2026-03-16 baseline.
- The reference solution is still blocked by Redis authentication, committed secrets, Stack C parity gaps, hardcoded Keycloak endpoints in Stack A/C, and one remaining dead-code file.
- The remaining non-blockers are mostly low-effort consistency and hardening tasks.

## Next Steps

- Execute the P1 list first: Redis auth, secret externalization, Stack C compose parity, Keycloak URL externalization, strong Stack C OIDC secrets, and `PhpMyAdminCookieFilter` deletion.
- Follow with the P2 consistency pass across Groovy, nginx, cookies, and Linux portability.
- Keep the lab exceptions visible in the production checklist so deferred TLS and Vault hardening are not mistaken for acceptable production defaults.

> [!tip]
> This report is ready to drive an implementation batch directly because every blocking item already points to gateway-side files only.

## Files Changed

- `docs/audit/2026-03-17-production-readiness-gap-report.md`
- `docs/obsidian/debugging/2026-03-17-production-readiness-gap-report.md`
