---
title: Cross-Stack Review Summary Rewrite
tags:
  - openig
  - security-review
  - cross-stack
  - obsidian
date: 2026-03-14
status: done
---

# Cross-Stack Review Summary Rewrite

## Context

Rebuilt the cross-stack summary for [[OpenIG]] SSO/SLO review evidence using only:

- `docs/reviews/2026-03-14-stack-a-openig-code-security-review.md`
- `docs/reviews/2026-03-14-stack-b-openig-code-security-review.md`
- `docs/reviews/2026-03-14-stack-c-openig-code-security-review.md`

The rewrite had to preserve source fidelity for findings, severities, stack attribution, and review-method differences across [[Stack A]], [[Stack B]], and [[Stack C]].

> [!success] Confirmed outcome
> `docs/reviews/2026-03-14-cross-stack-review-summary.md` now reflects only source-backed findings from the three review files.

## What Done

- Read all three stack review files end to end before editing the summary.
- Replaced the old cross-stack summary with the required structure:
  - Universal Findings
  - Stack-Specific Findings
  - Login Mechanism Pattern Risk Matrix
  - Recommended Standard Pattern
  - Review Method Comparison
  - Next Steps
- Mapped Stack A and Stack B review-method coverage from their review-source comparison sections.
- Recorded Stack C as Codex-only (`gpt-5.4`) with no internal source-comparison table.

## Decisions

- Treated only source-backed, cross-confirmed items as universal table rows:
  - committed secrets
  - revocation TTL mismatch
  - fail-open revocation
  - `Host`-derived redirect/public URL behavior
- Kept plaintext HTTP and Redis timeout gaps in the summary, but noted that Stack A carries them only in `§6 Codex-only additions`, while [[Stack B]] and [[Stack C]] list them as direct findings.
- Grouped two-stack findings where the same pattern appeared in both reviews:
  - browser-bound `JwtSession` storing sensitive backend material
  - backchannel internal failures returned as HTTP 400

> [!warning] Source-bound constraint
> The summary intentionally avoids inventing cross-stack findings that were not explicitly present in the three source reviews. This matters for [[Keycloak]] logout semantics, [[Vault]] credential handling, and adapter-pattern comparisons.

## Current State

- Cross-stack review summary is aligned to the three dated source reviews.
- Review-method section now distinguishes:
  - Stack A: 4 sources
  - Stack B: 4 sources
  - Stack C: Codex-only review
- Pattern matrix now covers:
  - OIDC Standard
  - Credential Injection
  - Token Injection + `localStorage`
  - Trusted Header Injection
  - HTTP Basic Auth Injection

## Next Steps

- Use the rewritten summary as the baseline input for reusable gateway-pattern design work in [[OpenIG]].
- Validate Stack A validation-only items if they are promoted into implementation work:
  - plaintext HTTP
  - Redis socket timeout gap
  - WordPress adapter edge cases
- Keep future cross-stack summaries tied to the per-stack review files instead of prior summaries.

> [!tip] Best practice
> Rebuild comparison documents from stack review artifacts directly. Do not cascade summaries from earlier summaries when standardizing gateway controls.

## Files Changed

- `docs/reviews/2026-03-14-cross-stack-review-summary.md`
- `docs/obsidian/debugging/2026-03-14-cross-stack-review-summary-rewrite.md`
