---
title: Stack B Redmine Cookie Pass-Through JwtSession Overflow Fix
tags:
  - debugging
  - stack-b
  - openig
  - redmine
  - jwt-session
  - cookies
date: 2026-03-18
status: complete
---

# Stack B Redmine Cookie Pass-Through JwtSession Overflow Fix

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack B]]

## Context

- Scope: Stack B only.
- Target file: `stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy`
- Symptom: the old Redmine injector stored full Redmine session cookies in `session['redmine_session_cookies']`.
- Risk: with [[OpenIG]] `JwtSession`, serialized Redmine cookies increase the `IG_SSO_B` payload and can push the JWT cookie over browser/header size limits.
- Reference pattern: Stack A `CredentialInjector.groovy` had already been refactored to browser cookie pass-through with only small session markers retained.

> [!warning]
> Caching full upstream app cookies inside [[OpenIG]] `JwtSession` is not safe for apps that issue large or multiple cookies. The cookie value becomes part of the JWT session payload and grows every browser request header.

## Root Cause

- Stack B Redmine SSO used server-side caching of `_redmine_session` in `session['redmine_session_cookies']`.
- That design assumed session state lived server-side, but Stack B now uses cookie-backed `JwtSession`.
- Redmine session cookies do not need to live inside the JWT. The browser can hold them directly if OpenIG rewrites the `Set-Cookie` domain from the internal host to the public host.

## Change

- Removed legacy use of `session['redmine_session_cookies']` and added cleanup to delete it when encountered.
- Kept the existing Redmine-specific login handshake:
  - `GET http://redmine:3000/login`
  - parse CSRF token from the HTML
  - `POST http://redmine:3000/login` with Vault-provided `login` and `password`
- Added browser cookie pass-through logic modeled on Stack A:
  - reuse browser Redmine cookies only when `session['redmine_user_sub'] == attributes.openid['user_info']['sub']`
  - otherwise perform a fresh Redmine login
  - store only `session['redmine_user_sub']` and `session['redmine_cookie_names']`
- Rewrote successful Redmine `Set-Cookie` headers to:
  - `Domain=redmine-b.sso.local`
  - `SameSite=Lax`
- Returned rewritten cookies with `response.headers.add('Set-Cookie', ...)` so multiple cookies are preserved.
- Added expiry handling for upstream redirects back to `/login`:
  - clear browser-held Redmine cookies with `Max-Age=0`
  - redirect the browser to retry via `CANONICAL_ORIGIN_APP3`

> [!success]
> Stack B now follows the same session-size-safe pattern as [[Stack A]]: browser keeps Redmine cookies, [[OpenIG]] keeps only small identity markers.

## Verification

- Restarted `sso-b-openig-1` and `sso-b-openig-2` on `2026-03-18`.
- Checked `docker logs sso-b-openig-1 2>&1 | grep -E 'Loaded the route|ERROR|WARN' | tail -20`.
- Result:
  - route `02-redmine` loaded successfully after restart
  - no new Groovy compile/runtime errors from the updated injector
  - remaining warnings were pre-existing `Router` heap-name noise plus JDK `sun.misc.Unsafe` deprecation warnings

## Files Changed

- `stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy`

## Current State

- Requested code commit created: `cdb5425`
- Commit message: `fix: Stack B RedmineCredentialInjector — cookie pass-through to fix JwtSession overflow`
- Redmine SSO no longer stores full Redmine cookies in [[OpenIG]] `JwtSession`

## Next Steps

- Validate end-to-end in a browser with a real Stack B login:
  - first request should trigger Redmine form login and return rewritten browser cookies
  - subsequent requests should reuse browser cookies without growing `IG_SSO_B`
  - expired Redmine cookies should be cleared automatically and retried through the canonical origin

> [!tip]
> If the same overflow pattern still exists elsewhere, search for any app-specific `session[...]` field that stores raw upstream cookies or tokens instead of small markers.
