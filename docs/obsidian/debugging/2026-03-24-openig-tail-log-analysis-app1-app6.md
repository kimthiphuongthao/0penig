---
title: OpenIG tail log analysis for app1-app6
tags:
  - openig
  - debugging
  - logs
  - oauth2
date: 2026-03-24
status: completed
---

# Context

Analyzed only Docker log output from `shared-openig-1` and `shared-openig-2` using:

```bash
docker logs shared-openig-1 2>&1 | tail -300
docker logs shared-openig-2 2>&1 | tail -300
```

No log files on disk were read.

Related components: [[OpenIG]], [[Keycloak]], [[Stack A]], [[Stack B]], [[Stack C]]

# Findings

## Snapshot scope

- `shared-openig-2` tail on 2026-03-24 included several container restart cycles from March 23, 2026:
  - 08:28:12 -> 08:28:24
  - 08:45:42 -> 08:45:54
  - 09:07:30 -> 09:07:44
  - 10:49:02 -> 10:49:13
- This means the last 300 lines for `shared-openig-2` were partly startup noise, not only live request traffic.

> [!warning]
> Tail-based analysis is accurate for the sampled window only. Restart churn on `shared-openig-2` reduces depth for application traffic visibility.

## Error summary

- `app3` had the only real OAuth2 errors in the analyzed tails.
- Observed on `shared-openig-2`:
  - `error="invalid_request", error_description="Authorization call-back failed because there is no authorization in progress"`
  - `error="invalid_token", error_description="Token verification failed"` twice in the sampled window
- No `ERROR` lines were seen for `app1`, `app2`, `app4`, `app5`, or `app6`.

## TokenReference patterns

- `app1`: normal `Store -> Restore` pattern, with one short repeated restore burst for the same token ref (`fee0a178-...`) but no OAuth2 error after it.
- `app2`: normal pattern, low volume, two pending-state skips, no error.
- `app3`: normal pattern before failure, then callback/state break and `invalid_token` retries.
- `app4`: heavy concurrent traffic pattern. Multiple bursts of repeated restore for the same token ref (`55f97475-...`, `5caa785a-...`, `257ffb40-...`) after newer stores already existed. This looks like stale/concurrent session reads, not a hard failure in this window.
- `app5`: strongest stale restore burst. `0fd592e2-...` was restored repeatedly after newer refs had already been stored. No matching OAuth2 failure in the same window.
- `app6`: smaller version of the same behavior as `app5`; `248a711a-...` restored repeatedly after a newer store existed, but no error followed.

## State and mixed-state checks

- No `Mixed state` log observed.
- No `state parameter unexpected` log observed.
- `app3` did show `no authorization in progress`, which is the only clear callback-state issue in the sampled window.

## SLO / cleanup checks

- No `OAuth2 error response, cleaning stale token ref` log observed in the sampled tails.
- Therefore cleanup success could not be confirmed from this window.

## No oauth2 session value found

- Only one occurrence in the final snapshot:
  - `app3`: `No oauth2 session value found during response phase`
- This did not look broadly abnormal across all apps in the sampled window.

# Decisions

- Treat `app3` as the current failure focus.
- Treat `app4`, `app5`, and `app6` as warning-level concurrency/stale-reference patterns until they correlate with OAuth2 errors or user-visible failures.

> [!success]
> The `hasPendingState` fix appears effective for the sampled window because no `Mixed state` log or `state parameter unexpected` log was present.

# Current state

- `app1`: healthy in sampled tail
- `app2`: healthy in sampled tail
- `app3`: failing in sampled tail
- `app4`: warning due to stale/concurrent restore bursts
- `app5`: warning due to stale/concurrent restore bursts
- `app6`: warning due to stale/concurrent restore bursts

# Next steps

1. Capture a longer live window for `app3` around the callback path to confirm what breaks authorization progress before token verification.
2. If `app4`/`app5`/`app6` become user-visible issues, correlate repeated restore bursts with browser fan-out or parallel XHR requests before changing TokenRef logic.
3. Check why `shared-openig-2` restarted repeatedly on March 23, 2026 because it reduces debugging signal in short tail samples.

# Files changed

- Added this note only: `docs/obsidian/debugging/2026-03-24-openig-tail-log-analysis-app1-app6.md`
