---
title: Backchannel logout ES256 acceptance across all stacks
tags:
  - debugging
  - openig
  - keycloak
  - jwt
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-19
status: done
---

# Backchannel logout ES256 acceptance across all stacks

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Phase 1 switched Keycloak clients to `ES256`.
- The three OpenIG backchannel logout handlers still hardcoded `RS256` in the JWT header algorithm check.
- Result: backchannel logout JWT validation failed with `Invalid algorithm: expected RS256, got ES256`.

> [!warning]
> Scope was intentionally limited to the algorithm gate in the three `BackchannelLogoutHandler.groovy` copies. No JWKS fetch, signature verification, route JSON, or target application config changed.

## Root cause

Each handler rejected any JWT header `alg` value other than `RS256`, even though the environment had already moved Keycloak clients to `ES256`. The failure happened before the handler reached JWKS-based signature verification.

## What changed

- `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy` now accepts `RS256` and `ES256` in the header algorithm check and logs the actual accepted algorithm value.
- `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy` now accepts `RS256` and `ES256` in the header algorithm check and logs the actual accepted algorithm value.
- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy` now accepts `RS256` and `ES256` in the header algorithm check and logs the actual accepted algorithm value.

> [!tip]
> Logging `alg={}` instead of a fixed `alg=RS256` keeps future triage aligned with the actual JWT header value seen by the handler.

## Verification

> [!success]
> Restarted `sso-openig-1`, `sso-openig-2`, `sso-b-openig-1`, `sso-b-openig-2`, `stack-c-openig-c1-1`, and `stack-c-openig-c2-1` successfully.

> [!success]
> After a 15-second wait, `docker logs stack-c-openig-c1-1 2>&1 | grep 'Loaded the route' | tail -5` returned route load lines, including `00-backchannel-logout-app6`, `00-grafana-logout`, `11-phpmyadmin`, `10-grafana`, and `00-backchannel-logout-app5`.

## Current state

- All three OpenIG stacks accept backchannel logout JWT headers signed with either `RS256` or `ES256`.
- The requested code change was committed as `646a45a` with message `fix: BackchannelLogoutHandler accept ES256 in addition to RS256`.

## Files changed

- `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `docs/obsidian/debugging/2026-03-19-all-stacks-backchannel-logout-es256-acceptance.md`
