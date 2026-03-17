---
title: Stack C OIDC Client Secret Rotation
tags:
  - debugging
  - stack-c
  - openig
  - keycloak
  - security
date: 2026-03-17
status: blocked
---

# Stack C OIDC Client Secret Rotation

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Task: security hardening for Stack C only.
- Reference: `[M-5/S-9]` in `.omc/plans/phase2-security-hardening.md`.
- Scope requested: rotate `OIDC_CLIENT_SECRET_APP5` and `OIDC_CLIENT_SECRET_APP6` in `stack-c/docker-compose.yml`, then sync Keycloak clients `openig-client-c-app5` and `openig-client-c-app6`.

## What changed

- Generated two new 32-byte random secrets with `openssl rand -base64 32`.
- Replaced both `secret-c` placeholders in `stack-c/docker-compose.yml` for:
  - `openig-c1`
  - `openig-c2`
- New compose secret lengths:
  - app5: `44`
  - app6: `44`

> [!success]
> `stack-c/docker-compose.yml` no longer contains the literal value `secret-c`.

## Blocker

- Keycloak admin update could not be executed from this Codex session.
- `curl` to `http://auth.sso.local:8080` and `http://127.0.0.1:8080` both failed with connection refused.
- Docker commands were denied by the sandbox when attempting `docker ps`, `docker compose`, and `docker logs` because the Docker daemon socket was not accessible.

> [!warning]
> Current state is only partially applied: Stack C compose values are rotated, but Keycloak client secrets were not synced from this session, and container restart/log verification was not possible here.

## Commands attempted

```bash
curl -X POST "http://auth.sso.local:8080/realms/master/protocol/openid-connect/token" ...
curl http://127.0.0.1:8080/health/ready
docker compose -f stack-c/docker-compose.yml up -d
docker restart stack-c-openig-c1-1 stack-c-openig-c2-1
docker logs stack-c-openig-c1-1 2>&1 | grep 'Loaded the route'
grep -n "secret-c" stack-c/docker-compose.yml
```

## Current state

- Compose file updated: yes.
- Keycloak client sync: no.
- Stack C restart: no.
- Route load verification: no.
- Placeholder verification: yes, `grep -n "secret-c" stack-c/docker-compose.yml` returned empty.

## Next steps

1. Run the Keycloak admin token flow from a host where `auth.sso.local:8080` is reachable.
2. Update `openig-client-c-app5` and `openig-client-c-app6` in realm `sso-realm`.
3. Verify the actual secrets returned by Keycloak and keep `stack-c/docker-compose.yml` aligned with those values.
4. Run `docker compose -f stack-c/docker-compose.yml up -d && docker restart stack-c-openig-c1-1 stack-c-openig-c2-1`.
5. Confirm `docker logs stack-c-openig-c1-1 2>&1 | grep 'Loaded the route'`.

## Files changed

- `stack-c/docker-compose.yml`
- `docs/obsidian/debugging/2026-03-17-stack-c-oidc-client-secret-rotation.md`
