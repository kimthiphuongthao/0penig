---
title: Auth Pattern Audit 2026-03-12
tags:
  - audit
  - auth
  - logout
  - openig
  - keycloak
date: 2026-03-12
status: completed
---

# Auth Pattern Audit 2026-03-12

Context: audited current [[OpenIG]]-mediated SSO behavior for [[Stack A]], [[Stack B]], and [[Stack C]] against `docs/legacy-auth-patterns-definitive.md`, limited to gateway evidence only. Target apps were treated as immutable; all remediation was constrained to gateway-side changes around [[OpenIG]], [[Keycloak]], nginx, and [[Vault]].

## What Done

- Read the definitive reference first and extracted the five control domains, ten audit questions, and the `0/1/2` scoring rubric.
- Read all scoped route JSON, referenced Groovy scripts, stack compose env sections, and nginx configs for WordPress, WhoAmI, Redmine, Jellyfin, Grafana, and phpMyAdmin.
- Wrote `docs/audit-auth-patterns.md` with:
  - per-app inventory
  - scored control matrix
  - answers for Q1-Q10
  - prioritized gap list
  - overall score

## Key Findings

> [!warning] Highest-risk gaps
> 1. [[Stack A]] WhoAmI is header-spoofable because `X-Authenticated-User` is added without stripping inbound values first.
> 2. [[Stack B]] Redmine can bypass the gateway because it is published on host port `3000`.
> 3. [[Stack B]] Jellyfin writes its downstream access token into browser `localStorage`.
> 4. [[Stack B]] Jellyfin logout wiring is inconsistent between `/openid/app3` and `/openid/app4`.

> [!success] Confirmed working patterns
> WordPress, Redmine, Grafana, and phpMyAdmin all have local logout interception plus Redis-backed backchannel blacklist handling.

## Decisions Captured

- Treat missing runtime proof as `UNVERIFIABLE` rather than inferring behavior.
- Keep all suggested fixes gateway-side only:
  - nginx header stripping and ingress restriction
  - [[OpenIG]] route/Groovy changes
  - [[Vault]] secret externalization
- Do not assign an aggregate interpretation tier from the reference doc because the reference defines only per-control `0/1/2` scores and no total-score bands.

## Current State

- `docs/audit-auth-patterns.md` is the canonical audit output for this pass.
- Overall score recorded: `34 / 60`.
- Lowest-scoring app: WhoAmI (`3 / 10`).
- Main stack-specific concerns:
  - [[Stack A]]: app2 logout and header trust boundary
  - [[Stack B]]: Redmine direct exposure; Jellyfin token/logout mismatches
  - [[Stack C]]: mostly coherent, but still missing stronger session hardening and observability

## Next Steps

> [!tip] Recommended order
> 1. Fix WhoAmI header stripping and add app2 logout/backchannel routes.
> 2. Remove Redmine direct host exposure so all browser traffic stays behind the gateway.
> 3. Rework Jellyfin so downstream tokens stay server-side and unify its client endpoint/client ID wiring.
> 4. Externalize hardcoded OIDC client secrets and add structured auth/logout audit logging across all stacks.

## Files Changed

- `docs/audit-auth-patterns.md`
- `docs/obsidian/debugging/2026-03-12-auth-pattern-audit.md`
