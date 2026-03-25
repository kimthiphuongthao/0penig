---
title: Shared infra cookie session design vs implementation
tags:
  - debugging
  - shared-infra
  - openig
  - sessions
date: 2026-03-24
status: complete
---

# Shared infra cookie session design vs implementation

## Context

Reviewed shared infra session design against live route implementation for [[OpenIG]].

Files checked:
- `shared/openig_home/config/config.json`
- `shared/openig_home/config/routes/*.json`
- `.omc/plans/shared-infra.md`
- `.claude/rules/architecture.md`
- `shared/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `shared/nginx/nginx.conf`

## Findings

> [!success]
> Route implementation is using intentional per-route `JwtSession` isolation, not accidentally falling back to the global `Session` heap.

- `config.json` still defines global heap `Session` with cookie `IG_SSO` and `cookieDomain: ".sso.local"`.
- Stateful route files do **not** use that global session. They declare route-local `session` values `SessionApp1` .. `SessionApp6` and matching route-local `JwtSession` heap objects with cookie names `IG_SSO_APP1` .. `IG_SSO_APP6`.
- Front-channel logout intercept routes duplicate the same per-app session manager so logout handlers read the correct app cookie.
- Backchannel logout routes are stateless and do not declare route-local sessions.
- Route-local `JwtSession` blocks include `sharedSecret`, `keystore`, alias, and password. No missing crypto references were found.
- Route-local `JwtSession` blocks omit `cookieDomain`, so cookies are host-only. This further reduces cross-app cookie bleed across `*.sso.local`.

## Mismatch Assessment

> [!warning]
> There is a documentation mismatch between plan/current `config.json` assumptions and route behavior.

- `.omc/plans/shared-infra.md` still describes a single shared cookie `IG_SSO` with per-app `tokenRefKey` isolation.
- `.claude/rules/architecture.md` explicitly documents that OpenIG 6 supports per-route `JwtSession` isolation with distinct `cookieName` values, and the route JSON follows that pattern correctly.
- Result: the implementation is internally consistent with the architecture rule, but the shared-infra plan and proxy assumptions are stale.

## Real Risks

> [!warning]
> Per-route isolation exposed one real Stack A SLO gap.

- `app1` and `app2` both use Keycloak client `openig-client`, but only `00-backchannel-logout-app1.json` exists.
- `BackchannelLogoutHandler.groovy` writes exactly one Redis blacklist key using the configured `redisKeyPrefix`, so the `app1` route blacklists only `app1:blacklist:<sid>`.
- Because `02-app2.json` has its own isolated session/cookie (`SessionApp2`, `IG_SSO_APP2`) and `oidc_sid_app2`, Keycloak backchannel logout for client `openig-client` does not obviously invalidate `app2` local session state.
- `shared/nginx/nginx.conf` still applies `proxy_cookie_flags IG_SSO samesite=lax;` even though routes now emit `IG_SSO_APP1` .. `IG_SSO_APP6`. If nginx is expected to enforce cookie flags, it is targeting the wrong cookie names.

## Current state

- Cross-app session bleed risk from shared gateway cookie is low because cookies are per-app and host-only.
- Primary follow-up is to reconcile docs/config assumptions with the per-route isolation model and decide how Stack A backchannel logout should cover both `app1` and `app2`.

## Related links

- [[OpenIG]]
- [[Keycloak]]
- [[Vault]]
- [[Stack A]]
