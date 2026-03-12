---
title: Cross-Stack SLO Jellyfin Backchannel Fix
tags:
  - slo
  - stack-b
  - jellyfin
  - keycloak
  - backchannel
date: 2026-03-12
status: fixed
---

# Cross-Stack SLO Jellyfin Fix

Links: [[Stack B]] [[Keycloak]] [[OpenIG]]

## Context
On 2026-03-12, cross-stack SLO behavior was corrected for Jellyfin (`app4`) in Stack B.

## Root Cause
Jellyfin was sharing the same Keycloak client (`openig-client-b`) with Redmine (`app3`).
Keycloak supports only one backchannel logout URL per client, so Jellyfin backchannel logout was not covered.

## Fix Implemented
- Added Jellyfin to Keycloak backchannel setup by creating a dedicated client:
  - Client ID: `openig-client-b-app4`
  - Backchannel logout URL: `http://host.docker.internal:9080/openid/app4/backchannel_logout`
- Updated route file: `stack-b/openig_home/config/routes/01-jellyfin.json`
  - `clientId` -> `openig-client-b-app4`
  - `clientSecret` -> `openig-client-b-app4-secret`
  - `clientEndpoint` -> `/openid/app4`
  - `SessionBlacklistFilterApp3` -> `SessionBlacklistFilterApp4`

> [!success] Backchannel Registration Complete
> All 5 backchannel endpoints are now registered:
> - `app1` (Stack A)
> - `app3` (Redmine)
> - `app4` (Jellyfin)
> - `app5` (Grafana)
> - `app6` (phpMyAdmin)

## Validation
- OpenIG route reload completed successfully.

> [!warning] Operational Note
> Route change requires OpenIG restart to take effect.
