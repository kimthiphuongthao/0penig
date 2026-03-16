---
title: JwtSession timeout, Redis TTL, and logout verification
tags:
  - debugging
  - openig
  - keycloak
  - redis
date: 2026-03-16
status: complete
---

# JwtSession timeout, Redis TTL, and logout verification

Verified current runtime behavior across [[OpenIG]], [[Keycloak]], and Redis for [[Stack A]], [[Stack B]], and [[Stack C]].

## Findings

- `JwtSession.sessionTimeout` is `8 hours` in all three stacks:
  - `stack-a/openig_home/config/config.json:24`
  - `stack-b/openig_home/config/config.json:25`
  - `stack-c/openig_home/config/config.json:24`
- Redis blacklist TTL is hardcoded as `EX 28800` in all three backchannel logout handlers:
  - `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:320-321`
  - `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:327-328`
  - `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:319-320`
- The code comment explicitly couples Redis TTL to `JwtSession.sessionTimeout`.
- [[OAuth2ClientFilter]] is the first filter in the protected route chains and is responsible for the Keycloak redirect path.
- Existing logs show two distinct failure modes:
  - `invalid_grant` / `Session not active` during refresh when OpenIG still has session state but the upstream Keycloak SSO session is no longer active.
  - `Authorization call-back failed because there is no authorization in progress` when the callback lands without matching OpenIG authorization state.

> [!warning]
> Reducing `JwtSession.sessionTimeout` without updating the `28800` Redis TTL leaves revocation keys alive longer than necessary. It is safer than the reverse, but it keeps blacklist entries longer than the maximum gateway session lifetime.

> [!tip]
> If the target is `30 minutes`, the runtime-aligned Redis TTL is `1800` seconds, because the handlers currently document the rule `TTL must be >= JwtSession.sessionTimeout`.

> [!success]
> No Keycloak session-timeout settings were found in `keycloak/realm-import/realm-export.json` or `keycloak/docker-compose.yml`. The project currently imports the realm and sets DB/hostname/admin env vars only.

## Evidence

- Route behavior:
  - `stack-a/openig_home/config/routes/01-wordpress.json:33-60`
  - `stack-a/openig_home/config/routes/01-wordpress.json:96-104`
  - `stack-b/openig_home/config/routes/01-jellyfin.json:92-108`
  - `stack-c/openig_home/config/routes/10-grafana.json:33-49`
- Refresh failure logs:
  - `stack-a/openig_home/logs/route-01-wordpress-2026-03-10.0.log:21-22`
  - `stack-c/openig_home/logs/route-10-grafana-2026-03-08.0.log:1-2`
- Callback-without-state logs:
  - `stack-a/openig_home/logs/route-01-wordpress-2026-03-10.0.log:82`
  - `stack-c/openig_home/logs/route-11-phpmyadmin.log:1-3`

## Next steps

- If changing gateway session lifetime to `30 minutes`, update all three `config.json` files and all three `BackchannelLogoutHandler.groovy` TTL literals together.
- If a smoother post-expiry UX is required after upstream refresh failure, review whether `failureHandler` should remain a static error response or be replaced with a re-auth redirect strategy.
