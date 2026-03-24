# Conventions

## Non-negotiable rule

**Never modify target application code or application-owned config.**

Target application means anything outside the gateway layer: app server code, app container images, application database schema, Keycloak realm logic, or app-owned reverse proxies.

Active shared-infra changes are limited to:

- `shared/openig_home/config/routes/*.json`
- `shared/openig_home/scripts/groovy/*.groovy`
- `shared/openig_home/config/config.json`
- `shared/nginx/nginx.conf`
- `shared/vault/`
- `shared/redis/`
- `shared/docker-compose.yml`

Legacy `stack-a/`, `stack-b/`, and `stack-c/` paths are rollback references only.

When reporting an issue, use this format:

- Which file or section changes?
- Does the change stay on the gateway side only? (`Yes` / `No`)
- What is the proposed change?

## Shared-infra naming

- Use `shared-*` container names in active docs and runbooks
- Use hostname routing on port 80 for browser URLs
- Active gateway cookies are `IG_SSO_APP1..APP6`
- `openig.sso.local` is the shared management/debug hostname

## Nginx baseline

- Upstream pool naming: `<app>_pool` when app-specific pools exist
- Always set `proxy_set_header Host $host` and `X-Real-IP $remote_addr`
- Strip trusted identity headers from client input before injecting your own values
- Use `ip_hash` for sticky routing and keepalive for connection reuse

## Vault bootstrap

- HCL is multi-line; do not compress it into semicolon-separated inline syntax
- Use `command: server` in Docker Compose
- Handle sealed status safely in bootstrap scripts: `code=$(vault status >/dev/null 2>&1; echo $?)`
- Shared-infra AppRoles are `openig-app1..6`

## Docs

- Use shared-infra wording for the active runtime
- Only mention old stacks when the context is rollback or historical background
- Do not describe legacy non-shared-infra ports or old stack cookies as active deployment details

## /etc/hosts

```text
127.0.0.1  auth.sso.local
127.0.0.1  wp-a.sso.local
127.0.0.1  whoami-a.sso.local
127.0.0.1  redmine-b.sso.local
127.0.0.1  jellyfin-b.sso.local
127.0.0.1  grafana-c.sso.local
127.0.0.1  phpmyadmin-c.sso.local
127.0.0.1  openig.sso.local
```
