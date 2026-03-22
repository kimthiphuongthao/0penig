---
title: Stack A SEC COOKIE STRIP
tags:
  - stack-a
  - openig
  - cookie
  - security
date: 2026-03-22
status: fixed
---

# Stack A SEC COOKIE STRIP

Related: [[Stack A]] [[OpenIG]] [[Keycloak]]

## Root cause

`[[OpenIG]]` loads the `JwtSession` from `IG_SSO*` before Groovy filters run, but Stack A was still forwarding gateway session cookies to [[WordPress]] and the WhoAmI backend.
That exposed `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C` to applications that do not need OpenIG session cookies.

## Fix

Updated `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`:

- Extended the existing cookie-strip helper to remove exact-match gateway cookie names `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C`.
- Kept the strip point before upstream forwarding, alongside the existing WordPress cookie filtering.

Updated `stack-a/openig_home/config/routes/02-app2.json` and `stack-a/openig_home/scripts/groovy/StripGatewaySessionCookies.groovy`:

- Added a new post-blacklist `ScriptableFilter` for the WhoAmI route.
- Stripped exact-match gateway cookie names `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C` before the request reaches the backend handler.

> [!success]
> Stack A backend requests for [[WordPress]] and WhoAmI no longer forward OpenIG session cookies from these gateway paths.

## Decision rationale

The strip is safe in Groovy because the gateway session has already been restored by framework-level cookie processing.
Placing the WhoAmI strip filter after `SessionBlacklistFilter` preserves session-dependent checks while preventing backend cookie exposure.

> [!tip]
> Keep gateway cookie stripping on exact names only so application cookies with unrelated prefixes are not affected.

> [!warning]
> Requested runtime verification could not run from Codex because Docker socket access is blocked by the sandbox: `dial unix /Users/duykim/.docker/run/docker.sock: connect: operation not permitted`.

## Files changed

- `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
- `stack-a/openig_home/scripts/groovy/StripGatewaySessionCookies.groovy`
- `stack-a/openig_home/config/routes/02-app2.json`
