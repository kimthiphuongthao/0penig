---
title: Stack C phpMyAdmin SLO Log Monitor
tags:
  - debugging
  - stack-c
  - openig
  - phpmyadmin
  - vault
date: 2026-03-20
status: partial
---

# Stack C phpMyAdmin SLO Log Monitor

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Requested action: monitor Stack C OpenIG logs during a live phpMyAdmin SSO/SLO browser test on `http://phpmyadmin-c.sso.local:18080`.
- Target containers: `stack-c-openig-c1-1` and `stack-c-openig-c2-1`.
- Requested evidence: last 100 log lines from both OpenIG containers, filtered for phpMyAdmin/app6 auth, logout, token reference, blacklist, and error lines.

## Findings

- Direct `docker logs` access was blocked in this Codex sandbox because the Docker daemon socket was not reachable.
- Fallback evidence was available through the shared OpenIG volume at `stack-c/openig_home/logs/`.
- Current phpMyAdmin login activity was visible at `16:14:31` to `16:14:38`.
- `TokenReferenceFilter` stored and restored app6 oauth2 session state multiple times during that window.
- `VaultCredentialFilter` failed repeatedly while fetching phpMyAdmin credentials from Vault with `HTTP 503`.
- No current front-channel phpMyAdmin logout lines were present for the `16:14` to `16:15` session window.
- A backchannel logout for app6 did fire at `16:15:17` and completed Redis blacklist update successfully.
- No re-login activity appeared after the `16:15:17` backchannel logout in the current phpMyAdmin route log.

> [!success]
> Backchannel logout executed for app6 at `16:15:17` and reached `Redis blacklist updated successfully`.

> [!warning]
> The current session has no matching `SloHandler` or `PhpMyAdminAuthFailureHandler` entries, so the browser-side logout route was either not hit or not observable from the shared route logs.

> [!warning]
> The strongest current failure signal is not OAuth or logout classification. It is repeated `Vault AppRole login failed with HTTP 503` during phpMyAdmin access.

## Evidence

- `route-11-phpmyadmin.log`
  - `16:14:31.958` stored app6 token reference with `sidCached=false`
  - `16:14:37.020` restored app6 token reference
  - `16:14:37.266` Vault credential fetch failed with `HTTP 503`
  - `16:14:37.994` Vault credential fetch failed again
  - `16:14:38.140` Vault credential fetch failed again
  - `16:14:38.176` Vault credential fetch failed again
- `route-00-backchannel-logout-app6.log`
  - `16:15:17.276` backchannel logout started
  - `16:15:17.439` JWT validations passed
  - `16:15:17.443` Redis blacklist updated successfully
- `route-00-phpmyadmin-logout.log`
  - latest entries are older at `15:47:27`
  - those entries show `SloHandler` without `id_token_hint`, not the current session

## Analysis

- Logout classification:
  - No current evidence that phpMyAdmin logout triggered a browser-side `logoutRequest=true` path.
  - No current evidence that it was misclassified as a non-logout 401 either.
  - The current session only shows backchannel logout, not front-channel logout.
- Token restore ordering:
  - `TokenReferenceFilter` restore definitely occurred before the later backchannel logout.
  - There is no current `SloHandler` line to prove restore-before-`SloHandler` ordering for a front-channel logout.
- Loop behavior:
  - The current session shows repeated app6 route re-entry with token restore/store followed by Vault failure.
  - This looks like repeated protected-resource attempts after credential injection failure, not a confirmed logout redirect loop.
- Errors:
  - Current session error is repeated Vault failure, not OAuth callback failure.
  - No `OidcFilterApp6` lines were found in the shared logs for this session.

## Current State

- Raw per-container `docker logs`: blocked in this session
- Shared OpenIG route logs: available and analyzed
- phpMyAdmin login path: reached app6 token restore/store
- phpMyAdmin credential injection: failing against Vault with `HTTP 503`
- phpMyAdmin front-channel logout visibility: absent in current session
- phpMyAdmin backchannel logout: confirmed
- phpMyAdmin re-login after logout: not observed

## Next Steps

1. Re-run the browser logout while someone with Docker access captures `docker logs stack-c-openig-c1-1 --tail 100` and `docker logs stack-c-openig-c2-1 --tail 100` at the same time.
2. Check Stack C Vault availability and AppRole login health before repeating phpMyAdmin login or re-login tests.
3. If browser logout is expected, add or increase phpMyAdmin failure-handler logging around logout classification so `logoutRequest=true/false` is emitted directly in a shared route log.

## Files Changed

- `docs/obsidian/debugging/2026-03-20-stack-c-phpmyadmin-slo-log-monitor.md`
