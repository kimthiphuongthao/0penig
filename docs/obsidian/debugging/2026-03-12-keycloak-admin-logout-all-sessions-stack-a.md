---
title: Keycloak Admin Logout All Sessions vs Stack A Backchannel
tags:
  - debugging
  - keycloak
  - openig
  - stack-a
  - backchannel
date: 2026-03-12
status: investigated
---

# Keycloak Admin Logout All Sessions vs Stack A Backchannel

Links: [[Keycloak]] [[OpenIG]] [[Stack A]]

## Context

Investigated why Keycloak Admin "Logout all sessions" did not appear to trigger backchannel logout toward Stack A OpenIG.

## Evidence

1. Keycloak realm import registers client `openig-client` with:
   - `backchannel.logout.url = http://host.docker.internal/openid/app1/backchannel_logout`
   - `backchannel.logout.session.required = true`
   Source: `keycloak/realm-import/realm-export.json`

2. Stack A OpenIG route accepts backchannel logout on path only:
   - `POST /openid/app1/backchannel_logout`
   Source: `stack-a/openig_home/config/routes/00-backchannel-logout-app1.json`

3. Stack A Nginx hardening for backchannel exists only on vhost `wp-a.sso.local`:
   - `location = /openid/app1/backchannel_logout`
   - `proxy_request_buffering off`
   - `proxy_next_upstream off`
   Source: `stack-a/nginx/nginx.conf`

4. Because Keycloak calls `http://host.docker.internal/...`, request host is not `wp-a.sso.local`.
   Nginx therefore does not hit the dedicated backchannel location on the `wp-a.sso.local` server block.
   The request instead falls through to the default port-80 server path, which lacks the backchannel-specific retry/body protections.

5. Existing Stack A OpenIG route logs show malformed backchannel requests were received previously:
   - `logout_token is missing`
   - `logout_token is not a JWT`
   Sources:
   - `stack-a/openig_home/logs/route-00-backchannel-logout-app1.log`
   - `stack-a/openig_home/logs/route-00-backchannel-logout-app1-2026-03-07.0.log`

## Analysis

> [!warning]
> Runtime verification was partial only. Docker socket access and local HTTP access were blocked in the Codex sandbox, so live `docker logs` and live Admin API reads could not be executed from this session.

### What is certain

- Stack A is configured to receive backchannel logout at `http://host.docker.internal/openid/app1/backchannel_logout`.
- OpenIG route itself does not require `Host`, so any request that reaches OpenIG with that path and method can match.
- The Nginx protection needed for logout POST bodies is only wired on `wp-a.sso.local`, not on `host.docker.internal`.
- Prior malformed requests already hit the OpenIG backchannel handler, which is consistent with a proxy/body handling issue.

### Most likely explanation

> [!tip]
> Two separate issues are likely being conflated:
> 1. Admin "Logout all sessions" is a different flow from RP-initiated user logout.
> 2. Even when Keycloak does send a backchannel POST, Stack A's registered URL currently enters Nginx through the wrong virtual host path.

- For normal app logout, [[OpenIG]] `SloHandler.groovy` redirects browser to Keycloak end-session with `id_token_hint`, and lab docs state this path works.
- For admin mass logout, there is no OpenIG `SloHandler` involvement. Any propagation depends entirely on Keycloak's server-side backchannel behavior.
- If Keycloak sends the POST to the currently registered URL, Stack A can still mishandle it because the request lands on `host.docker.internal`, not the vhost with the hardened `/backchannel_logout` location.
- This aligns with the documented lab gotcha: missing `proxy_next_upstream off` can produce empty/malformed `logout_token` bodies.

## Recommended fixes

1. Add a dedicated Nginx server/location that matches `Host: host.docker.internal` for `/openid/app1/backchannel_logout`, with:
   - `proxy_request_buffering off`
   - `proxy_next_upstream off`
2. Or register the backchannel URL to a host/path that definitely terminates on the hardened backchannel location.
3. Prefer per-app Keycloak clients moving forward. Current Stack A shares `openig-client` across app1 and app2, while project guidance now recommends separate clients per app.
4. Re-run with live verification outside the sandbox:
   - get admin token
   - query `/admin/realms/sso-realm/clients?clientId=openig-client`
   - trigger admin "Logout all sessions"
   - inspect Keycloak container logs for outbound backchannel attempts
   - inspect OpenIG route log for a fresh entry timestamp

> [!success]
> Investigation narrowed the issue to Keycloak admin-flow uncertainty plus a concrete Stack A Nginx/backchannel URL mismatch that can break otherwise valid backchannel POSTs.
