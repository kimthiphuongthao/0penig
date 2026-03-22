---
title: Stack B Redmine SEC COOKIE STRIP Consolidation
tags:
  - stack-b
  - openig
  - redmine
  - cookie
  - security
date: 2026-03-22
status: fixed
---

# Stack B Redmine SEC COOKIE STRIP Consolidation

Related: [[Stack B]] [[OpenIG]] [[Keycloak]] [[Redmine]]

## Root cause

`[[Redmine]]` in [[Stack B]] was still stripping gateway session cookies inside `RedmineCredentialInjector.groovy`.
That duplicated the `SEC-COOKIE-STRIP` behavior instead of using the shared route-level filter pattern already applied in other stacks.

## Fix

Updated `stack-b/openig_home/scripts/groovy/StripGatewaySessionCookies.groovy`:

- Added the shared helper to Stack B with the same exact implementation used in Stack A.
- The helper strips exact-match `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C` values from the inbound `Cookie` header and removes the header entirely when nothing remains.

Updated `stack-b/openig_home/config/routes/02-redmine.json`:

- Added `StripGatewaySessionCookiesApp3` as a `ScriptableFilter` using `StripGatewaySessionCookies.groovy`.
- Inserted the filter after `SessionBlacklistFilterApp3` and before `VaultCredentialFilterRedmine` and `RedmineCredentialInjectorFilter`.

Updated `stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy`:

- Removed the local `GATEWAY_SESSION_COOKIE_NAMES` constant.
- Kept `stripRedmineCookies`, but limited it to Redmine cookie removal only.

> [!success]
> Stack B Redmine now uses the shared route-level gateway cookie strip pattern instead of embedded injector logic.

## Decision rationale

The route-level strip remains safe after `SessionBlacklistFilterApp3` because [[OpenIG]] has already restored the gateway session before Groovy filters execute.
Moving gateway cookie stripping out of `RedmineCredentialInjector.groovy` keeps the Redmine injector focused on Redmine-specific cookie handling and aligns Stack B with the shared consolidation pattern.

> [!tip]
> Keep the shared filter on exact cookie-name matches only so application cookies with similar prefixes are not stripped accidentally.

> [!warning]
> Verification in this session was static only: JSON parse validation succeeded, the copied helper matched Stack A byte-for-byte, and `RedmineCredentialInjector.groovy` no longer referenced `GATEWAY_SESSION_COOKIE_NAMES`.

## Files changed

- `stack-b/openig_home/config/routes/02-redmine.json`
- `stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy`
- `stack-b/openig_home/scripts/groovy/StripGatewaySessionCookies.groovy`
