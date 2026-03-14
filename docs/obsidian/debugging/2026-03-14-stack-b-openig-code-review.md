---
title: Stack B OpenIG code review
tags:
  - debugging
  - openig
  - keycloak
  - stack-b
  - code-review
date: 2026-03-14
status: done
---

# Stack B OpenIG code review

Context: reviewed Stack B [[OpenIG]] implementation for login flow correctness, token injection, logout/revocation, backchannel logout, route/filter interactions, timeout behavior, and regressions.

## Verdict

Implementation is close, but not acceptable as-is for logout and backchannel correctness.

> [!warning] Material issues
> The review found four material problems:
> 1. backchannel blacklist TTL is shorter than the [[JwtSession]] lifetime
> 2. subject-based backchannel logout is accepted but not enforced reliably
> 3. Jellyfin logout is wired to the wrong OIDC callback namespace
> 4. several Redis and upstream auth-path calls have no timeout guard

## Findings

### 1. Backchannel blacklist expires too early

- [BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy) stores `blacklist:<sid>` with `EX 3600`.
- [config.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/config.json) keeps the shared [[JwtSession]] alive for `8 hours`.
- Result: a backchannel-logged-out browser session can become valid again after one hour if the JWT session cookie is still present.

### 2. `sub` logout tokens do not line up with request-time blacklist checks

- [BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy) accepts either `sid` or `sub` and writes one Redis key.
- [SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy) and [SessionBlacklistFilterApp4.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy) derive a single identifier from the stored ID token, preferring `sid`.
- Result: a valid subject-wide logout token can blacklist `sub`, while the active session still checks `sid`, so the session survives.

### 3. Jellyfin logout still looks in the app3 session namespace

- [SloHandlerJellyfin.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy) resolves the ID token from `/openid/app3`.
- [01-jellyfin.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/01-jellyfin.json) registers Jellyfin on `/openid/app4`.
- Result: `id_token_hint` is often missing on Jellyfin logout, which weakens RP-initiated logout and can break redirect behavior.

### 4. Redis and Dotnet auth-path calls are missing timeout protection

- [SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy), [SessionBlacklistFilterApp4.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy), and [BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy) use raw Redis sockets without connect/read timeouts.
- [DotnetCredentialInjector.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/DotnetCredentialInjector.groovy) performs Vault and Dotnet login calls without `connectTimeout` and `readTimeout`.
- Result: a stalled Redis, Vault, or upstream app can hang login/logout traffic longer than the route proxy timeout.

> [!tip] Follow-up
> Keep the per-app logout wiring aligned with route `clientEndpoint` and `clientId`, and keep revocation TTL at least as long as the shared session lifetime.

## Files reviewed

- [stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
- [stack-b/openig_home/scripts/groovy/DotnetCredentialInjector.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/DotnetCredentialInjector.groovy)
- [stack-b/openig_home/scripts/groovy/DotnetSloHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/DotnetSloHandler.groovy)
- [stack-b/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy)
- [stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy)
- [stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy)
- [stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy)
- [stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp3.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp3.groovy)
- [stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy)
- [stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy)
- [stack-b/openig_home/scripts/groovy/SloHandlerRedmine.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandlerRedmine.groovy)
- [stack-b/openig_home/scripts/groovy/VaultCredentialFilterJellyfin.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/VaultCredentialFilterJellyfin.groovy)
- [stack-b/openig_home/scripts/groovy/VaultCredentialFilterRedmine.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/VaultCredentialFilterRedmine.groovy)
- [stack-b/openig_home/config/config.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/config.json)
- [stack-b/openig_home/config/routes/00-backchannel-logout-app3.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/00-backchannel-logout-app3.json)
- [stack-b/openig_home/config/routes/00-backchannel-logout-app4.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/00-backchannel-logout-app4.json)
- [stack-b/openig_home/config/routes/00-dotnet-logout.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/00-dotnet-logout.json)
- [stack-b/openig_home/config/routes/00-jellyfin-logout.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/00-jellyfin-logout.json)
- [stack-b/openig_home/config/routes/00-redmine-logout.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/00-redmine-logout.json)
- [stack-b/openig_home/config/routes/01-dotnet.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/01-dotnet.json)
- [stack-b/openig_home/config/routes/01-jellyfin.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/01-jellyfin.json)
- [stack-b/openig_home/config/routes/02-redmine.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/02-redmine.json)
