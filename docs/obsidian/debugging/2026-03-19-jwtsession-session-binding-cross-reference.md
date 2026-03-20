---
title: JwtSession Session Binding Cross Reference
tags:
  - debugging
  - openig
  - jwt-session
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-19
status: complete
---

# JwtSession Session Binding Cross Reference

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Request: cross-reference the 2026-03-16 pre-packaging audit set, the pattern consolidation plan, external OpenIG session docs, the three stack `config.json` files, and the repo rules on JWT session behavior.
- Core question: why all three stacks still show `JSESSIONID` instead of `IG_SSO*`, and what the exact fix should be.

## Findings

- `docs/external/openig_audit_results.md` confirms `"JwtSession"` is only a type alias that resolves to `JwtSessionManager.class`.
- `docs/external/openig_heap_session.md` distinguishes two binding mechanisms:
  - default session manager = heap object named `Session`
  - explicit binding = top-level or route-level `"session": "<heap-object-name>"`
- All three live stack configs still define the heap object as `"name": "JwtSession"` and do not declare a top-level `"session"` property.
- Route search across all three stacks shows no route-level `"session"` binding either.
- Project rules already record the live behavior: if the heap object is named `"JwtSession"` instead of `"Session"`, OpenIG falls back to servlet `HttpSession` and the browser sees `JSESSIONID`.

> [!warning]
> The type alias finding does not make the current configs correct. It only proves that `type: "JwtSession"` instantiates `JwtSessionManager`. It does not make that heap object the active session provider.

## Verdict

- Current runtime state:
  - [[Stack A]], [[Stack B]], and [[Stack C]] are still on `HttpSession` fallback.
  - The configured `cookieName` values `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C` are inert until the JWT session manager is actually bound.
- Correct project fix:
  - rename the heap object from `"JwtSession"` to `"Session"` in each `config.json`
  - keep `type: "JwtSession"` unchanged
  - no extra top-level `"session"` property is required if the heap name is `Session`

> [!success]
> The missing piece is session binding, not the type alias. The special default heap name `Session` is what the current repo is expecting.

## 4 KB Follow-up

- Historical evidence shows real overflow happened when cookie-backed JWT sessions were active:
  - Stack A route logs recorded `JWT session is too large` at 6652 and 4803 chars.
- Current code has already reduced that risk:
  - [[Stack A]] removes legacy `wp_session_cookies`
  - [[Stack B]] removes legacy `redmine_session_cookies`
  - the session now keeps smaller markers instead of raw upstream cookies for those two apps
- Remaining answer:
  - overflow was real before
  - after the binding fix on current code, it must be re-measured instead of assumed

## Files Cross-Referenced

- `docs/audit/2026-03-16-pre-packaging-audit/`
- `.omc/plans/pattern-consolidation.md`
- `docs/external/openig_heap_session.md`
- `docs/external/openig_audit_results.md`
- `stack-a/openig_home/config/config.json`
- `stack-b/openig_home/config/config.json`
- `stack-c/openig_home/config/config.json`
- `.claude/rules/gotchas.md`
- `.claude/rules/architecture.md`

## Next Steps

1. Rename the heap object to `Session` in all three `config.json` files.
2. Restart the OpenIG nodes.
3. Validate that fresh authenticated browser flows now issue `IG_SSO*` instead of `JSESSIONID`.
4. Re-measure cookie size after login on Stack A and Stack B before declaring Phase 1 complete.

> [!tip]
> If a non-special heap name is preferred, the documented alternative is explicit binding with `"session": "JwtSession"`, but the repo rules and current architecture notes standardize on the `Session` heap-name pattern.
