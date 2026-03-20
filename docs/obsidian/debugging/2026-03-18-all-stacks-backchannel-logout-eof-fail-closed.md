---
title: Backchannel logout EOF fail-closed across all stacks
tags:
  - debugging
  - openig
  - redis
  - security
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-18
status: done
---

# Backchannel logout EOF fail-closed across all stacks

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

Context: implemented the confirmed `[M-11]` item from `.omc/plans/phase2-security-hardening.md` directly with no investigation pass. Scope was limited to the three OpenIG Groovy backchannel logout handlers.

## Root cause

`readRespLine` treated Redis socket EOF as a normal loop exit. If the connection dropped before a full RESP line was received, backchannel logout could continue with a truncated or empty reply instead of failing closed.

> [!warning]
> The fix was intentionally constrained to one line in each file. No other Redis, JWT, TTL, or request-handling logic changed.

## What changed

- `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:52` now throws `IOException('Unexpected EOF from Redis')` instead of breaking on `current == -1`.
- `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:51` now throws `IOException('Unexpected EOF from Redis')` instead of breaking on `current == -1`.
- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:51` now throws `IOException('Unexpected EOF from Redis')` instead of breaking on `current == -1`.

## Verification

> [!success]
> `docker restart sso-openig-1 sso-openig-2 sso-b-openig-1 sso-b-openig-2 stack-c-openig-c1-1 stack-c-openig-c2-1` completed successfully.

> [!success]
> `docker logs sso-openig-1 2>&1 | grep 'Loaded the route'` returned the expected route load lines, including:
> `00-wp-logout`, `00-backchannel-logout-app1`, `01-wordpress`, `02-app2`.

## Current state

- All three stacks now fail closed when Redis drops the connection mid-RESP line during backchannel logout processing.
- A Redis EOF now propagates into the existing runtime error path so OpenIG returns an error instead of silently accepting partial reads.

> [!tip]
> Keep this behavior aligned with any future RESP helpers. Silent EOF handling should remain disallowed for blacklist writes.
