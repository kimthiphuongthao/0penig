---
title: 2026-03-19 End-of-Session Audit Phase 1 Phase 2 Tracking Sync
tags:
  - debugging
  - docs
  - openig
  - jwt-session
  - redis
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-19
status: complete
---

# 2026-03-19 End-of-Session Audit Phase 1 Phase 2 Tracking Sync

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Session work completed both phases of the `JwtSession` production-pattern restoration on branch `fix/jwtsession-production-pattern`.
- Phase 1 restored real `Session` heap binding and reduced token size, but still exceeded the browser 4 KB JWT session limit.
- Phase 2 offloaded the heavy OAuth session blob to [[Redis]] and then required one follow-up fix to discover the real `oauth2:` key dynamically from `session.keySet()`.

## Audit Sync Performed

- Updated tracking state in:
  - `.memory/MEMORY.md`
  - `.claude/rules/gotchas.md`
  - `.claude/rules/architecture.md`
  - `CLAUDE.md`
- Restored `stack-c/openig_home/config/config.json` from live substituted values back to:
  - `__JWT_SHARED_SECRET__`
  - `__KEYSTORE_PASSWORD__`
- Included all uncommitted debugging notes produced during the investigation so session evidence stays together in the vault.

> [!success]
> Tracking docs now treat Phase 1 + Phase 2 as implementation-complete, with only end-user validation and the Stack C `bob` MariaDB gap left open.

## Key Technical Evidence

- Real OAuth session key format is `oauth2:<full-app-URL>/<clientEndpoint>`, not `oauth2:/openid/appX`.
- `session.keySet()` works on `org.forgerock.openig.jwt.JwtCookieSession`, so dynamic discovery is a valid production pattern for `TokenReferenceFilter.groovy`.
- Sampled [[Stack C]] browser cookie size dropped from `4803` chars to `849` chars after the dynamic key discovery fix.

> [!warning]
> The remaining validation gap is still real: login + logout need to be exercised by the user on all three stacks on the fix branch before the task can be treated as fully closed.

## Current State

- [[Stack A]] and [[Stack B]]: code wired, no full user validation yet.
- [[Stack C]]: targeted verification passed, no fresh `JWT session is too large` lines observed after the final fix.
- Open infra item remains: Stack C MariaDB user `bob` is still not provisioned.

> [!tip]
> Future fixes that need the OAuth session entry should always enumerate `session.keySet()` first and only use request-derived candidates as fallback.
