---
title: OpenIG 6 built-in gap analysis confirms custom Groovy remains required
tags:
  - decision
  - openig
  - groovy
  - keycloak
  - shared-infra
date: 2026-03-25
status: decision
---

# OpenIG 6 built-in gap analysis confirms custom Groovy remains required

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

`docs/deliverables/openig-builtin-gap-analysis.md` was added to verify whether the lab's current Groovy scripts can be replaced by OpenIG 6 built-in filters and handlers.

> [!success]
> The analysis confirmed `0/14` Groovy scripts are fully replaceable by OpenIG 6 built-ins for the current Keycloak-based lab architecture.

## Decision

Keep the custom Groovy layer in place for the OpenIG 6 lab baseline. Treat the scripts as intentional gateway adapters, not accidental over-engineering.

## Why

- OpenIG 6 has no built-in Redis-backed session provider for `JwtSession` offload.
- No built-in RP backchannel logout handler exists for local JWT validation plus Redis blacklist writes.
- No built-in Vault AppRole credential fetch path exists.
- No built-in SLO orchestration covers `id_token_hint`, token cleanup, and multi-app coordination.
- SPA-aware `401` behavior, HTML body rewriting, and cookie prefix stripping still need script logic.

> [!warning]
> `StripGatewaySessionCookies.groovy` is only partially replaceable today. `CookieFilter` can suppress exact names, but not the `IG_SSO_APP*` prefix pattern used in the shared lab.

## Current state

- Deliverable added: `docs/deliverables/openig-builtin-gap-analysis.md`
- Verdict: `6` `CUSTOM-NEEDED`, `8` `PARTIALLY-REPLACEABLE`, `0` `REPLACEABLE`
- Capability gaps documented: `12`
- Deferred recommendation: `REC-001` for possible `CookieFilter` partial replacement later

## Next steps

- Re-run the same analysis before any move to OpenIG 7+ or PingGateway.
- Recheck `REC-001` only if app count grows or cookie-name maintenance becomes painful.

> [!tip]
> This note should be read together with the deliverable and the earlier pre-packaging audit: both point to the same conclusion that [[OpenIG]] 6 still needs targeted Groovy extensions for this Keycloak-centric SSO/SLO pattern.
