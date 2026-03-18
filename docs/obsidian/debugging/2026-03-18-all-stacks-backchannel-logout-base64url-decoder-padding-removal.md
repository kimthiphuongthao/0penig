---
title: Backchannel logout base64url decoder padding removal across all stacks
tags:
  - debugging
  - openig
  - security
  - stack-a
  - stack-b
  - stack-c
  - jwt
date: 2026-03-18
status: done
---

# Backchannel logout base64url decoder padding removal across all stacks

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Implemented confirmed `[M-12]` from `.omc/plans/phase2-security-hardening.md` directly with no investigation pass.
- Scope was limited to the three OpenIG backchannel logout handlers.
- Requirement was to replace the `base64UrlDecode` closure body only and leave all callers unchanged.

> [!warning]
> No other helper functions, JWT validation paths, or route files were modified for this task.

## What changed

- In `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, removed the manual base64url padding logic from `base64UrlDecode` and changed the closure to call `Base64.getUrlDecoder().decode(input)` directly.
- In `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, removed the manual base64url padding logic from `base64UrlDecode` and changed the closure to call `Base64.getUrlDecoder().decode(input)` directly.
- In `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, removed the manual base64url padding logic from `base64UrlDecode` and changed the closure to call `Base64.getUrlDecoder().decode(input)` directly.

> [!tip]
> This keeps decoding behavior aligned with the platform decoder instead of normalizing malformed input in the handler.

## Verification

> [!success]
> Ran `docker restart sso-openig-1 sso-openig-2 sso-b-openig-1 sso-b-openig-2 stack-c-openig-c1-1 stack-c-openig-c2-1` successfully.

> [!success]
> Ran `docker logs sso-openig-1 2>&1 | grep 'Loaded the route'` and confirmed route registration lines for `00-wp-logout`, `00-backchannel-logout-app1`, `01-wordpress`, and `02-app2`.

## Current state

- All three stacks now use the direct JDK base64url decoder path in backchannel logout handling.
- The `base64UrlDecode` callers were left untouched in all three files.
- OpenIG containers restarted cleanly after the change and Stack A route load messages were present in logs.

## Files changed

- `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `docs/obsidian/debugging/2026-03-18-all-stacks-backchannel-logout-base64url-decoder-padding-removal.md`
