---
title: Stack Independence Principle
tags:
  - architecture
  - independence
  - decision
date: 2026-03-12
status: decision
---

# Stack Independence Principle

Related: [[OpenIG]] [[Keycloak]] [[Stack A]] [[Stack B]] [[Stack C]]

## Rule

Each stack is fully independent and must not share runtime components:
- Own nginx
- Own `openig-x1` and `openig-x2`
- Own `vault-x`
- Own `redis-x`

## Shared Component (Only)

- Shared IdP only: Keycloak at `http://auth.sso.local:8080`, realm `sso-realm`.

## JwtSession Secret Isolation

- `JwtSession.sharedSecret` must be unique per stack.
- Current configs confirm stack-a, stack-b, and stack-c use different values in:
  - `stack-a/openig_home/config/config.json`
  - `stack-b/openig_home/config/config.json`
  - `stack-c/openig_home/config/config.json`

## SLO Isolation Behavior

- OpenIG-layer logout in one stack does not invalidate sessions in other stacks.
- Cross-stack SLO, when needed, is Keycloak-side via backchannel logout.

## Why This Principle Exists

- Teams can deploy stacks independently.
- A failure or config mistake in one stack does not create blast radius in others.
- Security boundaries stay explicit and auditable.

> [!warning]
> If two stacks accidentally share the same `JwtSession.sharedSecret`, sessions become cross-readable across stacks.
