---
title: SSO Workflow Security Assessment
tags:
  - debugging
  - security
  - openig
  - keycloak
  - vault
date: 2026-03-12
status: done
---

# SSO Workflow Security Assessment

## Context

Synthesized two sources into one gateway-focused assessment:

- Codex repo review for code evidence in `stack-a/`, `stack-b/`, `stack-c/`
- Gemini architecture review for standards alignment and control gaps

Primary output: `docs/sso-workflow-security.md`

Related systems: [[OpenIG]], [[Keycloak]], [[Vault]], [[Stack A]], [[Stack B]], [[Stack C]]

## What Done

- Verified gateway evidence for all 10 hops `H1-H10`
- Wrote hop-by-hop status table with `file:line` anchors
- Added priority-ordered critical gaps with OpenIG-side remediation only
- Added cross-cutting analysis for lifecycle mismatch, failure cascade, orphan sessions, and audit trail

> [!success]
> The assessment now separates what is implemented in code from what is still a standards or architecture gap.

## Key Findings

> [!warning]
> `H8` is the top issue: `logout_token` is decoded but not cryptographically validated before Redis blacklist writes. This makes forged backchannel logout possible if the endpoint is reachable.

> [!warning]
> `H10` is the main resilience/security conflict: Redis read failure logs a warning and allows the request through, so SLO enforcement is effectively disabled during Redis outage.

> [!warning]
> `H4` still uses plaintext Vault transport and caches Vault-derived state inside `JwtSession`-backed gateway session data.

- `H3`: every reviewed `OAuth2ClientFilter` route keeps `requireHttps: false`
- `H9`: blacklist TTL is fixed at `3600`, not tied to session lifetime
- `H1` / `H5`: Stack C strips sensitive inbound headers, Stacks A and B do not show the same hardening

## Decisions

- Kept all recommendations within gateway scope only: nginx, OpenIG routes, Groovy handlers, Vault listener/config
- Did not recommend target-app code or app-local config changes
- Treated unverified items explicitly as unverified instead of overstating certainty

> [!tip]
> For the next remediation pass, implement JWT validation for backchannel logout before touching transport or lifecycle tuning. It removes the only `CRITICAL` integrity gap in the chain.

## Current State

- Assessment document exists and is evidence-backed
- Priority order is clear: `H8` -> `H10` -> `H4` -> `H3`
- Repo still shows fragmented app-specific logout handling rather than one centralized policy layer

## Next Steps

1. Add JWKS-based `logout_token` validation in the OpenIG Groovy backchannel handler
2. Change blacklist enforcement from fail-open to explicit deny/degraded mode on protected routes
3. Turn on Vault TLS and update gateway trust material
4. Move all OIDC-facing nginx/OpenIG paths to HTTPS-only
5. Rework revocation TTL to match validated session/token expiry

## Files Changed

- `docs/sso-workflow-security.md`
- `docs/obsidian/debugging/2026-03-12-sso-workflow-security-assessment.md`
