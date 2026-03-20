---
title: SSO Lab Restart Checklist
tags:
  - debugging
  - sso
  - openig
  - keycloak
  - vault
date: 2026-03-18
status: complete
---

# SSO Lab Restart Checklist

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Requested action: restart the full SSO lab in the fixed order `Keycloak -> Stack A -> Stack B -> Stack C`.
- Required post-start tasks:
  - re-run Vault bootstrap for each stack
  - regenerate OpenIG AppRole credentials from Vault
  - restart OpenIG nodes after writing the fresh role and secret IDs
  - verify route load lines from the primary OpenIG node in each stack

## Findings

- `sso-keycloak` started successfully on 2026-03-18 and Docker marked the container `healthy`.
- Stack A, Stack B, and Stack C all completed `docker compose up -d`, Vault bootstrap, AppRole credential regeneration, and OpenIG restarts without command failures.
- All three primary OpenIG nodes emitted `Loaded the route` log lines after restart.

> [!success]
> Runtime verification on 2026-03-18: `sso-openig-1`, `sso-b-openig-1`, and `stack-c-openig-c1-1` all came back `healthy` and reloaded their expected routes.

> [!warning]
> The requested Keycloak poll command `docker exec sso-keycloak curl -s http://localhost:8080/health/ready` failed because the `sso-keycloak` image does not contain `curl`. This was an image/tooling issue, not a Keycloak startup failure.

## Evidence

- Keycloak:
  - `docker compose up -d` completed in `keycloak/`
  - `docker inspect --format 'STATUS={{.State.Health.Status}}' sso-keycloak` returned `STATUS=healthy`
  - `docker exec sso-keycloak curl -s http://localhost:8080/health/ready` returned `exec: "curl": executable file not found in $PATH`
- Stack A:
  - `sso-vault` bootstrap reported `Already bootstrapped.`
  - regenerated `vault/file/openig-role-id` and `vault/file/openig-secret-id`
  - restarted `sso-openig-1` and `sso-openig-2`
  - unique loaded routes:
    - `00-backchannel-logout-app1`
    - `00-wp-logout`
    - `01-wordpress`
    - `02-app2`
- Stack B:
  - `sso-b-vault` bootstrap reported `Already bootstrapped.`
  - regenerated `vault/file/openig-role-id` and `vault/file/openig-secret-id`
  - restarted `sso-b-openig-1` and `sso-b-openig-2`
  - unique loaded routes:
    - `00-backchannel-logout-app3`
    - `00-backchannel-logout-app4`
    - `00-jellyfin-logout`
    - `00-redmine-logout`
    - `01-jellyfin`
    - `02-redmine`
- Stack C:
  - `stack-c-vault-c-1` bootstrap reported `Already bootstrapped.`
  - regenerated `openig_home/vault/role_id` and `openig_home/vault/secret_id`
  - restarted `stack-c-openig-c1-1` and `stack-c-openig-c2-1`
  - unique loaded routes:
    - `00-backchannel-logout-app5`
    - `00-backchannel-logout-app6`
    - `00-grafana-logout`
    - `00-phpmyadmin-logout`
    - `10-grafana`
    - `11-phpmyadmin`

> [!tip]
> For future restart automation, change the Keycloak readiness probe to a tool guaranteed to exist in the image, or poll Docker health status directly before moving to [[Stack A]].

## Current State

- `sso-keycloak`: `healthy`
- `sso-openig-1`, `sso-openig-2`: `healthy`
- `sso-b-openig-1`, `sso-b-openig-2`: `healthy`
- `stack-c-openig-c1-1`, `stack-c-openig-c2-1`: `healthy`
- Vault containers for all three stacks remained up during bootstrap and AppRole regeneration.

## Next Steps

1. If the restart checklist is reused, replace the Keycloak `curl` poll with a working probe for the current image.
2. Keep using the existing Vault bootstrap scripts because they proved idempotent across all three stacks.

## Files Changed

- `docs/obsidian/debugging/2026-03-18-sso-lab-restart-checklist.md`
