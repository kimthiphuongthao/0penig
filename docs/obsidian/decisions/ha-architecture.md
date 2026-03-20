---
title: HA Architecture Design
tags:
  - ha
  - openig
  - jwt
  - decision
date: 2026-03-12
status: decision
---

# HA Architecture Design

Related: [[OpenIG]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## HA Components

- nginx `ip_hash` for sticky routing at ingress.
- OpenIG `JwtSession` as stateless cookie session; both nodes can read the same cookie.
- Vault shared mount: `openig-x1` and `openig-x2` both mount `vault/file/`, so both use the same `role_id` and `secret_id` files.

## Why Not Redis Session Store

- Added operational complexity with no clear benefit for this lab setup.
- `JwtSession` is already stateless and shared-cookie based.
- Both OpenIG nodes can validate/decrypt the same cookie when `sharedSecret` is aligned per stack.

## Known Weakness

- OAuth2 `state` remains in-memory on one node during the authorization flow.
- If failover happens mid-flow, callback can fail with `Authorization call-back failed`.
- Probability is low with `ip_hash`, but not zero.
- Production fix: move transient auth attributes to `RedisAttributesFactory`.

> [!warning]
> If `openig-x2` container is recreated, Vault credential files may be empty. Re-run bootstrap to regenerate `role_id`/`secret_id` before restarting OpenIG.
