---
title: Stack A BackchannelLogoutHandler consolidation correction
tags:
  - debugging
  - openig
  - keycloak
  - stack-a
  - pattern-consolidation
date: 2026-03-17
status: done
---

# Stack A BackchannelLogoutHandler consolidation correction

Context: Step 3 pattern consolidation had been marked complete, but [[Stack A]] still used the pre-consolidation [[OpenIG]] backchannel logout handler while [[Stack B]] and [[Stack C]] already used the shared consolidated template.

## Root cause

- The earlier Step 3 completion status was inaccurate for [stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy).
- Stack A still had `@Field static volatile` JWKS cache state and a single-string audience validation path.
- [stack-a/openig_home/config/routes/00-backchannel-logout-app1.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/routes/00-backchannel-logout-app1.json) still lacked the route `args` block used by the consolidated pattern.

## Changes made

- Replaced Stack A BackchannelLogoutHandler with the Stack B canonical Groovy template exactly.
- Verified the Stack A Groovy file is byte-identical to [stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy).
- Added route-scoped `args` to Stack A for `audiences`, `redisHost`, `jwksUri`, `issuer`, and `ttlSeconds`.

> [!success] Confirmed pattern markers
> Stack A now contains `globals.compute('jwks_cache')` and the polymorphic `def expectedAudience` validation signatures used by the consolidated template.

> [!tip] Stack alignment
> Stack A now follows the same backchannel logout script structure as [[Stack B]] and [[Stack C]], while keeping Stack A-specific route values in JSON.

> [!warning] Runtime verification gap
> `docker restart sso-openig-1 sso-openig-2` could not be executed from this session because the sandbox denied access to `/Users/duykim/.docker/run/docker.sock`.
> The requested log check was therefore not completed here.

## Current state

- Stack A backchannel logout script uses the shared consolidated handler template.
- Stack A route `00-backchannel-logout-app1` now passes the expected audience and environment-specific dependencies through handler args.
- Source-level verification is complete.
- Runtime restart and log validation still need to be run from an environment with Docker socket access.

## Next steps

- Run `docker restart sso-openig-1 sso-openig-2`.
- Run `docker logs sso-openig-1 2>&1 | grep -E 'Loaded the route|ERROR'`.
- Optionally repeat the same log check for `sso-openig-2`.

## Files changed

- [[OpenIG]]
  File: [stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
- [[OpenIG]]
  File: [stack-a/openig_home/config/routes/00-backchannel-logout-app1.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/routes/00-backchannel-logout-app1.json)
