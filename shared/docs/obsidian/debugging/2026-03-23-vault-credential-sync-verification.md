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

- [[Jellyfin]] had stale Vault passwords (`AliceJelly2026` / `BobJelly2026`) while the live app accepted `alice123` / `bob123`.
- [[WordPress]], [[Redmine]], and [[phpMyAdmin]] were already aligned to their current live credentials, but some external instructions referenced outdated passwords.
- `.env` does not match the variable names consumed by `docker-compose.yml` for `shared-mysql-a` and `shared-mariadb`, so those services currently run with fallback root passwords.

> [!success]
> Confirmed live Jellyfin logins:
> `alice / alice123`
> `bob / bob123`

## What changed

- Updated Vault path `secret/jellyfin-creds/alice` to `username=alice`, `email=alice@lab.local`, `password=alice123`
- Updated Vault path `secret/jellyfin-creds/alice@lab.local` to `username=alice`, `email=alice@lab.local`, `password=alice123`
- Updated Vault path `secret/jellyfin-creds/bob` to `username=bob`, `email=bob@lab.local`, `password=bob123`
- Updated Vault path `secret/jellyfin-creds/bob@lab.local` to `username=bob`, `email=bob@lab.local`, `password=bob123`

## Verified state

- [[WordPress]]
  - `alice_wp / SDCNDniqeJaYQq3gDcexAa1@` verified via `wp_check_password(...)`
  - `bob_wp / aLOgjDTxlOTWwjEy5QFTBb2#` verified via `wp_check_password(...)`
- [[Redmine]]
  - `alice / Nvt2vrmNrbjcG4aF9XhBj0aMAa7!` verified via `User.try_to_login(...)`
  - `bob / 5RUgSmgwA70jttqhkL4TketBb8@` verified via `User.try_to_login(...)`
- [[phpMyAdmin]]
  - MariaDB user `alice / AlicePass123` verified directly
  - MariaDB user `bob / OYHupH3pbskR6sY5vcKr6X0Dd4` verified directly
- Root passwords currently in effect
  - `shared-mysql-a`: `changeme_mysql_root_password_a`
  - `shared-mysql-b`: `root_pass`
  - `shared-mariadb`: `changeme_mysql_root_password_c`

> [!warning]
> Did not change `.env`. That is outside the gateway-only scope for this task, and it would not affect already-running containers without a restart.

> [!tip]
> For [[WordPress]], the route in `openig_home/config/routes/01-wordpress.json` expects Vault fields `username` and `password`. Writing only `wp_username` / `wp_password` would not satisfy `VaultCredentialFilter.groovy`.

## Current state

Only [[Jellyfin]] required a Vault update. [[WordPress]], [[Redmine]], and [[phpMyAdmin]] are already synchronized to the live credentials in their current Vault secrets. The `.env` naming mismatch remains as an infra follow-up for a separate, non-gateway change.

Files changed: `docs/obsidian/debugging/2026-03-23-vault-credential-sync-verification.md`

Related systems: [[OpenIG]], [[Vault]], [[Jellyfin]], [[WordPress]], [[Redmine]], [[phpMyAdmin]], [[Stack A]], [[Stack B]], [[Stack C]]
