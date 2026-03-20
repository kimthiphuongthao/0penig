---
title: Standard gateway pattern ACT-3 expiry clarification
tags:
  - openig
  - debugging
  - documentation
  - jwt
date: 2026-03-20
status: completed
---

# Standard gateway pattern ACT-3 expiry clarification

## Context

- Follow-up to the `ACT-3` anchor verification note for `docs/deliverables/standard-gateway-pattern.md`.
- Scope was limited to two insertions in the reference document; no [[OpenIG]] route JSON, Groovy script, nginx config, or [[Vault]] bootstrap/runtime files were changed.
- The clarification needed to state how OpenIG 6 `JwtSession` actually enforces `sessionTimeout` so test plans and runbooks do not assert the wrong cookie attribute.

> [!success] Result
> `docs/deliverables/standard-gateway-pattern.md` now explicitly states that OpenIG 6 `JwtSession` lifetime is enforced via JWT `_ig_exp` plus cookie `Expires`, and the checklist now tells reviewers not to assert `Max-Age`.

## What changed

- Inserted a new paragraph directly under `### 1. Revocation Contract` explaining that `JwtCookieSession` writes `Expires` only and that validation should use cookie `Expires` or decoded JWT `_ig_exp`.
- Inserted a matching checklist bullet directly under the `BackchannelLogoutHandler` TTL bullet in `### Session and revocation`.

## Decision

> [!tip] Review guidance
> For [[OpenIG]] 6 `JwtSession`, session-lifetime verification should treat JWT `_ig_exp` and cookie `Expires` as the authoritative expiry signals. `Max-Age` is not the correct assertion target for this implementation.

- Kept the edit strictly documentation-only and confined to the two user-requested insertions in the deliverable.
- Did not rewrite surrounding prose, reorder bullets, or touch unrelated sections.

## Current state

- `docs/deliverables/standard-gateway-pattern.md` contains both ACT-3 insertions.
- The revocation contract prose and the session checklist now carry the same expiry-validation guidance.
- No runtime files were modified.

## Files changed

- `docs/deliverables/standard-gateway-pattern.md`
- `docs/obsidian/debugging/2026-03-20-standard-gateway-pattern-act-3-expiry-clarification.md`
