---
title: TokenReferenceFilter pending-state wipe on mixed OAuth2 keys
tags:
  - openig
  - debugging
  - slo
  - grafana
date: 2026-03-23
status: done
---

# TokenReferenceFilter pending-state wipe on mixed OAuth2 keys

## Context

After SLO, [[OpenIG]] could restore stale revoked tokens for [[Grafana]] from Redis into session key `oauth2:http://grafana-c.sso.local:80/openid/app5` while `OAuth2ClientFilter` created a new pending authorization state under `oauth2:http://grafana-c.sso.local/openid/app5`.

Both keys ended with the same `clientEndpoint` suffix, so `discoverOauth2SessionKeys()` matched both during the `.then()` response phase.

> [!warning] Root cause
> The response path only gated on `hasRealTokens`. In the mixed-state case, targeted cleanup removed both the stale token entry and the pending OAuth2 state, then cleared `token_ref_id`. The next callback reached [[Keycloak]] with no in-progress authorization state and failed with `no authorization in progress` 500 on [[Stack C]].

## Change

Added a `hasPendingState` check in `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy` immediately after the 401 cleanup branch and before the existing Redis store block.

When a mixed state is detected:

- Remove only OAuth2 session entries that still contain `atr` or `access_token`
- Remove `token_ref_id`
- Return the current response without persisting the pending state to Redis

This preserves the live pending OAuth2 state so the callback can complete normally while still clearing stale restored tokens from [[Vault]]-backed Redis state.

> [!success] Verification
> Restarted `shared-openig-1`, waited 5 seconds, then restarted `shared-openig-2`.
> Recent `shared-openig-1` logs showed route loads and no `ERROR` entries in the last 10 `Loaded the route|ERROR` matches.

> [!tip] Reason for the narrow fix
> The new guard stays inside the existing `.then()` response path and does not change the 401 cleanup logic or the normal Redis offload flow for sessions that contain only real tokens.

## Files changed

- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `docs/obsidian/debugging/2026-03-23-token-ref-pending-state-wipe.md`
