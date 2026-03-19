---
title: Stack C Backchannel Logout Live Log Monitor
tags:
  - debugging
  - stack-c
  - openig
  - slo
  - backchannel-logout
date: 2026-03-19
status: observed-no-events
---

# Stack C Backchannel Logout Live Log Monitor

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Task: monitor live logs from `stack-c-openig-c1-1` for 3 minutes.
- Interval: every 10 seconds for 18 checks.
- Filter set:
  - `BackchannelLogoutHandler`
  - `SloHandler`
  - JWT/session error lines
  - algorithm / JWKS / signature lines
  - `WARN` and `ERROR`

## Method

- Command shape used:
  - `docker logs stack-c-openig-c1-1 --since 10s 2>&1`
- Match pattern used:
  - `BackchannelLogoutHandler|SloHandler|jwt.*session|session.*jwt|algorithm|jwks|signature|RS256|ES256|\bERROR\b|\bWARN\b`

## Findings

> [!success]
> All 18 polling windows were quiet. No matching backchannel logout, SLO handler, JWT/session error, algorithm, JWKS, signature, `WARN`, or `ERROR` lines were emitted by `stack-c-openig-c1-1` during the observed 3-minute window.

| Timestamp (+07) | Result |
| --- | --- |
| 2026-03-19 17:26:56 | quiet |
| 2026-03-19 17:27:06 | quiet |
| 2026-03-19 17:27:16 | quiet |
| 2026-03-19 17:27:26 | quiet |
| 2026-03-19 17:27:36 | quiet |
| 2026-03-19 17:27:46 | quiet |
| 2026-03-19 17:27:56 | quiet |
| 2026-03-19 17:28:07 | quiet |
| 2026-03-19 17:28:17 | quiet |
| 2026-03-19 17:28:27 | quiet |
| 2026-03-19 17:28:37 | quiet |
| 2026-03-19 17:28:47 | quiet |
| 2026-03-19 17:28:57 | quiet |
| 2026-03-19 17:29:07 | quiet |
| 2026-03-19 17:29:18 | quiet |
| 2026-03-19 17:29:28 | quiet |
| 2026-03-19 17:29:38 | quiet |
| 2026-03-19 17:29:48 | quiet |

## Interpretation

> [!warning]
> This capture does not prove successful or failed backchannel logout. It only shows that no relevant logout activity reached `stack-c-openig-c1-1` during the monitored period.

- Backchannel logout success:
  - Not observed.
  - No `Redis blacklist updated successfully`, `blacklist`, `sid`, or similar Redis write evidence appeared.
- Errors:
  - None observed in the monitored windows.
- Algorithm:
  - Not observed.
  - No `RS256`, `ES256`, `algorithm`, `JWKS`, or `signature` lines appeared in this window.

> [!tip]
> If a live backchannel logout test is triggered while the monitor is running, repeat the same polling loop on both `stack-c-openig-c1-1` and `stack-c-openig-c2-1` to confirm which OpenIG node receives the request and whether Redis blacklist writes occur.

## Files Changed

- `docs/obsidian/debugging/2026-03-19-stack-c-backchannel-logout-live-log-monitor.md`
