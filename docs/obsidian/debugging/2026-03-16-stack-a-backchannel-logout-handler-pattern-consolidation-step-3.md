---
title: Stack A BackchannelLogoutHandler pattern consolidation step 3
tags:
  - debugging
  - openig
  - keycloak
  - stack-a
  - pattern-consolidation
date: 2026-03-16
status: done
---

# Stack A BackchannelLogoutHandler pattern consolidation step 3

Context: consolidated [[OpenIG]] Stack A backchannel logout config and JWKS caching in [stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy) and wired script args in [stack-a/openig_home/config/routes/00-backchannel-logout-app1.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/routes/00-backchannel-logout-app1.json).

## Root cause

- Stack A still used script-level static JWKS cache fields instead of the shared `globals.compute(...)` cache pattern.
- Audience matching only handled a single configured audience value.
- Redis host, JWKS URI, and issuer were hardcoded in the Groovy script instead of being passed through route args.
- Redis blacklist TTL was still lower than the requested consolidation target.

## Changes made

- Moved Stack A runtime configuration to top-level Groovy binding variables: `audiences`, `redisHost`, `jwksUri`, and `issuer`.
- Replaced `@Field` JWKS cache state with an atomic `globals.compute('jwks_cache')` loader and explicit cache invalidation on `kid` miss before one retry.
- Added the Stack B polymorphic audience matcher as `validateClaims(Map payload, def expectedAudience)` and used it from the main logout-claim validation path.
- Increased the Redis blacklist TTL to `28800` seconds while keeping the existing Redis write flow and JWT validation flow intact.
- Added ScriptableHandler `args` for Stack A route config so the script no longer depends on embedded realm-specific constants.

> [!success] Confirmed scope
> Only Stack A [[OpenIG]] files were changed.
> No target application code, [[Keycloak]] configuration, or non-gateway components were modified.

> [!tip] Pattern alignment
> Stack A now follows the same audience-polymorphic validation shape as [[Stack B]] while using route-driven configuration values.

> [!warning] Verification gap
> Validation in this session was limited to source diff review. No runtime Groovy execution or end-to-end backchannel logout test was run here.

## Current state

- Stack A backchannel logout reads issuer, JWKS URI, Redis host, and audiences from route-provided args.
- JWKS cache refresh is atomic at the global map level and retries once after clearing cache on unknown `kid`.
- Audience validation now accepts either a single expected audience or a list of expected audiences without changing the rest of claim validation.
- Redis blacklist entries now use an `EX 28800` TTL in seconds.

## Files changed

- [[OpenIG]]
  File: [stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
- [[OpenIG]]
  File: [stack-a/openig_home/config/routes/00-backchannel-logout-app1.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/routes/00-backchannel-logout-app1.json)
