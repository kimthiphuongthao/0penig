---
title: SSO Lab Restart Checklist
tags:
  - debugging
  - sso
  - openig
  - keycloak
  - vault
date: 2026-03-22
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

- `sso-keycloak` started successfully on 2026-03-22 and Docker marked the container `healthy`.
- [[Stack A]], [[Stack B]], and [[Stack C]] all completed `docker compose up -d`, Vault bootstrap, AppRole credential regeneration, and OpenIG restarts without command failures.
- `docker logs --since <StartedAt> ... | grep "Loaded the route"` confirmed the expected route count for each primary OpenIG node.
- Startup log scans since the current container start showed no `ERROR`, `Exception`, `SEVERE`, or `Failed` lines for `sso-openig-1`, `sso-b-openig-1`, or `stack-c-openig-c1-1`.

> [!success]
> Runtime verification on 2026-03-22: `sso-openig-1`, `sso-b-openig-1`, and `stack-c-openig-c1-1` all came back `healthy` and reloaded their expected routes for the current restart cycle.

## Evidence

- Keycloak:
  - `docker compose up -d` completed in `keycloak/`
  - `kc-mysql`: `Up ... (healthy)`
  - `sso-keycloak`: `Up ... (healthy)`
- Stack A:
  - `sso-vault` bootstrap reported `Already bootstrapped.`
  - regenerated `vault/file/openig-role-id` and `vault/file/openig-secret-id`
  - restarted `sso-openig-1` and `sso-openig-2`
  - `sso-openig-1` started at `2026-03-22T07:54:55.163809214Z`
  - loaded routes for this startup:
    - `00-wp-logout`
    - `00-backchannel-logout-app1`
    - `01-wordpress`
    - `02-app2`
- Stack B:
  - `sso-b-vault` bootstrap reported `Already bootstrapped.`
  - regenerated `vault/file/openig-role-id` and `vault/file/openig-secret-id`
  - restarted `sso-b-openig-1` and `sso-b-openig-2`
  - `sso-b-openig-1` started at `2026-03-22T07:55:20.273181629Z`
  - loaded routes for this startup:
    - `00-redmine-logout`
    - `02-redmine`
    - `00-jellyfin-logout`
    - `01-jellyfin`
    - `00-backchannel-logout-app3`
    - `00-backchannel-logout-app4`
- Stack C:
  - `stack-c-vault-c-1` bootstrap reported `Already bootstrapped.`
  - regenerated `openig_home/vault/role_id` and `openig_home/vault/secret_id`
  - restarted `stack-c-openig-c1-1` and `stack-c-openig-c2-1`
  - `stack-c-openig-c1-1` started at `2026-03-22T07:55:43.885029792Z`
  - loaded routes for this startup:
    - `00-backchannel-logout-app5`
    - `00-phpmyadmin-logout`
    - `00-backchannel-logout-app6`
    - `00-grafana-logout`
    - `11-phpmyadmin`
    - `10-grafana`

> [!tip]
> When counting loaded routes, use `docker logs --since "$(docker inspect -f '{{.State.StartedAt}}' <container>)"` instead of the full container log so repeated hot-reload history does not inflate the count.

## Current State

- `sso-keycloak`: `healthy`
- `sso-openig-1`, `sso-openig-2`: `healthy`
- `sso-b-openig-1`, `sso-b-openig-2`: `healthy`
- `stack-c-openig-c1-1`, `stack-c-openig-c2-1`: `healthy`
- Vault containers for all three stacks remained up during bootstrap and AppRole regeneration.

> [!success]
> Current lab state on 2026-03-22 is consistent with the restart checklist: Keycloak is healthy, all three stacks are up, and each primary [[OpenIG]] node loaded the expected route set with no startup errors detected.

## Next Steps

1. Reuse the same `--since StartedAt` verification pattern for future restart runs.
2. If a future restart reports missing routes, compare the fresh route list in this note against the corresponding [[Stack A]], [[Stack B]], or [[Stack C]] route inventory first.

## Files Changed

- `docs/obsidian/debugging/2026-03-22-sso-lab-restart-checklist.md`
