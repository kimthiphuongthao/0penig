---
title: Stack C phpMyAdmin Post-Unseal Recheck
tags:
  - debugging
  - stack-c
  - vault
  - openig
  - phpmyadmin
  - sso
date: 2026-03-19
status: investigated
---

# Stack C phpMyAdmin Post-Unseal Recheck

Related: [[OpenIG]] [[Vault]] [[Keycloak]] [[Stack C]]

## Context

- Follow-up investigation after Vault unseal and AppRole refresh for Stack C phpMyAdmin SSO.
- Scope: inspect fresh OpenIG logs after the latest restart, verify Vault/AppRole health, and check whether Stack C bootstrap/config still breaks phpMyAdmin SSO.

## Findings

- Vault is now healthy: `vault status` reports `Sealed false`.
- OpenIG AppRole inputs are present and non-empty in both the host-mounted and in-container files.
- AppRole login succeeds with HTTP `200` from `openig-c1` to `http://vault-c:8200/v1/auth/approle/login`.
- Vault credential lookup for `secret/data/phpmyadmin/alice` succeeds with HTTP `200`.
- Both OpenIG nodes load route `11-phpmyadmin` cleanly after the latest restart.
- No fresh `Vault AppRole login failed with HTTP 503` appears after the latest restart.
- No fresh `OAuth2ClientFilter` `no authorization in progress` appears after the latest restart.

> [!success]
> The original Stack C failure mode from the earlier note is resolved. Current evidence does not show an active Vault or route-loading failure.

## Evidence

- `docker exec -e VAULT_ADDR=http://127.0.0.1:8200 stack-c-vault-c-1 vault status`
  - `Initialized true`
  - `Sealed false`
- `stack-c/openig_home/vault/role_id`
  - non-empty
- `stack-c/openig_home/vault/secret_id`
  - non-empty
- `docker exec stack-c-openig-c1-1 cat /opt/openig/vault/role_id`
  - matches the host-mounted `role_id`
- `docker exec stack-c-openig-c1-1 cat /opt/openig/vault/secret_id`
  - matches the host-mounted `secret_id`
- `docker exec stack-c-openig-c1-1 ... curl http://vault-c:8200/v1/auth/approle/login`
  - HTTP `200`
- `docker exec stack-c-openig-c1-1 ... curl http://vault-c:8200/v1/secret/data/phpmyadmin/alice`
  - HTTP `200`
- `docker logs stack-c-openig-c1-1 --tail 200`
  - latest restart at `2026-03-19T01:35:42Z`
  - route `11-phpmyadmin` loaded successfully
  - no fresh Vault `503` after restart
- `docker logs stack-c-openig-c2-1 --tail 200`
  - latest restart at `2026-03-19T01:35:43Z`
  - route `11-phpmyadmin` loaded successfully
  - no fresh Vault `503` after restart
- `curl -H 'Host: phpmyadmin-c.sso.local:18080' http://127.0.0.1:18080/`
  - returns `302` to Keycloak with callback `http://phpmyadmin-c.sso.local:18080/openid/app6/callback`

> [!warning]
> The older `OAuth2ClientFilter` error `Authorization call-back failed because there is no authorization in progress` is still present in historical logs, but only before the latest restart. After restart, a stale browser callback or cookie is the most likely explanation if the user still sees failure.

## Root Cause

- Previous failure: [[2026-03-19-stack-c-phpmyadmin-vault-sealed-sso-failure]] caused by a sealed [[Vault]] returning AppRole login `503`.
- Current state: no active Stack C gateway misconfiguration was found in `docker-entrypoint.sh`, `config.json`, `11-phpmyadmin.json`, or `VaultCredentialFilter.groovy`.
- Remaining operational risk: `vault-bootstrap.sh` writes refreshed AppRole files to `/vault/init/role_id` and `/vault/init/secret_id`, while OpenIG reads `/opt/openig/vault/role_id` and `/opt/openig/vault/secret_id`. The repo contains no automatic sync between those locations.

## Current State

- Stack C Vault: unsealed and serving AppRole login
- Stack C OpenIG c1/c2: route `11-phpmyadmin` loaded, no fresh Vault errors
- phpMyAdmin route: live and redirecting to Keycloak
- Most likely remaining failure mode: stale browser auth state from before restart, not an active gateway/Vault failure

## Next Steps

1. Retest phpMyAdmin in a fresh browser session or incognito window to avoid stale `JSESSIONID` and `IG_SSO_C` state.
2. If it still fails, capture fresh logs during that exact attempt before changing Stack C config.
3. Consider a follow-up Stack C improvement to sync regenerated AppRole files from Vault bootstrap output into `openig_home/vault/`.

> [!tip]
> There is no evidence that `stack-c/docker/openig/docker-entrypoint.sh` overwrites Vault AppRole files. It only substitutes `JWT_SHARED_SECRET` and `KEYSTORE_PASSWORD` into `config.json`.

## Files Changed

- `docs/obsidian/debugging/2026-03-19-stack-c-phpmyadmin-post-unseal-recheck.md`
