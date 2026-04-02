---
title: BUG-002 nginx OAuth2 callback retry fix
tags:
  - debugging
  - nginx
  - openig
  - oauth2
  - stack-a
  - stack-b
  - stack-c
date: 2026-04-02
status: done
---

# BUG-002 nginx OAuth2 callback retry fix

Related: [[OpenIG]] [[Keycloak]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

The shared nginx layer proxies six application vhosts through `openig_pool`. Each vhost catch-all `location /` enabled `proxy_next_upstream`, which meant an OAuth2 callback request to `/openid/appN/callback` could be retried on the second OpenIG node if the first upstream attempt failed or timed out.

Because the authorization code in the callback is single-use, that retry path could replay the same code against a different OpenIG node and surface `invalid_grant`, breaking SSO even though the initial redirect flow was otherwise valid.

## Root cause

`shared/nginx/nginx.conf` handled OAuth2 callbacks as generic proxied traffic. The config already protected backchannel logout endpoints with `proxy_next_upstream off`, but the `/openid/appN/callback` endpoints had no equivalent retry guard.

> [!warning]
> Scope stayed inside the approved gateway layer only. No target application config, database config, or [[Keycloak]] config was modified.

## What changed

- `shared/nginx/nginx.conf`

Added the same callback-specific location block before each app `location /` block for:

- `wp-a.sso.local`
- `whoami-a.sso.local`
- `redmine-b.sso.local`
- `jellyfin-b.sso.local`
- `grafana-c.sso.local`
- `phpmyadmin-c.sso.local`

The new block matches `^/openid/app[0-9]+/callback$`, disables upstream retry with `proxy_next_upstream off`, and still proxies the callback to `http://openig_pool` with the standard host and client IP headers.

> [!tip]
> OAuth2 authorization codes are single-use by design. If a reverse proxy retries the same callback to another node, `invalid_grant` is an expected failure mode rather than a Keycloak bug.

## Verification

> [!success]
> `git diff -- shared/nginx/nginx.conf` showed the six callback retry guards were already the pending gateway-side change in the worktree before validation.

> [!success]
> `docker exec shared-nginx nginx -t` returned `syntax is ok` and `test is successful`.

> [!success]
> `docker exec shared-nginx nginx -s reload` returned `signal process started`.

> [!success]
> `docker logs shared-nginx 2>&1 | tail -10` showed recent access logs and no nginx reload errors immediately after the config update.

## Current state

OAuth2 callback paths `/openid/appN/callback` are now non-retryable across all six shared nginx application vhosts, while the existing retry behavior for normal application traffic under `location /` remains unchanged.

Confirmed callback guard locations in `shared/nginx/nginx.conf`:

- `wp-a.sso.local`: lines 44-50
- `whoami-a.sso.local`: lines 78-84
- `redmine-b.sso.local`: lines 140-146
- `jellyfin-b.sso.local`: lines 202-208
- `grafana-c.sso.local`: lines 252-258
- `phpmyadmin-c.sso.local`: lines 300-306

## Files changed

- `shared/nginx/nginx.conf`
