---
title: Stack C OpenIG Login and Logout Log Verification
tags:
  - debugging
  - stack-c
  - openig
  - sso
  - slo
date: 2026-03-19
status: verified-with-limits
---

# Stack C OpenIG Login and Logout Log Verification

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Task: verify user-reported Stack C SSO/SLO success from OpenIG logs on both nodes.
- Requested checks:
  - successful OIDC login
  - successful credential injection
  - successful logout and backchannel logout
  - no `ERROR 1045` for `alice` after the fix
  - both `grafana-sso` and `phpmyadmin-sso` routes handling requests

## Constraint

> [!warning]
> Direct `docker logs stack-c-openig-c1-1` and `docker logs stack-c-openig-c2-1` were not readable from this Codex sandbox because Docker socket access is denied. Verification below is based on the persisted Stack C OpenIG route logs under `stack-c/openig_home/logs/` plus earlier same-day session evidence already captured in the vault.

## What the accessible logs confirm

> [!success]
> Both OpenIG nodes loaded both Stack C application routes after the latest restart window.
>
> Evidence:
> - `route-system.log` shows:
>   - `01:35:54.162` and `01:35:54.375` loaded `11-phpmyadmin` as `phpmyadmin-sso`
>   - `01:35:54.178` and `01:35:54.393` loaded `10-grafana` as `grafana-sso`
> - `route-system-2026-03-18.0.log` shows a later dual-node restart:
>   - `04:01:28.450` and `04:01:28.480` loaded `11-phpmyadmin`
>   - `04:01:28.511` and `04:01:28.560` loaded `10-grafana`

> [!success]
> Logout and backchannel logout are directly confirmed for both app5 and app6.
>
> Evidence:
> - Grafana logout:
>   - `route-00-grafana-logout.log` at `02:36:46.772` logged `[SloHandler] Redirecting with id_token_hint=PRESENT, clientId=openig-client-c-app5`
> - Grafana backchannel logout:
>   - `route-00-backchannel-logout-app5-2026-03-18.0.log` at `02:36:47.xxx` logged `logout_token received`
>   - same sequence shows `JWT signature validation: VALID`, `aud validation: OK (aud=openig-client-c-app5)`, and `Redis blacklist updated successfully`
> - phpMyAdmin logout:
>   - `route-11-phpmyadmin-2026-03-18.0.log` at `02:17:56.020`, `02:41:56.303`, and `03:33:59.184` logged `[SloHandler] Redirecting with id_token_hint=PRESENT, clientId=openig-client-c-app6`
> - phpMyAdmin backchannel logout:
>   - `route-00-backchannel-logout-app6-2026-03-18.0.log` at `02:17:56.xxx`, `02:41:41.xxx`, `02:41:56.xxx`, and `03:34:00.xxx` logged `logout_token received`
>   - the same entries show `JWT signature validation: VALID`, `aud validation: OK (aud=openig-client-c-app6)`, and `Redis blacklist updated successfully`

> [!success]
> No accessible OpenIG route log contains `1045` after the phpMyAdmin Vault credential fix.
>
> Evidence:
> - Full-text search for `1045` under `stack-c/openig_home/logs/` returned no matches.

## What is direct vs inferred

> [!tip]
> OIDC session establishment is strongly indicated, but the exact `OAuth2ClientFilter callback processed -> 302 back to app` success line was not present in the persisted route logs available in the workspace.
>
> Strong indicators:
> - `id_token_hint=PRESENT` on both app5 and app6 logout flows means OpenIG had a valid OIDC session with an `id_token` stored.
> - app5 and app6 then both received valid Keycloak backchannel logout tokens immediately after logout.

> [!warning]
> phpMyAdmin credential injection success is not logged with a clean positive `HttpBasicAuthFilter succeeded` line in the persisted route files.
>
> What is directly visible:
> - historical `VaultCredentialFilter` success for `alice` exists in `route-11-phpmyadmin-2026-03-06.0.log` with:
>   - `Cache miss - fetching from Vault for username: alice`
>   - `Stored credentials for: alice`
> - same-day vault recheck note already recorded that Vault lookup for `secret/data/phpmyadmin/alice` returned HTTP `200`
> - same-day credential-drift fix note recorded that `secret/phpmyadmin/alice` was aligned to live MariaDB password `AlicePass123`
>
> Because there is no fresh post-fix positive Basic Auth success line in the accessible route files, phpMyAdmin login success should be treated as:
> - user-confirmed operationally
> - log-supported indirectly by successful OIDC session/logout behavior
> - not directly proven from persisted OpenIG route logs alone

## Remaining visible errors

> [!warning]
> Historical stale errors still exist in older/current rotated route files, but they predate the latest healthy route-load windows and do not contradict the later logout/backchannel evidence.
>
> Visible stale examples:
> - phpMyAdmin:
>   - `Vault AppRole login failed with HTTP 503`
>   - `Authorization call-back failed because there is no authorization in progress`
> - Grafana:
>   - older `unauthorized_client`
>   - older `invalid_grant` / `Code not valid`

## Verification Summary

- `grafana-sso` route: confirmed loaded on both nodes; logout and backchannel logout confirmed.
- `phpmyadmin-sso` route: confirmed loaded on both nodes; logout and backchannel logout confirmed.
- OIDC login: strongly supported by `id_token_hint=PRESENT` on logout, but not directly shown by an accessible success callback line.
- Credential injection for phpMyAdmin: no fresh `1045` in accessible OpenIG logs and no direct positive `HttpBasicAuthFilter` success line in persisted route files; user report remains the primary confirmation for final downstream login success.
- Remaining errors: only stale historical OAuth2/Vault failures are visible in persisted files; no new fatal error is visible in the accessible post-fix route-load windows.

## Files Changed

- `docs/obsidian/debugging/2026-03-19-stack-c-openig-login-logout-log-verification.md`
