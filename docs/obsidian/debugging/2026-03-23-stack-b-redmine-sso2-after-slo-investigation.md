---
title: Stack B Redmine SSO2 failure after SLO investigation
tags:
  - debugging
  - stack-b
  - openig
  - redmine
  - jellyfin
  - oauth2
  - jwt-session
  - slo
date: 2026-03-23
status: investigated
---

# Stack B Redmine SSO2 failure after SLO investigation

Related: [[Stack B]] [[OpenIG]] [[Redmine]] [[Jellyfin]] [[Keycloak]] [[Vault]]

## Context

- Symptom: `SSO -> SLO -> SSO2` fails on Redmine with `SSO authentication failed. Please contact support.`
- Key log evidence from `sso-b-openig-1`:
  - `OAuth2ClientFilter`: `Authorization call-back failed because there is no authorization in progress`
  - `TokenReferenceFilterApp3`: `No oauth2 session value found during response phase endpoint=/openid/app3`
  - `TokenReferenceFilterApp3`: `oauth2 entries found but no real tokens (likely pending state), skipping Redis offload endpoint=/openid/app3`
- Session observed during the app3 callback error still contained `token_ref_id_app4`, Jellyfin markers, and `oauth2:http://redmine-b.sso.local:9080/openid/app3`, but no `token_ref_id_app3`.

> [!warning]
> Stack B is still configured with a shared cookie-backed `JwtSession` (`IG_SSO_B`) across `redmine-b.sso.local` and `jellyfin-b.sso.local`. That means concurrent responses can overwrite each other at the browser cookie layer.

## Root cause hypothesis

### 1. Shared `JwtSession` cookie race is the most likely root cause

- `stack-b/openig_home/config/config.json` still uses `JwtSession` with `cookieName: IG_SSO_B` and `cookieDomain: .sso.local`.
- Both logout handlers clear the entire session (`session.clear()`), but that only affects the response currently being written.
- Any later response from an in-flight Redmine or Jellyfin request can still serialize an older session view back into the same browser cookie.

Most likely sequence:

1. Redmine logout clears the session and returns a cleared `IG_SSO_B`.
2. A concurrent Jellyfin or Redmine response finishes later and writes an older session image back into `IG_SSO_B`.
3. Redmine SSO2 starts and creates app3 pending OAuth state.
4. Another concurrent request writes a different cookie image, so the original app3 authorization state is gone by the time `/openid/app3/callback` arrives.
5. The callback fails with `there is no authorization in progress`.
6. A separate in-flight app3 request can then create a fresh pending `oauth2:...app3` entry, which explains why the key is visible after the callback failure even though the failing callback could not use it.

### 2. `TokenReferenceFilter` amplifies the race by rewriting the whole session

- `stripOauth2EntriesFromSession` snapshots session keys, calls `session.clear()`, then rebuilds the session with preserved entries plus the new token reference.
- On a cookie-backed session, that behavior means the winning response writes a full cookie image, not a small isolated delta.
- The app4 filter is correctly namespace-scoped, so it does not intentionally delete app3 keys. The risk is indirect: a late app4 response can write a stale cookie image that never contained the latest app3 pending state.

> [!tip]
> The current `isOauthCallback` guard for `/openid/app3/callback` looks correct. It should match the callback path and is not the likely failure point in this incident.

## Code paths inspected

- `stack-b/openig_home/config/config.json`
  - `JwtSession` is enabled for `IG_SSO_B` on `.sso.local`.
- `stack-b/openig_home/scripts/groovy/SloHandler.groovy`
  - Redmine logout calls `session.clear()`.
- `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
  - Jellyfin logout also calls `session.clear()`.
- `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
  - Request phase restore is skipped for callback paths.
  - Response phase skips Redis offload when only pending state exists.
  - `stripOauth2EntriesFromSession` still clears and rebuilds the entire session.
  - Offload still allocates a fresh UUID every time instead of reusing the existing `tokenRefKey`, which remains a concurrency risk for same-endpoint parallel flows.

## Answers to the investigation questions

1. Can app3 request phase overwrite/remove pending state before callback?
   - Not in the observed scenario. The app3 request phase only restores from Redis when `token_ref_id_app3` exists and the live app3 OAuth namespace is empty. It does not clear session state on request.
   - The destructive behavior is in response processing, not request processing.
2. Does `token_ref_id_app3` persist after SLO clears session?
   - No, not from the logout response itself. `SloHandler.groovy` clears the session after reading the `id_token`.
   - It can reappear only if a later concurrent response writes an older cookie image.
3. Can a concurrent Jellyfin response indirectly affect app3 pending state?
   - Yes. Because both apps share one cookie-backed session, a late app4 response can overwrite the browser cookie with a stale session image that lacks the latest app3 pending state.
4. Is `isOauthCallback` reliable for `/openid/app3/callback`?
   - Yes for this route. `request.uri.path?.contains(configuredClientEndpoint + '/callback')` should match `/openid/app3/callback`.
   - It is broad rather than exact, but it is not the most plausible cause of this failure.

## Proposed fix

### Primary fix

- Remove `JwtSession` from Stack B and fall back to server-side `ServletSession` / `JSESSIONID`.
- That eliminates the shared browser-cookie last-response-wins race across Redmine and Jellyfin.

### Secondary hardening

- Update `TokenReferenceFilter.groovy` to reuse the existing `tokenRefKey` value during offload instead of always generating a new UUID.
- If Redmine issues parallel unauthenticated non-HTML requests during auth bootstrap, add an auth guard pattern so those requests return `401` instead of triggering competing OIDC authorization flows.

> [!warning]
> Staying on shared cookie-backed `JwtSession` leaves this class of race fundamentally possible even if the app-specific `TokenReferenceFilter` logic is otherwise correct.

## Current state

- Investigation only. No gateway code was changed in this task.
- Most likely root cause: shared `JwtSession` cookie overwrite from concurrent responses, with app4 session data reappearing after logout and app3 pending state being replaced before callback completion.

## Files inspected

- `stack-b/openig_home/config/config.json`
- `stack-b/openig_home/config/routes/00-redmine-logout.json`
- `stack-b/openig_home/config/routes/00-jellyfin-logout.json`
- `stack-b/openig_home/config/routes/01-jellyfin.json`
- `stack-b/openig_home/config/routes/02-redmine.json`
- `stack-b/openig_home/scripts/groovy/SloHandler.groovy`
- `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
- `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
