---
title: 2026-03-18 Stack A WordPress redirect loop after CredentialInjector refactor
tags:
  - debugging
  - stack-a
  - openig
  - wordpress
  - keycloak
  - jwtsession
date: 2026-03-18
status: complete
---

# 2026-03-18 Stack A WordPress redirect loop after CredentialInjector refactor

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[WordPress]]

## Context

- User-reported symptom: after OIDC login on [[Stack A]], Firefox reports an infinite redirect loop instead of landing in [[WordPress]].
- Scope for this investigation was read-only: inspect recent logs from both OpenIG nodes and review the current `CredentialInjector.groovy` flow.
- No target application code or config was modified.

## What found

- `sso-openig-1` still logs fresh `SessionFilter` failures after the latest restart:
  - `JWT session is too large (7051 chars)`
- `sso-openig-2` showed only startup warnings and no Stack A request activity in the inspected window.
- The current Stack A route runs `OidcFilter` before `WpSessionInjector`, so the legacy `wp_session_cookies` cleanup in `CredentialInjector.groovy` happens too late for `/openid/app1` callback handling.
- The refactored `CredentialInjector.groovy` also has a second loop risk in the response hook:
  - when WordPress returns `302` to `wp-login.php`, the script returns a retry redirect before appending `pendingBrowserSetCookies`
  - that drops fresh browser cookies and can trap the browser in `original URL -> wp-login.php -> original URL`

> [!warning]
> The live runtime evidence points first to an oversized OpenIG JWT session still breaking callback/session persistence on `sso-openig-1`. The response-hook issue is code-level and high-confidence, but it was not directly logged in the current post-restart window.

> [!tip]
> The two issues are related but distinct:
> 1. legacy session cleanup is placed after `OidcFilter`
> 2. fresh browser cookies are skipped on the `wp-login.php` retry branch

## Root cause

- Primary observed cause:
  - [[OpenIG]] still receives a JWT session large enough to fail save (`7051 chars`), so the login flow can loop before the current `CredentialInjector.groovy` logic takes control.
  - Inference: the legacy `wp_session_cookies` removal is in `CredentialInjector.groovy`, but `OidcFilter` owns `/openid/app1` and can try to persist the session before that cleanup runs.
- Secondary code regression:
  - `CredentialInjector.groovy` Step 6 returns `retryResp` for `wp-login.php` redirects before adding `pendingBrowserSetCookies`.
  - Inference: if WordPress redirects to login after an internal login POST, the browser never receives the refreshed WordPress cookies and will loop.

## Proposed fix shape

- Move legacy session cleanup to run before `OidcFilter` on Stack A routes, or otherwise invalidate/rotate the old JWT session cookie so callback handling no longer tries to save the oversized legacy payload.
- Keep the fix inside gateway files only: [[OpenIG]] route JSON / Groovy filters.
- In `CredentialInjector.groovy`, ensure the `wp-login.php` retry response also carries `pendingBrowserSetCookies`, or gate that redirect rewrite so it does not swallow the just-issued WordPress cookies.

> [!success]
> The proposed remediation stays within the no-target-app rule because it only touches Stack A [[OpenIG]] route/filter behavior.

## Current state

- Investigation only. No runtime or code changes applied.
- The live node evidence still shows `JWT session is too large` on `sso-openig-1`.
- The current Stack A gateway code contains a separate redirect-loop hazard in the Step 6 response hook.

## Files reviewed

- `stack-a/openig_home/config/routes/01-wordpress.json`
- `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`

## Files changed

- `docs/obsidian/debugging/2026-03-18-stack-a-wordpress-redirect-loop-after-credentialinjector-refactor.md`
