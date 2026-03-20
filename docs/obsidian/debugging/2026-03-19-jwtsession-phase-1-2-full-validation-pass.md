---
title: JwtSession Phase 1 and Phase 2 full validation pass
tags:
  - debugging
  - openig
  - keycloak
  - redis
  - jwt
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-19
status: done
---

# JwtSession Phase 1 and Phase 2 full validation pass

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Scope

- Branch validated: `fix/jwtsession-production-pattern`
- Included fixes:
  - Phase 1 `JwtSession` production restore (`0454796`)
  - Phase 2 Redis Token Reference Pattern (`9b2d109`, `47cbab9`)
  - BackchannelLogoutHandler `ES256` / EC support (`646a45a`, `d2eb8e9`)

## Confirmed results

> [!success]
> Full login+logout validation passed on all three stacks on 2026-03-19.

- Stack A:
  - `IG_SSO` present
  - TokenRef Store/Restore OK
  - SLO backchannel Redis blacklist OK
- Stack B:
  - `IG_SSO_B` present
  - TokenRef Store/Restore OK
  - SLO backchannel Redis blacklist OK
- Stack C:
  - `IG_SSO_C` present and observed at `971` chars during full validation
  - TokenRef Store/Restore OK
  - SLO backchannel Redis blacklist OK

> [!tip]
> Earlier targeted verification sampled `IG_SSO_C` at `849` chars after dynamic key discovery. The full-validation capture reached `971` chars but remained well below the 4 KB JwtSession limit because the Redis token reference pattern kept the heavy `oauth2:*` payload out of the cookie.

## Technical conclusions

- `ES256` is confirmed working end-to-end for the four OpenIG Keycloak clients.
- `BackchannelLogoutHandler` must support both `RS256` and `ES256`, plus EC JWKS lookup (`kty=EC`, `crv=P-256`) and `SHA256withECDSA`.
- JWS ECDSA raw `R\|\|S` signatures must be DER-encoded before Java signature verification.
- Phase 1 + Phase 2 pattern is now ready to merge; remaining follow-up is Stack C phpMyAdmin `bob` support.

## Next steps

- Merge `fix/jwtsession-production-pattern` -> `main`
- Provision MariaDB user `bob` for Stack C or document explicit `alice`-only support
