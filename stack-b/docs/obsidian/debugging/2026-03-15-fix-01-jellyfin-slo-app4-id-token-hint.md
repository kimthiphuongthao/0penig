---
title: FIX-01 Jellyfin SLO app4 session key and id_token_hint fallback
tags:
  - debugging
  - openig
  - stack-b
  - jellyfin
  - keycloak
date: 2026-03-15
status: completed
---

# FIX-01 Jellyfin SLO app4 session key and id_token_hint fallback

## Context

`[[Stack B]]` had a confirmed FIX-01 gap in `[[OpenIG]]` logout handling for `[[Keycloak]]`-backed Jellyfin SSO.

The Jellyfin route uses `clientEndpoint` `/openid/app4`, but `openig_home/scripts/groovy/SloHandlerJellyfin.groovy` was still looking up OAuth2 session state under `/openid/app3`.

## What Changed

- Updated the OAuth2 session keys from `/openid/app3` to `/openid/app4` so the script reads the same session namespace populated by `openig_home/config/routes/01-jellyfin.json`.
- Changed logout URL construction to only build the Keycloak end-session URL when `id_token_hint` is present.
- Added a graceful fallback that logs a warning and redirects to the post-logout URI after local session invalidation when `id_token_hint` is missing.

> [!success]
> The local OpenIG session is cleared before either redirect path, so missing `id_token_hint` no longer blocks local logout completion.

## Root Cause

The SLO script was using stale app3 OAuth2 session keys after the Jellyfin route moved to app4. That prevented recovery of `id_token`, which made RP-initiated logout incomplete or inconsistent.

> [!warning]
> I could not perform the requested Docker restart and container log verification in this sandbox because access to the local Docker socket was denied.

## Files Changed

- `openig_home/scripts/groovy/SloHandlerJellyfin.groovy`

## Current State

- Script logic now aligns with the Jellyfin route's app4 client endpoint.
- Missing `id_token_hint` degrades to local logout plus redirect instead of constructing an incomplete end-session request.
- Runtime restart and route reload confirmation still need to be executed from an environment with Docker daemon access.

## Next Steps

1. Run `docker restart sso-b-openig-1 sso-b-openig-2`.
2. Run `docker logs sso-b-openig-1 2>&1 | grep 'Loaded the route'`.
3. Validate logout from `[[Jellyfin]]` through `[[OpenIG]]` on `[[Stack B]]`.

> [!tip]
> If route reload fails after restart, inspect the newest `route-system` and `route-01-jellyfin` logs under `openig_home/logs/` to separate route parsing issues from runtime SLO behavior.
