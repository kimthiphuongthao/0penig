---
title: 2026-03-20 Stack C phpMyAdmin SLO fix verification blocked by sandbox
tags:
  - debugging
  - stack-c
  - openig
  - phpmyadmin
  - logout
  - oidc
  - sandbox
date: 2026-03-20
status: blocked
---

# 2026-03-20 Stack C phpMyAdmin SLO fix verification blocked by sandbox

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Branch: `fix/phpmyadmin-slo-regression`
- Goal: verify the uncommitted Stack C phpMyAdmin logout and retry-loop fix, then commit and restart OpenIG
- Scope checked:
  - `stack-c/openig_home/config/routes/00-phpmyadmin-logout.json`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json`
  - `stack-c/openig_home/scripts/groovy/PhpMyAdminAuthFailureHandler.groovy`

## Verified logic

> [!success]
> The current working tree matches the intended fix shape.

- `00-phpmyadmin-logout.json`
  - intercepts `GET /?logout=1`
  - intercepts `GET|POST /index.php?route=/logout`
  - passes `sessionCacheKey: oidc_sid_app6` into `TokenReferenceFilter`
- `11-phpmyadmin.json`
  - passes `logoutPathNeedles: ["logout"]`
  - passes `logoutQueryNeedles: ["logout=1", "route=/logout", "route=%2flogout", "old_usr="]`
  - passes `retryQueryParam: "_ig_pma_retry"` and `retryQueryValue: "1"`
- `PhpMyAdminAuthFailureHandler.groovy`
  - classifies logout traffic from either the path needle or any configured query needle
  - treats `_ig_pma_retry=1` as the retry marker
  - clears app session keys before retry redirect or terminal failure
  - redirects first non-logout `401` once with `_ig_pma_retry=1`
  - returns `502` on retry or non-idempotent downstream `401`
  - clears the full session and redirects logout-shaped `401` traffic to Keycloak end-session

## Blockers

> [!warning]
> This sandbox can read and write workspace files, but it cannot write into `.git` or access the Docker daemon.

- `git add` / `git commit`
  - blocked by `fatal: Unable to create '.git/index.lock': Operation not permitted`
- `docker restart stack-c-openig-c1-1 stack-c-openig-c2-1`
  - blocked by Docker socket permission denial at `/Users/duykim/.docker/run/docker.sock`
- `docker logs ...`
  - not runnable for the same reason

## Current state

- The three Stack C fix files remain modified in the working tree
- No commit was created in this session
- No restart or runtime log verification was possible in this session

> [!tip]
> Run the git commit and Docker restart from a shell with local `.git` and Docker socket access to finish deployment validation.
