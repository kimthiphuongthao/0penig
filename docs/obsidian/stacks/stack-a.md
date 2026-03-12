---
title: Stack A
tags:
  - sso
  - stack-a
  - openig
  - wordpress
date: 2026-03-12
status: complete
---

# Stack A

Related: [[Stack B]] [[Stack C]] [[OpenIG]] [[Keycloak]] [[Vault]]

## Services

| Service | Compose service / container | Ports | Purpose |
|---|---|---|---|
| `nginx-a` | `nginx` / `sso-nginx` | `80:80` (host:container) | Public entrypoint and LB to OpenIG HA pool. |
| `openig-a1/a2` | `openig-1` + `openig-2` / `sso-openig-1` + `sso-openig-2` | `8080` (internal only) | OpenIG gateway nodes handling OIDC, session, and app routing. |
| `wordpress-a` | `wordpress` / `sso-wordpress` | `80` (internal only) | Legacy app behind OpenIG. |
| `redis-a` | `redis-a` / `sso-redis-a` | `6379` (internal only) | Session blacklist store for backchannel logout invalidation. |
| `vault-a` | `vault` / `sso-vault` | `8200` (internal only) | Secret store + AppRole auth for WP credentials. |

## Public Entry

- Host: `wp-a.sso.local`
- Port: `80`
- URL: `http://wp-a.sso.local`

## Auth Mechanism

- Form-based login intercept via OpenIG:
  - `OAuth2ClientFilter` enforces OIDC login with Keycloak.
  - `VaultCredentialFilter.groovy` loads mapped WordPress credentials from Vault.
  - `CredentialInjector.groovy` performs backend POST to `wp-login.php`, captures WP cookies, caches them in `JwtSession`, and injects them into proxied requests.

> [!success]
> SSO + SLO confirmed working for Stack A (WordPress + WhoAmI), including frontchannel logout intercept and Keycloak backchannel logout with Redis blacklist invalidation.

> [!warning]
> Known issues from `gotchas.md`:
> - Stack B Jellyfin WebSocket route still uses `http://` instead of `ws://` (`01-jellyfin.json`, pending fix).
> - Stack B `cookieDomain: ".sso.local"` is missing (low priority).
> - Operational gotcha for Stack A: empty `/vault/file/openig-role-id` or `/vault/file/openig-secret-id` on one OpenIG node can trigger `"SSO authentication failed"`.

## Routes

| File | Purpose |
|---|---|
| `00-backchannel-logout-app1.json` | Matches `POST /openid/app1/backchannel_logout`; delegates to `BackchannelLogoutHandler.groovy` to parse `logout_token` and write `blacklist:{sid}` in Redis (TTL 3600). |
| `00-wp-logout.json` | Intercepts WordPress logout requests (`wp-login.php?action=logout`) for `/app1` and `wp-a.sso.local`; delegates to `SloHandler.groovy`. |
| `01-wordpress.json` | Main WordPress route for `Host: wp-a.sso.local.*`; chain = `OidcFilter` (`/openid/app1`) -> `SessionBlacklistFilter` -> `VaultCredentialFilter` -> `WpSessionInjector`; proxies to `http://wordpress`. |
| `02-app2.json` | Main WhoAmI route for `Host: whoami-a.sso.local.*`; chain = `OidcFilter` (`/openid/app2`) -> `SessionBlacklistFilterApp2` -> `App2HeaderFilter`; proxies to `http://whoami`. |

## Groovy Scripts

| Script | Purpose |
|---|---|
| `BackchannelLogoutHandler.groovy` | Parses backchannel `logout_token` JWT, extracts `sid/sub`, and writes Redis key `blacklist:{sid}` with expiry. |
| `CredentialInjector.groovy` | Uses Vault-provided app creds to submit WordPress login form, caches WordPress session cookies in OpenIG session, injects cookies into upstream request, and clears cache if WP redirects back to login. |
| `VaultCredentialFilter.groovy` | Logs into Vault via AppRole (`role_id`/`secret_id` files), caches Vault token in session, reads `secret/data/wp-creds/{preferred_username}`, sets `attributes.wp_credentials`. |
| `SessionBlacklistFilter.groovy` | App1 blacklist enforcement: resolve OIDC sid from id token, check Redis blacklist, clear session and force re-entry redirect when blacklisted. |
| `SessionBlacklistFilterApp2.groovy` | App2 blacklist enforcement with stricter Redis protocol parsing and app2-specific sid cache key (`oidc_sid_app2`). |
| `SloHandler.groovy` | Frontchannel logout handler: resolve `id_token` from OpenIG session keys, clear session, redirect to Keycloak end-session with dynamic `post_logout_redirect_uri` and `id_token_hint` when available. |
| `App1ResponseRewriter.groovy` | Empty file (placeholder, currently unused in any route chain). |

## Vault AppRole Setup Summary

Source: `stack-a/vault/init/vault-bootstrap.sh`

1. Wait for Vault readiness (`vault status` return code `0` unsealed or `2` sealed-but-reachable).
2. Initialize and unseal Vault if needed; persist keys in `/vault/data/.vault-keys.*`.
3. Enable `kv-v2` on `secret/` and enable `approle` auth method.
4. Write policy `openig-readonly` with read capability on `secret/data/wp-creds/*`.
5. Create AppRole `openig` (`token_ttl=1h`, `token_max_ttl=4h`, policy `openig-readonly`).
6. Export AppRole credentials to shared files:
   - `/vault/file/openig-role-id`
   - `/vault/file/openig-secret-id`
7. Seed credentials:
   - `secret/wp-creds/alice`
   - `secret/wp-creds/bob`
8. Mark bootstrap complete with `/vault/data/.bootstrap-done` for idempotency.
