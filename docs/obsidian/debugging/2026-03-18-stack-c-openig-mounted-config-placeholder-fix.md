---
title: Stack C OpenIG Mounted Config Placeholder Fix
tags:
  - debugging
  - stack-c
  - openig
  - grafana
  - secrets
date: 2026-03-18
status: partial
---

# Stack C OpenIG Mounted Config Placeholder Fix

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Requested scope: Stack C only.
- Reported failure: `stack-c/openig_home/config/config.json` still contained `__KEYSTORE_PASSWORD__` and `__JWT_SHARED_SECRET__`, so `JwtSessionManager` failed during OpenIG startup and Grafana SSO stayed down.
- Constraint: only Stack C gateway files could be changed.

## Root Cause

- `stack-c/docker/openig/docker-entrypoint.sh` copied `/opt/openig` to `/tmp/openig` and ran `sed` against `/tmp/openig/config/config.json`.
- Stack C runtime evidence indicates OpenIG still reads the active config from `/opt/openig/config/config.json`, not the temporary copy.
- Result: the mounted config kept the placeholders and OpenIG failed with `JsonValueException: /heap/2/config/password: Expecting a value`.

> [!warning]
> The path mismatch was the primary defect. The previous `sed` logic was also brittle for future secrets containing `&`, `|`, or `\`.

## What Changed

- Updated `stack-c/docker/openig/docker-entrypoint.sh` to:
  - resolve the config file from `${OPENIG_BASE:-/opt/openig}/config/config.json`
  - fail fast if the expected config file is missing
  - escape `&`, `|`, and `\` before substitution
  - render secrets directly into the mounted config OpenIG actually reads
- Kept `stack-c/openig_home/config/config.json` templated so Stack C still sources secrets from environment at startup.

> [!success]
> A local dry-run against a temporary copy of `stack-c/openig_home` replaced both placeholders with the current `stack-c/.env` values and produced a config with no `__KEYSTORE_PASSWORD__` or `__JWT_SHARED_SECRET__` tokens.

## Current State

- Stack C entrypoint fix: implemented
- Repository `config.json`: still templated by design
- Docker recreate and live log verification: blocked in this Codex sandbox because Docker socket access is denied

## Next Steps

1. Run `cd stack-c && docker compose up -d --force-recreate openig-c1 openig-c2` on a host with Docker access.
2. Wait 8 seconds and check `docker logs stack-c-openig-c1-1 2>&1 | tail -20`.
3. Confirm the `JsonValueException: /heap/2/config/password` error is gone and Grafana SSO can complete.

## Files Changed

- `stack-c/docker/openig/docker-entrypoint.sh`
- `docs/obsidian/debugging/2026-03-18-stack-c-openig-mounted-config-placeholder-fix.md`
