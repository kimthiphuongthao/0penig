---
title: Per-route JwtSession isolation for shared OpenIG
tags:
  - openig
  - debugging
  - shared-infra
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-23
status: done
---

# Per-route JwtSession isolation for shared OpenIG

Context: fixed shared-session contamination in [[OpenIG]] by moving the shared apps from the global `Session` heap cookie to per-route `JwtSession` objects, while keeping global `JwtKeyStore` in `config.json` unchanged.

## Root cause

All six app routes were reading and writing the same global `IG_SSO` cookie. That let `TokenReferenceFilter.then()` on one app clear OAuth2 pending state for a different app, which broke the SSO2-after-SLO path across [[Stack A]], [[Stack B]], and [[Stack C]].

## What changed

- Added top-level `session` references to six app routes:
  - `SessionApp1` -> `IG_SSO_APP1`
  - `SessionApp2` -> `IG_SSO_APP2`
  - `SessionApp3` -> `IG_SSO_APP3`
  - `SessionApp4` -> `IG_SSO_APP4`
  - `SessionApp5` -> `IG_SSO_APP5`
  - `SessionApp6` -> `IG_SSO_APP6`
- Added matching `JwtSession` heap objects to those six app routes, using:
  - `cookieDomain: .sso.local`
  - `sessionTimeout: 30 minutes`
  - `sharedSecret: ${env['JWT_SHARED_SECRET']}`
  - `keystore: JwtKeyStore`
  - `alias: openig-jwt`
  - `password: ${env['KEYSTORE_PASSWORD']}`
- Added route-local `heap` plus matching `session` entries to five logout intercept routes so logout reads the same browser cookie as its app route.
- Updated `StripGatewaySessionCookies.groovy` to strip only:
  - `IG_SSO_APP1`
  - `IG_SSO_APP2`
  - `IG_SSO_APP3`
  - `IG_SSO_APP4`
  - `IG_SSO_APP5`
  - `IG_SSO_APP6`

> [!success]
> Restarted `shared-openig-1` and `shared-openig-2` after the route changes. In the fresh startup window, both containers loaded all target routes and showed no `session` or `JwtSession` startup errors.

## Verification

- Confirmed startup route loads after restart for:
  - `01-wordpress`
  - `02-app2`
  - `02-redmine`
  - `01-jellyfin`
  - `10-grafana`
  - `11-phpmyadmin`
  - `00-wp-logout`
  - `00-redmine-logout`
  - `00-jellyfin-logout`
  - `00-grafana-logout`
  - `00-phpmyadmin-logout`
- Confirmed backchannel logout routes stayed unchanged.
- Commit created: `06ab634` (`feat(shared): per-route JwtSession isolation — 6 apps fully independent`)

> [!warning]
> Full historical container logs still contain older OAuth2 `WARN` and `ERROR` lines from before the restart. Those are not startup failures for this change. The restart-scoped logs were clean for `session` and `JwtSession`.

## Current state

Each app now owns an isolated gateway session cookie with no shared browser-side state between WordPress, WhoAmI, Redmine, Jellyfin, Grafana, and phpMyAdmin. Logout routes are aligned with the same cookie names, so each app can read and clear only its own gateway session.

> [!tip]
> If new shared-infra app routes are added later, define a route-local `JwtSession` immediately and give the app route plus its logout intercept the same `cookieName`. Do not reuse the global `Session` heap for multi-app shared ingress.

Files changed:
- `openig_home/config/routes/01-wordpress.json`
- `openig_home/config/routes/02-app2.json`
- `openig_home/config/routes/02-redmine.json`
- `openig_home/config/routes/01-jellyfin.json`
- `openig_home/config/routes/10-grafana.json`
- `openig_home/config/routes/11-phpmyadmin.json`
- `openig_home/config/routes/00-wp-logout.json`
- `openig_home/config/routes/00-redmine-logout.json`
- `openig_home/config/routes/00-jellyfin-logout.json`
- `openig_home/config/routes/00-grafana-logout.json`
- `openig_home/config/routes/00-phpmyadmin-logout.json`
- `openig_home/scripts/groovy/StripGatewaySessionCookies.groovy`
- `docs/obsidian/debugging/2026-03-23-per-route-jwtsession-isolation-shared.md`

Related systems: [[OpenIG]], [[Keycloak]], [[Vault]], [[Stack A]], [[Stack B]], [[Stack C]]
