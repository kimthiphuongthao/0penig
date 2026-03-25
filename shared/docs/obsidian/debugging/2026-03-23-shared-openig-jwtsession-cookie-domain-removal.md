---
title: Shared OpenIG JwtSession cookieDomain removal
tags:
  - openig
  - debugging
  - shared-infra
  - cookies
date: 2026-03-23
status: done
---

# Shared OpenIG JwtSession cookieDomain removal

Context: follow-up to [[2026-03-23-per-route-jwtsession-isolation-shared|Per-route JwtSession isolation for shared OpenIG]]. The first per-route `JwtSession` rollout used `cookieDomain: .sso.local`, which made each app cookie visible to every `*.sso.local` host behind [[OpenIG]].

## Root cause

Each browser request to a shared `*.sso.local` hostname carried all six `IG_SSO_APP*` cookies instead of only the cookie for that app host. The combined JWT cookie payload was about 7 KB and pushed some requests over Tomcat's default 8 KB `maxHttpHeaderSize`, causing HTTP 400 `Request header is too large`.

## What changed

- Removed `cookieDomain` from the route-local `JwtSession` heap object in:
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
- This makes each `IG_SSO_APP*` cookie host-only, so the browser now returns only the cookie set by the exact app hostname.

> [!success]
> Restarted `shared-openig-1` and `shared-openig-2` on 2026-03-23. `shared-openig-1` loaded the updated app and logout routes after restart, including `01-wordpress`, `02-app2`, `02-redmine`, `01-jellyfin`, `10-grafana`, `11-phpmyadmin`, `00-wp-logout`, `00-redmine-logout`, `00-jellyfin-logout`, `00-grafana-logout`, and `00-phpmyadmin-logout`.

## Verification

- `tilth "cookieDomain" --scope openig_home/config/routes` returned zero matches after the edit.
- `docker restart shared-openig-1 shared-openig-2` completed successfully.
- Restart-window logs from `shared-openig-1` showed route load lines and one request-time `OAuth2ClientFilter` error: `invalid_request` with unexpected `state` value.

> [!warning]
> The observed `OAuth2ClientFilter` `invalid_request` log is an OAuth callback state mismatch, not a route parse/load failure. The route definitions themselves loaded after restart.

## Current state

Per-app gateway JWT cookies remain isolated by `cookieName`, and they are now isolated by host as well. This reduces request header size for shared ingress traffic and avoids cross-host cookie fan-out across [[Stack A]], [[Stack B]], and [[Stack C]].

> [!tip]
> For future shared-infra `JwtSession` routes, prefer host-only cookies unless a cross-subdomain browser cookie is explicitly required and the resulting header size budget has been measured.

Commit:
- `ceb1cbc` - `fix(shared): remove cookieDomain from per-route JwtSession to prevent header overflow`

Related systems: [[OpenIG]], [[Keycloak]], [[Vault]], [[Stack A]], [[Stack B]], [[Stack C]]
