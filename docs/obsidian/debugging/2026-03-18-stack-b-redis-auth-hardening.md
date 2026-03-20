---
title: Stack B Redis Auth Hardening
tags:
  - debugging
  - stack-b
  - openig
  - redis
  - security-hardening
date: 2026-03-18
status: complete
---

# Stack B Redis Auth Hardening

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack B]]

## Context

- Requested scope: Stack B only.
- Input status: `[H-4/S-2]` already confirmed in `.omc/plans/phase2-security-hardening.md`, so implementation proceeded directly with no extra investigation.
- Goal: require authentication on `redis-b` and make both Stack B Groovy Redis clients authenticate before blacklist `GET` and `SET`.

## Root Cause

- `stack-b/docker-compose.yml` started `redis-b` without `--requirepass`, so any container on the backend network could query or write blacklist keys without Redis authentication.
- `SessionBlacklistFilter.groovy` and `BackchannelLogoutHandler.groovy` wrote raw RESP commands over a socket but had no AUTH step, so enabling Redis password protection would break blacklist checks unless the client flow was updated at the same time.

> [!warning]
> Redis hardening had to be applied as a coordinated change across compose env wiring and both Groovy socket clients. Enabling `requirepass` alone would have caused fail-closed behavior in the OpenIG blacklist path.

## What Changed

- Generated a new Stack B-only Redis password with `openssl rand -hex 24` and rotated the `REDIS_PASSWORD` value in `stack-b/.env`.
- Added `REDIS_PASSWORD=<generate-strong-password>` to `stack-b/.env.example`.
- Updated `redis-b` to start with `redis-server --appendonly yes --appendfsync everysec --requirepass ${REDIS_PASSWORD}`.
- Passed `REDIS_PASSWORD` into `openig-b1` and `openig-b2`.
- Added RESP `AUTH` before Redis `GET` in `SessionBlacklistFilter.groovy` and required a `+OK` reply.
- Added the same RESP `AUTH` flow before Redis `SET ... EX ...` in `BackchannelLogoutHandler.groovy`.

> [!success]
> The Groovy change was intentionally narrow: only Redis AUTH handling was added. Existing blacklist lookup, TTL, redirect, JWT validation, and fail-closed behavior were left unchanged.

## Validation

- Ran `cd stack-b && docker compose up -d`.
- Waited 5 seconds, then restarted `sso-b-openig-1` and `sso-b-openig-2`.
- Verified `docker logs sso-b-openig-1 2>&1 | grep 'Loaded the route'` returned the expected Stack B route registrations after restart.
- Verified `docker exec sso-redis-b redis-cli PING` returned `NOAUTH Authentication required.`

> [!tip]
> Future Redis socket-based handlers in [[OpenIG]] should keep the same pattern: read `REDIS_PASSWORD` from env, send RESP `AUTH`, and fail closed if Redis returns anything other than `+OK`.

## Current State

- `redis-b` now requires authentication.
- Stack B OpenIG nodes receive `REDIS_PASSWORD` through compose environment variables.
- Blacklist `GET` and `SET` operations now authenticate before issuing Redis commands.
- Stack B restarted successfully after the hardening change.

## Files Changed

- `stack-b/.env`
- `stack-b/.env.example`
- `stack-b/docker-compose.yml`
- `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `docs/obsidian/debugging/2026-03-18-stack-b-redis-auth-hardening.md`
