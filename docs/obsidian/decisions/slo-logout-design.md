---
title: SLO Logout Design for Stack C
tags:
  - decisions
  - stack-c
  - slo
  - openig
  - keycloak
date: 2026-03-12
status: accepted
---

# SLO Logout Design

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

Stack C needs reliable SLO for Grafana and phpMyAdmin with:
- deterministic post-logout redirect
- compatibility with Keycloak 18+
- support for backchannel logout blacklist invalidation

## Decision

Use app-specific Groovy logout handlers (`ScriptableHandler`) instead of relying only on static `defaultLogoutGoto`.

## Rationale

1. Custom Groovy `SloHandler*` over static `defaultLogoutGoto`
- `defaultLogoutGoto` is static and cannot safely compute runtime `id_token_hint` from session keys.
- Groovy handlers can:
  - resolve multiple session key variants (`host:port`, `host`, `OPENIG_PUBLIC_URL`)
  - clear session before redirect
  - add fallback behavior and diagnostics (`KEYCLOAK_BROWSER_URL` warning)

2. `id_token_hint` behavior
- OIDC spec marks `id_token_hint` as RECOMMENDED for RP-initiated logout.
- On Keycloak 18+, when `post_logout_redirect_uri` is used, behavior is reliable only when the matching `id_token_hint` is present.
- Decision: always append `id_token_hint` when available; log warning when missing.

3. OpenIG capability constraint
- Runtime OpenIG version in this lab is `6.0.2`.
- `openIdEndSessionOnLogout` is not available in this runtime, so automatic end-session handling cannot be enabled via route config.
- Decision: keep logout logic in Groovy handlers and route interception.

4. phpMyAdmin logout semantics
- `auth_type=http` in phpMyAdmin triggers HTTP auth challenge on logout and commonly returns `401`.
- This maps naturally to `HttpBasicAuthFilter.failureHandler`.
- Decision: set `PhpMyAdminBasicAuth.failureHandler` to `SloHandlerPhpMyAdmin.groovy`.

5. Grafana logout semantics
- `GET /logout` in Grafana returns `302` (not a `401` challenge path).
- `HttpBasicAuthFilter.failureHandler` is not a reliable interception point for Grafana logout.
- Decision: add dedicated route `00-grafana-logout.json` to intercept `/logout` and invoke `SloHandlerGrafana.groovy`.

## Consequences

- Positive:
  - App-specific logout behavior is explicit and testable.
  - Redirect and token-hint handling are deterministic.
  - Works with current OpenIG version limits.
- Tradeoff:
  - More custom Groovy code to maintain.
  - Route ordering is now part of logout correctness.

> [!tip]
> Keep SLO handlers narrow: extract `id_token`, build Keycloak logout URL, clear session, redirect.

> [!warning]
> Route precedence is critical; dedicated logout routes must be loaded before app routes.

> [!success]
> Current design supports both Grafana (`302 /logout`) and phpMyAdmin (`401` failureHandler path) without changing target apps.

