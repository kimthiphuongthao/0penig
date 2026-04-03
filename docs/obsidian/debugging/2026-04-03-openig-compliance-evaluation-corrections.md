---
title: OpenIG compliance evaluation corrections
tags:
  - debugging
  - docs
  - audit
  - openig
  - keycloak
  - shared
date: 2026-04-03
status: done
---

# OpenIG compliance evaluation corrections

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

This task corrected `docs/audit/2026-04-02-openig-best-practices-compliance-evaluation.md` so it matches the repo-backed verification note in [[2026-04-02-openig-best-practices-audit-verification]].

The mismatch was in four findings and the category rollup:

- `OAUTH-001` was overstated as `CRITICAL`
- `OAUTH-002` was presented as confirmed even though gateway code does not prove it
- `SESS-001` claimed both `Secure` and `HttpOnly` were absent
- `OBS-001` implied token leakage without code evidence

> [!warning]
> This task changed documentation only. No [[OpenIG]] route JSON, Groovy script, nginx config, target app, database, or [[Keycloak]] realm config was modified.

## What changed

- Downgraded `OAUTH-001` from `CRITICAL` to `MAJOR`
- Added the confidential-client note explaining that `PKCE` is recommended hardening, not a blocking requirement for server-side clients with `clientSecret`
- Marked `OAUTH-002` as `UNVERIFIED` and replaced the old `MEMORY.md` evidence claim with a source-limited statement tied to gateway code only
- Narrowed `SESS-001` to missing explicit `Secure` hardening and removed the unsupported `HttpOnly` assertion
- Reframed `OBS-001` to missing structured audit logging only, with no token-leakage claim
- Updated the executive-summary category counts to show `1/8` non-compliant and `6/8` partial
- Updated the `OAuth2/OIDC` scorecard status to `PARTIAL` so the rollup matches the table

> [!success]
> The audit now distinguishes confirmed gateway-code findings from external-state assumptions and treats `PKCE` correctly for this confidential-client [[OpenIG]] pattern.

## Decision

> [!tip]
> Future audit docs in this lab should separate three cases explicitly:
> 1. gateway behavior evidenced in repo files
> 2. external [[Keycloak]] state not exported into the repo
> 3. best-practice hardening recommendations that are not blocking requirements

## Current state

- Commit `c119b43` records the audit document correction.
- `OAUTH-002` remains unverified until client exports or other authoritative external-state evidence are added.
- The audit now matches the verification note for `OAUTH-001`, `SESS-001`, and `OBS-001`.

## Next steps

- If the team wants `OAUTH-002` confirmed or closed, add [[Keycloak]] client export evidence to the audit source set.
- If the evaluation is used for rollout gates, reassess whether the unchanged `Production Ready` count should also be reduced after removing the unverified item from confirmed findings.

## Files changed

- `docs/audit/2026-04-02-openig-best-practices-compliance-evaluation.md`
- `docs/obsidian/debugging/2026-04-03-openig-compliance-evaluation-corrections.md`
