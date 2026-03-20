---
title: How to Add a New Stack
tags:
  - ops
  - architecture
  - how-to
date: 2026-03-12
status: guide
---

# How to Add a New Stack

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## 1. Apply Independence Principle

New stack must have isolated components:
- `nginx-x`
- `openig-x1`
- `openig-x2`
- `vault-x`
- `redis-x`
- Target app containers for that stack

No runtime sharing with existing stacks except Keycloak IdP.

## 2. Allocate Ports

Current allocations:
- Stack A: `80`
- Stack B: `9080`
- Stack C: `18080`

Choose the next free host port for the new stack ingress.

## 3. Create Compose Services

Required in new `docker-compose.yml`:
- `nginx-x`
- `openig-x1`
- `openig-x2`
- `redis-x`
- `vault-x`
- App containers (per app requirements)

## 4. Register Keycloak Clients

For each app, register new client names like:
- `openig-client-x-app1`
- `openig-client-x-app2`

Client settings:
- Set backchannel logout URL to new stack endpoint.
- Enable backchannel logout session required.

## 5. Add OpenIG Routes

Route naming convention:
- `00-backchannel-logout-appN.json`
- `XX-appname.json` (`XX` = `10`, `11`, `12`, ...)

## 6. Reuse Groovy Handlers

Reusable scripts:
- `BackchannelLogoutHandler.groovy` (change Redis host to `redis-x`)
- `SessionBlacklistFilter.groovy` (change Redis host to `redis-x`)
- `SloHandler.groovy` pattern per app

## 7. Configure JwtSession Secret

- Generate a new unique base64 key for `JwtSession.sharedSecret`.
- Never reuse a key from stack-a/stack-b/stack-c.

Example:
```bash
openssl rand -base64 32
```

## 8. Update Local DNS Mapping

Add host entries in `/etc/hosts`:
- `appname-x.sso.local -> 127.0.0.1`

> [!tip]
> Keep all stack-specific names aligned across nginx upstreams, OpenIG routes, Redis host, and Vault role names.

> [!warning]
> Reusing `JwtSession.sharedSecret` across stacks breaks isolation because sessions become cross-readable.
