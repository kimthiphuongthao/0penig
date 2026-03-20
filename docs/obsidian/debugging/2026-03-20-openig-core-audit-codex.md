---
title: OpenIG core audit of SSO and SLO claims
tags:
  - debugging
  - openig
  - keycloak
  - audit
  - stack-a
  - stack-b
date: 2026-03-20
status: done
---

# OpenIG core audit of SSO and SLO claims

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]]

## Context

- Audited 15 implementation claims against local gateway files plus upstream OpenIG source on 2026-03-20.
- Wrote the full report to [docs/audit/2026-03-20-openig-core-audit-codex.md](../../audit/2026-03-20-openig-core-audit-codex.md).
- Upstream repo search result: no `JwtSessionFactory` and no `Router.java` in `openig-core`; the relevant classes are `JwtSessionManager` and `RouterHandler`.

## Confirmed behavior

> [!success]
> Confirmed from source:
> `oauth2:` session key format, `atr.id_token` persistence, `JwtCookieSession.keySet()`, `session.clear()`, `session.remove()`, filename-based route ordering, and `cookieDomain` -> cookie `Domain`.

- `OAuth2Utils.sessionKey(...)` stores OAuth2 state under `oauth2:` plus the resolved `clientEndpoint` URI.
- `OAuth2Session.toJson()` stores the access-token response under `atr`, which is why `SloHandler` and blacklist code can read `atr.id_token`.
- `RouterHandler` sorts routes lexicographically by filename-derived route id, so numeric filename prefixes remain a valid precedence tool.

## Refuted assumptions

> [!warning]
> `user_info` is not persisted inside `session['oauth2:...']`.
> `OAuth2ClientFilter` adds `user_info` only to the runtime target map before writing `attributes.openid`.

> [!warning]
> `target: ${attributes.openid}` does not mirror data back into session.
> `atr.id_token` session reads still work because `OAuth2Utils.saveSession(...)` stores the OAuth2 session separately.

> [!warning]
> Oversized `JwtSession` cookies do not fail with a hard 500.
> The HTTP framework logs the save failure and returns the original response, which makes cookie-size regressions look like silent state loss.

- `SloHandlerJellyfin.groovy` is the local script that still assumes `session[oauth2Key].user_info.sub`.
- `TokenReferenceFilter.groovy` is safe on response timing: its `.then { ... }` mutation runs before `SessionFilter` saves the final session state.

## Current state

- Stack A assumptions around `oauth2:*` discovery, `atr.id_token`, and Redis token-reference offload are aligned with upstream behavior.
- Stack B Jellyfin logout logic has one weak fallback: `session[oauth2Key].user_info.sub` is not backed by OpenIG persistence.
- Default JwtSession override is wired correctly in this repo because the heap object is named `Session`.

> [!tip]
> When auditing JwtSession behavior in this codebase, inspect `JwtSessionManager` plus the HTTP framework `SessionFilter`, not just `JwtCookieSession`. Save timing and oversize-cookie behavior are split across those components.

## Next steps

- Replace any `session[oauth2Key].user_info.sub` fallback with `attributes.openid.user_info.sub`, a cached local session key such as `jellyfin_user_sub`, or ID-token decoding.
- Keep the Redis token-reference pattern; the source audit supports the current sequencing.
- If cookie-size regressions are suspected, look for `Failed to save session` in OpenIG logs instead of expecting an HTTP 500.

## Files changed

- `docs/audit/2026-03-20-openig-core-audit-codex.md`
- `docs/obsidian/debugging/2026-03-20-openig-core-audit-codex.md`
