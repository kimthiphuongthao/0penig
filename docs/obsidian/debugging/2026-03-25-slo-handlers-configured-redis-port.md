---
title: SloHandlers Configured Redis Port
tags:
  - debugging
  - shared-infra
  - openig
  - redis
  - groovy
date: 2026-03-25
status: completed
---

# SloHandlers Configured Redis Port

Related: [[OpenIG]] [[Vault]] [[stack-shared]]

## Context

`shared/openig_home/scripts/groovy/SloHandler.groovy` and `shared/openig_home/scripts/groovy/SloHandlerJellyfin.groovy` still opened Redis sockets on a hardcoded port `6379`, even when routes were configured with a different `redisPort` binding. `TokenReferenceFilter.groovy` already used the route-provided Redis port, so SLO cleanup behavior was out of sync with the current shared token-reference pattern.

> [!warning] Root cause
> Both handlers defined `configuredRedisHost` but not `configuredRedisPort`, and each `withRedisSocket` closure still called `new InetSocketAddress(configuredRedisHost, 6379)`.

## Fix Applied

- Added `def configuredRedisPort = binding.hasVariable('redisPort') ? (redisPort as String) : '6379'` to both handlers.
- Replaced the hardcoded `6379` socket connect port with `configuredRedisPort.toInteger()` in both `withRedisSocket` closures.
- Preserved `6379` as the fallback default so existing routes keep working without new arguments.

> [!success] Result
> Shared SLO handlers now follow the same configurable Redis host/port pattern as `TokenReferenceFilter`, so logout-side token cleanup honors route-level Redis port overrides.

## Validation

- Compared both handlers against `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`.
- Confirmed the only remaining `6379` references in the two handlers are the new default binding values.
- Committed the code change as `3598284` with message:
  - `fix(redis): use configuredRedisPort in SloHandler and SloHandlerJellyfin`

> [!tip] Follow-up
> Keep Redis host, port, auth, and key-prefix bindings aligned across shared Groovy scripts so route-level infra changes do not leave one logout or restore path behind.

## Files Changed

- `shared/openig_home/scripts/groovy/SloHandler.groovy`
- `shared/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
- `docs/obsidian/debugging/2026-03-25-slo-handlers-configured-redis-port.md`
