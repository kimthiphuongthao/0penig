---
title: Shared Bootstrap Wrapper
tags:
  - ops
  - vault
  - openig
  - how-to
date: 2026-03-25
status: guide
---

# Shared Bootstrap Wrapper

Related: [[Vault]] [[OpenIG]] [[Keycloak]]

## Context

`shared/bootstrap.sh` was added to turn the manual shared-infra restart checklist into a single repeatable command for the shared SSO lab.

> [!success]
> The wrapper now handles idempotent [[Vault]] initialization, AppRole `secret_id` regeneration, and [[OpenIG]] node restarts in one step.

## What Was Done

- Added `shared/bootstrap.sh`.
- Enforced a `shared/` working-directory guard by checking `docker-compose.yml`.
- Sourced `shared/.env` when present and warned when absent.
- Verified `shared-vault` is running before any bootstrap or AppRole work.
- Used `/vault/data/.bootstrap-done` to skip first-time Vault seeding when it is already complete.
- Copied `vault/init/vault-bootstrap.sh` into `shared-vault` and executed it only when bootstrap is still pending.
- Regenerated `vault/file/openig-app1-secret-id` through `vault/file/openig-app6-secret-id`.
- Restarted `shared-openig-1` and `shared-openig-2`, then checked recent `Loaded the route` log lines for both nodes.

## Decision

The wrapper passes credential variables into `docker exec` explicitly during the first Vault bootstrap.

Rationale:
- `shared/vault/init/vault-bootstrap.sh` requires the application password variables for secret seeding.
- `shared-vault` only receives `VAULT_ADDR` from `shared/docker-compose.yml`, so those passwords are not otherwise present inside the container.

> [!warning]
> If `.env` is missing or incomplete during the first bootstrap, the wrapper fails before secret seeding. Once `/vault/data/.bootstrap-done` exists, re-runs remain safe and skip that initialization path.

## Current State

- `shared/bootstrap.sh` is executable.
- The script was committed as `d8644d2`.
- The wrapper is ready to use from `shared/` with `./bootstrap.sh`.

## Next Steps

- Run `cd shared && ./bootstrap.sh` after `docker compose up -d` for shared infra.
- Re-run the wrapper after Vault data loss or when AppRole `secret_id` values need regeneration.

> [!tip]
> This wrapper replaces the repeated manual sequence documented in `.claude/rules/restart.md` for the shared-infra lab workflow.

## Files Changed

- `shared/bootstrap.sh`
