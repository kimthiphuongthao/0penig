---
title: Shared Infra SSO Lab Audit
tags:
  - openig
  - debugging
  - shared-infra
  - audit
  - security
date: 2026-03-25
status: done
---

# Shared Infra SSO Lab Audit

Related: [[OpenIG]] [[Vault]] [[Redis]] [[Keycloak]]

## Context

Performed a full audit of the shared-infra SSO lab surface:

- `shared/openig_home/scripts/groovy/`
- `shared/openig_home/config/routes/`
- `shared/docker-compose.yml`
- `shared/nginx/nginx.conf`
- `shared/redis/acl.conf`
- `shared/docker/redis/redis-entrypoint.sh`
- `shared/vault/init/vault-bootstrap.sh`
- `shared/bootstrap.sh`
- `shared/openig_home/config/config.json`
- `shared/.env.example`
- key restart and shared debugging docs

## Top Findings

> [!warning]
> One in-scope Obsidian note under `shared/docs/obsidian/debugging/` contains live downstream passwords in plaintext. That is the highest-risk issue in the current repository state and should be treated as a credential leak.

> [!warning]
> `JellyfinTokenInjector.groovy` still proxies non-HTML requests when no gateway-managed Jellyfin token exists. Because it does not strip client-supplied auth headers first, downstream account isolation can be bypassed.

> [!warning]
> Shared backchannel logout coverage is incomplete for app2. Nginx exposes `/openid/app2/backchannel_logout`, but there is no matching `00-backchannel-logout-app2.json` handler in the route set.

## Findings Summary

- `CRITICAL`: 1
- `HIGH`: 3
- `MEDIUM`: 5
- `LOW`: 4
- `INFO`: 5

## Confirmed Good State

> [!success]
> Redis ACLs are scoped per app (`openig-app1` to `openig-app6`) with `user default off` and `~appN:*` key restrictions.

> [!success]
> Route-local `JwtSession` heaps and `IG_SSO_APP1` to `IG_SSO_APP6` cookies line up correctly across the active app routes and logout intercept routes.

> [!success]
> Source-time placeholders in `shared/openig_home/config/config.json` are expected. `shared/docker/openig/docker-entrypoint.sh` replaces `__JWT_SHARED_SECRET__` and `__KEYSTORE_PASSWORD__` in the runtime copy under `/tmp/openig/config/config.json`, and `shared/openig_home/keystore.p12` exists.

## Current State

The shared lab is functionally close, but it is not production-ready yet. The remaining gaps are concentrated in secrets hygiene, a Jellyfin authorization fail-open path, incomplete app2 backchannel coverage, and a few infra consistency issues around bootstrap, hardcoded fallbacks, and mutable image tags.

## Next Steps

1. Remove the plaintext-password note and rotate every exposed credential it contains.
2. Harden `JellyfinTokenInjector.groovy` to strip client auth headers and fail closed when no managed token is available.
3. Add or explicitly retire app2 backchannel logout support.
4. Fix `shared/bootstrap.sh` file permissions and move the Vault bootstrap marker to the true end of `vault-bootstrap.sh`.
5. Replace stale `openiga/openigb` and `:9080/:18080` fallbacks with shared port-80 defaults or explicit hard failures.

Files changed:

- `docs/obsidian/debugging/2026-03-25-shared-infra-sso-lab-audit.md`
