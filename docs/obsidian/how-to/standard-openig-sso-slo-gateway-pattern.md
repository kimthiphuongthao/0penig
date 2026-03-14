---
title: Standard OpenIG SSO SLO Gateway Pattern
tags:
  - openig
  - keycloak
  - vault
  - redis
  - gateway-pattern
  - how-to
date: 2026-03-14
status: completed
---

# Standard OpenIG SSO SLO Gateway Pattern

Source document: `docs/standard-gateway-pattern.md`

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

`docs/standard-gateway-pattern.md` was created as a prescriptive reference document for [[OpenIG]] + [[Keycloak]] + [[Vault]] + Redis. The document intentionally describes the target pattern, not the current lab implementations.

It standardizes:

- runtime secret sourcing
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
> All three reviews treat the logout-token validator as the strongest mechanism in the design. The standard pattern keeps RS256 pinning, JWKS-by-`kid`, signature verification, and `iss`/`aud`/`events`/`iat`/`exp` checks as mandatory.

> [!tip] Adapter-specific lesson
> Presence of a Groovy script is not evidence that a control is active. [[Stack C]] showed a cookie-reconciliation safeguard that existed in code but was absent from the live route chain.

## Key Decisions

1. Revocation is authoritative only if Redis TTL covers the full session lifetime and request-time lookup fails closed.
2. [[Vault]] and downstream credentials must stay out of browser-bound `JwtSession` state even when the downstream adapter uses credential or Basic Auth injection.
3. Redirect targets and OIDC session namespaces must be pinned in config, not derived from `Host`.
4. Adapter cleanup and logout hooks are route-contract requirements, not optional helper logic.
5. Transport security is part of the gateway mechanism; HTTP-only lab wiring is explicitly non-reference scaffolding.

## Current State

- Pattern document written at `docs/standard-gateway-pattern.md`
- Every major section cites the review findings it is derived from
- Checklist grouped by secret management, session and revocation, transport, adapter contract, logout flows, and observability

## Files Changed

- `docs/standard-gateway-pattern.md`
- `docs/obsidian/how-to/standard-openig-sso-slo-gateway-pattern.md`
