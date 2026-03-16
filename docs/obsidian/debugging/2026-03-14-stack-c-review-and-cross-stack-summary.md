---
title: Stack C review and cross-stack summary
tags:
  - debugging
  - openig
  - keycloak
  - vault
  - stack-c
  - review
date: 2026-03-14
status: done
---

# Stack C review and cross-stack summary

Context: produced primary review evidence for [[OpenIG]] Stack C and then consolidated the three stack review artifacts to identify reusable SSO/SLO gateway requirements across [[Stack C]], Stack A, and Stack B.

## What was done

- Wrote `docs/reviews/2026-03-14-stack-c-openig-code-security-review.md`.
- Wrote `docs/reviews/2026-03-14-cross-stack-review-summary.md`.
- Reviewed Stack C gateway code for logout, backchannel logout, revocation enforcement, Grafana trusted-header injection, phpMyAdmin HTTP Basic Auth injection, and [[Vault]] credential retrieval.

## Main findings

> [!warning] Recurring cross-stack defects
> The same three issues now recur across Stack A, Stack B, and [[Stack C]]:
> 1. committed secrets in gateway config or route files
> 2. revocation TTL shorter than the OpenIG session lifetime
> 3. fail-open revocation checks when Redis is unavailable

> [!warning] Stack C-specific evidence
> Stack C adds two important adapter-pattern findings:
> 1. ~~`vault_token` and phpMyAdmin credentials are stored in the browser-bound `JwtSession`~~ **RESOLVED (FIX-09, commits 76b648a + c0c491d)**: Vault token → `globals` cache; phpMyAdmin creds → transient `attributes`
> 2. `PhpMyAdminCookieFilter.groovy` exists to expire stale downstream cookies but is not wired into `11-phpmyadmin.json`

> [!success] Confirmed strong area
> `BackchannelLogoutHandler.groovy` remains the strongest implementation area across stacks: RS256 pinning, JWKS resolution, RSA signature verification, and `iss`/`aud`/`events`/`iat`/`exp` checks are all present.

## Why this matters for the standard pattern

- [[OpenIG]] cannot be standardized from these stacks by copying route files as-is.
- The reusable pattern needs server-side storage for privileged adapter state instead of browser-carried session contents.
- HTTPS, pinned public origins, and bounded Redis failure behavior need to be part of the baseline contract, not deployment-specific assumptions.

## Files changed

- `docs/reviews/2026-03-14-stack-c-openig-code-security-review.md`
- `docs/reviews/2026-03-14-cross-stack-review-summary.md`

## Next steps

- Convert the repeated findings into a formal gateway pattern checklist for revocation, secret handling, transport security, and adapter contract requirements.
- Use Stack C as the evidence case for mandatory downstream cookie-cleanup controls in HTTP Basic Auth injection patterns.

> [!tip]
> When documenting the reusable pattern, keep the backchannel JWT validation approach and route-ordering discipline, but redesign the session-storage and revocation semantics around them.
