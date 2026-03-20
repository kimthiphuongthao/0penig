---
title: All Stacks JwtSession Server Side Session Fallback
tags:
  - debugging
  - openig
  - jwt-session
  - oauth2
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-18
status: complete
---

# All Stacks JwtSession Server Side Session Fallback

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Symptom: fresh private-window logins failed across all stacks after the JwtSession refactor.
- Scope: gateway-only fix in OpenIG `config.json` for [[Stack A]], [[Stack B]], and [[Stack C]].
- Constraint: do not modify target apps; only OpenIG gateway config is in scope.

> [!warning]
> Fresh sessions were still failing because the problem was not stale cookies. The callback flow itself was exceeding the 4 KB cookie limit during OAuth2 session persistence.

## Root Cause

- `stack-a/openig_home/config/config.json`
- `stack-b/openig_home/config/config.json`
- `stack-c/openig_home/config/config.json`

All three stacks were configured to use `JwtSession`, which stores the OpenIG session in a client cookie. During the OAuth2 callback, `OAuth2ClientFilter` persisted the authorization state plus the full token response in session before downstream cleanup filters could run.

That made the serialized session exceed the browser cookie ceiling even on a brand-new login:

- [[Stack A]] logged `JWT session is too large (7051 chars)` and later `6993 chars`
- [[Stack B]] logged `JWT session is too large (6993 chars)`
- [[Stack C]] did not show the same overflow in the sampled fresh logs, but it shared the same fragile session architecture

The WordPress and Redmine injectors were not the main cause in the current code path. Post-refactor logs showed the injector storing small markers only, and post-restart [[Stack A]] login attempts reached `[CredentialInjector] WP login OK` without Groovy runtime failures.

## Fix Applied

- Removed the explicit `JwtSession` heap object from all three stack `config.json` files.
- Removed the unused JWT keystore heap object from the same files.
- Left each stack with only the `Router` heap object so OpenIG falls back to Tomcat server-side `ServletSession`.

> [!success]
> After the change, initial app requests issue `JSESSIONID` instead of `IG_SSO*`, which moves the OAuth2 session state off the browser cookie and avoids the 4 KB overflow.

## Verification

- Restarted the affected OpenIG containers for all nodes:
  - `sso-openig-1`
  - `sso-openig-2`
  - `sso-b-openig-1`
  - `sso-b-openig-2`
  - `stack-c-openig-c1-1`
  - `stack-c-openig-c2-1`
- Read container logs using each container's exact `StartedAt` timestamp.
- Confirmed no fresh `JwtCookieSession.save` or `SessionFilter Failed to save session` errors after restart.
- Confirmed route loading is clean in all sampled nodes.
- Confirmed no fresh Groovy `NullPointerException` or `ClassCastException` entries in the post-restart windows.
- Confirmed post-fix callback behavior changed from `there is no authorization in progress` to state mismatch / `invalid_grant Code not valid` during synthetic callback checks, which shows the authorization session now survives the round-trip.
- Probed live entrypoints and confirmed current unauthenticated behavior is `302` to Keycloak with `Set-Cookie: JSESSIONID=...` for:
  - `http://wp-a.sso.local/`
  - `http://redmine-b.sso.local:9080/`
  - `http://grafana-c.sso.local:18080/`

## Current State

- [[Stack A]]: post-restart logs show WordPress injector activity and no fresh JWT-size failures.
- [[Stack B]]: post-restart logs show clean route load plus expected synthetic `invalid_grant` callback noise, with no fresh JWT-size failures.
- [[Stack C]]: post-restart logs show clean route load and no JWT-size failures in the sampled window.

> [!tip]
> All three nginx upstreams already use `ip_hash`, so server-side session stickiness is compatible with the current topology.

> [!warning]
> [[Stack C]] still showed a separate phpMyAdmin Vault issue in earlier logs: `Vault AppRole login failed with HTTP 503`. That is independent of the JwtSession overflow fix and was not changed here.

## Files Changed

- `stack-a/openig_home/config/config.json`
- `stack-b/openig_home/config/config.json`
- `stack-c/openig_home/config/config.json`
