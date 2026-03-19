---
title: 2026-03-19 Stack C TokenReferenceFilter Diagnosis
tags:
  - debugging
  - openig
  - redis
  - jwt-session
  - stack-c
date: 2026-03-19
status: in-progress
---

# 2026-03-19 Stack C TokenReferenceFilter Diagnosis

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Request: diagnose why [[Stack C]] still logs `JWT session is too large` after `TokenReferenceFilter.groovy` was added to Grafana.
- Scope requested: inspect `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`, inspect `stack-c/openig_home/config/routes/10-grafana.json`, add temporary debug logging for session keys, and verify what the actual OAuth session key is.

## What was verified

- `TokenReferenceFilterApp5` is wired before `OidcFilterApp5` in the Grafana route chain, so the filter is not positioned after `OAuth2ClientFilter`.
- Existing mounted route logs already show `[TokenRef] Stored oauth2 session ...` immediately before `SessionFilter` throws `JWT session is too large`.
- That proves:
  - the filter is running
  - the `.then()` response path is running
  - Redis write-offload succeeds at least for the logged requests
- Current filter logic still assumes candidate keys only from:
  - `oauth2:/openid/app5`
  - `oauth2:http://<host>:<port>/openid/app5`
  - `oauth2:http://<host>/openid/app5`
  - `oauth2:${OPENIG_PUBLIC_URL}/openid/app5`

> [!warning]
> Docker daemon access and local HTTP access were blocked in this Codex sandbox, so the container could not be restarted here and no fresh request could be generated to produce the new `phase=` session-key dump lines.

## Temporary diagnostics added

- Added `dumpSessionKeys(phase)` to log all session keys, `oauth2*` keys, candidate keys, and `token_ref_id`.
- Added logs on:
  - request entry
  - after primary-key aliasing
  - after Redis restore
  - start of `.then()`
  - after `removeOauth2SessionValues()`
- Added explicit `.then()` and missing-oauth2 warnings.

> [!tip]
> The new diagnostics live only in `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy` and are intended to identify the exact session key set before changing the Redis logic.

## Evidence from current logs

- `stack-c/openig_home/logs/route-10-grafana.log` contains:
  - `[TokenRef] Stored oauth2 session for endpoint=/openid/app5 token_ref_id=...`
  - followed immediately by `SessionFilter | Failed to save session`
  - with cookie size `5201 chars`
- Earlier failures in the same log were `4803 chars`, so the session remained oversized even after the filter reported a successful store.

> [!success]
> The route log is enough to rule out two hypotheses already: wrong filter position and filter not running at all.

## Current diagnosis

- Most likely root cause: the filter is not removing the actual large session entry that `OAuth2ClientFilter` saves, or it is removing only one of several OAuth-related keys.
- Less likely root cause: `session.remove()` is not mutating the saved session. This is less likely because existing Groovy filters in this lab rely on session mutation (`session[...] = ...`, `session.clear()`), and the response-path log proves the filter can see the post-OIDC session state.
- A separate design risk also exists in the current code: `restoreOauth2SessionValue()` mirrors the restored blob into every candidate `oauth2*` key, which can duplicate large payloads if more than one key variant is retained in the session.

## Proposed fix after key dump confirmation

1. Capture the real session key list from the new `phase=` logs on a live login.
2. Change `TokenReferenceFilter.groovy` to offload and restore only the exact key or keys observed in the session, instead of guessing and mirroring across multiple host variants.
3. Remove the primary-alias and multi-key restore behavior unless a specific downstream script proves it is required.
4. Keep only lightweight keys such as `token_ref_id` and `oidc_sid_app5` in `JwtSession`.

## Files changed

- `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `docs/obsidian/debugging/2026-03-19-stack-c-tokenreferencefilter-diagnosis.md`
