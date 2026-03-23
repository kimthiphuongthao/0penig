---
title: Shared Infra Step 2 - Groovy and Routes
tags:
  - debugging
  - openig
  - redis
  - vault
  - shared-infra
date: 2026-03-23
status: done
---

# Shared Infra Step 2

## Context

Implemented Step 2 of the shared infrastructure plan for [[OpenIG]] route/script consolidation:

- copied the 13 shared Groovy scripts into `shared/openig_home/scripts/groovy/`
- copied the 16 shared route files into `shared/openig_home/config/routes/`
- adapted Redis access for ACL auth (`AUTH <user> <password>`) and per-app key prefixes
- applied the 3-part BUG-SSO2-AFTER-SLO fix across token offload and logout handlers
- switched shared routes to per-app [[Vault]] AppRole file paths

## What Changed

### Groovy scripts

- `TokenReferenceFilter.groovy`
  - added `redisUser`, `redisPasswordEnvVar`, `redisKeyPrefix`
  - prefixed Redis token keys as `appN:token_ref:<uuid>`
  - changed response-phase cleanup from `session.clear()` style behavior to targeted oauth2 key removal only
- `BackchannelLogoutHandler.groovy`
  - added ACL auth support
  - prefixed blacklist keys as `appN:blacklist:<sid>`
- `SessionBlacklistFilter.groovy`
  - added ACL auth support
  - reads prefixed blacklist keys
  - defaults Redis host to `shared-redis` if no env/arg is present
- `VaultCredentialFilter.groovy`
  - added `appRoleName`, `vaultRoleIdFile`, `vaultSecretIdFile`
  - changed cache key from `vault_token` to `vault_token_<appRoleName>`
- `SloHandler.groovy`
  - added Redis connection helpers
  - deletes all `token_ref_id*` Redis entries before `session.clear()`
- `SloHandlerJellyfin.groovy`
  - added the same Redis cleanup before `session.clear()`

### Route files

- all Redis route references now point to `shared-redis`
- TokenReferenceFilter / BackchannelLogoutHandler / SessionBlacklistFilter configs now carry per-app:
  - `redisUser`
  - `redisPasswordEnvVar`
  - `redisKeyPrefix`
- SLO handlers now carry:
  - `redisHost`
  - `redisUser`
  - `redisPasswordEnvVar`
  - `redisKeyPrefix`
  - `tokenRefKey`
- Vault routes now carry per-app AppRole file paths

## Compatibility Decision

> [!warning] WordPress and phpMyAdmin were not compatible with the stack-b `VaultCredentialFilter.groovy` defaults.
> `01-wordpress.json` previously relied on stack-a defaults (`wp-creds`, `preferred_username`, `attributes.wp_credentials`).
> `11-phpmyadmin.json` previously relied on stack-c defaults (`phpmyadmin`, direct username/password attributes).

Decision:

- WordPress route now passes explicit Vault args for `wp-creds` and `attributes.wp_credentials`
- phpMyAdmin route now passes explicit Vault args for `phpmyadmin`
- phpMyAdmin `HttpBasicAuthFilter` now reads from `attributes.phpmyadmin_credentials['username']` / `['password']`

> [!success] This keeps a single shared `VaultCredentialFilter.groovy` while preserving per-app downstream expectations.

## Validation

> [!success]
> Acceptance checks passed:
> - 13 Groovy scripts present in `shared/openig_home/scripts/groovy/`
> - 16 route JSON files present in `shared/openig_home/config/routes/`
> - `TokenReferenceFilter.groovy` contains `redisUser`
> - `BackchannelLogoutHandler.groovy` contains `redisKeyPrefix`
> - `VaultCredentialFilter.groovy` contains `vault_token_`
> - `SloHandler.groovy` contains `token_ref_id`
> - `01-wordpress.json` contains `redisUser`

- all 16 shared route files parse as valid JSON
- no old Redis host strings remained in shared routes (`redis-a`, `redis-b`, `redis-c`, `sso-redis-*`, `stack-c-redis-c-1`)
- no containers were started during this step

## Files Changed

- `shared/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `shared/openig_home/scripts/groovy/CredentialInjector.groovy`
- `shared/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy`
- `shared/openig_home/scripts/groovy/JellyfinTokenInjector.groovy`
- `shared/openig_home/scripts/groovy/PhpMyAdminAuthFailureHandler.groovy`
- `shared/openig_home/scripts/groovy/RedmineCredentialInjector.groovy`
- `shared/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `shared/openig_home/scripts/groovy/SloHandler.groovy`
- `shared/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
- `shared/openig_home/scripts/groovy/SpaAuthGuardFilter.groovy`
- `shared/openig_home/scripts/groovy/StripGatewaySessionCookies.groovy`
- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `shared/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
- all 16 route files under `shared/openig_home/config/routes/`

## Current State

- shared script directory is populated and usable for shared infra
- shared route directory is populated with per-app Redis ACL and Vault AppRole args
- BUG-SSO2-AFTER-SLO protections are present in token offload and SLO paths

## Next Steps

> [!tip]
> Next implementation step should validate these shared files under the shared compose stack before proceeding to broader migration work.

- wire the same assumptions into `shared/docker-compose.yml` and entrypoint env substitution
- verify OpenIG loads all routes cleanly in the shared stack
- validate SSO/SLO for app1 and app2 first, then continue with the remaining apps and [[Keycloak]] redirect/backchannel updates
