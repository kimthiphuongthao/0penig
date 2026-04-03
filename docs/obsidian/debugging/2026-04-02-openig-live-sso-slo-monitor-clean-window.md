---
title: OpenIG live SSO/SLO monitor clean 3-minute window
tags:
  - debugging
  - openig
  - sso
  - slo
  - monitoring
date: 2026-04-02
status: complete
---

# OpenIG live SSO/SLO monitor clean 3-minute window

## Context

Ran a live 3-minute filtered log watch against both [[OpenIG]] nodes while browser-driven SSO/SLO tests were expected across:

- `wp-a.sso.local`
- `whoami-a.sso.local`
- `redmine-b.sso.local`
- `jellyfin-b.sso.local`
- `grafana-c.sso.local`
- `phpmyadmin-c.sso.local`

Test credentials in scope:

- `alice / alice123`
- `bob / bob123`

Observed containers:

- `shared-openig-1`
- `shared-openig-2`

## Commands

```bash
docker logs -f shared-openig-1 2>&1 | grep -v 'DEBUG' | grep -E 'ERROR|WARN|invalid_grant|invalid_token|no authorization|Missing Redis|Vault|backchannel|TokenRef|SloHandler|JwtSession|cookie overflow|CRITICAL'
docker logs -f shared-openig-2 2>&1 | grep -v 'DEBUG' | grep -E 'ERROR|WARN|invalid_grant|invalid_token|no authorization|Missing Redis|Vault|backchannel|TokenRef|SloHandler|JwtSession|cookie overflow|CRITICAL'
```

## Result

> [!success] Clean monitoring window
> No lines matched the filter on either `shared-openig-1` or `shared-openig-2` during the full 3-minute watch.

Severity summary:

- `CRITICAL`: none
- `WARN`: none
- `INFO`: none captured by the keyword filter

## Interpretation

- No `ERROR`, `CRITICAL`, `invalid_grant`, or `cookie overflow` events appeared in the live watch.
- No `WARN` lines appeared from either node during the window.
- No filtered SSO/SLO indicators such as `backchannel`, `TokenRef`, `SloHandler`, or `JwtSession` appeared.
- This is consistent with a clean runtime window for the specific patterns monitored through [[OpenIG]] during the observed test period.

> [!tip] What this proves
> The watched patterns did not occur during the 3-minute test window.

> [!warning] What this does not prove
> A clean filtered stream does not guarantee every SSO/SLO step executed, only that the selected anomaly patterns were absent. Routine success paths may not emit any matching lines, and some state may be handled through [[Keycloak]] or [[Vault]] without triggering these filters.

## Current State

- Live monitoring succeeded.
- Docker log access was functional in this session.
- No gateway config, route, Groovy, or target application files were changed.

## Next Steps

- Re-run the same 3-minute watch during a higher-volume SSO/SLO test if deeper confidence is needed.
- If browser behavior looks wrong despite clean logs, expand the filter temporarily to include route-level success markers and request correlation IDs.
- Cross-check [[Keycloak]] events if logout propagation appears incomplete without corresponding [[OpenIG]] warnings.
