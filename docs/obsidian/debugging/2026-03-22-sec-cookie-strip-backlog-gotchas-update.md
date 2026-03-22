---
title: SEC-COOKIE-STRIP backlog and gotchas update
tags:
  - debugging
  - openig
  - cookies
  - security
  - tracking
date: 2026-03-22
status: done
---

# SEC-COOKIE-STRIP backlog and gotchas update

Context: follow-up to [[2026-03-22-ig-sso-cookie-forwarding-investigation]] to make sure the `IG_SSO*` forwarding issue is tracked in the active backlog and recurring gotchas for [[OpenIG]], [[Stack A]], [[Stack B]], and [[Stack C]].

## What changed

> [!success] Tracking added
> Added `SEC-COOKIE-STRIP` to `docs/fix-tracking/master-backlog.md` as `P2-SHOULD` and `OPEN`.

> [!success] Gotcha recorded
> Added a new row to `.claude/rules/gotchas.md` documenting that all backend apps under `.sso.local` currently receive `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C` unless OpenIG strips them before proxying.

## Decision

> [!warning] Do not strip at ingress
> Stripping `IG_SSO*` in nginx before [[OpenIG]] would break session restoration and therefore break SSO/SLO behavior.

> [!tip] Safe strip point
> Strip `IG_SSO*` only in the OpenIG route chain after session load and before the request is forwarded to the backend app.

## Current state

- Backlog item `SEC-COOKIE-STRIP` is open.
- The known-gotchas table now documents the safe and unsafe strip locations.
- No route or Groovy fix was implemented in this task.

## Next steps

1. Add outbound cookie-strip logic in the affected OpenIG routes/Groovy chain.
2. Verify login, front-channel logout, and backchannel logout on all 3 stacks after the strip logic lands.
3. Update backlog status and gotchas wording again when the implementation commit is merged.

## Files changed

- `docs/fix-tracking/master-backlog.md`
- `.claude/rules/gotchas.md`
- `docs/obsidian/debugging/2026-03-22-sec-cookie-strip-backlog-gotchas-update.md`
