---
title: Keycloak sso-realm sslRequired Admin API Update Blocked
tags:
  - debugging
  - keycloak
  - sso
  - oidc
  - realm
  - ssl
date: 2026-03-20
status: blocked
---

# Keycloak sso-realm sslRequired Admin API Update Blocked

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Task: set `sso-realm` `sslRequired` to `none` through the Keycloak admin REST API.
- Reason: browser rejects `AUTH_SESSION_ID` when Keycloak marks it `Secure` in an HTTP-only lab flow, which breaks OIDC login.
- Requested report:
  - current `sslRequired` before fix
  - HTTP status of the `PUT`
  - `sslRequired` after fix

## Commands Used

- token request against browser hostname:

```bash
curl -sS -D - -X POST 'http://auth.sso.local:8080/realms/master/protocol/openid-connect/token' \
  -d 'client_id=admin-cli&username=admin&password=admin&grant_type=password'
```

- token request against local loopback:

```bash
curl -sS -D - -X POST 'http://127.0.0.1:8080/realms/master/protocol/openid-connect/token' \
  -d 'client_id=admin-cli&username=admin&password=admin&grant_type=password'
```

- local listener check:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

- repo verification of the intended Keycloak publish port:

```bash
tilth '/Volumes/OS/claude/openig/sso-lab/keycloak/docker-compose.yml' --scope /
```

## Findings

> [!warning]
> The Keycloak admin API update could not be applied from this session because no reachable Keycloak process was listening on port `8080`.

- `keycloak/docker-compose.yml` confirms the lab intends to publish Keycloak on host port `8080` with `KC_HOSTNAME: "auth.sso.local"`.
- `POST http://auth.sso.local:8080/.../token` failed with:

```text
curl: (7) Failed to connect to auth.sso.local port 8080 after 0 ms: Couldn't connect to server
```

- `POST http://127.0.0.1:8080/.../token` failed with:

```text
curl: (7) Failed to connect to 127.0.0.1 port 8080 after 0 ms: Couldn't connect to server
```

- `lsof -nP -iTCP:8080 -sTCP:LISTEN` returned no output, which indicates there was no local listener bound to `8080` during this task.

## Requested Report

- Current `sslRequired` before fix:
  - not retrievable from this session because the admin API was unreachable
- HTTP status of `PUT /admin/realms/sso-realm`:
  - not available because no admin token could be obtained
- `sslRequired` after fix:
  - not retrievable from this session because the update was never sent

## Interpretation

> [!tip]
> Once Keycloak is actually reachable on `http://auth.sso.local:8080` or `http://127.0.0.1:8080`, the requested admin API fix should work with the exact token and `PUT` flow that was provided.

- This blocker is environmental, not a JSON payload problem.
- The first required success condition is a live Keycloak listener on host port `8080`.
- After the service is reachable, rerun:
  - token request
  - `GET /admin/realms/sso-realm` to capture current `sslRequired`
  - `PUT /admin/realms/sso-realm` with `{"sslRequired":"none"}`
  - final `GET` for verification

## Files Changed

- `docs/obsidian/debugging/2026-03-20-keycloak-sso-realm-sslrequired-admin-api-blocked.md`
