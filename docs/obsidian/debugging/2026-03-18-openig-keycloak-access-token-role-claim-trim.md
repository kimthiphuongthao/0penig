---
title: 2026-03-18 OpenIG Keycloak access token role claim trim
tags:
  - debugging
  - keycloak
  - openig
  - jwtsession
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-18
status: complete
---

# 2026-03-18 OpenIG Keycloak access token role claim trim

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- [[OpenIG]] stores OIDC tokens inside `JwtSession`, so unnecessary access-token claims directly increase the encrypted session payload.
- The four gateway clients in [[Keycloak]] still inherited the shared `roles` client scope mappers, which leave `realm roles` and `client roles` enabled for access tokens by default.
- Scope for this task was runtime [[Keycloak]] config only: `openig-client`, `openig-client-b`, `openig-client-c-app5`, and `openig-client-c-app6`.

## What done

- Retrieved a master-realm admin token with `admin-cli` and inspected all four clients in realm `sso-realm`.
- Confirmed each client had no direct protocol mappers at the start of the task, so the role-claim behavior came from the shared `roles` client scope.
- Added or normalized two client-level protocol mappers per client:
  - `realm roles` using `oidc-usermodel-realm-role-mapper`
  - `client roles` using `oidc-usermodel-client-role-mapper`
- Set `access.token.claim=false` on both mappers while leaving `introspection.token.claim=true` and the original claim names unchanged.
- Left ID token and userinfo behavior untouched. No mapper deletion was used.

> [!success]
> Fresh authorization-code exchanges for all four clients returned access tokens without `realm_access` and `resource_access`.

> [!tip]
> This change is reversible without deleting anything: flip each client-level mapper back to `access.token.claim=true` if a future gateway flow actually needs those claims in the access token.

> [!warning]
> The verification user `alice` has a minimal role footprint, so the strongest proof of the intended config change is the client-level mapper state plus the post-change token decode showing both role containers absent.

## Per-client changes

- [[Stack A]] `openig-client`
  - `realm roles` client-level mapper present with `access.token.claim=false`
  - `client roles` client-level mapper present with `access.token.claim=false`
- [[Stack B]] `openig-client-b`
  - created `realm roles` with `access.token.claim=false`
  - created `client roles` with `access.token.claim=false`
- [[Stack C]] `openig-client-c-app5`
  - created `realm roles` with `access.token.claim=false`
  - created `client roles` with `access.token.claim=false`
- [[Stack C]] `openig-client-c-app6`
  - created `realm roles` with `access.token.claim=false`
  - created `client roles` with `access.token.claim=false`

## Verification

- Used fresh browser-style auth-code flows with `alice/alice123` for each client and decoded the returned JWT access token.
- `openig-client` via `http://localhost/openid/callback`
  - token length: `1227`
  - `realm_access`: absent
  - `resource_access`: absent
- `openig-client-b` via `http://redmine-b.sso.local:9080/openid/app3/callback`
  - token length: `1205`
  - `realm_access`: absent
  - `resource_access`: absent
- `openig-client-c-app5` via `http://grafana-c.sso.local:18080/openid/app5/callback`
  - token length: `1185`
  - `realm_access`: absent
  - `resource_access`: absent
- `openig-client-c-app6` via `http://phpmyadmin-c.sso.local/openid/app6/callback`
  - token length: `1185`
  - `realm_access`: absent
  - `resource_access`: absent

## Current state

- All four OpenIG gateway clients in realm `sso-realm` now carry explicit client-level overrides to suppress role containers in access tokens.
- The shared `roles` client scope still exists, but these gateway clients now have their own reversible access-token settings.
- No OpenIG route JSON, Groovy scripts, nginx config, Vault bootstrap, or target application config was modified by this task.

## Files changed

- `docs/obsidian/debugging/2026-03-18-openig-keycloak-access-token-role-claim-trim.md`
- `docs/obsidian/stacks/stack-a.md`
- `docs/obsidian/stacks/stack-b.md`
- `docs/obsidian/stacks/stack-c.md`
