---
title: Stack B and C BackchannelLogoutHandler pattern consolidation step 3
tags:
  - debugging
  - openig
  - keycloak
  - stack-b
  - stack-c
  - pattern-consolidation
date: 2026-03-17
status: done
---

# Stack B and C BackchannelLogoutHandler pattern consolidation step 3

Context: applied the confirmed [[OpenIG]] Stack A backchannel logout template to [[Stack B]] and [[Stack C]], then wired route-level args for each backchannel logout endpoint.

## Root cause

- [[Stack B]] and [[Stack C]] still used older per-stack BackchannelLogoutHandler variants instead of the parameterized Stack A template.
- The Groovy handlers still embedded issuer, JWKS URI, and audience defaults instead of reading them from ScriptableHandler args.
- The requested consolidation target required the shared `globals.compute('jwks_cache')` pattern and route-driven config.

## Changes made

- Replaced [stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy) with the Stack A template and changed only the `redisHost` fallback from `redis-a` to `redis-b`.
- Replaced [stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy) with the Stack A template and changed only the `redisHost` fallback from `redis-a` to `redis-c`.
- Added ScriptableHandler `args` blocks to the Stack B routes for app3 and app4 with route-specific audiences plus shared Redis host, JWKS URI, and issuer values.
- Added ScriptableHandler `args` blocks to the Stack C routes for app5 and app6 with route-specific audiences plus shared Redis host, JWKS URI, and issuer values.

> [!success] Source verification
> Local diff verification confirmed Stack B and Stack C are identical to the Stack A template except for the single `configuredRedisHost` fallback line in each file.

> [!warning] Runtime verification gap
> `docker restart` and `docker logs` could not be executed in this session because the sandbox could not access `/Users/duykim/.docker/run/docker.sock`.

> [!tip] Pattern alignment
> B and C now use the same route-parameterized logout validation shape as [[Stack A]], including the shared global JWKS cache and the extended Redis TTL.

## Current state

- [[Stack B]] app3 and app4 routes pass `audiences`, `redisHost`, `jwksUri`, and `issuer` into the common Groovy handler.
- [[Stack C]] app5 and app6 routes pass `audiences`, `redisHost`, `jwksUri`, and `issuer` into the common Groovy handler.
- Both stacks now share the same JWKS cache lifecycle and JWT validation flow as [[Stack A]].
- Runtime restart and route-load log validation still need to be run from an environment with Docker daemon access.

## Files changed

- [[OpenIG]]
  File: [stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
- [[OpenIG]]
  File: [stack-b/openig_home/config/routes/00-backchannel-logout-app3.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/00-backchannel-logout-app3.json)
- [[OpenIG]]
  File: [stack-b/openig_home/config/routes/00-backchannel-logout-app4.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/00-backchannel-logout-app4.json)
- [[OpenIG]]
  File: [stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
- [[OpenIG]]
  File: [stack-c/openig_home/config/routes/00-backchannel-logout-app5.json](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/config/routes/00-backchannel-logout-app5.json)
- [[OpenIG]]
  File: [stack-c/openig_home/config/routes/00-backchannel-logout-app6.json](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/config/routes/00-backchannel-logout-app6.json)
