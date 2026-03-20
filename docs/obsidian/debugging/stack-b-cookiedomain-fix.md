---
title: Stack B CookieDomain Fix
tags:
  - stack-b
  - cookie
  - openig
date: 2026-03-12
status: fixed
---

# Stack B CookieDomain Fix

Related: [[Stack B]] [[OpenIG]]

## Root cause

Missing `cookieDomain` in `JwtSession` caused the session cookie to be scoped per subdomain.
When switching between `redmine-b` and `jellyfin-b` subdomains, OpenIG could not consistently reuse the same session, leading to an extra redirect.

## Fix

Updated `stack-b/openig_home/config/config.json` in `JwtSession.config`:

- Added `"cookieDomain": ".sso.local"` at the same level as `cookieName`.

> [!success]
> Cookie is now shared across `.sso.local` subdomains.
