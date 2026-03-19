---
title: 2026-03-19 Phase 1 JwtSession Production Pattern Restore
tags:
  - debugging
  - openig
  - keycloak
  - jwt-session
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-19
status: in-progress
---

# 2026-03-19 Phase 1 JwtSession Production Pattern Restore

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Request: execute the Phase 1 restore sequence end to end.
- Goal: restore the production-style [[OpenIG]] JWT session binding by renaming the default heap object to `Session`, then reduce token size pressure in [[Keycloak]] before restarting the gateways.
- Branch at execution time: `fix/jwtsession-production-pattern`
- Commit created for the config rename: `0454796`

## What changed

- [[Stack A]] `stack-a/openig_home/config/config.json`
  - heap object renamed from `"name": "JwtSession"` to `"name": "Session"`
  - `type: "JwtSession"` unchanged
- [[Stack B]] `stack-b/openig_home/config/config.json`
  - heap object renamed from `"name": "JwtSession"` to `"name": "Session"`
  - `type: "JwtSession"` unchanged
- [[Stack C]] `stack-c/openig_home/config/config.json`
  - heap object renamed from `"name": "JwtSession"` to `"name": "Session"`
  - `type: "JwtSession"` unchanged
- [[Keycloak]] clients updated through Admin REST:
  - `openig-client`
  - `openig-client-b`
  - `openig-client-c-app5`
  - `openig-client-c-app6`
- Applied client attribute changes:
  - `attributes["access.token.signed.response.alg"] = "ES256"`
  - `attributes["id.token.signed.response.alg"] = "ES256"`
  - `attributes["use.refresh.tokens"] = "false"`

> [!warning]
> This [[Keycloak]] server build rejects a top-level `useRefreshTokens` field on `ClientRepresentation` with HTTP 400 `Unrecognized field "useRefreshTokens"`. The supported persistent change in this environment is the client attribute `use.refresh.tokens=false`.

## Verification

- All six [[OpenIG]] containers restarted successfully and became `healthy`:
  - `sso-openig-1`, `sso-openig-2`
  - `sso-b-openig-1`, `sso-b-openig-2`
  - `stack-c-openig-c1-1`, `stack-c-openig-c2-1`
- Representative startup tails for [[Stack A]], [[Stack B]], and [[Stack C]] show normal Tomcat boot, config load, route load, and server start completion.
- [[Keycloak]] re-read after PUT confirms for all four clients:
  - access token signing alg = `ES256`
  - ID token signing alg = `ES256`
  - `attributes["use.refresh.tokens"] = "false"`
- GET responses still show top-level `useRefreshTokens: null`, matching the unsupported-field behavior above.
- During restart, [[Stack C]] rewrote live secrets into the mounted `config.json`; the repo copy was restored back to `__JWT_SHARED_SECRET__` and `__KEYSTORE_PASSWORD__` placeholders immediately after verification.

> [!success]
> The session heap binding fix is now present in all three configs, and the four target [[Keycloak]] clients persist the ES256 plus `use.refresh.tokens=false` attribute changes.

## Current state

- Startup after restart is clean.
- Post-restart log scans still show `JWT session is too large` errors on [[Stack A]] and [[Stack B]].
- [[Stack C]] does not show the JWT size error in the scanned logs, but still shows separate Vault AppRole 503 noise for phpMyAdmin credential fetches.
- Additional post-restart OAuth callback errors (`invalid_request`, `invalid_grant`) are present in logs and look consistent with stale or concurrent browser sessions rather than the config rename itself.

> [!tip]
> The next validation should use a fresh private browser window to avoid stale `state`, code, and legacy cookie collisions while checking whether the active session cookie is now `IG_SSO*` instead of `JSESSIONID`.

## Next steps

1. Test [[Stack A]], [[Stack B]], and [[Stack C]] in a private browser window.
2. In DevTools, verify cookie names under `Application -> Cookies`:
   - [[Stack A]] expects `IG_SSO`
   - [[Stack B]] expects `IG_SSO_B`
   - [[Stack C]] expects `IG_SSO_C`
3. Confirm whether login succeeds without new `JWT session is too large` errors.
4. If Stack A or Stack B still overflows, continue to Phase 2 token-reference work rather than expanding the JWT cookie.

## Files changed

- `stack-a/openig_home/config/config.json`
- `stack-b/openig_home/config/config.json`
- `stack-c/openig_home/config/config.json`
- `docs/obsidian/debugging/2026-03-19-phase-1-jwtsession-production-pattern-restore.md`
