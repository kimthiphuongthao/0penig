---
title: H8 JWT review verification
tags:
  - debugging
  - security
  - openig
  - keycloak
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-13
status: done
---

# H8 JWT review verification

Context: verified `docs/reviews/2025-03-13-security-review-h8-jwt.md` against source under [[OpenIG]] gateway configs and Groovy handlers for [[Stack A]], [[Stack B]], and [[Stack C]].

## What was checked

- `config.json` in all three stacks for `JwtSession`, `JwtKeyStore`, and shared secrets
- `BackchannelLogoutHandler.groovy` in all three stacks for JWKS cache logic, issuer/audience checks, and Redis blacklist write path
- `SessionBlacklistFilter*.groovy` for Redis failure behavior
- Route JSON files for `clientSecret`, `requireHttps`, and Keycloak endpoint configuration
- `SloHandler*.groovy` for `id_token_hint`
- Git history to separate H8-specific changes from pre-existing lab behavior

## Findings summary

> [!success]
> Confirmed report errors:
> - JWKS cache unit mismatch in [[Stack C]] is style-only, not a bug. Stack C uses milliseconds consistently.
> - Stack A does include `id_token_hint` when an `id_token` is present.
> - "Stack B missing JwtKeyStore" is true as a config difference, but "unsigned session cookies" is not supported by the source because Stack B still uses `JwtSession.sharedSecret`.

> [!warning]
> Confirmed real issues:
> - Hardcoded keystore password in Stack A and Stack C
> - Hardcoded Base64 `sharedSecret` values in all stacks
> - Redis blacklist checks fail open in all `SessionBlacklistFilter` variants
> - Route client secrets are hardcoded in plaintext
> - `requireHttps: false` is set across routes

> [!tip]
> Scope split:
> - H8-specific area: `BackchannelLogoutHandler.groovy`
> - Pre-existing lab hardening: route secrets, HTTP endpoints, `requireHttps`, `SessionBlacklistFilter*`, `SloHandler*`, `config.json`

## Git scope notes

- H8 commits `778df85`, `e4f9112`, and `b6e3f1f` only modify `BackchannelLogoutHandler.groovy` in stacks A/B/C.
- Non-handler findings come from earlier work and should not be presented as new H8 regressions.

## Decision

- Treat H8 remediation as limited to correctness of backchannel logout JWT validation and audience handling.
- Track secret management, TLS, and blacklist fail-open policy as separate hardening work.

## Current state

- Stack B backchannel audience handling is the main real H8 correctness gap because one handler serves both `/openid/app3/backchannel_logout` and `/openid/app4/backchannel_logout`, but `EXPECTED_AUDIENCE` is only `openig-client-b`.
- Stack A single audience is not a bug because the handler only serves `/openid/app1/backchannel_logout` and validates both string and list `aud`.
- Stack C already uses parameterized blacklist filtering and multi-audience validation.

## Next steps

1. Fix Stack B backchannel audience handling to accept both `openig-client-b` and `openig-client-b-app4`.
2. Keep JWKS cache logic unchanged unless standardizing naming improves readability.
3. Open separate hardening tickets for secrets, TLS, and fail-open policy.

## Files reviewed

- `docs/reviews/2025-03-13-security-review-h8-jwt.md`
- `stack-a/openig_home/config/config.json`
- `stack-b/openig_home/config/config.json`
- `stack-c/openig_home/config/config.json`
- `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy`
- `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp3.groovy`
- `stack-b/openig_home/scripts/groovy/SessionBlacklistFilterApp4.groovy`
- `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `stack-a/openig_home/scripts/groovy/SloHandler.groovy`
- `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
- `stack-b/openig_home/scripts/groovy/SloHandlerRedmine.groovy`
- `stack-b/openig_home/scripts/groovy/DotnetSloHandler.groovy`
- `stack-c/openig_home/scripts/groovy/SloHandlerGrafana.groovy`
- `stack-c/openig_home/scripts/groovy/SloHandlerPhpMyAdmin.groovy`
- Route JSON files under `stack-a/openig_home/config/routes/`, `stack-b/openig_home/config/routes/`, and `stack-c/openig_home/config/routes/`
