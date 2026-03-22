---
title: All Stacks SEC-COOKIE-STRIP and SpaAuthGuardFilter Race Fix
tags:
  - debugging
  - security
  - openig
  - grafana
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-22
status: done
---

# Context

Applied a cross-stack hardening fix in [[OpenIG]] to stop forwarding gateway session cookies (`IG_SSO`, `IG_SSO_B`, `IG_SSO_C`) to upstream apps, and fixed the unauthenticated XHR redirect race on [[Stack C]] Grafana.

The Grafana SPA was polling `/api/login/ping` before a valid oauth2 session existed. Because those requests were JSON/XHR and not full-page HTML navigations, letting them reach `OidcFilterApp5` caused multiple concurrent `sendAuthorizationRedirect()` executions and conflicting OIDC `state` values in Redis and browser cookies.

## What Changed

- Stack A: wired cookie stripping for App2 route and added `StripGatewaySessionCookies.groovy`.
- Stack B: updated injector scripts so forwarded backend requests no longer leak gateway session cookie state.
- Stack C: wired cookie stripping for Grafana and phpMyAdmin routes, added `StripGatewaySessionCookies.groovy`, and inserted `SpaAuthGuardFilter.groovy` between `TokenReferenceFilterApp5` and `OidcFilterApp5`.

## Technical Result

> [!success]
> Confirmed fix after full SSO/SLO validation: 4 XHR requests were blocked with HTTP 401 and no `state parameter unexpected value` errors were observed.

`SpaAuthGuardFilter` now detects requests that are effectively AJAX/XHR (`Accept: application/json`, not `text/html`) and, when no active oauth2 session is present, returns `401` instead of triggering another OIDC redirect.

This preserves normal browser redirect behavior for interactive HTML requests while preventing SPA background polling from racing the login flow in [[Keycloak]].

## Files Changed

- `stack-a/openig_home/config/routes/02-app2.json`
- `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
- `stack-a/openig_home/scripts/groovy/StripGatewaySessionCookies.groovy`
- `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy`
- `stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy`
- `stack-c/openig_home/config/routes/10-grafana.json`
- `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- `stack-c/openig_home/scripts/groovy/SpaAuthGuardFilter.groovy`
- `stack-c/openig_home/scripts/groovy/StripGatewaySessionCookies.groovy`

## Notes

> [!warning]
> Any SPA endpoint that polls JSON APIs before an authenticated browser session exists can recreate this class of OIDC race if it is allowed to fall through to a redirect-oriented auth filter.

> [!tip]
> Reuse this pattern for future [[OpenIG]] SPA integrations: strip gateway cookies before proxying, and short-circuit unauthenticated XHR/API traffic with `401` instead of issuing an authorization redirect.

The Redis-backed OIDC state flow remains valid; the bug was concurrent state issuance, not a [[Vault]] or Keycloak signing problem.
