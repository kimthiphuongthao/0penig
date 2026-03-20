---
title: 2026-03-20 OpenIG Final Audit Report
tags:
  - debugging
  - audit
  - openig
  - sso
  - slo
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-20
status: complete
---

# 2026-03-20 OpenIG Final Audit Report

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

> [!success]
> Wrote the definitive final audit report at `docs/audit/2026-03-20-openig-final-report.md` after reading the six required audit and verification inputs in order.

## Context

- Task: produce the final authoritative audit report for the OpenIG 6 SSO/SLO implementation.
- Required input order:
  - `docs/audit/2026-03-20-openig-core-audit-codex.md`
  - `docs/audit/2026-03-20-openig-core-audit-gemini.md`
  - `docs/audit/2026-03-20-openig-core-audit-architect.md`
  - `docs/audit/2026-03-20-openig-core-audit-architect-v2.md`
  - `docs/audit/2026-03-20-openig-core-audit-synthesis.md`
  - `docs/audit/2026-03-20-openig-conflict-verification.md`
- Constraints:
  - `B3` dead code removal and `A4` gotcha correction were already done in commit `b53c239`
  - `B4`, `A4`, and `F2` had to be resolved from the conflict-verification file as the final word
  - no code or config changes were allowed

## What was done

- Read all six source documents in the requested order.
- Locked the final claim set at 16 IDs: 11 `CONFIRM`, 5 `REFUTE`.
- Used `docs/audit/2026-03-20-openig-conflict-verification.md` as the override for `B4`, `A4`, and `F2`.
- Wrote the final report with:
  - header summary
  - final verdict table
  - confirmed claims section
  - refuted claims split into already-fixed vs action-required
  - architecture implications
  - open planning actions

## Decisions

> [!warning]
> The final report treats raw agent audit files as historical inputs, not as the canonical source of truth.

- `B4` is `REFUTE`: `target = ${attributes.openid}` writes request attributes only; persisted OAuth2 session state comes from a separate `saveSession()` path.
- `A4` remains `REFUTE`: oversized `JwtSession` cookies cause silent session-save loss, not a guaranteed hard HTTP 500.
- `E1` is `REFUTE`: `clientEndpoint` collisions are caused by route-local matching plus route order, not by a server-global registry.
- `F2` is `REFUTE`: `sessionTimeout` drives JWT expiry semantics plus cookie `Expires`, not cookie `Max-Age`.

> [!tip]
> Future OpenIG debugging should always separate request-scoped `attributes.openid` data from persisted `session[oauth2Key]` data. Mixing those paths is the main source of false conclusions in the rejected claims.

## Current state

- Canonical final report now exists:
  - `docs/audit/2026-03-20-openig-final-report.md`
- Already-fixed items remain closed:
  - `B3` dead code removed from `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
  - `A4` wording fixed in `.claude/rules/gotchas.md`
- Remaining follow-up is documentation only:
  - add the `B4` data-model clarification to a canonical auth-pattern document
  - add the `E1` route-local `clientEndpoint` explanation to architecture guidance
  - add the `F2` `Expires` vs `Max-Age` clarification to the gateway pattern doc

## Next steps

1. Update `docs/deliverables/legacy-auth-patterns-definitive.md` so it explains the split between `attributes.openid` and persisted `session[oauth2Key]`.
2. Update `.claude/rules/architecture.md` so the `clientEndpoint namespace` section explains the real collision mechanism.
3. Update `docs/deliverables/standard-gateway-pattern.md` so session lifetime guidance names cookie `Expires` and stops implying `Max-Age`.

## Files changed

- `docs/audit/2026-03-20-openig-final-report.md`
- `docs/obsidian/debugging/2026-03-20-openig-final-audit-report.md`
