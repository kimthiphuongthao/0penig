---
title: Stack A Redis auth hardening
tags:
  - debugging
  - stack-a
  - openig
  - redis
  - security
date: 2026-03-18
status: done
---

# Stack A Redis auth hardening

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]]

Context: implemented the confirmed `[H-4/S-2]` Stack A hardening item from `.omc/plans/phase2-security-hardening.md` with no investigation step. Scope was limited to Stack A gateway-side config and Groovy Redis socket flows.

## What changed

- Added `REDIS_PASSWORD` to `stack-a/.env` using a generated 48-hex-character value.
- Added `REDIS_PASSWORD` placeholder to `stack-a/.env.example`.
- Updated `stack-a/docker-compose.yml` so `redis-a` starts with `--requirepass ${REDIS_PASSWORD}`.
- Passed `REDIS_PASSWORD` into both `openig-1` and `openig-2`.
- Updated `SessionBlacklistFilter.groovy` to send Redis RESP `AUTH` before the existing `GET` command when `REDIS_PASSWORD` is present.
- Updated `BackchannelLogoutHandler.groovy` to send Redis RESP `AUTH` before the existing `SET` command when `REDIS_PASSWORD` is present.

> [!warning]
> The Groovy changes were intentionally narrow: only Redis authentication was added. Existing `GET`, `SET`, TTL, and `readRespLine` logic was left unchanged.

## Verification

> [!success]
> `docker compose up -d` completed successfully in `stack-a/` and recreated `sso-redis-a`, `sso-openig-1`, and `sso-openig-2`.

> [!success]
> After a 5 second wait, `docker restart sso-openig-1 sso-openig-2` succeeded and `docker logs sso-openig-1 | grep 'Loaded the route'` showed the expected routes:
> `00-wp-logout`, `00-backchannel-logout-app1`, `01-wordpress`, `02-app2`.

> [!success]
> Redis auth enforcement is active:
> `docker exec sso-redis-a redis-cli PING` returned `NOAUTH Authentication required.`
> `docker exec sso-redis-a redis-cli -a <generated-password> PING` returned `PONG`.

## Current state

- Stack A Redis now requires authentication at the container level.
- Both OpenIG nodes receive the Redis password through environment variables.
- Stack A blacklist `GET` and backchannel logout `SET` operations now authenticate before using Redis.

> [!tip]
> Keep `stack-a/.env.example` as the committed contract only. The generated password belongs in `stack-a/.env` or an external secret source, not in versioned examples.
