---
title: 2026-03-20 Stack C phpMyAdmin logout and 401 loop fix
tags:
  - debugging
  - stack-c
  - openig
  - phpmyadmin
  - logout
  - oidc
date: 2026-03-20
status: done
---

# 2026-03-20 Stack C phpMyAdmin logout and 401 loop fix

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Task: fix two Stack C phpMyAdmin regressions introduced after `4ab4865`
- Scope constraint: modify only `stack-c/openig_home/`
- Symptoms:
  - phpMyAdmin logout was broken
  - Firefox could hit an infinite redirect loop after re-auth when phpMyAdmin returned downstream `401`

## Root cause

> [!warning] phpMyAdmin logout route mismatch
> The dedicated route `[[00-phpmyadmin-logout]]` only intercepted `GET /?logout=1`.
> Live phpMyAdmin logout traffic actually used `POST /index.php?route=/logout`, followed by `GET /index.php?route=/&old_usr=<user>` and a backend `401`.

> [!warning] Failure handler logout detection was too narrow
> `PhpMyAdminAuthFailureHandler.groovy` only treated `logout` in the path or `logout=1` in the query as logout-shaped traffic.
> The real phpMyAdmin post-logout `401` carried `old_usr=` in the query instead, so the handler misclassified it as a generic auth failure.

> [!warning] Generic backend `401` recovery had no loop guard
> For non-logout `401`, the handler cleared app6 session state and redirected straight back to `http://phpmyadmin-c.sso.local:18080/`.
> If phpMyAdmin continued to reject the downstream credentials, OpenIG re-entered OIDC and repeated the same redirect forever.

> [!success] Credential injection chain was not the regression
> The phpMyAdmin Apache access log showed `alice` and `bob` usernames on the proxied `401` requests.
> That means [[OpenIG]] was already sending Basic Auth credentials to phpMyAdmin, so `VaultCredentialFilter.groovy` and `HttpBasicAuthFilter` were active on the initial request path.

## Fix

### 1. Expand the dedicated phpMyAdmin logout route

- File: `stack-c/openig_home/config/routes/00-phpmyadmin-logout.json`
- Added interception for `POST /index.php?route=/logout` in addition to `GET /?logout=1`
- Added `sessionCacheKey: oidc_sid_app6` to the route-local `TokenReferenceFilter`

### 2. Harden phpMyAdmin failure classification

- File: `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- Replaced single logout needles with explicit lists
- Added logout query detection for:
  - `logout=1`
  - `route=/logout`
  - `route=%2flogout`
  - `old_usr=`
- Added bounded retry args:
  - `_ig_pma_retry=1`

### 3. Stop the non-logout 401 redirect loop

- File: `stack-c/openig_home/scripts/groovy/PhpMyAdminAuthFailureHandler.groovy`
- Added list-based logout arg parsing
- Added request method awareness
- First non-logout `401` on `GET`/`HEAD`:
  - clear app6 session keys
  - redirect once to root with `_ig_pma_retry=1`
- Retry request or non-idempotent request with downstream `401`:
  - clear app6 session keys
  - return a terminal `502` HTML response instead of another redirect
- Logout-shaped `401` still redirects to Keycloak end-session

> [!tip]
> This keeps phpMyAdmin logout on the explicit SLO path while preventing a persistent downstream credential failure from turning into a browser-level redirect storm.

## Verification

- Restarted:
  - `stack-c-openig-c1-1`
  - `stack-c-openig-c2-1`
- Verified route load after restart:
  - `Loaded the route with id '00-phpmyadmin-logout' registered with the name '00-phpmyadmin-logout'`
  - `Loaded the route with id '11-phpmyadmin' registered with the name 'phpmyadmin-sso'`
- Smoke-tested gateway logout interception:
  - `GET http://phpmyadmin-c.sso.local:18080/?logout=1` returned `302` to Keycloak end-session
  - `POST http://phpmyadmin-c.sso.local:18080/index.php?route=/logout` returned `302` to Keycloak end-session

> [!success]
> The gateway now intercepts the real phpMyAdmin logout entrypoint instead of forwarding it to the backend logout/`old_usr` challenge path.

## Files changed

- `stack-c/openig_home/config/routes/00-phpmyadmin-logout.json`
- `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- `stack-c/openig_home/scripts/groovy/PhpMyAdminAuthFailureHandler.groovy`

## Current state

- Logout interception is fixed at the route layer
- phpMyAdmin logout-shaped `401` detection is broader and matches the observed `old_usr=` pattern
- Generic downstream `401` can no longer spin the browser in an unbounded redirect loop
- Full browser validation of a fresh logout and a real failed re-auth still remains the final end-to-end proof

## Audit follow-up

> [!success]
> Branch audit on `2026-03-20` confirmed `HEAD` at `cf027f1` with the expected `PhpMyAdminAuthFailureHandler` retry-guard changes present in [[OpenIG]] Stack C route and Groovy files.

- Working tree was not fully clean during the audit:
  - only untracked Obsidian notes were present
  - no modified tracked files were present
- `fix/jwtsession-production-pattern...HEAD` adds:
  - this phpMyAdmin fix note
  - a merge/debug note
  - `PhpMyAdminAuthFailureHandler.groovy`
  - `sessionCacheKey` wiring in `TokenReferenceFilter` route args
  - phpMyAdmin logout route expansion and array-based logout detection
- The same branch diff also shows `stack-c/openig_home/config/config.json` replacing placeholder secrets with concrete values, which should be reviewed before any broader merge or release decision.

> [!warning]
> `docs/fix-tracking/master-backlog.md` does not currently contain a dedicated phpMyAdmin SLO regression item marked `DONE`.

- Audit readiness result:
  - branch logic checks passed
  - conversation handoff is still blocked by the dirty working tree and missing explicit backlog tracking for this regression
