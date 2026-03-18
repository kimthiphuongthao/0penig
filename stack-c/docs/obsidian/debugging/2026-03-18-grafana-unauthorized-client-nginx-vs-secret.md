---
title: Stack C Grafana unauthorized_client - nginx vs secret analysis
tags:
  - debugging
  - stack-c
  - openig
  - grafana
  - keycloak
date: 2026-03-18
status: complete
---

# Stack C Grafana unauthorized_client - nginx vs secret analysis

Context: [[Stack C]] Grafana SSO still fails with `unauthorized_client`. The working hypothesis was broken nginx stickiness causing both [[OpenIG]] nodes to process the same OAuth2 callback, or one node running with a stale `OIDC_CLIENT_SECRET_APP5`.

## What was checked

- `nginx/nginx.conf`
- `openig_home/config/routes/10-grafana.json`
- `docker-compose.yml`
- `.env`
- `docker/openig/docker-entrypoint.sh`
- `openig_home/config/config.json`
- `openig_home/logs/route-10-grafana.log`

## Findings

> [!success] Nginx config in repo is sticky and self-contained
> `openig_c_pool` uses `ip_hash;` with only `openig-c1` and `openig-c2`. The mounted `nginx.conf` has no `include` directives, and `nginx-c` mounts only this file to `/etc/nginx/nginx.conf`.

> [!success] Grafana route does not hardcode or render the client secret
> `openig_home/config/routes/10-grafana.json` keeps `clientSecret` as `${env['OIDC_CLIENT_SECRET_APP5']}`.

> [!success] Compose resolves the same APP5 secret for both OpenIG nodes
> `docker-compose.yml` injects `OIDC_CLIENT_SECRET_APP5: "${OIDC_CLIENT_SECRET_APP5}"` into both `openig-c1` and `openig-c2`. `.env` currently sets `OIDC_CLIENT_SECRET_APP5=OwTURCqQbQQH6ygACrBXaAnxzCDzFNRa`.

> [!warning] Runtime container env could not be read directly in this session
> Docker socket access is blocked by the sandbox, so `docker exec ... printenv` and `nginx -T` could not be run here. Repo state and `docker compose config` are consistent, but direct proof of live container drift is still missing.

> [!warning] Shared OpenIG log volume hides node identity
> Both `openig-c1` and `openig-c2` mount the same `./openig_home:/opt/openig`, so `openig_home/logs/route-10-grafana.log` can contain entries from either node. The file shows two `unauthorized_client` failures 37 ms apart, which proves repeated token attempts but not which node emitted them.

## Decision

Current evidence does **not** support "repo nginx config is sending Grafana traffic to both nodes" as the root cause.

Current evidence also does **not** support "the route file cached a stale APP5 secret on disk," because the route reads the env var and the entrypoint does not rewrite that field.

The most likely remaining cause is runtime drift outside the checked files:

- one running [[OpenIG]] container still has an old `OIDC_CLIENT_SECRET_APP5`, or
- the live nginx container is not running the checked config, or
- duplicate token exchange is being triggered by live traffic behavior not visible from repo state alone.

## Next steps

1. Read live env from both containers:
   - `docker exec openig-c1 printenv OIDC_CLIENT_SECRET_APP5`
   - `docker exec openig-c2 printenv OIDC_CLIENT_SECRET_APP5`
2. Dump active nginx config:
   - `docker exec nginx-c nginx -T`
3. Add a temporary diagnostic response header from [[OpenIG]] or nginx to identify which node handled `/openid/app5`.
4. If drift is confirmed, recreate only the gateway services. This stays compliant because it touches [[OpenIG]]/nginx/Vault-side infrastructure, not target apps.

## Files referenced

- `nginx/nginx.conf`
- `openig_home/config/routes/10-grafana.json`
- `docker-compose.yml`
- `.env`
- `docker/openig/docker-entrypoint.sh`
- `openig_home/config/config.json`
- `openig_home/logs/route-10-grafana.log`
