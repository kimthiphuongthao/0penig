---
title: Stack A SessionBlacklistFilter consolidation
tags:
  - debugging
  - openig
  - stack-a
  - groovy
  - redis
date: 2026-03-16
status: done
---

# Stack A SessionBlacklistFilter consolidation

Context: consolidated duplicate [[OpenIG]] Stack A blacklist filters into one parameterized Groovy script for [[Stack A]] route configs [stack-a/openig_home/config/routes/01-wordpress.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/routes/01-wordpress.json) and [stack-a/openig_home/config/routes/02-app2.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/routes/02-app2.json).

## Root cause

- Stack A carried two separate session blacklist filters with the same Redis fail-closed behavior and only route-specific constants changed.
- OpenIG 6 binds route `args` entries as top-level Groovy variables, so a reusable script must read `clientEndpoint`, `sessionCacheKey`, and origin settings directly from the binding instead of expecting an `args` map.

## Changes made

- Replaced [stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy) with a parameterized template that:
  - reads `clientEndpoint`, `sessionCacheKey`, `canonicalOriginEnvVar`, and `canonicalOrigin` from top-level Groovy variables
  - preserves the existing [[Redis]] blacklist lookup, session invalidation, redirect flow, and fail-closed HTTP 500 behavior
  - throws `IOException` on Redis response EOF instead of silently accepting truncated lines
  - decodes JWT payloads with `Base64.getUrlDecoder().decode(...)`
- Updated the WordPress route to pass Stack A app1 args.
- Updated the whoami route to reuse the shared script and pass Stack A app2 args.
- Removed the duplicate app2-specific blacklist script [stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy).

> [!success] Scope
> Only Stack A [[OpenIG]] routes and Groovy scripts were changed. No target application code, database config, or [[Keycloak]] configuration was modified.

> [!warning] Verification blocker
> Docker daemon access is denied in this Codex sandbox, so `docker restart sso-openig-1 sso-openig-2` and the requested `docker logs` verification could not be executed here.

> [!tip] Route args now passed
> `01-wordpress.json` passes `/openid/app1`, `oidc_sid`, `http://wp-a.sso.local`, `CANONICAL_ORIGIN_APP1`.
> `02-app2.json` passes `/openid/app2`, `oidc_sid_app2`, `http://whoami-a.sso.local`, `CANONICAL_ORIGIN_APP2`.

## Current state

- Stack A uses one shared `SessionBlacklistFilter.groovy` script for both routes.
- Both edited route files are valid JSON.
- Runtime reload and route-load log verification are still pending in an environment with Docker socket access.

## Files changed

- [stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy)
- [stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy)
- [stack-a/openig_home/config/routes/01-wordpress.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/routes/01-wordpress.json)
- [stack-a/openig_home/config/routes/02-app2.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/routes/02-app2.json)
