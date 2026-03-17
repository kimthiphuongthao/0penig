---
title: 2026-03-17 Production Readiness Audit
tags:
  - session-note
  - audit
  - production-readiness
  - openig
  - keycloak
  - vault
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-17
status: complete
---

# 2026-03-17 Production Readiness Audit

## Context

Cross-checked the original pre-packaging audit set in `docs/audit/2026-03-16-pre-packaging-audit/` plus `.omc/plans/pattern-consolidation.md` against the **current live repo state** for [[OpenIG]], [[Keycloak]], [[Vault]], [[Stack A]], [[Stack B]], and [[Stack C]].

Scope included:
- Groovy templates and app-specific adapters in all 3 stacks
- Route JSON bindings and `args`
- `config.json`, `docker-compose.yml`, and `nginx.conf`
- Vault bootstrap/config
- Key deliverables in `docs/deliverables/`

## What Was Verified

### Pattern consolidation

> [!success]
> `SessionBlacklistFilter`, `BackchannelLogoutHandler`, and standard `SloHandler` consolidation are present in code now.

- `SessionBlacklistFilter.groovy` exists once per stack and is byte-identical across A/B/C.
- `SloHandler.groovy` exists once per stack and is byte-identical across A/B/C.
- `BackchannelLogoutHandler.groovy` is consolidated functionally; A/B are byte-identical and C differs only by fallback Redis host.
- Route JSONs now pass `args` for the shared templates.
- phpMyAdmin inline `failureHandler` now points to `SloHandler.groovy`.

### Confirmed bug fixes

> [!success]
> Historical defects fixed in code:
> - JWKS cache now uses `globals.compute()` in `BackchannelLogoutHandler`
> - audience handling uses polymorphic `validateClaims(..., def expectedAudience)`
> - standard `SloHandler` now has `try/catch`
> - Stack C JWKS TTL unit mismatch is gone
> - dead files removed: `App1ResponseRewriter.groovy`, old per-app blacklist filters, old per-app SLO handlers

### Still-open production blockers

> [!warning]
> Production readiness is still blocked by unresolved security and infra items.

- App session artifacts still stored in browser-bound `JwtSession` for WordPress, Redmine, and Jellyfin.
- Redis still has no authentication in any stack.
- Secrets are still committed directly in all `docker-compose.yml` files.
- Stack C still diverges materially from A/B in compose and nginx hardening.
- Vault still runs with `tls_disable = true`, `ui = true`, and bootstrap scripts still contain hardcoded app passwords.
- nginx still has no security response headers and cookie flags are still missing.

## Decisions / Classification Rules

- `RESOLVED`: fix is directly visible in current source or config.
- `PARTIAL`: some code moved in the right direction, but the audited gap still exists materially.
- `STILL OPEN`: audited gap is still present in current code/config.

## Current State

### High-confidence resolved items

- `C-1` JWKS cache race
- `H-1` SloHandler try-catch
- `H-2` `vault/keys/` gitignore
- `H-3` Redmine host port exposure
- `H-6` JWKS TTL inconsistency
- `H-8` divergent App2 blacklist implementation
- `H-9` Stack C proxy buffer sizing
- `M-2` A/B canonical origin env vars
- `M-10` Stack A SloHandler hardcoded Keycloak URL
- `M-14` dead `App1ResponseRewriter.groovy`

### Notable partial items

- Hardcoded Keycloak values were removed from shared Groovy logic, but Stack A/C route JSONs still hardcode issuer/JWKS and OIDC endpoints.
- `readRespLine` EOF handling is fixed in `SessionBlacklistFilter`, but not in `BackchannelLogoutHandler`.
- Deliverable docs were updated for templates, but their update banners still say Step 6 is “current” even though the plan marks it complete.

### Notable still-open items

- Redis auth
- Secrets in compose
- no nginx security headers
- missing `Secure` / `SameSite` cookie handling
- weak Stack C OIDC secrets
- A/B OpenIG containers run as root
- Vault TLS/UI hardening
- Vault bootstrap hardcoded passwords
- Stack C docker-compose parity gaps
- `host.docker.internal` portability

## Files Reviewed

- `stack-a/openig_home/scripts/groovy/*.groovy`
- `stack-b/openig_home/scripts/groovy/*.groovy`
- `stack-c/openig_home/scripts/groovy/*.groovy`
- `stack-a/openig_home/config/routes/*.json`
- `stack-b/openig_home/config/routes/*.json`
- `stack-c/openig_home/config/routes/*.json`
- `stack-a/openig_home/config/config.json`
- `stack-b/openig_home/config/config.json`
- `stack-c/openig_home/config/config.json`
- `stack-a/docker-compose.yml`
- `stack-b/docker-compose.yml`
- `stack-c/docker-compose.yml`
- `stack-a/nginx/nginx.conf`
- `stack-b/nginx/nginx.conf`
- `stack-c/nginx/nginx.conf`
- `.gitignore`
- `stack-*/vault/config/vault.hcl`
- `stack-*/vault/init/vault-bootstrap.sh`
- `docs/deliverables/legacy-auth-patterns-definitive.md`
- `docs/deliverables/standard-gateway-pattern.md`
- `docs/deliverables/legacy-app-team-checklist.md`

## Files Changed

- `docs/obsidian/debugging/2026-03-17-production-readiness-audit.md`

## Next Steps

> [!tip]
> If a follow-up task is requested, prioritize the still-open Critical/High items first:
> `C-2`, `H-4`, `H-5`, `H-7`, `M-3`, `M-4`, `M-5`, `M-6`, `M-7`, `M-8`, `M-13`.
