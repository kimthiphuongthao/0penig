---
title: Stack C phpMyAdmin Vault MariaDB Credential Drift
tags:
  - debugging
  - stack-c
  - vault
  - mariadb
  - phpmyadmin
  - openig
date: 2026-03-19
status: partial-fix
---

# Stack C phpMyAdmin Vault MariaDB Credential Drift

Related: [[OpenIG]] [[Vault]] [[Keycloak]] [[Stack C]]

## Context

- Task: investigate Stack C phpMyAdmin `ERROR 1045` and align Vault-backed credentials with live MariaDB state without modifying target app data or config.
- Constraint respected: only [[Vault]] state and `vault-bootstrap.sh` were changed.

## Findings

- The workspace does not contain `stack-c/mariadb/init/` or any Stack C SQL init files.
- Live MariaDB was seeded only from `stack-c/.env` and `stack-c/docker-compose.yml`.
- `stack-c-mariadb-1` was created with:
  - `MYSQL_USER=alice`
  - `MYSQL_PASSWORD=AlicePass123`
- MariaDB logs confirm initialization created only user `alice` for schema `appdb`.
- Querying `mysql.user` shows `alice@'%'` exists and no `bob` row exists.
- Direct MariaDB login succeeds for `alice` with `AlicePass123`.
- Direct MariaDB login fails for `alice` with the Vault bootstrap password `mF0k6thz34B8XbAWEn0tIRVCc3!`.
- Live Vault values before the fix were:
  - `secret/phpmyadmin/alice` -> `alice / mF0k6thz34B8XbAWEn0tIRVCc3!`
  - `secret/phpmyadmin/bob` -> `bob / OYHupH3pbskR6sY5vcKr6X0Dd4$`
- The host `admin` token could not read `secret/data/phpmyadmin/*` because policy `vault-admin` lacked KV data path permissions.

## Root Cause

- [[Vault]] bootstrap seeded phpMyAdmin credentials that did not match live [[MariaDB]] initialization for `alice`.
- Stack C live MariaDB does not currently provision `bob`, so there is no MariaDB password to align for `bob`.
- `vault-admin` policy was also incomplete, which blocked the maintenance workflow from reading or updating phpMyAdmin secrets with the documented admin token.

> [!success]
> `secret/phpmyadmin/alice` now matches the live MariaDB password `AlicePass123`, and both OpenIG nodes reloaded route `11-phpmyadmin` after restart.

## Fix Applied

- Recovered temporary root access from the existing unseal key.
- Updated live Vault policy `vault-admin` to allow:
  - `read`, `create`, `update` on `secret/data/phpmyadmin/*`
  - `read`, `list` on `secret/metadata/phpmyadmin/*`
- Updated live secret `secret/phpmyadmin/alice` to:
  - `username=alice`
  - `password=AlicePass123`
- Restarted:
  - `stack-c-openig-c1-1`
  - `stack-c-openig-c2-1`
- Patched `stack-c/vault/init/vault-bootstrap.sh` so future bootstrap keeps:
  - `alice` aligned to `AlicePass123`
  - `vault-admin` able to manage phpMyAdmin KV secrets

## Current State

- `vault kv get secret/phpmyadmin/alice` with the host admin token now succeeds.
- `vault kv get secret/phpmyadmin/bob` with the host admin token now succeeds.
- `alice` can authenticate directly to MariaDB with `AlicePass123`.
- `bob` still cannot authenticate because there is no MariaDB `bob` account in the live stack.
- `docker logs` after restart show route `11-phpmyadmin` loaded on both OpenIG nodes.

> [!warning]
> This task could only fix `alice` within the allowed Vault-side scope. `bob` remains unresolved because Stack C live MariaDB has no `bob` user to match. Fixing `bob` requires a target-app/database change, which is outside the allowed scope for this task.

## Follow-up

1. Decide whether Stack C should support phpMyAdmin login for `bob` at all.
2. If yes, provision `bob` on the MariaDB side through the approved app-side workflow, then align `secret/phpmyadmin/bob` to that real password.
3. If no, remove `bob` from the phpMyAdmin credential flow or document `alice` as the only supported MariaDB identity for Stack C.

> [!tip]
> The documented Vault maintenance flow should use the host admin token only after the `vault-admin` policy includes `secret/data/phpmyadmin/*` access. Without that policy, `vault kv get` and `vault kv put` fail with `403 permission denied`.

## Files Changed

- `stack-c/vault/init/vault-bootstrap.sh`
- `docs/obsidian/debugging/2026-03-19-stack-c-phpmyadmin-vault-mariadb-credential-drift.md`
