---
title: OpenIG post-02:30 log scan for app1-app6
tags:
  - openig
  - debugging
  - logs
  - oauth2
  - token-reference
date: 2026-03-24
status: completed
---

# Context

Analyzed Docker logs from `shared-openig-1` and `shared-openig-2` starting at `2026-03-24T02:30:00` with focused grep filters for warnings and OAuth2/token-reference failure signals.

Commands used:

```bash
docker logs --since=2026-03-24T02:30:00 shared-openig-1 2>&1 | grep -E 'ERROR|WARN|Mixed state|invalid_token|authorization in progress|TokenRef|pending|stale|no oauth2' | tail -80
docker logs --since=2026-03-24T02:30:00 shared-openig-2 2>&1 | grep -E 'ERROR|WARN|Mixed state|invalid_token|authorization in progress|TokenRef|pending|stale|no oauth2' | tail -80
```

Related components: [[OpenIG]], [[Keycloak]], [[Stack A]], [[Stack B]], [[Stack C]]

# Findings

## Raw window summary

- `shared-openig-1` last 80 filtered lines were dominated by `TokenReferenceFilter` store/restore activity for `app4`, `app5`, and `app6`.
- `shared-openig-2` matched only startup warnings for evaluation mode and heap object naming.
- A broader full-window search after `02:30:00` found additional issues that did not appear in the `tail -80` slice.

> [!warning]
> The `tail -80` slice alone understates the failure picture. The full post-`02:30:00` search still matters for exact `ERROR`, `invalid_token`, and missing-oauth-session checks.

## Error and warning details

- No `Mixed state` log was found in either container during the post-`02:30:00` window.
- No `no authorization in progress` log was found in either container during the post-`02:30:00` window.
- `shared-openig-1` had one app-specific Redis/token-reference failure:
  - `TokenReferenceFilterApp5`: `Missing Redis payload for tokenRefKey=token_ref_id_app5 tokenRefId=59d1d5bd-71aa-4dce-9a11-c3d3728fef90 endpoint=/openid/app5`
- `shared-openig-1` had repeated OAuth2 client failures:
  - `OAuth2ClientFilter`: `error="invalid_token", error_description="Token verification failed"` appeared three times, each paired with `An error occurred in the OAuth2 process`
- `No oauth2 session value found during response phase` warnings appeared repeatedly for:
  - `app1`: 11
  - `app3`: 11
  - `app4`: 10
  - `app5`: 17
  - `app6`: 11
- `app2` had no matching `No oauth2 session value found` warning in the scanned window.

> [!success]
> The pending-state guard still looks effective in this window because no `Mixed state` warning was emitted.

# Current state

- `app1`: warning state due to repeated missing oauth2 session warnings; no app-specific `ERROR` seen
- `app2`: healthy in this scan; no target error pattern found
- `app3`: warning state due to repeated missing oauth2 session warnings; no app-specific `ERROR` seen
- `app4`: warning state due to repeated missing oauth2 session warnings; no app-specific `ERROR` seen
- `app5`: unhealthy in this scan; missing Redis payload error plus repeated `invalid_token` failures and the highest missing-session warning count
- `app6`: warning state due to repeated missing oauth2 session warnings; no app-specific `ERROR` seen

# Decisions

- Treat `app5` as the primary failure focus for the post-`02:30:00` window.
- Treat repeated `No oauth2 session value found` warnings on `app1`, `app3`, `app4`, `app5`, and `app6` as abnormal enough to monitor, even where they did not escalate to an `ERROR`.

# Next steps

1. Correlate the `app5` missing Redis payload with the adjacent logout and callback flow because the error occurs immediately after an `app5` restore and SLO redirect sequence.
2. Capture request-correlated context around the repeated missing-oauth-session warnings to determine whether they are expected response-phase cleanup noise or a real session restoration gap.
3. Keep watching for any return of `Mixed state` or `no authorization in progress`, because neither appeared in this window.

# Files changed

- Added this note: `docs/obsidian/debugging/2026-03-24-openig-post-0230-log-scan-app1-app6.md`
