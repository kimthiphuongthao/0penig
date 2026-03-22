---
title: Stack B SEC COOKIE STRIP
tags:
  - stack-b
  - openig
  - cookie
  - security
date: 2026-03-22
status: fixed
---

# Stack B SEC COOKIE STRIP

Related: [[Stack B]] [[OpenIG]]

## Root cause

`[[OpenIG]]` loads the `JwtSession` from `IG_SSO_B` before Groovy filters run, but Stack B was still forwarding gateway session cookies to Redmine and Jellyfin.
That leaked `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C` into backend requests even though the applications do not need them.

## Fix

Updated `stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy`:

- Extended the existing cookie-strip helper to remove exact-match gateway cookie names `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C`.
- Kept the strip point before upstream forwarding, alongside the existing Redmine cookie filtering.

Updated `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy`:

- Added a gateway cookie parsing and stripping helper for exact-match `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C`.
- Applied stripping before both Jellyfin forward paths:
  - non-HTML passthrough when no token is created
  - authenticated request forwarding after token/session handling

> [!success]
> Stack B backend requests no longer forward OpenIG session cookies to [[Redmine]] or [[Jellyfin]] from these gateway filters.

## Decision rationale

The strip is safe in Groovy because the gateway session has already been read at framework level.
Filtering after session-dependent logic preserves OpenIG behavior while preventing backend cookie exposure.

> [!warning]
> Requested container verification could not run from Codex because Docker socket access was denied by the sandbox: `dial unix /Users/duykim/.docker/run/docker.sock: connect: operation not permitted`.

## Files changed

- `stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy`
- `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy`
