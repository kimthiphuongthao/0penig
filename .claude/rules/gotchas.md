# Gotchas & Decision Log

## Current status

- Active deployment: `shared/`
- Browser entrypoints are hostname-routed on port 80
- Old `stack-a/`, `stack-b/`, and `stack-c/` directories are rollback-only references
- No known functional gap is intentionally left open in the shared-infra rules baseline; the remaining gaps are production-hardening items

## Shared-infra gotchas

| Issue | Why it matters | Current rule / fix |
|-------|----------------|--------------------|
| Vault AppRole `secret_id` expires after 72h | OpenIG gets `403` from Vault after long lab downtime | Regenerate `openig-app1..6` `secret_id` files and restart `shared-openig-1/2` |
| `config.json` does not evaluate `${env['...']}` in heap config | Global session and keystore secrets fail at startup | Keep placeholders in `shared/openig_home/config/config.json` and substitute them in `docker-entrypoint.sh` |
| OIDC client secrets containing `+`, `/`, or `=` | OpenIG posts `client_secret` without URL-encoding | Use strong random alphanumeric-only secrets for `OAuth2ClientFilter` clients |
| `openidentityplatform/openig:latest` is not stable in this lab | Mutable tag changed runtime behavior and broke startup | Pin `openidentityplatform/openig:6.0.1` |
| Missing route-local session config | Route falls back to the global `Session` heap and wrong cookie | Every app route must set `"session": "SessionAppN"` and define `cookieName: "IG_SSO_APPN"` |
| `TokenReferenceFilter` not app-scoped | OAuth callback state or token refs can collide across apps | Keep unique `clientEndpoint`, `tokenRefKey`, `redisUser`, and `redisKeyPrefix` per app; skip restore on callback; remove only current-app OAuth2 keys |
| Host-derived redirects or logout targets | Wrong redirect base or header-influenced logout flow | Use `CANONICAL_ORIGIN_APP1..6`, never raw inbound `Host` |
| Redis ACL is minimal by design | Unsupported Redis commands will fail at runtime | Use `AUTH <user> <password>` and only `SET`, `GET`, `DEL`, `EXISTS`, `PING` on `appN:*` keys |
| Global `IG_SSO` heap still exists in `config.json` | Debuggers may think the shared runtime still uses one cookie | Active shared-infra routes override it with `SessionApp1..6`; browser cookies are `IG_SSO_APP1..APP6` |
| Lab transport is still HTTP-only | The current lab is not transport-secure | Production requires TLS between components, Vault Transit for Redis payload protection, and network segmentation |
| `SloHandler.groovy` and `SloHandlerJellyfin.groovy` have legacy hostname fallbacks | If `OPENIG_PUBLIC_URL` or `CANONICAL_ORIGIN_APP4` env vars are missing, logout redirect URIs silently use wrong hostnames: `openiga.sso.local` (old Stack A) and `jellyfin-b.sso.local:9080` (old Stack B port) | AUD-009 OPEN LOW — always set `OPENIG_PUBLIC_URL` and `CANONICAL_ORIGIN_APP4` in docker-compose env; fix pending in `SloHandler.groovy` line 86 and `SloHandlerJellyfin.groovy` line 127 |

## Decision log

| Decision | Reason |
|----------|--------|
| One shared nginx/OpenIG/Redis/Vault runtime fronts all 6 apps | Lower operational overhead while keeping app-level isolation through route-local sessions, Redis ACL, and Vault AppRoles |
| Route-local host-only cookies per app | Stronger browser isolation than one shared `.sso.local` cookie |
| Per-app Redis ACL users instead of one shared Redis password | Prevent cross-app key access and match future Sentinel/Cluster patterns |
| Per-app Vault AppRoles instead of one shared role | Limit blast radius to a single app secret scope |
| `user: root` remains on OpenIG containers in the lab | macOS host mounts keep Vault files root-readable only; production should move to init-container or sidecar patterns |
| Legacy stacks are rollback-only documentation | Active instructions must describe `shared/`, not the old stack split |
