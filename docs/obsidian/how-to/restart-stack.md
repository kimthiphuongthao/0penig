---
title: How to Restart a Stack
tags:
  - ops
  - restart
  - how-to
date: 2026-03-12
status: guide
---

# How to Restart a Stack

Related: [[Vault]] [[OpenIG]] [[Stack A]] [[Stack B]] [[Stack C]]

Based on `.claude/rules/restart.md`.

## Compose Files

- `stack-a/docker-compose.yml`
- `stack-b/docker-compose.yml`
- `stack-c/docker-compose.yml`

## Procedure

1. Restart shared IdP first:
   - `cd keycloak && docker compose up -d`
2. For each stack (`stack-a`, `stack-b`, `stack-c`):
   - `cd <stack> && docker compose down`
   - `docker compose up -d`
3. Wait for Vault:
   - Check Vault becomes reachable, then unseal if needed.
   - Run stack bootstrap script to ensure AppRole + secrets are present.
4. Verify services:
   - OpenIG routes loaded in logs.
   - Nginx/OpenIG/Vault/Redis containers are healthy.
5. Test browser endpoints:
   - `http://wp-a.sso.local`
   - `http://redmine-b.sso.local:9080`
   - `http://jellyfin-b.sso.local:9080`
   - `http://grafana-c.sso.local:18080`
   - `http://phpmyadmin-c.sso.local:18080`

## Common Issues

- Vault sealed after restart:
  - Manually unseal Vault, then continue bootstrap.
- `openig-x2` missing Vault credentials:
  - Re-run bootstrap and regenerate `role_id`/`secret_id`.
- Keycloak not ready yet:
  - Wait longer and retry stack login tests.

> [!warning]
> Do not trust OpenIG readiness until Vault credentials are regenerated and both OpenIG nodes are restarted.
