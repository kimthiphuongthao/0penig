---
title: Per-route JwtSession isolation for shared OpenIG infra
tags:
  - decision
  - openig
  - jwtsession
  - shared-infra
date: 2026-03-23
status: decision
---

# Per-route JwtSession isolation for shared OpenIG infra

Related: [[OpenIG]] [[Vault]] [[Keycloak]] [[Stack A]] [[Stack B]] [[Stack C]]

## Decision

Build `stack-shared/` as the next infra experiment: 1 nginx HA entry, 2 OpenIG nodes, 1 Vault, 1 Redis for all 6 apps. Each app route will override the default shared session by declaring its own route-level `JwtSession` manager with a dedicated `cookieName`.

> [!success]
> OpenIG 6 capability was source-confirmed on 2026-03-23: route JSON can set `"session": "NamedSessionManager"`, and the route can define a dedicated `JwtSession` heap object for that route scope.

## Source-confirmed basis

- `Keys.java`: `SESSION_FACTORY_HEAP_KEY = "Session"`
- `RouteBuilder.java`: reads `config.get("session")` for per-route override
- `JwtSessionManager.java`: `cookieName` is configured per instance

## Why this matters

Current default behavior in a shared OpenIG instance is one global `Session` heap object unless a route overrides it. That means apps in the same instance can affect each other through the same session namespace and cookie scope.

Per-route `JwtSession` isolation gives each app its own cookie, crypto config, and route-local session boundary. This is the banking-grade isolation pattern for a shared OpenIG deployment with near-zero blast radius between apps.

> [!warning]
> Per-route isolation is only meaningful if each app also keeps its own `cookieName`, `sharedSecret`, and keystore material. Reusing those values recreates coupling even if the route declares a custom session object.

## Impact on BUG-SSO2-AFTER-SLO

The current SSO2-after-SLO bug should not be patched with another `session.clear()` variation in the existing per-stack pattern. The proper fix direction is to migrate to per-route `JwtSession` isolation in shared infra, then resolve token/session cleanup inside that isolated model.

> [!tip]
> App domains stay unchanged during the shared-infra migration. The migration target is infra consolidation plus route/session isolation, not URL changes.

## Checkpoint

- Working per-stack baseline tagged and pushed as `v1.0-per-stack-validated`
- `MEMORY.md` current task switched to shared infra planning
- `BUG-SSO2-AFTER-SLO` marked deferred pending the shared-infra build
