---
title: Stack C Keycloak RS256 Revert Blocked
tags:
  - debugging
  - stack-c
  - keycloak
  - openig
  - oidc
  - grafana
  - phpmyadmin
date: 2026-03-22
status: blocked
---

# Stack C Keycloak RS256 Revert Blocked

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Task: revert Keycloak clients `openig-client-c-app5` and `openig-client-c-app6` from `ES256` back to `RS256` for ID token and access token signing in Stack C.
- Hypothesis: OpenIG `6.0.1` `OAuth2ClientFilter` cannot verify `ES256`-signed ID tokens in this flow, while the custom [[OpenIG]] backchannel logout handler explicitly reconstructs EC keys and supports `ES256`.
- Requested evidence:
  - algorithm before change
  - exact admin API calls and responses
  - restart result for `stack-c-openig-c1-1` and `stack-c-openig-c2-1`
  - last 30 log lines from `stack-c-openig-c1-1`

## Repo Evidence

- Stack C Grafana route uses `openig-client-c-app5` in `stack-c/openig_home/config/routes/10-grafana.json` at line `21`.
- Stack C phpMyAdmin route uses `openig-client-c-app6` in `stack-c/openig_home/config/routes/11-phpmyadmin.json` at lines `21` and `120`.
- `stack-c/openig_home/logs/route-11-phpmyadmin.log` contains repeated `invalid_token` / `Token verification failed` entries at lines `314`, `341`, `346`, and `373`.
- `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy` explicitly handles `ES256`:
  - `verifySignature` definition at line `189`
  - `alg == 'ES256'` branch at line `200`
  - algorithm guard `RS256` or `ES256` at lines `356` to `359`

> [!tip]
> The committed Groovy backchannel handler already supports `ES256`, which makes the failure pattern more consistent with an `OAuth2ClientFilter` verification gap than with a JWKS publication issue in the custom logout path.

## Commands Attempted

- Admin token request:

```bash
curl -s -o /tmp/keycloak_master_token_probe.json -w '%{http_code}\n' -X POST \
  'http://host.docker.internal:8080/realms/master/protocol/openid-connect/token' \
  -d 'client_id=admin-cli' \
  -d 'username=admin' \
  -d 'password=admin' \
  -d 'grant_type=password'
```

- Docker daemon reachability check:

```bash
docker ps --format '{{.Names}}'
```

## Findings

> [!warning]
> The requested Keycloak client update could not be executed from this session because the sandbox blocked both the admin API path and Docker socket access.

- The token request returned HTTP status `000` with curl exit code `6`, which indicates `host.docker.internal:8080` was not reachable from this environment.
- `docker ps` failed with:

```text
permission denied while trying to connect to the Docker daemon socket at unix:///Users/duykim/.docker/run/docker.sock: Get "http://%2FUsers%2Fduykim%2F.docker%2Frun%2Fdocker.sock/v1.51/containers/json": dial unix /Users/duykim/.docker/run/docker.sock: connect: operation not permitted
```

- Independent of the sandbox, this repo also carries a scope rule that forbids direct Keycloak config changes from the workspace task flow.

## Requested Report

- What algorithm was set before the change?
  - not retrievable from this session because no Keycloak admin token could be obtained
- What was changed?
  - no Keycloak client update was sent
- Exact API calls and responses:
  - attempted `POST /realms/master/protocol/openid-connect/token` to `http://host.docker.internal:8080`
  - response status `000`
  - no `GET /admin/realms/sso-realm/clients?...` or `PUT /admin/realms/sso-realm/clients/<UUID>` calls were possible afterward
- Any errors during restart?
  - restart was not attempted because Docker socket access was denied before container control commands could run
- Last 30 lines of `stack-c-openig-c1-1` logs after restart?
  - unavailable because restart and log access were both blocked by the Docker permission failure

## Next Steps

> [!success]
> The fastest operational path remains the user-proposed Keycloak client revert, but it must be run from an environment with real network access to Keycloak and Docker access to Stack C.

- Run the supplied admin token, client `GET`, client `PUT`, and `docker restart` commands from the host or another unsandboxed shell.
- Capture the current `attributes.id.token.signed.response.alg` and `attributes.access.token.signed.response.alg` for both clients before the `PUT`.
- After restart, verify whether Grafana login succeeds and whether `route-10-grafana.log` stops producing token verification errors.
- If policy requires staying inside repo-owned artifacts only, the alternative is a gateway-side investigation into replacing or wrapping the built-in `OAuth2ClientFilter` path for these apps rather than changing [[Keycloak]] client signing settings.

## Files Changed

- `docs/obsidian/debugging/2026-03-22-stack-c-keycloak-rs256-revert-blocked.md`
