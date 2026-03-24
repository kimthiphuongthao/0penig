---
title: Audit auth patterns shared-infra refresh
tags:
  - sso-lab
  - docs
  - shared-infra
  - audit
  - debugging
date: 2026-03-24
status: done
---

# Audit auth patterns shared-infra refresh

## Context

`docs/deliverables/audit-auth-patterns.md` still described the retired three-stack ingress model for Stack B and Stack C, even though runtime traffic now goes through a single shared hostname-routed gateway on port `80`.

The stale references were concentrated in topology rows, Redmine app numbering, and shared logout/backchannel wording for [[OpenIG]] routes and [[Keycloak]] clients.

> [!warning]
> The audit remains partly historical by design. Stack-era file references were retained where they still capture the original audited implementation details, but runtime topology statements now reflect shared-infra.

## What changed

- Updated the audit intro to state that the active deployment is `shared-nginx` -> `shared-openig-1/shared-openig-2` on port `80`.
- Rewrote all per-app topology rows to use hostname routing through shared-infra containers instead of `:9080` and `:18080`.
- Corrected Redmine from the stale Stack B app4 mapping to the current app3 mapping:
  - backchannel route `/openid/app3/backchannel_logout`
  - shared session/client references for `IG_SSO_APP3`
  - current `shared/openig_home/config/routes/00-redmine-logout.json`
- Updated Grafana and phpMyAdmin rows to point at the shared app5/app6 routes and current shared nginx behavior.
- Fixed the audit score summary inconsistency: Jellyfin totals now sum to `6`, so the aggregate score is `32 / 50`.

> [!success]
> Post-edit stale-reference verification was clean for `9080`, `18080`, `IG_SSO_B`, `IG_SSO_C`, `sso-openig-1`, `sso-b-openig`, and `stack-c-openig`.

## Decisions

- Preserve historical findings such as the old Redmine `3000:3000` exposure, but mark them explicitly as historical and pair them with the current shared-infra state.
- Prefer current shared route/container references when the statement is about runtime behavior.
- Keep legacy stack file references only where they still document the original audited logic and do not contradict the shared-infra runtime description.

## Current state

- The deliverable now describes a single shared ingress model on port `80`.
- Redmine/Jellyfin/Grafana/phpMyAdmin now align with current app numbering and hostname routing.
- The document still intentionally carries historical audit context for earlier consolidation steps around [[OpenIG]], [[Keycloak]], and [[Vault]].

## Files changed

- `docs/deliverables/audit-auth-patterns.md`

## Next steps

- If the shared Groovy defaults are cleaned up later, re-check for any remaining hardcoded `:9080`/`:18080` fallback values in route/script citations.
- If the audit is promoted from "historical comparison" to "current readiness scoreboard", replace the remaining stack-era evidence references with shared equivalents end-to-end.
