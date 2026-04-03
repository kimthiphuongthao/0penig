---
title: Standard gateway pattern EN VI gap analysis
tags:
  - openig
  - documentation
  - architecture
  - presentation
  - sso
  - slo
date: 2026-04-02
status: completed
---

# Standard gateway pattern EN VI gap analysis

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Redis]] [[Stack C]]

## Context

Task: compare `docs/deliverables/standard-gateway-pattern.md` and `docs/deliverables/standard-gateway-pattern-vi.md` against the active shared-runtime architecture in `.claude/rules/architecture.md`.

Focus:

- what the English deliverable still omits relative to the current architecture source of truth
- what the Vietnamese deliverable omits relative to the English deliverable
- whether either document is suitable for stakeholder presentation

## What Done

- Read the English deliverable, the Vietnamese deliverable, and the architecture rule for the active `shared/` runtime.
- Compared the deliverables against the current shared-infra topology, per-app isolation model, Redis and Vault isolation details, and documented production gaps.
- Evaluated both documents for stakeholder presentation readiness, not just technical correctness.

> [!success]
> `docs/deliverables/standard-gateway-pattern.md` is the strongest current technical baseline because it matches the active shared runtime and its per-app isolation model.

> [!warning]
> `docs/deliverables/standard-gateway-pattern-vi.md` is still anchored to the older 3-stack review framing and does not describe the current `shared/` runtime explicitly enough to be used as-is.

> [!tip]
> The safest path for a Vietnamese stakeholder deliverable is to rewrite the VI doc directly from the current English shared-runtime version, then add one short stakeholder-oriented summary section on business value, risk reduction, and rollout scope.

## Key Findings

### EN doc vs active architecture

- The EN doc captures the shared runtime and the main control contract, but it does not include the current per-app routing matrix: hostnames, internal upstreams, `clientEndpoint`, Keycloak client IDs, session heaps, and cookies.
- The EN doc omits several live-environment inventory details that exist in the architecture rule: container names, public URLs, Keycloak test users, and the app-by-app Vault AppRole scope table.
- The EN doc describes Redis and Vault isolation generically, but not the app-by-app ACL and AppRole mapping that exists in the current architecture reference.
- The EN doc lists transport hardening gaps, but it does not capture all currently documented production gaps such as missing Vault Transit wrapping for Redis payloads, single-node Vault limitations, and storage-encryption expectations.

### VI doc vs EN doc

- The VI doc does not state the active shared runtime shape introduced in the EN doc: one nginx, two OpenIG nodes, one Redis, one Vault, and six apps behind hostname routing.
- The VI doc lacks the EN shared-infra deployment contract and implementation-status view for route-local cookies, per-app Redis ACL, per-app Vault AppRole isolation, cookie stripping, and current TLS exception.
- The VI doc does not define the EN shared-runtime session-isolation contract in concrete terms: unique `SessionAppN`, `IG_SSO_APPN`, `tokenRefKey`, Redis user/prefix, and Vault AppRole per app.
- The VI doc is missing several EN implementation rules around `TokenReferenceFilter.groovy`: placement after `OAuth2ClientFilter`, callback restore skip, offload skip when OAuth state is not populated, and deletion of only the current app namespace.
- The VI doc does not include the EN anti-pattern about forwarding gateway cookies downstream or the EN parameterized-template section and OpenIG 6.0.1 args-binding rule.

## Decisions

- Use the EN deliverable as the source of truth for any next revision of the VI document.
- Do not present the VI document as current architecture without rewrite.
- Do not use either document as a board-facing presentation artifact without a shorter stakeholder summary layer.

## Current State

- EN document: technically strong and current, but still written as an engineering reference rather than a stakeholder narrative.
- VI document: informative and detailed, but outdated in framing and missing the shared-runtime architecture contract from the current EN version.
- Stakeholder readiness: neither is board-ready as-is; EN is acceptable as a technical stakeholder briefing after light editing.

## Next Steps

- Add one current-architecture appendix or table to the EN doc with the per-app routing/session/client map from `.claude/rules/architecture.md`.
- Rewrite the VI doc from the EN shared-runtime baseline instead of incrementally patching the older 3-stack narrative.
- Produce a presentation variant with a short executive summary, architecture diagram, control highlights, production gaps, and rollout scope.

## Files Changed

- `docs/obsidian/how-to/2026-04-02-standard-gateway-pattern-en-vi-gap-analysis.md`
