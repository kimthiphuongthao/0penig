---
title: Stack A code review - OpenIG routes and Groovy
tags:
  - sso
  - stack-a
  - openig
  - code-review
  - debugging
date: 2026-03-16
status: complete
---

# Stack A code review

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]]

## Context

Reviewed only the Stack A gateway layer:

- `stack-a/openig_home/scripts/groovy/*.groovy`
- `stack-a/openig_home/config/routes/*.json`
- `stack-a/openig_home/config/config.json`
- `stack-a/nginx/nginx.conf`
- Requested path `stack-a/docker-entrypoint.sh` does not exist; reviewed `stack-a/docker/openig/docker-entrypoint.sh` instead

Focus areas:

- correctness and logic errors
- null handling and fail-closed behavior
- dependency outage handling for [[Vault]], Redis, and [[Keycloak]]
- environment variable usage
- OpenIG 6 route/script usage
- dead code

## Findings summary

> [!warning]
> Blocking findings:
> 1. `BackchannelLogoutHandler.groovy` does not validate JWKS HTTP status before caching/parsing the body. A non-JWKS error body from [[Keycloak]] can be cached and later downgraded into `400`, which stops retry for a transient infra failure.
> 2. `CredentialInjector.groovy` accepts `301/302` login responses without `Set-Cookie` and still proxies the request onward. That is not fail-closed.
> 3. `docker-entrypoint.sh` does not fail fast when `JWT_SHARED_SECRET` or `KEYSTORE_PASSWORD` are missing and performs unsafe raw `sed` substitution on secrets.

> [!tip]
> Strong parts of the current implementation:
> - Redis blacklist checks deny on transport/runtime errors in both app1 and app2 filters.
> - Backchannel logout JWT validation is otherwise strict: `alg`, signature, `iss`, `aud`, `events`, `iat`, and `exp`.
> - Vault lookup fails closed with `502` when Vault or AppRole login is unavailable.

## Evidence

### High

1. `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
   - `fetchJwks` parses and caches the response body without checking `responseCode` first.
   - Main flow then treats a second miss as `400` when `kid` is still unresolved.
   - Lines: `75-84`, `261-281`

2. `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
   - A `301/302` from `wp-login.php` with missing or empty `Set-Cookie` only logs a warning.
   - The script then falls through to the "no fresh WP cookies" branch and proxies the request.
   - Lines: `82-103`, `126-159`

3. `stack-a/docker/openig/docker-entrypoint.sh`
   - Critical secrets are substituted into `config.json` without any presence checks.
   - `config.json` relies on the substituted values for `KeyStore.password`, `JwtSession.sharedSecret`, and `JwtSession.password`.
   - Lines: `15-20`
   - Supporting config: `stack-a/openig_home/config/config.json:15,25,29`

### Medium

1. `stack-a/docker/openig/docker-entrypoint.sh`
   - Raw `sed` replacement is not escaping secret values.
   - Secrets containing replacement metacharacters such as `&` or `|` can corrupt the generated config.
   - Lines: `16-19`

### Low

1. `stack-a/openig_home/config/routes/02-app2.json`
   - `App2PathStripper` is declared but never added to the filter chain.
   - Lines: `87-92`, `105-109`

2. `stack-a/docker/openig/docker-entrypoint.sh`
   - `__OIDC_CLIENT_SECRET__` is substituted, but `config.json` does not contain that placeholder. Route files read `OIDC_CLIENT_SECRET` directly from `env`.
   - Lines: `19-20`

> [!success]
> Reviewed files were internally consistent on the current `8 hours` JwtSession timeout vs Redis blacklist TTL (`28800`), but that coupling is duplicated in code and should stay aligned if the timeout changes later.

## Current state

- Stack A is close to correct on the main OIDC, Vault, and Redis flows.
- The main correctness risk is not the happy path. It is infra degradation behavior:
  - [[Keycloak]] JWKS failures can be misclassified.
  - WordPress login edge cases can fall through instead of terminating the request.
  - startup secret injection is too permissive for a security-sensitive entrypoint.

## Next steps

1. Make JWKS fetch fail only on `2xx` plus valid `keys`, and keep infra failures as `500`.
2. Make WordPress login require non-empty session cookies before proxying any request.
3. Make the OpenIG entrypoint validate required env vars and use safe replacement/templating.
4. Remove dead config/script fragments or wire them into the chain deliberately.
