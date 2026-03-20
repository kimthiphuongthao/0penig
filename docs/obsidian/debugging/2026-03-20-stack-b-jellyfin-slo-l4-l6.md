---
title: Stack B Jellyfin SLO L-4 and L-6
tags:
  - debugging
  - stack-b
  - openig
  - jellyfin
  - slo
  - keycloak
date: 2026-03-20
status: done
---

# Stack B Jellyfin SLO L-4 and L-6

Context: fixed the remaining Stack B [[Jellyfin]] logout issues so [[OpenIG]] no longer depends on `session.hashCode()` for device correlation and still completes browser logout through [[Keycloak]] when `id_token_hint` is unavailable.

## Root cause

- [stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy) only built the Keycloak end-session URL when an `id_token` was present. If the token was missing, the handler cleared local state and redirected straight back to Jellyfin.
- Stack B Jellyfin used `session.hashCode()` as a fallback `deviceId`. That value is not stable across restarts or JVM instances, so logout/device correlation was not deterministic.

## Changes made

- [stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy) now always redirects through the Keycloak end-session endpoint for app4 and appends `id_token_hint` only when it is available.
- [stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy) and [stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy) now share the same `deviceId` derivation: `SHA-256("jellyfin-<sub>")`, truncated to 32 hex chars.
- [stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy) now persists `jellyfin_user_sub` in session and clears it together with the rest of the Jellyfin session markers on `401`.
- [stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy) can rebuild the same stable `deviceId` from the stored `jellyfin_user_sub` or the `sub` claim inside the restored `id_token` before calling Jellyfin `/Sessions/Logout`.

> [!success] Restart verification
> `docker restart sso-b-openig-1 sso-b-openig-2` completed on `2026-03-20`. `docker logs --since 2026-03-20T02:08:00.590644839Z sso-b-openig-1 2>&1 | grep -E 'Loaded the route|ERROR'` showed all six Stack B routes loading with no fresh `ERROR` lines after startup.

> [!warning] Raw log buffer noise
> The unscoped `docker logs sso-b-openig-1 2>&1 | grep -E 'Loaded the route|ERROR'` output still includes older unrelated entries already present in the container log buffer, including `SessionFilter` save failures, an OAuth2 `invalid_grant`, and the known Jellyfin WebSocket `http://` -> `ws://` issue. Those were not introduced by this fix.

## Current state

- [[Stack B]] Jellyfin logout now covers both the upstream Jellyfin session and the browser-side [[Keycloak]] logout path even when `id_token_hint` is absent.
- Stable Jellyfin `deviceId` derivation is now consistent between login-time token injection and the dedicated Jellyfin logout handler.
- Remaining Stack B Jellyfin-specific debt is the known WebSocket route issue, not logout/device-ID handling.

## Files changed

- [[OpenIG]]
  File: [stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy)
- [[OpenIG]]
  File: [stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy)
- [[Stack B]]
  File: [docs/obsidian/stacks/stack-b.md](/Volumes/OS/claude/openig/sso-lab/docs/obsidian/stacks/stack-b.md)
- [[OpenIG]]
  File: [docs/fix-tracking/master-backlog.md](/Volumes/OS/claude/openig/sso-lab/docs/fix-tracking/master-backlog.md)
- [[OpenIG]]
  File: [docs/audit/2026-03-17-production-readiness-gap-report.md](/Volumes/OS/claude/openig/sso-lab/docs/audit/2026-03-17-production-readiness-gap-report.md)
- [[OpenIG]]
  File: [.claude/rules/gotchas.md](/Volumes/OS/claude/openig/sso-lab/.claude/rules/gotchas.md)
