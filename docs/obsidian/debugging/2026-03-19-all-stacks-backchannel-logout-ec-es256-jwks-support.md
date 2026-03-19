---
title: Backchannel logout EC and ES256 JWKS support across all stacks
tags:
  - debugging
  - openig
  - keycloak
  - jwks
  - jwt
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-19
status: done
---

# Backchannel logout EC and ES256 JWKS support across all stacks

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

Keycloak client signing was switched from `RS256` to `ES256`. Backchannel logout tokens therefore started arriving with `alg=ES256` and `kid` values pointing to EC keys in the JWKS (`kty=EC`, `crv=P-256`). The three OpenIG `BackchannelLogoutHandler.groovy` copies still assumed RSA-only key reconstruction and `SHA256withRSA` signature verification.

## Root cause

Each handler had two RSA-only assumptions:

- `getPublicKeyFromJwks` rejected any JWK where `kty != RSA`.
- `verifySignature` always used `Signature.getInstance('SHA256withRSA')` and never converted JWS ECDSA raw `R||S` signatures into DER before verification.

> [!warning]
> Scope stayed within the approved gateway files only: the three OpenIG Groovy handlers. No target application config, Keycloak config, or route JSON was modified.

## What changed

- `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`

All three files now:

- import `ECPublicKeySpec`, `ECPoint`, `AlgorithmParameters`, and `ECGenParameterSpec`
- reconstruct RSA keys exactly as before when `kty=RSA`
- reconstruct EC public keys when `kty=EC` and `crv=P-256` by resolving `secp256r1` parameters and building an `ECPublicKeySpec`
- warn and return `null` for unsupported `kty`, non-signing keys, or unsupported EC curves
- accept an `alg` parameter in `verifySignature`
- verify `RS256` with `SHA256withRSA`
- verify `ES256` with `SHA256withECDSA` after converting the JWS raw 64-byte signature into ASN.1 DER
- pass `alg` from the JWT header into the signature verifier call site

> [!tip]
> `ES256` JWS signatures are raw `R||S`, while Java's `SHA256withECDSA` expects DER. The handler now performs that conversion explicitly so the JWS format matches the JCA verifier.

## Verification

> [!success]
> Restarted `sso-openig-1`, `sso-openig-2`, `sso-b-openig-1`, `sso-b-openig-2`, `stack-c-openig-c1-1`, and `stack-c-openig-c2-1` successfully.

> [!success]
> After a 15-second wait, `docker logs stack-c-openig-c1-1 2>&1 | grep 'Loaded the route' | tail -5` returned recent route load entries including `00-backchannel-logout-app6`, confirming OpenIG route loading after the script update.

> [!warning]
> A local standalone Groovy parse check was not available because the `groovy` CLI is not installed in this workspace. Verification here relied on container restart success and OpenIG route-load logs.

## Current state

- Commit: `d2eb8e9`
- Commit message: `fix: BackchannelLogoutHandler support EC/ES256 keys for JWKS lookup and signature verification`
- All three stacks now support JWKS key lookup for both RSA and EC logout-token signing keys and route startup completed after restart.

## Files changed

- `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
