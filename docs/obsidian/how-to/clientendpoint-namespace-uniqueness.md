---
title: clientEndpoint namespace uniqueness
tags:
  - openig
  - oauth2
  - documentation
date: 2026-03-20
status: done
---

# clientEndpoint namespace uniqueness

This note documents why `clientEndpoint` values must stay unique per app when multiple routes run inside the same [[OpenIG]] instance and connect to [[Keycloak]].

> [!tip]
> Treat `clientEndpoint` as a route-local namespace that still needs instance-wide uniqueness in practice.

## Root cause

OpenIG does not maintain a server-global `clientEndpoint` registry. Each `OAuth2ClientFilter` evaluates its own login, callback, and logout URIs inside the route-local filter chain.

When two routes can both match the same `clientEndpoint`, route resolution can collide and lexicographic route order may send the request into the wrong route first.

> [!warning]
> Reusing the same `clientEndpoint` across apps in one OpenIG instance can make authentication callbacks and logout handling land on the wrong route.

## Documentation change

Updated [.claude/rules/architecture.md](/Volumes/OS/claude/openig/sso-lab/.claude/rules/architecture.md) to insert an explicit rationale paragraph directly above the `clientEndpoint` namespace table.

## Current state

> [!success]
> The architecture rule now states the collision mechanism explicitly, including route-local matching and lexicographic route selection.
