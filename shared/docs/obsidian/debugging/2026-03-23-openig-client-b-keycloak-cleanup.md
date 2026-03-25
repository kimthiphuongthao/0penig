---
title: openig-client-b Keycloak cleanup
tags:
  - openig
  - keycloak
  - stack-b
  - debugging
date: 2026-03-23
status: done
---

# openig-client-b Keycloak cleanup

Context: aligned `openig-client-b` in [[Keycloak]] with shared infra standards used by `openig-client-c-app5/app6`, adapted for Redmine app3 on [[Stack B]].

## What changed

- Replaced `redirectUris` with:
  - `http://redmine-b.sso.local/openid/app3/*`
  - `http://redmine-b.sso.local:80/openid/app3/*`
- Replaced `webOrigins` with `*`
- Replaced `attributes.post.logout.redirect.uris` with:
  - `http://redmine-b.sso.local/`
  - `http://redmine-b.sso.local:80/`
- Preserved all other client fields as returned by the live admin API

> [!warning]
> The backchannel logout endpoint is not exposed as top-level `backchannelLogoutUrl` in this realm export. The active value is stored in `attributes.backchannel.logout.url`.

## Root cause

The client still contained stale legacy stack-b values in `redirectUris`, `webOrigins`, and post-logout redirects, including `:9080`, `openigb`, `jellyfin-b`, and localhost entries. Those no longer match the shared port-80 ingress standard for Redmine app3 behind [[OpenIG]].

## Verified state

> [!success]
> Verified through the Keycloak admin API after the PUT completed with HTTP `204`.

- `redirectUris`
  - `http://redmine-b.sso.local/openid/app3/*`
  - `http://redmine-b.sso.local:80/openid/app3/*`
- `webOrigins`
  - `*`
- `attributes.post.logout.redirect.uris`
  - `http://redmine-b.sso.local/##http://redmine-b.sso.local:80/`
- `attributes.backchannel.logout.url`
  - `http://host.docker.internal:80/openid/app3/backchannel_logout`
- Preserved flags checked after update:
  - `standardFlowEnabled=true`
  - `directAccessGrantsEnabled=false`
  - `publicClient=false`

> [!tip]
> For future cleanups, fetch the full client JSON first and patch only the required keys before issuing `PUT`. This avoids accidental resets of unrelated client settings.

## Current state

`openig-client-b` now matches the shared infra redirect/logout pattern expected for Redmine app3 and no longer advertises the stale stack-b legacy entries in the user-facing redirect fields.

Files changed: `docs/obsidian/debugging/2026-03-23-openig-client-b-keycloak-cleanup.md`

Related systems: [[OpenIG]], [[Keycloak]], [[Vault]], [[Stack B]]
