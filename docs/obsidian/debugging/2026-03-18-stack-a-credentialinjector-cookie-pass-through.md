---
title: Stack A CredentialInjector cookie pass-through refactor
tags:
  - debugging
  - openig
  - stack-a
  - groovy
  - cookies
date: 2026-03-18
status: complete
---

# Stack A CredentialInjector cookie pass-through refactor

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]]

## Context

- [stack-a/openig_home/scripts/groovy/CredentialInjector.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/CredentialInjector.groovy) cached `session['wp_session_cookies']` inside the OpenIG `JwtSession`.
- The encrypted gateway session grew to about `7051 chars`, which exceeded the 4KB cookie limit and caused a login redirect loop in [[Stack A]] WordPress.
- The route order already guaranteed OIDC state and Vault credentials were available before `CredentialInjector.groovy` ran, so the correct fix was to move WordPress session ownership back to the browser.

## Root cause

- The previous implementation treated WordPress app cookies as gateway session data.
- In OpenIG HA mode that meant every WordPress login cookie was serialized into the encrypted `IG_SSO` cookie.
- WordPress session cookies are the wrong payload class for `JwtSession`; only small gateway markers should be stored there.

## Changes made

- Removed the legacy `session['wp_session_cookies']` usage and added one-time cleanup for that field when an old session hits the script.
- Added browser cookie inspection based on the incoming `Cookie` header and the current OIDC user marker `attributes.openid['user_info']['sub']`.
- Reused browser-held WordPress cookies only when a `wordpress_logged_in_*` cookie is present and `session['wp_user_sub']` matches the current user `sub`.
- Kept only small markers in `JwtSession`:
  - `session['wp_user_sub']`
  - `session['wp_cookie_names']`
- Preserved the existing form-login flow to `http://wordpress/wp-login.php` when no reusable browser cookies exist or the stored `sub` belongs to another user.
- Rewrote downstream `Set-Cookie` headers to `Domain=wp-a.sso.local` and forced `SameSite=Lax`, then added them back to the browser response with `response.headers.add('Set-Cookie', ...)`.
- Injected the fresh WordPress cookies into the current upstream request so the first proxied request after login succeeds without waiting for the next browser round-trip.
- On a WordPress redirect back to `wp-login.php`, cleared browser cookies named in `session['wp_cookie_names']` with `Max-Age=0` and redirected the browser back to the current canonical URL.

> [!success]
> The JwtSession payload now keeps only OIDC state plus small WordPress markers instead of the full WordPress cookie jar.

> [!warning]
> The architecture note mentioned `attributes.vault_creds`, but Stack A currently uses `attributes.wp_credentials` from `VaultCredentialFilter.groovy`. The refactor preserved the live producer/consumer contract and only changed the WordPress cookie handling.

## Verification

- Restarted `sso-openig-1` and `sso-openig-2`.
- Confirmed both containers came back healthy.
- Checked `docker logs --since 2m sso-openig-1 2>&1 | grep -E 'Loaded the route|ERROR|WARN' | tail -50`.
- Verified Stack A route load after restart:
  - `Loaded the route with id '01-wordpress' registered with the name 'wordpress-sso'`
- Observed one existing generic OpenIG startup warning:
  - heap object name `Router` transformed to URL-friendly name `router`
- Did not observe new Groovy parse errors or route-load failures after restart.

## Current state

- [[Stack A]] OpenIG is running with the cookie pass-through implementation in place.
- The oversized `wp_session_cookies` field is no longer written into `JwtSession`.
- Browser-managed WordPress cookies are now the source of truth for the downstream app session.

## Files changed

- [stack-a/openig_home/scripts/groovy/CredentialInjector.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/CredentialInjector.groovy)
