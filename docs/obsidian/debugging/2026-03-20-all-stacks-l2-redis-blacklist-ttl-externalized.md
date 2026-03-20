---
title: All Stacks L-2 Redis Blacklist TTL Externalized
tags:
  - debugging
  - openig
  - redis
  - backchannel-logout
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-20
status: done
---

# All Stacks L-2 Redis Blacklist TTL Externalized

Context: fixed backlog item `L-2` so [[OpenIG]] backchannel logout TTL is no longer hardcoded only in route JSON. All three `BackchannelLogoutHandler.groovy` copies now read `ttlSeconds` from the route binding with a `28800` fallback, and every backchannel route passes the TTL through `REDIS_BLACKLIST_TTL`.

## Root cause

- [stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy), [stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy), and [stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy) still carried an internal `REDIS_BLACKLIST_TTL_SECONDS = 28800` constant, so route-level configurability was incomplete.
- [stack-b/openig_home/config/routes/00-backchannel-logout-app3.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/00-backchannel-logout-app3.json), [stack-b/openig_home/config/routes/00-backchannel-logout-app4.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/00-backchannel-logout-app4.json), [stack-c/openig_home/config/routes/00-backchannel-logout-app5.json](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/config/routes/00-backchannel-logout-app5.json), and [stack-c/openig_home/config/routes/00-backchannel-logout-app6.json](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/config/routes/00-backchannel-logout-app6.json) did not pass `ttlSeconds` at all.
- [stack-a/openig_home/config/routes/00-backchannel-logout-app1.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/routes/00-backchannel-logout-app1.json) passed a literal `28800`, so Stack A still required a JSON edit to change the TTL.

## Changes made

- All three `BackchannelLogoutHandler.groovy` files now read `ttlSeconds` from the script binding with `binding.hasVariable('ttlSeconds') ? (ttlSeconds as int) : 28800`.
- Redis `SET ... EX ...` now uses the resolved binding value instead of a Groovy constant, keeping the default inside the handler as a fallback only.
- All five backchannel route JSON files now pass `ttlSeconds`.
- The route argument uses `${env['REDIS_BLACKLIST_TTL'] != null ? env['REDIS_BLACKLIST_TTL'] : '28800'}` so the effective TTL can be changed without editing the route file again.

> [!success] Restart verification
> `docker restart sso-openig-1 sso-openig-2 sso-b-openig-1 sso-b-openig-2 stack-c-openig-c1-1 stack-c-openig-c2-1` completed on `2026-03-20`.
> `docker inspect -f '{{.Name}} {{.State.Status}}' ...` reported all six OpenIG containers as `running`.
> `docker logs sso-openig-1 2>&1 | grep 'Loaded the route'` showed `00-backchannel-logout-app1` loading successfully after restart.

> [!tip] Effective TTL precedence
> Runtime precedence is now route arg `ttlSeconds` -> environment-backed EL expression `REDIS_BLACKLIST_TTL` -> Groovy fallback `28800`.

## Current state

- [[Stack A]], [[Stack B]], and [[Stack C]] now share the same TTL behavior for backchannel logout blacklist entries.
- The TTL can be tuned through environment without reopening the Groovy scripts.
- `L-2` is resolved at both layers: route configuration and script execution.

## Next steps

- If a non-default TTL is required, set `REDIS_BLACKLIST_TTL` for the relevant OpenIG containers and restart them.
- Keep the Groovy fallback at `28800` unless `JwtSession.sessionTimeout` or the revocation-retention requirement changes.

## Files changed

- [[OpenIG]]
  File: [stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
- [[OpenIG]]
  File: [stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
- [[OpenIG]]
  File: [stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
- [[OpenIG]]
  File: [stack-a/openig_home/config/routes/00-backchannel-logout-app1.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/routes/00-backchannel-logout-app1.json)
- [[OpenIG]]
  File: [stack-b/openig_home/config/routes/00-backchannel-logout-app3.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/00-backchannel-logout-app3.json)
- [[OpenIG]]
  File: [stack-b/openig_home/config/routes/00-backchannel-logout-app4.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/00-backchannel-logout-app4.json)
- [[OpenIG]]
  File: [stack-c/openig_home/config/routes/00-backchannel-logout-app5.json](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/config/routes/00-backchannel-logout-app5.json)
- [[OpenIG]]
  File: [stack-c/openig_home/config/routes/00-backchannel-logout-app6.json](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/config/routes/00-backchannel-logout-app6.json)
- [[OpenIG]]
  File: [docs/fix-tracking/master-backlog.md](/Volumes/OS/claude/openig/sso-lab/docs/fix-tracking/master-backlog.md)
- [[OpenIG]]
  File: [docs/obsidian/debugging/2026-03-20-all-stacks-l2-redis-blacklist-ttl-externalized.md](/Volumes/OS/claude/openig/sso-lab/docs/obsidian/debugging/2026-03-20-all-stacks-l2-redis-blacklist-ttl-externalized.md)
