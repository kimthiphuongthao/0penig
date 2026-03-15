---
title: Stack A BackchannelLogoutHandler fixes
tags:
  - debugging
  - openig
  - keycloak
  - stack-a
date: 2026-03-13
status: done
---

# Stack A BackchannelLogoutHandler fixes

Context: fixed four scoped bugs in [[OpenIG]] backchannel logout handling for [[Stack A]] at [stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy).

## Root causes

- The configured issuer and JWKS URI pointed to realm `sso-lab` while the actual [[Keycloak]] realm is `sso-realm`.
- RSA key reconstruction was building invalid DER bytes manually instead of using a proper `RSAPublicKeySpec`.
- Groovy truthiness treated the required OIDC backchannel logout event value `{}` as false, rejecting valid tokens.
- JWKS was fetched from [[Keycloak]] on every request with no cache.

## Changes made

- Changed `KEYCLOAK_ISSUER` and `KEYCLOAK_JWKS_URI` to use `sso-realm`.
- Replaced manual ASN.1/X509 reconstruction with `BigInteger` + `RSAPublicKeySpec` + `KeyFactory.generatePublic(...)`.
- Updated event validation to allow the spec-required empty map and only reject `null` or non-`Map` values.
- Added script-level JWKS cache fields with `@Field` and a 600-second TTL.

> [!success] Confirmed scope
> `EXPECTED_AUDIENCE` remains `openig-client`.
> `REDIS_HOST` default remains `redis-a`.
> No JWT full-token logging was introduced.

> [!warning] Verification gap
> This environment does not have the `groovy` runtime installed, so validation here was limited to source-level review after patching.

## Current state

- Stack A backchannel logout script now targets the correct realm.
- RSA public key generation aligns with JWK modulus/exponent handling for standard Keycloak RSA keys.
- Valid backchannel logout `events` payloads containing `{}` should no longer be rejected by Groovy truthiness.
- Repeated backchannel requests within 10 minutes should reuse cached JWKS data.

## Files changed

- [[OpenIG]]
  File: [stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
