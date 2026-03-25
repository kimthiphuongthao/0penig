---
title: Vault credential sync verification
tags:
  - openig
  - vault
  - debugging
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-23
status: done
---

# Vault credential sync verification

Context: verified live application and database credentials used by [[OpenIG]] via [[Vault]] across [[Stack A]], [[Stack B]], and [[Stack C]] without changing target app databases.

## Root cause

The reported drift was mixed:

- [[Jellyfin]] had stale Vault passwords (`[REDACTED]` / `[REDACTED]`) while the live app accepted `[REDACTED]` / `[REDACTED]`.
- [[WordPress]], [[Redmine]], and [[phpMyAdmin]] were already aligned to their current live credentials, but some external instructions referenced outdated passwords.
- `.env` does not match the variable names consumed by `docker-compose.yml` for `shared-mysql-a` and `shared-mariadb`, so those services currently run with fallback root passwords.

> [!success]
> Confirmed live Jellyfin logins:
> `alice / [REDACTED]`
> `bob / [REDACTED]`

## What changed

- Updated Vault path `secret/jellyfin-creds/alice` to `username=alice`, `email=alice@lab.local`, `password=[REDACTED]`
- Updated Vault path `secret/jellyfin-creds/alice@lab.local` to `username=alice`, `email=alice@lab.local`, `password=[REDACTED]`
- Updated Vault path `secret/jellyfin-creds/bob` to `username=bob`, `email=bob@lab.local`, `password=[REDACTED]`
- Updated Vault path `secret/jellyfin-creds/bob@lab.local` to `username=bob`, `email=bob@lab.local`, `password=[REDACTED]`

## Verified state

- [[WordPress]]
  - `alice_wp / [REDACTED]` verified via `wp_check_password(...)`
  - `bob_wp / [REDACTED]` verified via `wp_check_password(...)`
- [[Redmine]]
  - `alice / [REDACTED]` verified via `User.try_to_login(...)`
  - `bob / [REDACTED]` verified via `User.try_to_login(...)`
- [[phpMyAdmin]]
  - MariaDB user `alice / [REDACTED]` verified directly
  - MariaDB user `bob / [REDACTED]` verified directly
- Root passwords currently in effect
  - `shared-mysql-a`: `[REDACTED]`
  - `shared-mysql-b`: `[REDACTED]`
  - `shared-mariadb`: `[REDACTED]`

> [!warning]
> Did not change `.env`. That is outside the gateway-only scope for this task, and it would not affect already-running containers without a restart.

> [!tip]
> For [[WordPress]], the route in `openig_home/config/routes/01-wordpress.json` expects Vault fields `username` and `password`. Writing only `wp_username` / `wp_password` would not satisfy `VaultCredentialFilter.groovy`.

## Current state

Only [[Jellyfin]] required a Vault update. [[WordPress]], [[Redmine]], and [[phpMyAdmin]] are already synchronized to the live credentials in their current Vault secrets. The `.env` naming mismatch remains as an infra follow-up for a separate, non-gateway change.

Files changed: `docs/obsidian/debugging/2026-03-23-vault-credential-sync-verification.md`

Related systems: [[OpenIG]], [[Vault]], [[Jellyfin]], [[WordPress]], [[Redmine]], [[phpMyAdmin]], [[Stack A]], [[Stack B]], [[Stack C]]
