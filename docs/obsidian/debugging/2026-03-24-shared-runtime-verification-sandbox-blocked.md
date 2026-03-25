---
title: Shared Runtime Verification Sandbox Blocked
tags:
  - debugging
  - shared-infra
  - openig
  - redis
  - vault
date: 2026-03-24
status: completed
---

# Shared Runtime Verification Sandbox Blocked

Related: [[OpenIG]] [[Vault]] [[Redis]] [[Shared Stack]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

Validated the current shared runtime state without making any infrastructure changes. The task required exact raw output for Docker runtime checks plus filesystem checks for the shared compose and shared OpenIG route/script directories.

> [!warning] Runtime verification limit
> The Codex sandbox could not access the local Docker daemon socket at `/Users/duykim/.docker/run/docker.sock`.
> As a result, `docker ps`, `docker inspect`, and `docker logs` could not return live container state from this session.

## What Was Verified

- `shared/docker-compose.yml` exists.
- Shared compose defines:
  - `shared-openig-1`
  - `shared-openig-2`
  - `shared-redis`
  - `shared-vault`
- Both shared OpenIG services bind `./openig_home` through the shared volume anchor:
  - `./openig_home:/opt/openig-config:ro`
- Shared route directory is populated with 16 route JSON files covering app1-app6 plus logout routes.
- Shared Groovy directory is populated with the expected shared script set.

> [!success] Config-side alignment
> Repository state matches the shared-infrastructure target shape: 2 shared [[OpenIG]] nodes, 1 shared [[Redis]], 1 shared [[Vault]], and a consolidated shared route/script set.

## Plan vs Reality

- Plan target from [[Shared Stack]] and `.omc/plans/shared-infra.md` is a single shared HA gateway pair with shared Redis and Vault.
- Repository configuration matches that target.
- Live runtime reality could not be confirmed from Docker because daemon access was blocked in the sandbox.
- Therefore the only confirmed mismatch is between requested verification scope and what the sandbox permits, not between the checked shared config and the consolidation plan.

## Current State

- Shared OpenIG routes present:
  - `00-backchannel-logout-app1.json`
  - `00-backchannel-logout-app3.json`
  - `00-backchannel-logout-app4.json`
  - `00-backchannel-logout-app5.json`
  - `00-backchannel-logout-app6.json`
  - `00-grafana-logout.json`
  - `00-jellyfin-logout.json`
  - `00-phpmyadmin-logout.json`
  - `00-redmine-logout.json`
  - `00-wp-logout.json`
  - `01-jellyfin.json`
  - `01-wordpress.json`
  - `02-app2.json`
  - `02-redmine.json`
  - `10-grafana.json`
  - `11-phpmyadmin.json`
- Shared Groovy scripts present:
  - `BackchannelLogoutHandler.groovy`
  - `CredentialInjector.groovy`
  - `JellyfinResponseRewriter.groovy`
  - `JellyfinTokenInjector.groovy`
  - `PhpMyAdminAuthFailureHandler.groovy`
  - `RedmineCredentialInjector.groovy`
  - `SessionBlacklistFilter.groovy`
  - `SloHandler.groovy`
  - `SloHandlerJellyfin.groovy`
  - `SpaAuthGuardFilter.groovy`
  - `SpaBlacklistGuardFilter.groovy`
  - `StripGatewaySessionCookies.groovy`
  - `TokenReferenceFilter.groovy`
  - `VaultCredentialFilter.groovy`

## Next Steps

- Re-run the same Docker commands in an unsandboxed local shell to capture live container names, mounts, and loaded-route logs.
- Compare live `docker inspect` mount sources against the shared compose expectation:
  - `/Volumes/OS/claude/openig/sso-lab/shared/openig_home -> /opt/openig-config`
- Confirm `shared-openig-1` and `shared-openig-2` logs both show all expected `Loaded the route` entries.

## Files Changed

- `docs/obsidian/debugging/2026-03-24-shared-runtime-verification-sandbox-blocked.md`
