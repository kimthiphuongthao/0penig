---
title: Stack C phpMyAdmin SSO Failure From Sealed Vault
tags:
  - debugging
  - stack-c
  - vault
  - openig
  - phpmyadmin
  - sso
date: 2026-03-19
status: investigated
---

# Stack C phpMyAdmin SSO Failure From Sealed Vault

Related: [[OpenIG]] [[Vault]] [[Keycloak]] [[Stack C]]

## Context

- Requested task: debug Stack C phpMyAdmin SSO failure showing `SSO authentication failed. Please contact support.`
- Scope: inspect Stack C OpenIG node logs, read the phpMyAdmin route and Stack C `VaultCredentialFilter.groovy`, and check Vault health without changing gateway or app runtime.

## Findings

- The first failing component is `VaultCredentialFilter.groovy`, not phpMyAdmin and not the target app.
- Both OpenIG nodes log `Vault AppRole login failed with HTTP 503` while handling route `11-phpmyadmin`.
- Stack C Vault is currently `Sealed: true`, which explains the AppRole login `503`.
- The generic `SSO authentication failed` page is not thrown by any Groovy script.
- That message is embedded in `11-phpmyadmin.json` as the `OAuth2ClientFilter.failureHandler` response.
- No Stack C phpMyAdmin evidence showed `HttpBasicAuthFilter` reaching a downstream credential rejection during this investigation.

> [!warning]
> The user-facing message is misleading for this incident. The visible page comes from the OIDC filter failure handler, but the earlier route failure is Vault being sealed and refusing AppRole login.

## Evidence

- `docker logs stack-c-openig-c1-1 --tail 150`
  - `ERROR ... VaultCredentialFilter ... Failed to fetch phpMyAdmin credentials from Vault`
  - `java.lang.IllegalStateException: Vault AppRole login failed with HTTP 503`
- `docker logs stack-c-openig-c2-1 --tail 150`
  - same `VaultCredentialFilter` `HTTP 503` failure on Stack C node 2
- `docker exec stack-c-vault-c-1 vault status`
  - failed because Vault CLI defaulted to HTTPS against an HTTP listener
- `docker exec stack-c-vault-c-1 sh -lc 'VAULT_ADDR=http://127.0.0.1:8200 vault status'`
  - `Initialized true`
  - `Sealed true`
- `stack-c/openig_home/config/routes/11-phpmyadmin.json`
  - `OAuth2ClientFilter.failureHandler` returns `<h2>SSO authentication failed. Please contact support.</h2>`
  - filter order is `OidcFilterApp6 -> SessionBlacklistFilterApp6 -> VaultCredentialFilter -> PhpMyAdminBasicAuth`
- `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
  - AppRole login is executed at `POST ${VAULT_ADDR}/v1/auth/approle/login`
  - non-2xx login status throws `Vault AppRole login failed with HTTP ${loginStatus}`
- `stack-c/openig_home/logs/route-11-phpmyadmin.log`
  - `VaultCredentialFilter.groovy:108` throws on Vault login `503`
  - later requests show `OAuth2ClientFilter` `invalid_request` / `no authorization in progress`

> [!success]
> Root cause was isolated to Stack C Vault runtime state, not phpMyAdmin application code.

## Current State

- `stack-c-vault-c-1`: running but sealed
- `stack-c-openig-c1-1`: healthy but cannot fetch phpMyAdmin credentials from Vault
- `stack-c-openig-c2-1`: healthy but cannot fetch phpMyAdmin credentials from Vault
- phpMyAdmin SSO: broken until Vault is unsealed and AppRole credentials are revalidated

## Next Steps

1. Unseal/bootstrap Stack C Vault using the documented Stack C recovery flow.
2. Refresh `openig_home/vault/role_id` and `openig_home/vault/secret_id` if the AppRole was recreated during bootstrap.
3. Restart only Stack C OpenIG nodes and retest phpMyAdmin SSO.
4. Consider a follow-up gateway improvement so Vault failures return a Vault-specific page instead of surfacing later as a generic OIDC support message.

> [!tip]
> Repository docs already describe Stack C manual recovery with `docker exec stack-c-vault-c-1 sh /tmp/vault-bootstrap.sh` and then regenerating `role_id` / `secret_id` before restarting Stack C OpenIG.

## Files Changed

- `docs/obsidian/debugging/2026-03-19-stack-c-phpmyadmin-vault-sealed-sso-failure.md`
