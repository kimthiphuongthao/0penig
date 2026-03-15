---
title: Stack B backchannel audience validation
tags:
  - debugging
  - openig
  - keycloak
  - stack-b
date: 2026-03-13
status: done
---

# Stack B backchannel audience validation

Context: fixed [[OpenIG]] backchannel logout audience validation for [[Stack B]] in [stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy).

## Root cause

- Stack B exposes two backchannel logout routes: app3 for Redmine and app4 for Jellyfin.
- Redmine uses client ID `openig-client-b` from [stack-b/openig_home/config/routes/02-redmine.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/02-redmine.json).
- Jellyfin uses client ID `openig-client-b-app4` from [stack-b/openig_home/config/routes/01-jellyfin.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/01-jellyfin.json).
- The shared handler only accepted a single expected audience, so valid Jellyfin logout tokens from [[Keycloak]] were rejected.

## Fix

- Changed `EXPECTED_AUDIENCE` to `EXPECTED_AUDIENCES = ['openig-client-b', 'openig-client-b-app4']`.
- Updated `validateClaims(...)` call to pass the audience list.
- Extended the audience check so it now supports either:
  - expected audience as `String`
  - expected audiences as `List`

> [!success] Confirmed scope
> Only audience validation was changed.
> No Redis, issuer, JWKS, signature, route, or blacklist logic was modified.

> [!tip] Reference pattern
> The new audience handling follows the same list-based validation approach already used in [[Stack C]].

## Current state

- Backchannel logout for Redmine still accepts `openig-client-b`.
- Backchannel logout for Jellyfin now accepts `openig-client-b-app4`.
- The shared handler remains compatible with both string and list expected-audience inputs.

## Files changed

- [stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
