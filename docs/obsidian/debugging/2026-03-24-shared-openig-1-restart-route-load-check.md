---
title: shared-openig-1 restart route load check
tags:
  - debugging
  - openig
  - docker
  - sso-lab
date: 2026-03-24
status: completed
---

# [[OpenIG]] restart route load check

## Context

Restarted `shared-openig-1`, waited for startup, then checked the latest route-registration lines and current `openig` container health.

Command run:

```bash
docker start shared-openig-1 && sleep 35 && docker logs --timestamps shared-openig-1 2>&1 | grep 'Loaded the route' | tail -3 && docker ps --format '{{.Names}}\t{{.Status}}' | grep openig
```

## What was done

- Started `shared-openig-1`.
- Waited `35` seconds for startup completion.
- Read the last three `Loaded the route` log lines from `shared-openig-1`.
- Checked `docker ps` status for all `openig` containers.

## Findings

> [!success]
> `shared-openig-1` restarted and reported route loading during startup.

- Last three route-load lines:
  - `2026-03-24T03:04:59.368258506Z` `01-jellyfin` as `jellyfin-sso`
  - `2026-03-24T03:04:59.379193256Z` `00-backchannel-logout-app4` as `00-backchannel-logout-app4`
  - `2026-03-24T03:04:59.408490715Z` `00-redmine-logout` as `00-redmine-logout-intercept`
- Container status after the wait window:
  - `shared-openig-1`: `Up 35 seconds (healthy)`
  - `shared-openig-2`: `Up 35 minutes (healthy)`

## Decisions

> [!tip]
> Treat this as a targeted startup verification, not a full route inventory or end-to-end auth validation.

- The restart check is sufficient to confirm `shared-openig-1` came back healthy and loaded routes during bootstrap.
- A broader verification would still need full startup log review and browser or HTTP flow checks.

## Current state

> [!success]
> Both `openig` containers were healthy at the time of the check.

- `shared-openig-1` was newly restarted and healthy.
- `shared-openig-2` remained healthy and did not require action.

## Next steps

- If deeper validation is needed, scan the full `shared-openig-1` startup log for all route registrations and any `ERROR` lines.
- Run an application login/logout flow against the routes served by `shared-openig-1`.

## Files changed

- `docs/obsidian/debugging/2026-03-24-shared-openig-1-restart-route-load-check.md`
