---
title: Shared Infra Step 1 Foundation
tags:
  - sso-lab
  - shared-infra
  - openig
  - vault
  - redis
  - nginx
date: 2026-03-23
status: completed
---

# Shared Infra Step 1 Foundation

Context: created the Step 1 shared infrastructure skeleton that consolidates infra from [[Stack A]], [[Stack B]], and [[Stack C]] into a new shared runtime for [[OpenIG]], [[Vault]], [[Redis]], and [[Nginx]].

> [!success] Validation
> `cd shared && docker compose config 2>&1 | head -20` returned a valid Compose render with no errors.

## What Was Done

- Created `shared/` skeleton with compose, env template, gitignore, nginx, redis, vault, and OpenIG config directories.
- Copied direct carry-over files:
  - `shared/docker/openig/server.xml` from [[Stack A]]
  - `shared/docker/wordpress/app1.conf` from [[Stack A]]
  - `shared/docker/mysql/init.sql` from [[Stack A]]
  - `shared/vault/config/vault.hcl` from [[Stack A]]
  - `shared/openig_home/phpmyadmin/config.user.inc.php` from [[Stack C]]
- Added shared `docker-compose.yml` with:
  - single `shared-nginx`
  - two HA OpenIG nodes
  - one Redis 7 ACL service
  - one Vault service
  - WordPress, WhoAmI, Redmine, Jellyfin, Grafana, phpMyAdmin
  - MySQL A, MySQL B, MariaDB C
- Added `shared/nginx/nginx.conf` with 7 server blocks and merged backchannel logout endpoints for app1-app6.
- Added `shared/redis/acl.conf` with per-app ACL users `openig-app1` through `openig-app6`.
- Added `shared/docker/redis/redis-entrypoint.sh` to render Redis ACL passwords from env at container startup.
- Added `shared/openig_home/config/config.json` with a single shared `Session` cookie named `IG_SSO`.
- Added `shared/docker/openig/docker-entrypoint.sh` to copy mounted config into `/tmp/openig` and render secrets into `config.json`.
- Added `shared/vault/init/vault-bootstrap.sh` with 6 AppRoles, 6 policies, seeded secrets, role_id/secret_id file output, audit enablement, admin token flow, and role hardening.
- Created placeholder runtime directories for Vault/OpenIG and empty route/Groovy directories for Step 2.

## Decisions

> [!tip] Build Strategy
> `stack-a/docker/openig/Dockerfile` does not exist, so shared OpenIG uses the upstream image plus bind-mounted `docker-entrypoint.sh` instead of a local build context.

> [!warning] Source Gaps
> `stack-b` has no `docker/mysql-redmine` init SQL to copy, so `shared/docker/mysql-redmine/` is only a placeholder directory.
>
> `stack-b/vault/init/vault-bootstrap.sh` defines Jellyfin policy scope but does not seed Jellyfin credentials. The shared bootstrap therefore uses deterministic temporary lab defaults for `secret/jellyfin-creds/alice` and `secret/jellyfin-creds/bob` until the actual app-side passwords are confirmed.

## Current State

- `shared/openig_home/config/routes/` is intentionally empty for Step 2.
- `shared/openig_home/scripts/groovy/` is intentionally empty for Step 2.
- Redis ACL key scoping is aligned to the consolidation plan:
  - `app1:*`
  - `app2:*`
  - `app3:*`
  - `app4:*`
  - `app5:*`
  - `app6:*`
- Vault AppRoles are aligned to the same per-app isolation model.

## Next Steps

1. Populate shared route JSON files in `shared/openig_home/config/routes/`.
2. Populate shared Groovy scripts in `shared/openig_home/scripts/groovy/`.
3. Replace temporary Jellyfin Vault seed passwords if the canonical lab values are recovered.
4. Revisit `shared/docker/mysql/init.sql` if DB user passwords also need to move to template-driven startup instead of copied lab defaults.

## Files Changed

- `shared/docker-compose.yml`
- `shared/.env.example`
- `shared/.gitignore`
- `shared/nginx/nginx.conf`
- `shared/redis/acl.conf`
- `shared/docker/redis/redis-entrypoint.sh`
- `shared/vault/config/vault.hcl`
- `shared/vault/init/vault-bootstrap.sh`
- `shared/openig_home/config/config.json`
- `shared/openig_home/phpmyadmin/config.user.inc.php`
- `shared/docker/openig/docker-entrypoint.sh`
- `shared/docker/openig/server.xml`
- `shared/docker/wordpress/app1.conf`
- `shared/docker/mysql/init.sql`
