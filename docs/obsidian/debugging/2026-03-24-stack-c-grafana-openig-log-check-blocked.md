---
title: Stack C Grafana OpenIG log check blocked by Docker socket permission
tags:
  - debugging
  - openig
  - grafana
  - stack-c
date: 2026-03-24
status: blocked
---

# Stack C Grafana OpenIG log check blocked by Docker socket permission

## Context

Requested verification for recent [[Grafana]] SSO/SLO test through [[OpenIG]] using only these two commands:

```bash
docker logs shared-openig-1 2>&1 | tail -100
docker logs shared-openig-2 2>&1 | tail -100
```

## What happened

Both commands failed before returning any container output.

> [!warning] Sandbox blocker
> Docker daemon access is not permitted from the current Codex environment, so no OpenIG log lines were available for analysis.

Observed error from both commands:

```text
permission denied while trying to connect to the Docker daemon socket at unix:///Users/duykim/.docker/run/docker.sock: dial unix /Users/duykim/.docker/run/docker.sock: connect: operation not permitted
```

## Impact

- Could not verify whether `no authorization in progress` occurred.
- Could not verify whether `state parameter contained an unexpected value` occurred.
- Could not verify whether `Mixed state: pending OAuth2 state alongside stale tokens` appeared.
- Could not verify whether an `invalid_token` loop occurred.
- Could not reconstruct the app5/Grafana sequence `restore -> store -> callback`.
- Could not check for any other `ERROR` lines.

## Next step

> [!tip]
> Run the two allowed `docker logs ... | tail -100` commands in an environment with Docker socket access, then re-run the analysis against that output only.

## Related

- [[OpenIG]]
- [[Grafana]]
- [[Stack C]]
