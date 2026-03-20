---
title: All Stacks OpenIG Non-Root Hardening
tags:
  - debugging
  - security
  - openig
  - vault
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-18
status: partial
---

# All Stacks OpenIG Non-Root Hardening

Related: [[OpenIG]] [[Vault]] [[stack-a|Stack A]] [[stack-b|Stack B]] [[stack-c|Stack C]]

## Context

- Investigated `[M-6/S-10]` from `.omc/plans/phase2-security-hardening.md`.
- Goal was to remove `user: root` from both OpenIG nodes in all three stacks, but only if default non-root execution remained viable.
- Required follow-up included container restart, Stack C placeholder restoration check, and route-loading log review.

## What Changed

- Kept `user: root` in all six OpenIG services.
- Replaced the lab-only comments in the three compose files with:
  - `lab macOS exception: non-root requires chmod on host volumes; production MUST use non-root (UID 11111)`

> [!success]
> The compose files now document the lab exception explicitly instead of leaving ambiguous `lab only` comments around a production-sensitive security decision.

## Findings

- Stack A and Stack B are not currently safe for non-root because the mounted Vault role ID file remains owner-only:
  - `stack-a/vault/file/openig-role-id` -> `-rw-------`
  - `stack-b/vault/file/openig-role-id` -> `-rw-------`
- Stack C is not currently safe for non-root because its entrypoint writes `openig_home/config/config.json` in place and the mounted vault files are also owner-only:
  - `stack-c/openig_home/vault/role_id` -> `-rw-------`
  - `stack-c/openig_home/vault/secret_id` -> `-rw-------`
- Stack C config placeholders were still intact at investigation time:
  - `grep -c '__JWT_SHARED_SECRET__' stack-c/openig_home/config/config.json` returned `1`
  - No restore was needed

> [!warning]
> Docker runtime validation could not be executed from this Codex session. Both `docker run` and `docker compose up` failed before reaching the containers because access to `/Users/duykim/.docker/run/docker.sock` is blocked by the sandbox with `connect: operation not permitted`.

## Current State

- Files changed:
  - `stack-a/docker-compose.yml`
  - `stack-b/docker-compose.yml`
  - `stack-c/docker-compose.yml`
- Non-root status:
  - `[[stack-a|Stack A]]`: blocked by unreadable mounted Vault role ID
  - `[[stack-b|Stack B]]`: blocked by unreadable mounted Vault role ID
  - `[[stack-c|Stack C]]`: blocked by in-place config mutation plus unreadable mounted Vault credentials

## Next Steps

1. From a shell with Docker daemon access, run the requested `docker run` non-root probe and the three `docker compose up -d` commands.
2. If non-root is still desired, widen only the required host-mounted file permissions, especially the Vault role/secret files used by OpenIG.
3. Re-check `stack-c/openig_home/config/config.json` after restart and restore placeholders only if the count for `__JWT_SHARED_SECRET__` drops to `0`.
4. Collect the requested `ERROR|WARN|Loaded the route` log lines from all six OpenIG containers after restart.
