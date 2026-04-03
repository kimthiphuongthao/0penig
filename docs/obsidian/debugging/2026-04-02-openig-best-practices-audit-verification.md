---
title: OpenIG best practices audit verification
tags:
  - debugging
  - audit
  - verification
  - openig
  - keycloak
  - vault
  - redis
  - nginx
date: 2026-04-02
status: completed
---

# OpenIG best practices audit verification

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Verified `docs/audit/2026-04-02-openig-best-practices-compliance-evaluation.md` against the current shared gateway code.
- Scope reviewed:
  - `shared/openig_home/config/routes/*.json`
  - `shared/openig_home/config/config.json`
  - `shared/openig_home/scripts/groovy/*.groovy`
  - `shared/nginx/nginx.conf`
  - `shared/docker-compose.yml`
- Runtime version pinned in the repo is `openidentityplatform/openig:6.0.1`.

> [!success] Confirmed
> `PKCE` is not configured anywhere in the six `OAuth2ClientFilter` routes, Redis token-reference storage serializes full `oauth2Entries`, and there is no dedicated OpenIG audit-handler configuration in `config.json` or the route files.

> [!warning] Corrected
> The audit overstates two items:
> 1. `OAUTH-002` is not proven by the gateway code. The repo does not contain executable `use.refresh.tokens=false` configuration.
> 2. `SESS-001` is too broad. The code clearly lacks an explicit `Secure` flag, but `HttpOnly` is not proven absent from the checked files.

## Finding verdicts

### OAUTH-001

- Claim: PKCE is not implemented on any `OAuth2ClientFilter`.
- Verdict: Confirmed.
- Evidence:
  - `shared/openig_home/config/routes/01-wordpress.json:45-72`
  - `shared/openig_home/config/routes/02-app2.json:45-72`
  - `shared/openig_home/config/routes/02-redmine.json:147-164`
  - `shared/openig_home/config/routes/01-jellyfin.json:154-172`
  - `shared/openig_home/config/routes/10-grafana.json:45-72`
  - `shared/openig_home/config/routes/11-phpmyadmin.json:45-72`
- Notes:
  - All six routes use `OAuth2ClientFilter`.
  - No route file contains `pkce`, `code_challenge`, `code_verifier`, or `AuthorizationCodeOAuth2ClientFilter`.
  - This is a server-side confidential-client pattern because the route registrations include `clientSecret`, for example `shared/openig_home/config/routes/01-wordpress.json:19-24` and `shared/openig_home/config/routes/02-redmine.json:19-30`.

### OAUTH-002

- Claim: Refresh tokens are disabled.
- Verdict: False positive for the gateway codebase.
- Evidence:
  - `shared/openig_home/config/config.json:1-34`
  - `shared/openig_home/config/routes/01-wordpress.json:19-29`
  - `shared/openig_home/config/routes/02-redmine.json:19-31`
- Notes:
  - None of the inspected executable gateway files set `use.refresh.tokens=false`.
  - The route configs define normal confidential client registrations and do not show a refresh-token disable switch.
  - The claim may come from external Keycloak client state or previous notes, but it is not verified by the checked source files.

### SESS-001

- Claim: `JwtSession` cookies lack `Secure` and `HttpOnly` flags.
- Verdict: Partially correct.
- Evidence:
  - `shared/openig_home/config/config.json:20-30`
  - `shared/openig_home/config/routes/01-wordpress.json:33-42`
  - `shared/openig_home/config/routes/10-grafana.json:33-42`
  - `shared/nginx/nginx.conf:66`
- Notes:
  - The `JwtSession` configs do not explicitly set cookie security attributes.
  - Nginx only adds `samesite=lax` to `IG_SSO_APP*` cookies and does not add `secure` or `httponly`.
  - Because the checked files do not prove the upstream default for `HttpOnly`, the blanket claim that both flags are absent is too strong. The missing `Secure` posture is real because the deployment is plain HTTP on port 80.

### SECRET-001

- Claim: Redis stores plaintext OAuth2 token payloads.
- Verdict: Confirmed.
- Evidence:
  - `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy:349-351`
  - `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy:113-125`
  - `shared/openig_home/scripts/groovy/SloHandler.groovy:105-108`
  - `shared/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:109-112`
- Notes:
  - `TokenReferenceFilter` serializes `oauth2EntriesForResponse` with `JsonOutput.toJson(...)` and writes that JSON string directly to Redis with `SET ... EX`.
  - Other scripts read `session[key].atr.id_token` from those same OAuth2 session entries, which means token material is present inside the serialized object and not replaced with an opaque surrogate before Redis storage.

### PERF-001

- Claim: There is no circuit breaker for Redis or Vault dependencies.
- Verdict: Confirmed.
- Evidence:
  - `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy:76-90`
  - `shared/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:498-515`
  - `shared/openig_home/scripts/groovy/VaultCredentialFilter.groovy:102-147`
- Notes:
  - Redis and Vault calls use direct sockets or `HttpURLConnection` with short timeouts.
  - Failures return `500` or `502`, but there is no open/half-open circuit state, failure counter, degraded-mode branch, or dependency-level fallback for Redis or Vault.
  - JWKS fetches do have cache and failure backoff in `BackchannelLogoutHandler`, but that logic does not cover Redis or Vault.

### OBS-001

- Claim: There is no structured audit logging; potential token leakage.
- Verdict: Partially correct.
- Evidence:
  - `shared/openig_home/config/config.json:1-34`
  - `shared/openig_home/config/routes/01-wordpress.json:1-61`
  - `shared/nginx/nginx.conf:1-20`
  - `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy:341-346`
  - `shared/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:412-414`
  - `shared/openig_home/scripts/groovy/SloHandler.groovy:147-150`
- Notes:
  - The checked OpenIG and nginx configs do not define audit handlers, structured audit sinks, or custom `log_format` output.
  - The Groovy scripts use ordinary `logger.info/warn/error` calls, including free-form messages.
  - I did not find code that logs raw OAuth2 tokens directly. The stronger claim is the absence of structured audit logging, not proven token leakage via logs.
  - There is still a separate exposure consideration around redirect URLs that include `id_token_hint`, even though the scripts log only `PRESENT` rather than the raw token value.

## Current state

- Actual repo-backed high-confidence issues:
  - no PKCE in route configs
  - plaintext OAuth2 session offload to Redis
  - no circuit breaker for Redis and Vault
  - no dedicated structured audit logging configuration
- Findings needing downgrade or correction:
  - refresh-token disabling is not evidenced by the gateway code
  - the cookie finding should be narrowed to missing explicit cookie hardening, especially `Secure`

> [!tip]
> For future gateway audits in this lab, separate:
> 1. what the gateway code actually configures
> 2. what is only true in external Keycloak state
> 3. what is a best-practice recommendation versus a hard requirement for a confidential server-side flow

## Next steps

- Update the audit markdown to downgrade `OAUTH-002` from a verified finding to an unverified external-state assumption unless Keycloak client exports are added to the evidence set.
- Narrow `SESS-001` to missing explicit cookie hardening in code, and avoid claiming `HttpOnly` is absent without runtime validation or authoritative version-specific documentation.
- Reframe `OBS-001` around missing structured audit logging and separate it from any token-exposure discussion.
- Treat PKCE as recommended hardening for this OpenIG 6.0.1 confidential-client pattern, not a lab-blocking critical defect by itself.

## Files changed

- `docs/obsidian/debugging/2026-04-02-openig-best-practices-audit-verification.md`
