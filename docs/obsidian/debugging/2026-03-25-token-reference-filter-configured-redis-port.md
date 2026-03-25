---
title: TokenReferenceFilter Configured Redis Port
tags:
  - debugging
  - shared-infra
  - openig
  - redis
  - groovy
date: 2026-03-25
status: completed
---

# TokenReferenceFilter Configured Redis Port

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[stack-shared]]

## Context

`shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy` still opened Redis sockets with a hardcoded port `6379`, even when the route passed a different `redisPort` binding. Sibling filters already supported `redisPort`, so token-reference offload/restore behavior was inconsistent with the rest of the shared gateway scripts.

> [!warning] Root cause
> `withRedisSocket` connected with `new InetSocketAddress(configuredRedisHost, 6379)` and the script had no `configuredRedisPort` binding. Any non-default Redis port worked for blacklist filters but failed here.

## Fix Applied

- Added `configuredRedisPort` near the other Redis bindings:
  - `def configuredRedisPort = binding.hasVariable('redisPort') ? (redisPort as String) : '6379'`
- Replaced the hardcoded socket connect port with:
  - `new InetSocketAddress(configuredRedisHost, configuredRedisPort.toInteger())`
- Verified there were no other hardcoded `6379` connect paths left in this file beyond the new default value.

> [!success] Result
> `TokenReferenceFilter` now uses the same route-configurable Redis port behavior as the sibling blacklist filters, while preserving `6379` as the default fallback.

## Validation

- Confirmed sibling reference patterns in:
  - `shared/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `shared/openig_home/scripts/groovy/SpaBlacklistGuardFilter.groovy`
- Re-ran a targeted search on `TokenReferenceFilter.groovy` to confirm only the new default binding still references `6379`
- Committed as `05341f9` with message:
  - `fix(redis): use configuredRedisPort instead of hardcoded 6379 in TokenReferenceFilter`

> [!tip] Follow-up
> Keep Redis host, port, auth, ACL user, and key-prefix handling aligned across shared Groovy filters so route-level isolation changes only need one configuration update path.

## Files Changed

- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `docs/obsidian/debugging/2026-03-25-token-reference-filter-configured-redis-port.md`
