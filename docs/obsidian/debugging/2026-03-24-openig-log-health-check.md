---
title: OpenIG log health check from 2026-03-24T02:30:00
tags:
  - debugging
  - openig
  - logs
  - sso-lab
date: 2026-03-24
status: completed
---

# [[OpenIG]] log health check

## Context

Checked the following containers with `--since=2026-03-24T02:30:00`:

- `shared-openig-1`
- `shared-openig-2`

Filter used:

```bash
grep -E 'ERROR|invalid_token|authorization in progress|Mixed state|missing Redis|Missing Redis' | tail -30
```

## What was done

- Pulled filtered runtime logs from both OpenIG containers.
- Counted total `ERROR` lines per container in the same time window.
- Checked for `Mixed state`, `invalid_token`, and exact `no authorization in progress`.

## Findings

> [!warning]
> `shared-openig-1` is not clean in this window.

- `shared-openig-1`
  - `ERROR`: `7`
  - `invalid_token`: `6`
  - `Mixed state`: `0`
  - `no authorization in progress`: `0`
  - `Missing Redis`: `1`
- `shared-openig-2`
  - `ERROR`: `0`
  - `invalid_token`: `0`
  - `Mixed state`: `0`
  - `no authorization in progress`: `0`
  - `Missing Redis`: `0`

## Decisions

> [!tip]
> Treat this as an auth/session-path issue in `shared-openig-1`, not a cluster-wide failure, because `shared-openig-2` was clean for the same time window.

- `invalid_token` errors point toward token verification failures against [[Keycloak]] or stale/invalid tokens reaching [[OpenIG]].
- The `Missing Redis payload` line suggests at least one token reference lookup failed in `shared-openig-1`.

## Current state

> [!success]
> No `Mixed state` and no exact `no authorization in progress` messages were found in either container for this window.

- One container is clean: `shared-openig-2`.
- One container needs follow-up: `shared-openig-1`.

## Next steps

- Correlate the `tokenRefId=59d1d5bd-71aa-4dce-9a11-c3d3728fef90` lookup failure with Redis key expiry and request timing.
- Trace the failing requests around `/openid/app5` on `shared-openig-1`.
- Verify whether the failing tokens were expired, revoked, or signed for the wrong client/realm in [[Keycloak]].

## Files changed

- `docs/obsidian/debugging/2026-03-24-openig-log-health-check.md`
