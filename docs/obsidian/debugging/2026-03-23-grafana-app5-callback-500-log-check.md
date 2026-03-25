---
title: Grafana app5 callback 500 log check
tags:
  - debugging
  - openig
  - grafana
  - stack-c
  - oauth2
date: 2026-03-23
status: completed
---

# Grafana app5 callback 500 log check

## Context

Investigated [[OpenIG]] log evidence for a browser-visible `GET /openid/app5/callback?...` HTTP 500 in [[Grafana]] on [[Stack C]].

## What was checked

- Tried the requested `docker logs` commands for `stack-c-openig-c1-1` and `stack-c-openig-c2-1`.
- Confirmed the sandbox cannot access the Docker socket, so direct container log output was unavailable here.
- Used persisted OpenIG route logs under `stack-c/openig_home/logs/` as fallback evidence.

> [!warning]
> `stack-c/docker-compose.yml` mounts the same `./openig_home` directory into both `openig-c1` and `openig-c2`, so route log files are shared and do not prove which replica handled a given callback unless the log line itself includes node identity. No node identifier was present in the route log format.

## Findings

### Raw command outcome

- Command 1 returned no output because the Docker socket failure text was filtered out by `grep`.
- Command 2 returned no output for the same reason.
- Command 3 returned:

```text
permission denied while trying to connect to the Docker daemon socket at unix:///Users/duykim/.docker/run/docker.sock: Get "http://%2FUsers%2Fduykim%2F.docker%2Frun%2Fdocker.sock/v1.51/containers/stack-c-openig-c1-1/json": dial unix /Users/duykim/.docker/run/docker.sock: connect: operation not permitted
```

### Exact error sequences seen in persisted route logs

#### Sequence A: missing authorization state

From `route-10-grafana-2026-03-22.0.log`:

1. `09:32:42:642` restore of `token_ref_id_app5`
2. `09:32:42:691` `OAuth2ClientFilter` error: `Authorization call-back failed because there is no authorization in progress`
3. `09:32:42:693` `TokenReferenceFilterApp5` session keys still present
4. `09:32:42:730` `No oauth2 session value found during response phase endpoint=/openid/app5`

Also seen again on March 22, 2026 at `09:48:47:895` and `15:56:46:056`, and on March 20, 2026 at `10:13:44:236`.

#### Sequence B: token verification failure

From current `route-10-grafana.log`:

1. `02:54:54:284` `OAuth2ClientFilter` error: `invalid_token`, `Token verification failed`
2. `02:54:54:286` `TokenReferenceFilterApp5` session keys logged
3. `02:54:54:292` token reference stored again
4. `02:55:29:384` token reference restored
5. `02:55:29:455` `An error occurred in the OAuth2 process`
6. `02:55:29:460` `invalid_token`, `Token verification failed`

> [!success]
> The phrase `Authorization call-back failed because there is no authorization in progress` is present in Grafana app5 route logs.

> [!warning]
> The phrase `cleaning stale token ref` does not appear in the persisted OpenIG log files under `stack-c/openig_home/logs/`.

## Decision

Use persisted route logs as fallback evidence when direct `docker logs` access is blocked by the local sandbox.

## Current state

- Callback-specific browser path text was not present in persisted route logs.
- Shared route logs show both an `invalid_request` callback-state failure path and a separate `invalid_token` verification-failure path for app5.
- Exact replica ownership (`openig-c1` vs `openig-c2`) remains unproven from disk logs alone.

## Next steps

- Re-run the original `docker logs` commands from a shell with Docker socket access to identify the exact replica.
- If the failing browser event timestamp is known, correlate it against the nearest route-log sequence to distinguish `invalid_request` from `invalid_token`.
- If needed, add node identity to the OpenIG log pattern so shared-volume logs can attribute future failures to `openig-c1` or `openig-c2`.

## Files changed

- `docs/obsidian/debugging/2026-03-23-grafana-app5-callback-500-log-check.md`
