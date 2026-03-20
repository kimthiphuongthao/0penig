---
title: Stack C Redis AUTH hardening
tags:
  - debugging
  - stack-c
  - redis
  - openig
  - security
date: 2026-03-18
status: completed
---

# Stack C Redis AUTH hardening

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[stack-c]]

## Context

- Phase 2 hardening item `[H-4/S-2]` was already confirmed in `.omc/plans/phase2-security-hardening.md`.
- Scope was Stack C only.
- Constraint: do not change target apps; only touch Stack C gateway/runtime files.

## What changed

- `stack-c/.env`
  - Added `REDIS_PASSWORD` with a new `openssl rand -hex 24` value.
- `stack-c/.env.example`
  - Added `REDIS_PASSWORD=<generate-strong-password>`.
- `stack-c/docker-compose.yml`
  - `redis-c` now starts with `redis-server --appendonly yes --appendfsync everysec --requirepass ${REDIS_PASSWORD}`.
  - `openig-c1` and `openig-c2` now receive `REDIS_PASSWORD` via environment.
- `SessionBlacklistFilter.groovy`
  - Added RESP `AUTH` before the existing Redis `GET blacklist:{sid}` call.
  - Script still fails closed if Redis auth or lookup fails.
- `BackchannelLogoutHandler.groovy`
  - Added RESP `AUTH` before the existing Redis `SET blacklist:{sid}` call.
  - Existing blacklist write logic and retry-friendly error handling were left intact.

> [!success]
> Stack C Redis now rejects unauthenticated clients while the OpenIG blacklist read/write path authenticates explicitly over the raw socket protocol already used in the scripts.

## Verification

- `docker compose up -d` in `stack-c/`: completed successfully.
- Waited 5 seconds, then restarted:
  - `stack-c-openig-c1-1`
  - `stack-c-openig-c2-1`
- `docker logs stack-c-openig-c1-1 2>&1 | grep 'Loaded the route'`
  - Confirmed route load lines after restart, including both backchannel logout routes and both app routes.
- `docker exec stack-c-redis-c-1 redis-cli PING`
  - Returned `NOAUTH Authentication required.`

> [!tip]
> Because the Groovy scripts speak RESP directly over `Socket`, adding `AUTH` there is sufficient. No extra Redis client library or route change was needed.

## Current state

- Stack C Redis blacklist storage is password-protected.
- Stack C OpenIG nodes have the password in environment and authenticate before Redis `GET`/`SET`.
- Persistence settings remain `appendonly yes` and `appendfsync everysec`.

> [!warning]
> The generated Redis password is stored only in `stack-c/.env`; do not copy it into tracked documentation or committed examples.

## Files changed

- `stack-c/.env`
- `stack-c/.env.example`
- `stack-c/docker-compose.yml`
- `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `docs/obsidian/debugging/2026-03-18-stack-c-redis-auth-hardening.md`
