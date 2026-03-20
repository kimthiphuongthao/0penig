---
title: 2026-03-19 Phase 2 Redis Token Reference Pattern
tags:
  - debugging
  - openig
  - redis
  - jwt-session
  - keycloak
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-19
status: ready-for-test
---

# 2026-03-19 Phase 2 Redis Token Reference Pattern

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

> [!warning]
> 2026-03-20 follow-up: the original shared session field `token_ref_id` was later split into per-app keys (`token_ref_id_app1` .. `token_ref_id_app6`) by commit `8e9f729`. That change was required once multiple apps in the same stack started sharing one `JwtSession` cookie concurrently.

## Context

- Request: implement Phase 2 token offload for all three stacks so OIDC token payloads no longer live inside the `JwtSession` cookie.
- Production target: shared [[OpenIG]] cluster behind F5 with shared [[Redis]] and [[Vault]].
- Immediate problem: Phase 1 restored the `Session` heap binding, but all stacks still overflowed the 4 KB JWT cookie limit once the OAuth session blob was serialized.
- Commit created: `9b2d109`

## What changed

- Added identical `TokenReferenceFilter.groovy` to:
  - `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
  - `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
  - `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- Request path behavior:
  - reads the route-bound token reference key from session (`token_ref_id` in the original Phase 2 implementation; per-app `token_ref_id_appN` on the current shared-cookie flows)
  - restores the OAuth session blob from [[Redis]] when the in-session `oauth2:*` entry is absent
  - fails closed with HTTP 502 if a referenced token blob cannot be restored
- Response path behavior:
  - serializes the OAuth session blob to JSON
  - stores it in [[Redis]] under `token_ref:<uuid>` with TTL `1800`
  - removes the heavy `oauth2:*` session entry from `JwtSession`
  - leaves lightweight keys such as `oidc_sid`, app-specific cache keys, and local credential state in the cookie session
  - logs but does not block the response if Redis write-offload fails
- Added the filter before OIDC to all app routes:
  - [[Stack A]] `01-wordpress.json`, `02-app2.json`
  - [[Stack B]] `02-redmine.json`, `01-jellyfin.json`
  - [[Stack C]] `10-grafana.json`, `11-phpmyadmin.json`
- Wrapped logout handlers in a `Chain` so the same restore logic runs before `SloHandler`:
  - [[Stack A]] `00-wp-logout.json`
  - [[Stack B]] `00-redmine-logout.json`, `00-jellyfin-logout.json`
  - [[Stack C]] `00-grafana-logout.json`, `00-phpmyadmin-logout.json`

> [!tip]
> The filter restores the canonical `oauth2:/openid/appX` entry and also mirrors host-qualified variants already used by existing logout/session scripts. That keeps logout `id_token_hint` lookup compatible without changing `SloHandler.groovy`.

## Verification

- Restarted all six [[OpenIG]] containers:
  - `sso-openig-1`, `sso-openig-2`
  - `sso-b-openig-1`, `sso-b-openig-2`
  - `stack-c-openig-c1-1`, `stack-c-openig-c2-1`
- First restart produced transient route-reload errors on [[Stack A]] and [[Stack B]] where OpenIG attempted to compile `TokenReferenceFilter.groovy` before its temporary script cache had the new file.
- Second clean restart loaded all modified routes successfully on the primary containers with no fresh `ERROR`, `TokenRef`, or `JWT session is too large` lines in the post-restart log window.

> [!warning]
> No browser flow was executed during this task, so there are still no runtime `TokenRef` restore/store log lines yet. Current verification is startup-level only.

> [!success]
> After the clean restart, the route set for [[Stack A]], [[Stack B]], and [[Stack C]] loads successfully with the new token-reference filter in place.

## Current state

- Phase 2 code and route wiring are committed and deployed in the local lab containers.
- The next validation step is browser login/logout testing to confirm:
  - the session cookie stays small
  - no new `JWT session is too large` errors appear
  - logout still carries `id_token_hint`
- Runtime noise outside this change still exists in the worktree:
  - `stack-c/openig_home/config/config.json` was modified by container startup and was intentionally not included in the Phase 2 commit

## Next steps

1. Test [[Stack C]] Grafana at `http://grafana-c.sso.local:18080/` with `alice / alice123`.
2. In browser DevTools, confirm `IG_SSO_C` exists and the cookie payload remains under the browser 4 KB limit.
3. Tail `stack-c-openig-c1-1` logs during login to confirm no new `JWT session is too large` or `[TokenRef] Failed...` lines appear.
4. Test [[Stack A]] WordPress at `http://wp-a.sso.local/` and confirm `IG_SSO` appears with no new oversize-session log entries.
5. Exercise one logout path per stack to confirm `SloHandler` still has access to the restored `id_token_hint`.

## Files changed

- `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-a/openig_home/config/routes/00-wp-logout.json`
- `stack-a/openig_home/config/routes/01-wordpress.json`
- `stack-a/openig_home/config/routes/02-app2.json`
- `stack-b/openig_home/config/routes/00-jellyfin-logout.json`
- `stack-b/openig_home/config/routes/00-redmine-logout.json`
- `stack-b/openig_home/config/routes/01-jellyfin.json`
- `stack-b/openig_home/config/routes/02-redmine.json`
- `stack-c/openig_home/config/routes/00-grafana-logout.json`
- `stack-c/openig_home/config/routes/00-phpmyadmin-logout.json`
- `stack-c/openig_home/config/routes/10-grafana.json`
- `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- `docs/obsidian/debugging/2026-03-19-phase-2-redis-token-reference-pattern.md`
