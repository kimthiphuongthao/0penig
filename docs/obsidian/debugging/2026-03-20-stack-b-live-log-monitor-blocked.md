---
title: Stack B Live Log Monitor Blocked
tags:
  - debugging
  - stack-b
  - openig
  - redmine
  - jellyfin
  - slo
  - sso
date: 2026-03-20
status: blocked
---

# Stack B Live Log Monitor Blocked

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack B]] [[Redmine]] [[Jellyfin]]

## Context

- Task: monitor `sso-b-openig-1` and `sso-b-openig-2` while Redmine and Jellyfin SSO/SLO tests are about to run.
- Requested checks:
  - UTC timestamp snapshot
  - route-load confirmation on both nodes
  - fresh 120-second filtered logs after a 60-second wait
  - report on `ERROR`, `WARN`, `VaultCredential`, `TokenRef`, `SloHandler`, `Jellyfin`, `Redmine`, and `backchannel`

## Commands Used

- `date -u '+%Y-%m-%dT%H:%M:%SZ'`
- `docker logs sso-b-openig-1 2>&1 | grep 'Loaded the route' | tail -10`
- `docker logs sso-b-openig-2 2>&1 | grep 'Loaded the route' | tail -10`
- `docker logs sso-b-openig-1 --since 120s 2>&1 | grep -E 'ERROR|WARN|VaultCredential|TokenRef|SloHandler|Jellyfin|Redmine|backchannel'`
- `docker logs sso-b-openig-2 --since 120s 2>&1 | grep -E 'ERROR|WARN|VaultCredential|TokenRef|SloHandler|Jellyfin|Redmine|backchannel'`
- follow-up verification:
  - `docker logs sso-b-openig-1 --since 120s 2>&1 | tail -50`
  - `docker logs sso-b-openig-2 --since 120s 2>&1 | tail -50`

## Findings

> [!warning]
> This environment could not connect to the Docker daemon socket, so no live Stack B container logs were actually readable during this monitoring task.

- UTC snapshot captured: `2026-03-20T03:57:27Z`
- Initial route-load grep:
  - returned no lines for `sso-b-openig-1`
  - returned no lines for `sso-b-openig-2`
- Fresh filtered 120-second grep after the 60-second wait:
  - returned no lines for `sso-b-openig-1`
  - returned no lines for `sso-b-openig-2`
- Direct unfiltered verification exposed the real failure on both nodes:

```text
permission denied while trying to connect to the Docker daemon socket at unix:///Users/duykim/.docker/run/docker.sock: Get "http://%2FUsers%2Fduykim%2F.docker%2Frun%2Fdocker.sock/v1.51/containers/sso-b-openig-1/json": dial unix /Users/duykim/.docker/run/docker.sock: connect: operation not permitted
permission denied while trying to connect to the Docker daemon socket at unix:///Users/duykim/.docker/run/docker.sock: Get "http://%2FUsers%2Fduykim%2F.docker%2Frun%2Fdocker.sock/v1.51/containers/sso-b-openig-2/json": dial unix /Users/duykim/.docker/run/docker.sock: connect: operation not permitted
```

## Interpretation

> [!warning]
> The empty grep output does not mean Stack B was quiet. It only means the Docker permission error text did not match the grep patterns.

- Errors:
  - Not observable from container logs in this session.
- `TokenRef` Store/Restore activity:
  - Not observable from container logs in this session.
- `VaultCredential` activity:
  - Not observable from container logs in this session.
- `SloHandler` activity:
  - Not observable from container logs in this session.
- Route-load confirmation:
  - Not confirmed from this session because Docker log access was blocked.

> [!tip]
> Re-run the same commands in a shell that has access to `/Users/duykim/.docker/run/docker.sock`, or from the Docker host session directly, to capture the Redmine/Jellyfin test traffic and verify `TokenRef`, `VaultCredential`, and `SloHandler` behavior.

## Files Changed

- `docs/obsidian/debugging/2026-03-20-stack-b-live-log-monitor-blocked.md`
