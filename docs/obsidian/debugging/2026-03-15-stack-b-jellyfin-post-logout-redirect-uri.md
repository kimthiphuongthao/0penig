---
title: Stack B Jellyfin post logout redirect URI restoration
tags:
  - debugging
  - stack-b
  - jellyfin
  - keycloak
  - openig
date: 2026-03-15
status: in-progress
---

# Stack B Jellyfin post logout redirect URI restoration

Links: [[Stack B]] [[Jellyfin]] [[Keycloak]] [[OpenIG]]

## Context

`stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy` had been changed to drop `post_logout_redirect_uri` from the Keycloak logout URL.

That change avoided `Invalid redirect uri` for client `openig-client-b-app4`, but it also caused Keycloak to stop on its own logout screen instead of redirecting back to `[[Jellyfin]]`.

## Findings

- `[[OpenIG]]` Redmine logout in `stack-b/openig_home/scripts/groovy/SloHandlerRedmine.groovy` still includes `post_logout_redirect_uri`.
- `[[Jellyfin]]` logout now uses app4 session state and `client_id=openig-client-b-app4`, which is correct for the `id_token_hint`.
- Repo bootstrap under `keycloak/realm-import/realm-export.json` did not define a dedicated `openig-client-b-app4` client, even though earlier session notes say the runtime realm already had that client for backchannel logout.

> [!warning]
> Runtime verification via Keycloak admin API was blocked in this session because the sandbox denied TCP access even to `127.0.0.1:8080`, and Docker socket access was also denied. The repo fix is complete, but live realm state still needs confirmation outside the sandbox.

## Changes Made

- Restored `post_logout_redirect_uri` in `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`.
- Kept `client_id=openig-client-b-app4` and `id_token_hint` so logout remains aligned with the Jellyfin OIDC client.
- Preserved the incoming `Host` header when building the logout redirect URI, so both `http://jellyfin-b.sso.local/` and `http://jellyfin-b.sso.local:9080/` are supported when the browser uses them.
- Added dedicated bootstrap client `openig-client-b-app4` to `keycloak/realm-import/realm-export.json`.
- Registered `post.logout.redirect.uris` for:
  - `http://jellyfin-b.sso.local:9080/`
  - `http://jellyfin-b.sso.local/`

> [!success]
> The repo now expresses the intended Stack B Jellyfin logout flow explicitly: `id_token_hint` and `post_logout_redirect_uri` both target the same app4 client.

## Current State

- Source fix is applied in `[[OpenIG]]`.
- Keycloak import bootstrap now contains app4 post-logout redirect configuration.
- Live Keycloak realm still needs admin API verification.
- `sso-b-openig-1` and `sso-b-openig-2` still need restart in a non-sandboxed shell.

## Next Steps

1. Query Keycloak admin API for `openig-client-b-app4` and confirm `post.logout.redirect.uris`.
2. If the live realm differs from bootstrap, update the runtime client config to match the repo.
3. Restart `sso-b-openig-1` and `sso-b-openig-2`.
4. Re-test Jellyfin logout and confirm redirect returns to `http://jellyfin-b.sso.local:9080/` or the no-port host variant in use.

> [!tip]
> When Stack B splits backchannel logout into app-specific clients, keep `redirectUris`, `post.logout.redirect.uris`, and the Groovy logout handler in sync. One side drifting is enough to make RP-initiated logout look partially broken.
