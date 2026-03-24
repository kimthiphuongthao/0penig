---
title: Shared Infra Secrets Hygiene Fixes
tags:
  - debugging
  - shared-infra
  - security
  - openig
  - vault
  - redis
date: 2026-03-24
status: complete
---

# Shared Infra Secrets Hygiene Fixes

Applied the requested fixes for `AUD-001`, `AUD-004`, and `AUD-008` in the consolidated `shared/` stack used by [[OpenIG]], [[Vault]], [[Keycloak]], and [[Redis]].

> [!success] Fixed findings
> `AUD-001`: removed tracked credential literals from `shared/vault/init/vault-bootstrap.sh` and replaced them with fail-closed env reads.
>
> `AUD-004`: removed secret fallbacks from `shared/docker-compose.yml` for OIDC client secrets, JWT/keystore secrets, Redis passwords, and database passwords.
>
> `AUD-008`: rewired backchannel blacklist TTL args to the correct per-app env vars in the existing shared route set (`app1`, `app3`, `app4`, `app5`, `app6`).

## Files Changed

- `shared/vault/init/vault-bootstrap.sh`
- `shared/docker-compose.yml`
- `shared/.env.example`
- `shared/openig_home/config/routes/00-backchannel-logout-app1.json`
- `shared/openig_home/config/routes/00-backchannel-logout-app3.json`
- `shared/openig_home/config/routes/00-backchannel-logout-app4.json`
- `shared/openig_home/config/routes/00-backchannel-logout-app5.json`
- `shared/openig_home/config/routes/00-backchannel-logout-app6.json`

## What Changed

- `vault-bootstrap.sh`
  - Replaced all hardcoded app credential literals with `${VAR:?'ERROR: VAR is required'}` assignments.
  - Kept the Jellyfin alphanumeric-only constraint in comments because form injection still does not URL-encode those values.
- `docker-compose.yml`
  - Changed secret-bearing `${VAR:-default}` expressions to required `${VAR:?ERROR: VAR is required}` expressions.
  - Left non-secret defaults intact for URLs, ports, canonical origins, image-related values, and TTLs.
- `.env.example`
  - Added all newly required bootstrap credential vars with placeholder values.
  - Replaced example secret literals such as Redis password placeholders, `WORDPRESS_DB_PASSWORD`, and `MYSQL_PASSWORD_C` with explicit placeholder strings.
- Backchannel routes
  - Updated `ttlSeconds` to read `REDIS_BLACKLIST_TTL_APP1`, `APP3`, `APP4`, `APP5`, and `APP6` respectively.

> [!warning] Validation result
> The exact required check `cd shared && docker compose config 2>&1 | head -5` now fails closed against the active tracked-outside-env state because `shared/.env` is missing:
> `MYSQL_ROOT_PASSWORD_A`, `MYSQL_ROOT_PASSWORD_C`, and `MYSQL_PASSWORD_C`.
>
> Compose syntax itself still resolves with `shared/.env.example`.

> [!tip] Next step
> Populate the three missing vars in the active `shared/.env`, then rerun `cd shared && docker compose config` to restore a clean local validation pass.
