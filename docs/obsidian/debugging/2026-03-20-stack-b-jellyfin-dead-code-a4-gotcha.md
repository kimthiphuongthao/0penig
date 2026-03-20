---
title: Stack B Jellyfin dead code cleanup and A4 gotcha correction
tags:
  - debugging
  - stack-b
  - openig
  - jellyfin
date: 2026-03-20
status: done
---

# Stack B Jellyfin dead code cleanup and A4 gotcha correction

Context: cleanup for [[OpenIG]] Stack B Jellyfin logout/session handling plus a documentation correction in `.claude/rules/gotchas.md`. [[Keycloak]] and [[Vault]] were not changed in this task.

> [!success] Completed
> Commit `b53c239` removes the dead `user_info` session read from `SloHandlerJellyfin.groovy` and corrects the A4 JwtSession overflow gotcha from "HTTP 500" to "silent state loss".

## What changed

- Removed the dead `oauth2Entry?.get('user_info')?.get('sub')` read from `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`.
- Kept the `extractSubFromIdToken` fallback logic unchanged.
- Corrected the A4 gotcha to match OpenIG `SessionFilter.handleResult()` behavior: downstream response continues, session save fails, and no updated `Set-Cookie` is emitted.

## Decision

> [!tip]
> `JellyfinTokenInjector.groovy` was inspected for the same dead pattern but the current branch does not contain an `oauth2` session `user_info` read there. The existing `attributes.openid?.get('user_info')?.get('sub')` access is request-scoped and was left unchanged.

## Current state

- [[Stack B]] Jellyfin SLO now relies on stored `jellyfin_user_sub` or `id_token` fallback only.
- The A4 gotcha now documents the real failure mode as silent session state loss, not HTTP 500.
- TokenReferenceFilter remains the mitigation that prevents this overflow scenario in the current implementation.

## Files changed

- `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
- `.claude/rules/gotchas.md`

## Next steps

> [!warning]
> If a future change reintroduces reading `user_info` from serialized OAuth2 session blobs, it should be treated as dead code unless OpenIG session serialization behavior changes and is re-verified from source.
