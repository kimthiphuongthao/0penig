---
title: Cross-stack OpenIG review fix plan
tags:
  - openig
  - security-review
  - remediation-plan
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-15
status: completed
---

# Cross-stack OpenIG review fix plan

Context:
- Consolidated the three 2026-03-14 review files for [[OpenIG]] remediation planning across [[Stack A]], [[Stack B]], and [[Stack C]].
- Constraint stayed strict: gateway-side changes only, no app-server edits.
- Planning target was `.omc/plans/test-codex-comparison.md`.

> [!success]
> Wrote a single execution plan that groups fixes by priority, marks cross-stack vs stack-specific scope, lists exact target files, and adds independent acceptance criteria per fix.

## What was done

- Read:
  - `docs/reviews/2026-03-14-stack-a-openig-code-security-review.md`
  - `docs/reviews/2026-03-14-stack-b-openig-code-security-review.md`
  - `docs/reviews/2026-03-14-stack-c-openig-code-security-review.md`
- Produced a unified remediation plan in `.omc/plans/test-codex-comparison.md`.
- Normalized findings into:
  - cross-stack high items: secrets, revocation TTL, fail-closed revocation, HTTPS/public origin hardening, browser-stored privileged material, Redis timeouts
  - stack-specific high items: Jellyfin logout namespace mismatch, unwired phpMyAdmin cookie reconciliation
  - medium items: logout token logging, Host-derived redirects, backchannel status codes, `localStorage` token persistence, WordPress edge cases

## Key decisions captured

> [!warning]
> Several review findings cannot be fully executed without explicit user choices.

- Public URL/TLS boundary must be decided before finishing the HTTPS hardening work.
- Revocation failure semantics must be chosen: redirect to login, deny, or `503`.
- Sensitive session redesign for [[Vault]] and downstream credentials needs a storage pattern decision.
- Jellyfin needs a replacement for the current browser-visible token handoff.
- Stack B still contains an auxiliary dotnet route that may or may not belong in the remediation batch.

## Current state

- No confirmed `CRITICAL` findings were present in the source reviews.
- The first implementation batch should be revocation correctness because it restores the strongest security guarantee with the least dependency on app-specific behavior.
- Secret rotation and browser-session footprint changes are high value but should be scheduled after the storage/rollout decisions are explicit.

## Next steps

1. Get user answers for the blocking design decisions in the plan.
2. Execute the revocation batch first: TTL alignment, fail-closed read path, Redis timeouts, backchannel `5xx`, `sid` audit.
3. Execute localized high fixes next: Jellyfin logout namespace and phpMyAdmin cookie filter wiring.
4. Follow with secrets/session-storage hardening and then transport/redirect cleanup.

## Files changed

- `.omc/plans/test-codex-comparison.md`
- `docs/obsidian/debugging/2026-03-15-cross-stack-review-fix-plan.md`
