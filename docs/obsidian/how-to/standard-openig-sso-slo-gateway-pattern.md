---
title: Standard OpenIG SSO SLO Gateway Pattern
tags:
  - openig
  - keycloak
  - vault
  - redis
  - gateway-pattern
  - how-to
date: 2026-03-19
status: completed
---

# Standard OpenIG SSO SLO Gateway Pattern

Source document: `docs/deliverables/standard-gateway-pattern.md`

## Context

This note records the evidence basis for the standard gateway pattern written from the 2026-03-14 cross-stack review set:

- `docs/reviews/2026-03-14-cross-stack-review-summary.md`
- `docs/reviews/2026-03-14-stack-a-openig-code-security-review.md`
- `docs/reviews/2026-03-14-stack-b-openig-code-security-review.md`
- `docs/reviews/2026-03-14-stack-c-openig-code-security-review.md`

The reviewed stacks cover five adapter styles relevant to [[OpenIG]] acting as the enforcement layer in front of heterogeneous applications:

- OIDC standard flow
- Credential injection
- Token injection into browser-side app state
- Trusted header injection
- HTTP Basic Auth injection

## What Was Written

`docs/deliverables/standard-gateway-pattern.md` was created as a prescriptive reference document for [[OpenIG]] + [[Keycloak]] + [[Vault]] + Redis. The document intentionally describes the target pattern, not only the earlier lab implementations.

It standardizes:

- runtime secret sourcing
- browser-cookie session size control via `TokenReferenceFilter`
- revocation TTL and fail-closed behavior
- HTTPS requirements
- browser-session storage boundaries
- pinned origin and OIDC namespace rules
- bounded Redis behavior
- mandatory adapter filter-chain wiring
- RP-initiated and backchannel logout sequencing

## Evidence Basis

> [!warning] Universal defects seen across stacks
> The pattern is primarily driven by repeated gateway-level failures rather than app-local bugs: committed secrets, revocation TTL shorter than session lifetime, fail-open revocation on Redis failure, and `Host`-derived redirect or session-root behavior.

> [!success] Positive controls retained
> The validated pattern keeps dual-algorithm logout-token validation (`RS256` and `ES256`), JWKS-by-`kid`, RSA/EC signature verification, and `iss`/`aud`/`events`/`iat`/`exp` checks as mandatory.

> [!tip] Adapter-specific lesson
> Presence of a Groovy script is not evidence that a control is active. [[Stack C]] showed a cookie-reconciliation safeguard that existed in code but was absent from the live route chain.

## Key Decisions

1. Revocation is authoritative only if Redis TTL covers the full session lifetime and request-time lookup fails closed.
2. Browser-bound `JwtSession` stays viable only if heavyweight `oauth2:*` session blobs are offloaded server-side through `TokenReferenceFilter`.
3. [[Vault]] and downstream credentials must stay out of browser-bound `JwtSession` state even when the downstream adapter uses credential or Basic Auth injection.
4. Redirect targets and OIDC session namespaces must be pinned in config, not derived from `Host`.
5. Adapter cleanup and logout hooks are route-contract requirements, not optional helper logic.
6. [[Keycloak]] remains a shared dependency, so the production pattern needs explicit HA/availability planning for login and logout paths.
7. Transport security is part of the gateway mechanism; HTTP-only lab wiring is explicitly non-reference scaffolding.

## Current State

- Pattern document written at `docs/deliverables/standard-gateway-pattern.md`
- Every major section cites the review findings it is derived from
- Checklist grouped by secret management, session and revocation, transport, adapter contract, logout flows, and observability
- 2026-03-19 validation sync adds TokenReferenceFilter and `ES256` / EC logout-token coverage to the live reference

## Files Changed

- `docs/deliverables/standard-gateway-pattern.md`
- `docs/obsidian/how-to/standard-openig-sso-slo-gateway-pattern.md`
