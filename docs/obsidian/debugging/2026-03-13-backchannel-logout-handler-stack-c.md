---
title: Stack C BackchannelLogoutHandler fixes
tags:
  - debugging
  - openig
  - keycloak
  - stack-c
date: 2026-03-13
status: done
---

# Stack C BackchannelLogoutHandler fixes

Context: fixed four scoped bugs in [[OpenIG]] backchannel logout handling for [[Stack C]] at [stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy).

## Root causes

- The configured issuer and JWKS URI pointed to realm `sso-lab` while the actual [[Keycloak]] realm is `sso-realm`.
- RSA key reconstruction was manually assembling DER/X509 bytes instead of constructing an RSA public key from modulus and exponent.
- Groovy truthiness treated the required OIDC backchannel logout event value `{}` as false, rejecting valid tokens.
- JWKS was fetched from [[Keycloak]] on every request with no cache.

## Changes made

- Changed `KEYCLOAK_ISSUER` and `KEYCLOAK_JWKS_URI` to use `sso-realm`.
- Replaced manual ASN.1/X509 reconstruction with `BigInteger` + `RSAPublicKeySpec` + `KeyFactory.generatePublic(...)`.
- Updated event validation to allow the spec-required empty map and only reject `null` or non-`Map` values.
- Added script-level JWKS cache fields with `@Field` and a 600-second TTL.

> [!success] Confirmed scope
> `EXPECTED_AUDIENCES` remains `['openig-client-c-app5', 'openig-client-c-app6']` and the existing validation path already handles `aud` as either `String` or `List`.
> `REDIS_HOST` default remains `redis-c`.
> No other logout or Redis flow was changed.

> [!tip] Operational effect
> Repeated backchannel logout requests within 10 minutes should reuse cached JWKS data instead of hitting [[Keycloak]] for every token.

> [!warning] Verification gap
> This environment does not have the `groovy` runtime installed, so validation here was limited to source-level review and diff inspection after patching.

## Current state

- Stack C backchannel logout script now targets the correct [[Keycloak]] realm.
- RSA public key generation now uses the JWK modulus and exponent directly through `RSAPublicKeySpec`.
- Valid backchannel logout `events` payloads containing `{}` should no longer be rejected by Groovy truthiness.
- Repeated backchannel requests within 600 seconds should reuse cached JWKS data.

## Files changed

- [[OpenIG]]
  File: [stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
