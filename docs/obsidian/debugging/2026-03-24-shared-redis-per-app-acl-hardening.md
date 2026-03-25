---
title: Shared Redis Per-App ACL Hardening
tags:
  - debugging
  - security
  - redis
  - openig
  - shared-infra
date: 2026-03-24
status: completed
---

# Shared Redis Per-App ACL Hardening

Related: [[OpenIG]] [[Redis]] [[Vault]] [[Shared Stack]]

## Context

Hardened `shared/redis/acl.conf` so the six per-app Redis users no longer use `+@all`.

The task scope was:

- audit Redis commands in the shared Groovy scripts
- derive the minimal per-app ACL set
- apply the narrower ACL to `openig-app1` through `openig-app6`
- restart `shared-redis` and verify blocked versus allowed commands

## Redis Command Audit

- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
  - issues `AUTH`, `GET`, `SET`, `DEL`
  - `GET` reads `token_ref:*`
  - `SET ... EX` stores `token_ref:*`
  - `DEL` removes stale `token_ref:*`
- `shared/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - issues `AUTH`, `SET`
  - `SET ... EX` writes `blacklist:*`
- `shared/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - issues `AUTH`, `GET`
  - `GET` reads `blacklist:*`

> [!tip] ACL interpretation
> `EX` is a `SET` option, not a separate Redis ACL command. `AUTH` is implicit for ACL login and is not granted through the per-user command list.

## Root Cause And Fix

Per-app users were configured as:

- `~appN:* +@all`

That allowed high-risk commands such as `KEYS`, `SCAN`, and administrative/debug commands that the application scripts do not need.

Applied this narrower ACL pattern to all six users:

- `~appN:* -@all +set +get +del +exists +ping`

> [!warning] ACL rule-order gotcha
> Live testing showed that the requested order `+set +get +del +exists +ping -@all` removes the earlier grants. `SET` returned `NOPERM` until the rule order was reversed to `-@all +...`.

## Runtime Verification

- Restarted container: `shared-redis`
- Live generated ACL file at `/tmp/acl.conf` confirmed the six app users were loaded with the narrowed rules.
- No separate privileged admin ACL user exists in the generated file. The file contains `user default off nopass ...` plus only `openig-app1` through `openig-app6`.

Verification results with `openig-app1`:

- `ACL LIST` -> `NOPERM User openig-app1 has no permissions to run the 'acl|list' command`
- `KEYS '*'` -> `NOPERM User openig-app1 has no permissions to run the 'keys' command`
- `PING` -> `PONG`
- `SET app1:test:x val EX 60` -> `OK`
- `GET app1:test:x` -> `val`
- `EXISTS app1:test:x` -> `1`
- `DEL app1:test:x` -> `1`

> [!success] Hardened behavior confirmed
> Per-app Redis users can read and write only their prefixed keys with the narrow command set, and broad key-enumeration commands are blocked.

## Current State

- `shared/redis/acl.conf` is hardened for `openig-app1` to `openig-app6`
- commit created: `afef643`
- the requested `ACL LIST` verification cannot be executed post-hardening because no privileged ACL user exists in the current Redis ACL file

## Files Changed

- `shared/redis/acl.conf`
- `docs/obsidian/debugging/2026-03-24-shared-redis-per-app-acl-hardening.md`
