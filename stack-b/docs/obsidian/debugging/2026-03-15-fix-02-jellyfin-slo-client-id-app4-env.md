---
title: FIX-02 Jellyfin SLO uses app4 client id for id_token_hint logout
tags:
  - debugging
  - openig
  - stack-b
  - jellyfin
  - keycloak
date: 2026-03-15
status: completed
---

# FIX-02 Jellyfin SLO uses app4 client id for id_token_hint logout

## Context

`[[Stack B]]` had a client ID mismatch during `[[Keycloak]]` RP-initiated logout for `[[Jellyfin]]`.

`openig_home/scripts/groovy/SloHandlerJellyfin.groovy` was reading `OIDC_CLIENT_ID`, which resolves to `openig-client-b`, while Jellyfin's OAuth flow issues the `id_token` for app4 client `openig-client-b-app4`.

## What Changed

- Added `OIDC_CLIENT_ID_APP4: "openig-client-b-app4"` to both OpenIG services in `docker-compose.yml`.
- Updated the Jellyfin SLO script to read `OIDC_CLIENT_ID_APP4` instead of `OIDC_CLIENT_ID`.
- Updated the exception text so runtime diagnostics reference the correct environment variable.

> [!success]
> The logout handler now sends `client_id=openig-client-b-app4` when `id_token_hint` is present, which matches the client that issued the token.

## Root Cause

The SLO handler was using the shared Stack B OpenIG client ID instead of the Jellyfin app4 client ID. Keycloak rejects an `id_token_hint` when the `client_id` does not match the client that received that token.

## Files Changed

- `docker-compose.yml`
- `openig_home/scripts/groovy/SloHandlerJellyfin.groovy`

## Current State

- `openig-b1` and `openig-b2` now expose the app4 client ID separately from the shared Stack B client ID.
- Jellyfin logout logic now aligns with the `[[OpenIG]]` app4 OAuth client configuration.

## Next Steps

1. Restart `openig-b1` and `openig-b2` so the new environment variable is loaded.
2. Re-run Jellyfin login and logout on `[[Stack B]]`.
3. Confirm the Keycloak end-session request carries `client_id=openig-client-b-app4`.

> [!tip]
> Keep app-specific client IDs isolated from shared gateway client IDs when multiple relying parties in the same stack have separate OIDC clients.
