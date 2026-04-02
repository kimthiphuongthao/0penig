---
title: OpenIG log monitor blocked by Docker socket permissions
tags:
  - debugging
  - openig
  - docker
  - sso
  - slo
date: 2026-04-01
status: blocked
---

# OpenIG log monitor blocked by Docker socket permissions

## Context

Attempted to monitor `shared-openig-1` and `shared-openig-2` for 60 seconds during SSO/SLO testing using:

```bash
docker logs -f shared-openig-1 2>&1 | grep -v 'DEBUG'
docker logs -f shared-openig-2 2>&1 | grep -v 'DEBUG'
```

Target patterns:

- `ERROR` or `WARN`
- `invalid_grant`
- `invalid_token`
- `no authorization in progress`
- `Missing Redis`
- `Vault`
- `503`
- `502`
- Backchannel logout JWT validation issues
- `TokenReferenceFilter`

## What Happened

> [!warning] Monitoring was blocked before any OpenIG application logs could be read.
> Both `docker logs -f` commands failed at the Docker daemon socket.

Observed output:

```text
[shared-openig-2] permission denied while trying to connect to the Docker daemon socket at unix:///Users/duykim/.docker/run/docker.sock
[shared-openig-1] permission denied while trying to connect to the Docker daemon socket at unix:///Users/duykim/.docker/run/docker.sock
```

## Interpretation

- This was not an [[OpenIG]] runtime error from the containers themselves.
- This was a local environment access issue between the current shell session and the Docker daemon socket.
- Because of that, there was no evidence collected for SSO/SLO behavior, backchannel logout handling, `TokenReferenceFilter`, Redis, or Vault-related runtime issues.

## Current State

> [!warning] Blocked
> Container log inspection cannot proceed until Docker socket access is fixed for the session running the commands.

## Next Steps

> [!tip] After Docker access is restored
> Re-run the 60-second monitor during an active SSO/SLO test window so the log stream contains real traffic.

- Verify the shell can run `docker ps` without a permission error.
- Re-run the two `docker logs -f` commands.
- Trigger SSO and SLO flows while the watch is active.
- Re-check for `ERROR`, `WARN`, `invalid_grant`, `invalid_token`, `no authorization in progress`, `502`, `503`, backchannel logout JWT validation failures, and `TokenReferenceFilter` warnings.
