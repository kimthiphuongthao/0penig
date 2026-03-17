---
title: Stack C Grafana Invalid Client Credentials
tags:
  - debugging
  - stack-c
  - grafana
  - openig
  - keycloak
date: 2026-03-17
status: blocked
---

# Stack C Grafana Invalid Client Credentials

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Symptom: Grafana SSO on Stack C fails in OpenIG with `unauthorized_client` and `Invalid client or Invalid client credentials`.
- Affected OIDC client: `openig-client-c-app5`.
- Current Stack C gateway secret source: `stack-c/.env` via `OIDC_CLIENT_SECRET_APP5`.

## Evidence

- `stack-c/.env` currently sets `OIDC_CLIENT_SECRET_APP5=CVZ1FfkEwfDPbmcLV+d/wJuvngLK3Mo0PJgr0iIMce4=`.
- `stack-c/openig_home/config/routes/10-grafana.json` uses:
  - `clientId: openig-client-c-app5`
  - `clientSecret: ${env['OIDC_CLIENT_SECRET_APP5']}`
- `stack-c/docker-compose.yml` injects `OIDC_CLIENT_SECRET_APP5` into both `openig-c1` and `openig-c2`.
- `keycloak/realm-import/realm-export.json` still seeds:
  - `openig-client-c-app5` with `secret: secret-c`
  - `openig-client-c-app6` with `secret: secret-c`

> [!warning]
> The repo baseline is internally inconsistent: Stack C OpenIG expects the strong secret from `stack-c/.env`, but the committed Keycloak realm import still defines Stack C clients with `secret-c`.

## Likely Root Cause

Most likely runtime mismatch:

1. Stack C OpenIG uses the strong `OIDC_CLIENT_SECRET_APP5` value from `stack-c/.env`.
2. Keycloak still has `openig-client-c-app5` set to `secret-c`, or the realm was re-imported from the stale `realm-export.json`.
3. Keycloak rejects the token exchange with `Invalid client credentials`.

Secondary possibility:

1. The running `openig-c1` / `openig-c2` containers were created before `.env` rotation.
2. Docker kept the old container environment.
3. A plain `docker restart` would not refresh `OIDC_CLIENT_SECRET_APP5`.

> [!tip]
> If container env is stale, use `docker compose up -d --force-recreate openig-c1 openig-c2 nginx-c` instead of `docker restart`. Restart does not reload Compose environment variables.

## Runtime Validation Blocker

- This Codex sandbox could read repo files, but it could not:
  - connect to `127.0.0.1:8080` or `auth.sso.local:8080`
  - access the Docker daemon socket at `/Users/duykim/.docker/run/docker.sock`
- Because of that, live Keycloak secret reads, `docker exec env`, container restarts, and log verification could not be executed from this session.

> [!warning]
> The diagnosis is evidence-based from repo state, but live confirmation still requires running the Keycloak admin API and Docker commands outside this sandbox.

## Recommended Fix

1. Query Keycloak for `openig-client-c-app5` and compare its secret to `stack-c/.env`.
2. If Keycloak still returns `secret-c`, update the client secret to match `stack-c/.env`.
3. Recreate Stack C OpenIG containers so the current `.env` value is definitely injected.
4. Recheck OpenIG logs for `unauthorized_client`.

> [!tip]
> Shortcut: run `keycloak/sync-stack-c-oidc-secrets.sh` from the repo root on a host that can reach Keycloak.

## Commands To Run Outside Sandbox

```bash
NEW_SECRET="CVZ1FfkEwfDPbmcLV+d/wJuvngLK3Mo0PJgr0iIMce4="

KEYCLOAK_ADMIN_TOKEN=$(curl -s -X POST "http://127.0.0.1:8080/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli&username=admin&password=admin&grant_type=password" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

CLIENT_UUID=$(curl -s -H "Authorization: Bearer $KEYCLOAK_ADMIN_TOKEN" \
  "http://127.0.0.1:8080/admin/realms/sso-realm/clients?clientId=openig-client-c-app5" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['id'])")

curl -s -X PUT \
  -H "Authorization: Bearer $KEYCLOAK_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  "http://127.0.0.1:8080/admin/realms/sso-realm/clients/$CLIENT_UUID" \
  -d "{\"secret\":\"$NEW_SECRET\"}"

curl -s -H "Authorization: Bearer $KEYCLOAK_ADMIN_TOKEN" \
  "http://127.0.0.1:8080/admin/realms/sso-realm/clients/$CLIENT_UUID/client-secret"

DOCKER_HOST=unix:///Users/duykim/.docker/run/docker.sock docker compose -f stack-c/docker-compose.yml up -d --force-recreate openig-c1 openig-c2 nginx-c
sleep 8
DOCKER_HOST=unix:///Users/duykim/.docker/run/docker.sock docker logs stack-c-openig-c1-1 2>&1 | grep -E "(Loaded the route|ERROR|unauthorized)" | head -10
```

## Files Referenced

- `stack-c/.env`
- `stack-c/docker-compose.yml`
- `stack-c/openig_home/config/routes/10-grafana.json`
- `keycloak/realm-import/realm-export.json`
