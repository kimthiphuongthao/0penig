---
title: Shared Infra Cookie Isolation Sync
tags:
  - debugging
  - shared-infra
  - nginx
  - openig
  - docs
date: 2026-03-24
status: completed
---

# Shared Infra Cookie Isolation Sync

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[stack-shared]] [[Stack A]]

## Context

Shared infra on `feat/shared-infra` no longer uses one shared `IG_SSO` cookie. The active route files define isolated per-app `JwtSession` heaps:

- `SessionApp1` -> `IG_SSO_APP1`
- `SessionApp2` -> `IG_SSO_APP2`
- `SessionApp3` -> `IG_SSO_APP3`
- `SessionApp4` -> `IG_SSO_APP4`
- `SessionApp5` -> `IG_SSO_APP5`
- `SessionApp6` -> `IG_SSO_APP6`

`shared/openig_home/config/config.json` still defines a global fallback heap `Session` with cookie `IG_SSO`, but all shared app routes override it via `"session": "SessionAppN"`.

> [!warning] Root cause
> `shared/nginx/nginx.conf` still targeted `IG_SSO` in `proxy_cookie_flags`, and the plan/architecture docs still described a single shared-cookie model. The repository had already moved to route-local `JwtSession` isolation, so nginx and documentation were both describing the wrong runtime behavior.

## Fix Applied

- `shared/nginx/nginx.conf`
  - Replaced every `proxy_cookie_flags IG_SSO samesite=lax;` with `proxy_cookie_flags ~IG_SSO_APP samesite=lax;`
  - Used nginx regex cookie matching so all `IG_SSO_APP1..APP6` cookies are covered consistently across the shared host server blocks
- `.omc/plans/shared-infra.md`
  - Rewrote target-state, validation, and success-criteria wording from single `IG_SSO` to per-app `IG_SSO_APP1..APP6`
  - Documented primary isolation as route-local `SessionAppN` + per-app cookie, with `tokenRefKey` isolation (`token_ref_id_app1..app6`) as a secondary layer
  - Clarified that the global `config.json` `Session`/`IG_SSO` heap still exists but is overridden by all shared routes
  - Recorded the open item that `00-backchannel-logout-app2.json` is missing
- `.claude/rules/architecture.md`
  - Updated the `Cookie session` section to describe the shared-infra per-app cookie model
  - Noted that route-local `JwtSession` objects do not set `cookieDomain`, so shared-infra cookies are host-only and stricter than stack A/B/C

> [!success] Confirmed shared-infra model
> Shared route files define per-app cookies and omit per-route `cookieDomain`, so the actual shared runtime is stricter than the legacy stack-wide domain-cookie pattern.

## Open Item

- Missing route: `shared/openig_home/config/routes/00-backchannel-logout-app2.json`
- Impact: Keycloak backchannel logout initiated from app1 does not currently revoke app2 (WhoAmI) sessions

> [!tip] Follow-up
> Add the missing app2 backchannel logout route before treating Stack A cross-app SLO as complete in shared infra.

## Files Changed

- `shared/nginx/nginx.conf`
- `.omc/plans/shared-infra.md`
- `.claude/rules/architecture.md`
- `docs/obsidian/debugging/2026-03-24-shared-infra-cookie-isolation-sync.md`
