---
title: Stack A JwtSession Default Resolution
tags:
  - debugging
  - stack-a
  - openig
  - jwt-session
  - oauth2
date: 2026-03-18
status: complete
---

# Stack A JwtSession Default Resolution

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]]

## Context

- Scope: read-only investigation for [[Stack A]] only.
- Symptom: OIDC login succeeds functionally, but browser receives `JSESSIONID` and app cookies only. `IG_SSO` is never issued.
- Question: does `OAuth2ClientFilter` automatically use heap object `JwtSession` when no `session` property is set in the filter config?

## Findings

- `stack-a/openig_home/config/config.json` defines a heap object named `JwtSession` of type `JwtSession`.
- `stack-a/openig_home/config/routes/01-wordpress.json` and `stack-a/openig_home/config/routes/02-app2.json` both use `OAuth2ClientFilter` with no route-level `session`.
- Stack B and Stack C route files follow the same pattern: `OAuth2ClientFilter` present, no route-level `session`.
- OpenIG 6 documentation states the default session producer is the heap object named `Session`.
- OpenIG 6 documentation also states a non-default JWT session must be selected at the top level or route level with `"session": "MyJwtSession"`.
- `OAuth2ClientFilter` documentation does not expose a `session` property in the filter config.

> [!warning]
> The missing piece is not `OAuth2ClientFilter.config.session`. The issue is that Stack A defines a `JwtSession` heap object but does not bind it as the route/global session provider.

## Root Cause

- OpenIG is not auto-binding heap object `JwtSession` as the default session manager just because the object exists.
- Because the heap object is named `JwtSession` instead of `Session`, and the routes do not declare `"session": "JwtSession"`, OpenIG falls back to the servlet-backed session implementation.
- That matches the observed runtime behavior: `JSESSIONID` exists, `IG_SSO` does not, and scripts still read/write `session[...]` successfully through the servlet session.

> [!success]
> The investigation conclusion is: the effective misconfiguration is missing route/global session binding, not a missing `session` field inside `OAuth2ClientFilter`.

## Fix Options

- Preferred global fix:
  - In `stack-a/openig_home/config/config.json`, rename heap object `JwtSession` to `Session`.
  - This makes the JWT session producer the default for all Stack A routes.
- Alternative route-scoped fix:
  - Add top-level `"session": "JwtSession"` to routes that need the shared OpenIG session, especially `01-wordpress.json` and `02-app2.json`.

## Exact Change

```json
{
  "name": "Session",
  "type": "JwtSession",
  "config": {
    "cookieName": "IG_SSO",
    "sessionTimeout": "30 minutes",
    "sharedSecret": "__JWT_SHARED_SECRET__",
    "keystore": "JwtKeyStore",
    "alias": "openig-jwt",
    "cookieDomain": ".sso.local",
    "password": "__KEYSTORE_PASSWORD__"
  }
}
```

Alternative route-scoped form:

```json
{
  "name": "wordpress-sso",
  "session": "JwtSession",
  "baseURI": "http://wordpress"
}
```

## Risk

- Low functional risk if Stack A switches from servlet session to JWT session correctly.
- Medium operational risk if JWT payload grows too large, because Stack A scripts store OIDC token material plus cached WordPress cookies in `session[...]`, and JWT session state is carried in the `IG_SSO` cookie.
- Existing servlet-backed sessions will not carry over after rollout; active browser sessions should be considered disposable during the change window.

> [!tip]
> For this lab, the safest fix surface is global default binding in `config.json`. It is smaller, consistent with OpenIG’s documented default-session model, and avoids repeating route-level `session` declarations everywhere.
