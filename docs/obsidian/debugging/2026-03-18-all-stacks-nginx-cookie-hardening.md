---
title: All Stacks Nginx Cookie Hardening
tags:
  - debugging
  - security
  - nginx
  - openig
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-18
status: done
---

# All Stacks Nginx Cookie Hardening

Related: [[OpenIG]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Implemented `[M-4/S-8]` from `.omc/plans/phase2-security-hardening.md`.
- Scope was limited to the nginx reverse-proxy layers for all three stacks.
- Constraint was explicit: change only main `location /` blocks, leave backchannel logout blocks unchanged, and do not add `Secure` because the lab runs over HTTP.

## What Changed

- Added `proxy_cookie_flags IG_SSO SameSite=Lax;` to the main `/` locations for `whoami-a.sso.local` and `wp-a.sso.local`.
- Added `proxy_cookie_flags IG_SSO_B SameSite=Lax;` to the main `/` locations for `openigb.sso.local`, `redmine-b.sso.local`, and `jellyfin-b.sso.local`.
- Added `proxy_cookie_flags IG_SSO_C SameSite=Lax;` to the main `/` locations for `grafana-c.sso.local` and `phpmyadmin-c.sso.local`.

> [!success]
> The cookie hardening directives were inserted at the required positions, immediately after `proxy_next_upstream_tries` in each allowed main proxy block.

## Decisions

- Kept `SameSite=Lax` only. This is safe for the HTTP lab and matches the task requirement to avoid adding `Secure`.
- Did not touch any `location = /openid/.../backchannel_logout` block.
- Did not add the directive to `openiga.sso.local` because the task explicitly scoped Stack A to `wp-a.sso.local` and `whoami-a.sso.local`.

## Current State

- Files changed:
  - `stack-a/nginx/nginx.conf`
  - `stack-b/nginx/nginx.conf`
  - `stack-c/nginx/nginx.conf`
- Directive locations:
  - `stack-a/nginx/nginx.conf:52`
  - `stack-a/nginx/nginx.conf:92`
  - `stack-b/nginx/nginx.conf:72`
  - `stack-b/nginx/nginx.conf:137`
  - `stack-b/nginx/nginx.conf:199`
  - `stack-c/nginx/nginx.conf:59`
  - `stack-c/nginx/nginx.conf:99`

> [!warning]
> Requested container validation could not be executed from this Codex session because Docker socket access is blocked by the sandbox. The attempted `docker exec`, `docker restart`, and `docker logs` commands failed before reaching the containers with `permission denied while trying to connect to the Docker daemon socket`.

## Next Steps

1. Run `docker exec sso-nginx nginx -t`, `docker exec sso-b-nginx nginx -t`, and `docker exec stack-c-nginx-c-1 nginx -t` from a shell with Docker daemon access.
2. Restart `sso-nginx`, `sso-b-nginx`, and `stack-c-nginx-c-1`.
3. Check the last 20 log lines for each container to confirm there are no nginx startup warnings or errors after reload.
