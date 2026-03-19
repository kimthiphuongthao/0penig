---
title: All Stacks TokenReferenceFilter Dynamic Session Key Discovery
tags:
  - debugging
  - openig
  - redis
  - jwt-session
  - oauth2
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-19
status: complete
---

# All Stacks TokenReferenceFilter Dynamic Session Key Discovery

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Request: fix `TokenReferenceFilter.groovy` so it discovers the real OAuth2 session key dynamically and shrinks the browser session cookie again.
- Scope: gateway-only Groovy change in [[Stack A]], [[Stack B]], and [[Stack C]].
- Constraint: do not modify target application code or target app configuration.

> [!warning]
> Redis offload was already succeeding before this change. The actual failure was that the large `oauth2:` entry still remained in `JwtCookieSession` after response handling.

## Root Cause

- Live stack-c `.then()` logging proved `session.keySet()` works on `org.forgerock.openig.jwt.JwtCookieSession`.
- The actual OAuth2 session key observed during Grafana login was `oauth2:http://grafana-c.sso.local:18080/openid/app5`.
- The earlier filter version guessed multiple key variants and mirrored restored payloads back into session, which could duplicate large state.
- Even when the correct key was present, the previous `session.remove()` path did not effectively shrink the saved session in the observed response flow.

## Fix Applied

- Reworked `TokenReferenceFilter.groovy` in all three stacks to:
  - discover `oauth2:` session keys dynamically from `session.keySet()` for the current `clientEndpoint`
  - store the exact key/value map in Redis under `token_ref:*`
  - restore the exact key/value map on the next request
  - remove the aliasing and mirroring logic entirely
  - preserve only non-`oauth2:` session entries, then `session.clear()`, then restore the preserved entries plus `token_ref_id`

> [!success]
> After the fix, the sampled post-restart `IG_SSO_C` cookie for `http://grafana-c.sso.local:18080/` dropped to `849` characters instead of overflowing past 4 KB.

## Verification

- Restarted `stack-c-openig-c1-1` for live diagnosis, then restarted:
  - `sso-openig-1`
  - `sso-openig-2`
  - `sso-b-openig-1`
  - `sso-b-openig-2`
  - `stack-c-openig-c1-1`
  - `stack-c-openig-c2-1`
- Triggered:
  - `curl -v -L -c /tmp/testcookies.txt "http://grafana-c.sso.local:18080/"`
- Fresh stack-c log after the final restart showed:
  - `[TokenRef DEBUG] Session keys at .then(): [oauth2:http://grafana-c.sso.local:18080/openid/app5]`
  - `[TokenRef] Stored oauth2 session keys=[oauth2:http://grafana-c.sso.local:18080/openid/app5] endpoint=/openid/app5 token_ref_id=...`
- Fresh `docker logs --since 2m stack-c-openig-c1-1 2>&1 | grep -E "(too large|TokenRef|ERROR)"` output showed no new `JWT session is too large` lines.

> [!tip]
> The filter now depends on `session.keySet()` as the primary discovery mechanism. The request-derived candidate list remains only as a fallback if a future session implementation stops supporting enumeration.

## Files Changed

- `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `docs/obsidian/debugging/2026-03-19-all-stacks-tokenreferencefilter-dynamic-session-key-discovery.md`
