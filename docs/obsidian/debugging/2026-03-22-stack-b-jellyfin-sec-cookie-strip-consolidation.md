---
title: Stack B Jellyfin SEC COOKIE STRIP consolidation
tags:
  - stack-b
  - openig
  - jellyfin
  - cookie
  - security
date: 2026-03-22
status: fixed
---

# Stack B Jellyfin SEC COOKIE STRIP consolidation

Related: [[Stack B]] [[OpenIG]] [[Jellyfin]] [[Keycloak]]

## Root cause

`[[JellyfinTokenInjector.groovy]]` carried its own exact-name gateway cookie stripping for `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C`.
That duplicated the new SEC-COOKIE-STRIP route pattern and kept the Jellyfin path inconsistent with the shared OpenIG filter approach.

## Fix

Updated `stack-b/openig_home/config/routes/01-jellyfin.json`:

- Added `StripGatewaySessionCookiesApp4` as a `ScriptableFilter` using `StripGatewaySessionCookies.groovy`.
- Inserted the shared strip filter immediately after `SessionBlacklistFilterApp4` and before `VaultCredentialFilterJellyfin` and `JellyfinTokenInjectorFilter`.

Updated `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy`:

- Removed the local `GATEWAY_SESSION_COOKIE_NAMES` constant.
- Removed the local `stripGatewaySessionCookies` closure.
- Removed both `stripGatewaySessionCookies(request)` call sites so cookie stripping now happens in the route chain.

> [!success]
> Stack B Jellyfin now uses the shared SEC-COOKIE-STRIP route filter instead of an embedded helper inside the token injector.

## Decision rationale

`[[OpenIG]]` resolves the gateway session before downstream Groovy filters run, so stripping `IG_SSO*` in the route chain after `SessionBlacklistFilterApp4` is safe.
Centralizing the logic in `StripGatewaySessionCookies.groovy` removes duplicate cookie-name handling from the Jellyfin injector and keeps the contract aligned with the other stack refactors.

> [!tip]
> Keep the shared strip filter upstream of application-specific injectors so backend requests never see gateway session cookies, regardless of later auth or token logic.

> [!warning]
> This refactor intentionally left all other `JellyfinTokenInjector.groovy` logic unchanged; only the duplicated gateway cookie strip constant, closure, and call sites were removed.

## Verification

- Parsed `stack-b/openig_home/config/routes/01-jellyfin.json` successfully as JSON.
- Confirmed `StripGatewaySessionCookiesApp4` is present in the heap and ordered after `SessionBlacklistFilterApp4` in the handler chain.
- Confirmed there are no remaining `GATEWAY_SESSION_COOKIE_NAMES` or `stripGatewaySessionCookies` references under `stack-b/openig_home/scripts/groovy/`.

## Files changed

- `stack-b/openig_home/config/routes/01-jellyfin.json`
- `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy`
- `docs/obsidian/debugging/2026-03-22-stack-b-jellyfin-sec-cookie-strip-consolidation.md`
