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

Updated `stack-a/openig_home/config/routes/01-wordpress.json`:

- Added `StripGatewaySessionCookiesApp1` immediately after `SessionBlacklistFilter`.
- Kept `VaultCredentialFilter`, `WpSessionInjector`, and the backend proxy downstream of the strip point.

Updated `stack-a/openig_home/config/routes/02-app2.json`:

- Added `StripGatewaySessionCookiesApp2` immediately after `SessionBlacklistFilterApp2`.
- Kept `App2HeaderFilter` and the backend proxy downstream of the strip point.

Updated `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`:

- Removed the local helper that stripped exact-match gateway cookie names `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C`.
- Kept `stripWpCookies` for WordPress cookie removal only; gateway cookie stripping is now handled in the shared route filter.

Retained `stack-a/openig_home/scripts/groovy/StripGatewaySessionCookies.groovy` as the shared helper:

- Removes exact-match gateway cookie names `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C` before the request reaches the backend handler.
- Deletes the `Cookie` header entirely when those are the only cookies present.

> [!success]
> Stack A backend requests for [[WordPress]] and WhoAmI no longer forward OpenIG session cookies from these gateway paths.

## Decision rationale

The strip is safe after the session blacklist check because `[[OpenIG]]` has already restored `JwtSession` from the gateway cookie before Groovy filters run.
Using the shared route filter for both WordPress and WhoAmI removes duplicate cookie-name logic from `CredentialInjector.groovy` and keeps the strip contract in one place.

> [!tip]
> Keep gateway cookie stripping on exact names only so application cookies with unrelated prefixes are not affected.

> [!warning]
> Requested runtime verification could not run from Codex because Docker socket access is blocked by the sandbox: `dial unix /Users/duykim/.docker/run/docker.sock: connect: operation not permitted`.

## Files changed

- `stack-a/openig_home/config/routes/01-wordpress.json`
- `stack-a/openig_home/config/routes/02-app2.json`
- `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
- `stack-a/openig_home/scripts/groovy/StripGatewaySessionCookies.groovy`
