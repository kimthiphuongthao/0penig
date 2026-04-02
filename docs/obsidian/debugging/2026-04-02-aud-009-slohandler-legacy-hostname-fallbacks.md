---
title: AUD-009 SloHandler Legacy Hostname Fallbacks
tags:
  - debugging
  - openig
  - logout
  - aud-009
date: 2026-04-02
status: done
---

# AUD-009 SloHandler Legacy Hostname Fallbacks

Related components: [[OpenIG]], [[Keycloak]], [[Vault]], [[Stack C]]

> [!success] Fix applied
> Gateway-side logout handlers no longer rely on the obsolete Stack A `openiga` fallback, and the Jellyfin legacy fallback no longer carries the old `:9080` port.

## Context

AUD-009 targeted two Groovy logout handlers in shared OpenIG:

- `shared/openig_home/scripts/groovy/SloHandler.groovy`
- `shared/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`

The shared-infra deployment should not silently fall back to legacy per-stack hostnames or ports during SLO processing.

## Root Cause

- `SloHandler.groovy` used `http://openiga.sso.local` as the implicit `OPENIG_PUBLIC_URL` fallback, which is a retired Stack A hostname.
- `SloHandlerJellyfin.groovy` used `http://jellyfin-b.sso.local:9080` as the fallback `CANONICAL_ORIGIN_APP4` value, but shared-infra expects port 80.

> [!warning] Why this mattered
> Silent fallback to retired hostnames can generate incorrect logout redirect targets and can hide broken environment configuration instead of failing early.

## Change

- `SloHandler.groovy:86-90`
  Removed the hostname fallback, added an explicit error log, and throw `IllegalStateException` when `OPENIG_PUBLIC_URL` is missing.
- `SloHandlerJellyfin.groovy:127`
  Changed the legacy fallback from `http://jellyfin-b.sso.local:9080` to `http://jellyfin-b.sso.local`.

## Validation

- Restarted `shared-openig-1`
- Restarted `shared-openig-2`
- Waited 8 seconds
- Checked filtered logs for `Loaded the route|ERROR`

Result:

- Both containers reloaded routes successfully.
- No `ERROR` lines appeared in the filtered post-restart log output.

> [!tip] Operational note
> `SloHandler.groovy` now fails closed if `OPENIG_PUBLIC_URL` is absent. Any future environment drift will surface immediately in runtime logs instead of being masked by a legacy default.

## Current State

- Shared logout handler requires `OPENIG_PUBLIC_URL`.
- Jellyfin fallback origin now matches shared-infra hostname/port expectations.
- No target application code or app-side configuration was modified.
