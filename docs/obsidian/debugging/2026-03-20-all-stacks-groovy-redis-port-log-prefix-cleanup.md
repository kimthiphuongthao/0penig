---
title: All Stacks Groovy Redis Port and Log Prefix Cleanup
tags:
  - debugging
  - openig
  - stack-a
  - stack-b
  - stack-c
  - redis
  - groovy
date: 2026-03-20
status: done
---

# All Stacks Groovy Redis Port and Log Prefix Cleanup

Context: closed the remaining low-severity Groovy cleanup items for [[OpenIG]] across [[Stack A]], [[Stack B]], and [[Stack C]] without changing route logic or target applications.

## Root cause

- `[L-1]` remained open because the revocation Groovy scripts still embedded Redis port `6379` as a literal even though `redisHost` was already parameterized per route.
- `[L-3]` remained open because some Groovy scripts still logged with shortened or unbracketed prefixes, which made cross-stack log filtering inconsistent.

## Changes made

- [stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy), [stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy), and [stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy) now read `redisPort` from route args, then `REDIS_PORT`, then default `6379`.
- [stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy), [stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy), and [stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy) now use the same `redisPort` fallback chain for Redis blacklist writes.
- [stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy), [stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy), and [stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy) now use `[TokenReferenceFilter]` instead of the previous `TokenRef` variants.
- [stack-b/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy) now emits `[JellyfinResponseRewriter]` in both error paths.

> [!success] Restart verification
> `docker restart sso-openig-1 sso-openig-2 sso-b-openig-1 sso-b-openig-2 stack-c-openig-c1-1 stack-c-openig-c2-1` completed on `2026-03-20`.
> `docker logs sso-openig-1 2>&1 | grep 'Loaded the route'` showed Stack A routes reloading.
> Stack B and Stack C route-load checks also returned the expected route sets after restart.

> [!tip]
> Future route-level Redis overrides should set `redisPort` alongside `redisHost` so the Groovy revocation clients stay fully env/arg driven and do not drift back to literals.

## Current state

- `[L-1]` is closed in all three stacks: the revocation Groovy scripts no longer assume Redis listens on `6379`.
- `[L-3]` is closed for the current Groovy set: remaining nonstandard prefixes were normalized to `[ClassName]`.
- Remaining low-priority cleanup is now route-level `redisTtl` externalization and Vault credential filter deduplication, not Groovy Redis/log-prefix drift.

## Files changed

- [[Stack A]]
  File: [stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy)
- [[Stack A]]
  File: [stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
- [[Stack A]]
  File: [stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy)
- [[Stack B]]
  File: [stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy)
- [[Stack B]]
  File: [stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
- [[Stack B]]
  File: [stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy)
- [[Stack B]]
  File: [stack-b/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy)
- [[Stack C]]
  File: [stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy)
- [[Stack C]]
  File: [stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
- [[Stack C]]
  File: [stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy)
- [[OpenIG]]
  File: [docs/fix-tracking/master-backlog.md](/Volumes/OS/claude/openig/sso-lab/docs/fix-tracking/master-backlog.md)
- [[OpenIG]]
  File: [docs/audit/2026-03-17-production-readiness-gap-report.md](/Volumes/OS/claude/openig/sso-lab/docs/audit/2026-03-17-production-readiness-gap-report.md)
- [[Stack A]]
  File: [docs/obsidian/stacks/stack-a.md](/Volumes/OS/claude/openig/sso-lab/docs/obsidian/stacks/stack-a.md)
- [[Stack B]]
  File: [docs/obsidian/stacks/stack-b.md](/Volumes/OS/claude/openig/sso-lab/docs/obsidian/stacks/stack-b.md)
- [[Stack C]]
  File: [docs/obsidian/stacks/stack-c.md](/Volumes/OS/claude/openig/sso-lab/docs/obsidian/stacks/stack-c.md)
