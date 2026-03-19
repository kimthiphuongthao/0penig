---
title: Stack C phpMyAdmin OIDC vs Grafana investigation
tags:
  - debugging
  - stack-c
  - openig
  - keycloak
  - vault
  - phpmyadmin
date: 2026-03-19
status: completed
---

# Stack C phpMyAdmin OIDC vs Grafana investigation

## Context

Compare why [[Stack C]] `phpMyAdmin` login fails while `Grafana` works, even though both use the same [[Keycloak]] realm and the same [[OpenIG]] gateway.

Targets compared:

- Grafana: client `openig-client-c-app5`, clientEndpoint `/openid/app5`
- phpMyAdmin: client `openig-client-c-app6`, clientEndpoint `/openid/app6`

## What was checked

1. Fresh OpenIG logs from `stack-c-openig-c1-1` and `stack-c-openig-c2-1`
2. Route configs:
   - `stack-c/openig_home/config/routes/10-grafana.json`
   - `stack-c/openig_home/config/routes/11-phpmyadmin.json`
3. Keycloak admin API for both dedicated clients
4. `stack-c/nginx/nginx.conf`
5. Actual redirect URL emitted by each OpenIG node
6. phpMyAdmin Apache access log
7. [[Vault]] secret lookup path used by `VaultCredentialFilter.groovy`
8. MariaDB users and DB login with Vault-fetched credentials

## Findings

> [!success] OIDC config parity confirmed
> `Issuer`, `authorizeEndpoint`, `tokenEndpoint`, `jwksUri`, scopes, `requireHttps`, and callback structure are equivalent between app5 and app6. Keycloak client metadata also matches the expected `redirect_uri` shape for both apps.

> [!warning] Current live failure is after OIDC, not in OIDC
> On `2026-03-19T01:40:56Z`, `2026-03-19T01:41:01Z`, and `2026-03-19T01:51:11Z`, OpenIG `c1` logged `SloHandler` redirects for `openig-client-c-app6`. Those redirects only happen after phpMyAdmin returns `401` to `HttpBasicAuthFilter`.

> [!warning] phpMyAdmin rejects the injected credentials
> phpMyAdmin access log shows:
> - `2026-03-19T01:40:55Z` `GET /` `401` user `alice`
> - `2026-03-19T01:41:01Z` `GET /` `401` user `bob`
> - `2026-03-19T01:51:11Z` `GET /` `401` user `alice`

> [!tip] Historical callback errors are real but stale
> `OAuth2ClientFilter` callback errors (`no authorization in progress`, `unexpected state`) exist in older logs on `2026-03-18` and `2026-03-19T01:27:45Z`, aligned with [[Vault]] failures. They do not match the newer failure pattern after Vault recovery.

## Root cause

`11-phpmyadmin.json` adds a second auth hop that `10-grafana.json` does not have:

- `VaultCredentialFilter.groovy` reads `preferred_username` from the OIDC `id_token`
- It fetches `secret/data/phpmyadmin/<username>` from [[Vault]]
- `HttpBasicAuthFilter` injects those credentials to phpMyAdmin
- phpMyAdmin then authenticates against MariaDB

Current evidence shows the Vault-backed phpMyAdmin credentials do not match the MariaDB state:

- MariaDB has user `alice`
- MariaDB does **not** have user `bob`
- Login with Vault-fetched `alice` credentials fails: `ERROR 1045 (28000): Access denied`
- Login with Vault-fetched `bob` credentials fails: `ERROR 1045 (28000): Access denied`

So the dedicated app6 OIDC client is not the active problem. The active problem is the app6 post-OIDC credential bridge from `id_token preferred_username` -> `Vault secret` -> `MariaDB account`.

## Proxy/header check

> [!success] `AUTH_SESSION_ID` Secure warning is a red herring here
> `stack-c/nginx/nginx.conf` does **not** send `X-Forwarded-Proto`, which is correct for [[Stack C]]. Actual redirect URLs emitted by both OpenIG nodes are `http://...:18080/openid/appX/callback`, matching Keycloak client config. This is not an `invalid_redirect_uri` or Secure-cookie proxy issue.

## Decision-quality summary

- Grafana works because it stops at OIDC and passes a header to Grafana.
- phpMyAdmin fails because it adds Vault lookup plus injected Basic Auth against MariaDB.
- The same Keycloak realm is not the differentiator.
- The differentiator is app6's downstream credential mapping and database account state.

## Next steps

> [!tip] Best fix direction
> Align `secret/phpmyadmin/<username>` values in [[Vault]] with real MariaDB accounts and passwords, or create/update MariaDB users to match the existing Vault records. Also decide whether `bob` should be a supported phpMyAdmin user at all, because the DB currently lacks that account.

