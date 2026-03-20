---
title: Review of security-h8-jwt-validation
tags:
  - debugging
  - security
  - review
  - openig
  - keycloak
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-13
status: reviewed
---

# Review of security-h8-jwt-validation

Links: [[OpenIG]] [[Keycloak]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

Reviewed branch `security/h8-jwt-validation` against `feat/subdomain-test` using `/tmp/h8-jwt-diff.patch`.

Scope of change:

- `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`

Goal of the change: validate Keycloak backchannel `logout_token` JWTs before writing `sid` into Redis blacklist.

## Findings

> [!warning]
> The PR is not shippable. Multiple defects make valid backchannel logout fail in production across all three stacks.

### 1. RSA public key reconstruction is broken

- The code builds custom DER and passes it to `X509EncodedKeySpec`.
- The constructed bytes are not a valid SubjectPublicKeyInfo.
- It writes exponent before modulus while the comment says `SEQUENCE { INTEGER n, INTEGER e }`.
- It hardcodes one-byte long-form lengths, so a 2048-bit modulus length (`256`) overflows when written with `ByteArrayOutputStream.write(int)`.
- It does not prepend a leading `0x00` when ASN.1 INTEGER high bit is set.

Impact:

- `KeyFactory.generatePublic(...)` is expected to fail for real Keycloak RSA keys.
- Signature validation therefore fails closed for legitimate logout tokens.
- Result: Redis blacklist write never happens, so SLO does not propagate.

### 2. Issuer and JWKS realm are hardcoded to `sso-lab`, but repo wiring still uses `sso-realm`

- New Groovy constants use `http://auth.sso.local:8080/realms/sso-lab`.
- Existing route configs and Keycloak realm import still use `http://auth.sso.local:8080/realms/sso-realm`.

Impact:

- JWKS fetch points at the wrong realm path.
- Even if key reconstruction were fixed, issuer validation would still reject current lab tokens.
- This breaks stacks A, B, and C consistently.

### 3. `events` validation rejects the valid backchannel logout event

- The code expects `events['http://schemas.openid.net/event/backchannel-logout']` to be a `Map`.
- In logout tokens that value is the empty object `{}`.
- In Groovy, `{}` is falsy, so `!logoutEvent` evaluates true and valid tokens are rejected.

Impact:

- Any valid logout token with the standard empty event object fails claim validation.
- Result is another total SLO failure path even after fixing signature handling.

### 4. Claim validation does not match logout-token semantics

- `jti` is not validated or replay-tracked.
- `nonce` is not rejected.
- `exp` is treated as mandatory even though logout-token handling should not rely on it being present.

Impact:

- Spec-compliant logout tokens can be rejected if `exp` is absent.
- Captured valid logout tokens can be replayed because there is no `jti` cache or dedupe.
- Replays can keep resetting the Redis `EX 3600` TTL for the same `sid`.

## Operational Notes

> [!tip]
> Even after the correctness bugs are fixed, the implementation still needs operational hardening.

- JWKS is fetched on every backchannel request, with no caching or key reuse.
- Any Keycloak/JWKS outage turns logout propagation into a hard failure.
- Logging is too chatty at `INFO` and includes `sid` plus `blacklist:${sid}`, which are session correlators.
- HTTP status from JWKS fetch is not checked before treating the body as parsable JSON.

## Cross-Stack Notes

- Stacks A and B are structurally identical and therefore share the same failures.
- Stack C intentionally accepts either `openig-client-c-app5` or `openig-client-c-app6`, but the shared handler does not bind audience to request path.
- That means `/openid/app5/backchannel_logout` and `/openid/app6/backchannel_logout` are no longer endpoint-specific from a validation perspective.

> [!warning]
> There is also an existing repo inconsistency outside this diff for Stack B: current realm import still registers `openig-client-b` backchannel logout at `/openid/app3/backchannel_logout`, while the route handler is wired at `/openid/app4/backchannel_logout`.

## Recommendation

Reject the PR in its current form.

Required remediation order:

1. Replace manual DER/JWK reconstruction with a correct RSA key build path.
2. Stop hardcoding realm values that disagree with current route and Keycloak config.
3. Fix `events` validation to accept the empty logout event object.
4. Add logout-token specific checks for `jti`, replay handling, and `nonce` absence.
5. Cache JWKS or parsed public keys by `kid`.

## Files Referenced

- `/tmp/h8-jwt-diff.patch`
- `stack-a/openig_home/config/routes/01-wordpress.json`
- `stack-b/openig_home/config/routes/02-redmine.json`
- `stack-c/openig_home/config/routes/10-grafana.json`
- `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json`
- `stack-c/openig_home/config/routes/00-backchannel-logout-app6.json`
- `keycloak/realm-import/realm-export.json`
