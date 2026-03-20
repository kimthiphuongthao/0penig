---
title: Stack A OpenIG code + security review
tags:
  - debugging
  - openig
  - keycloak
  - stack-a
  - code-review
  - security-review
date: 2026-03-14
status: done
---

# Stack A OpenIG code + security review

Context: reviewed Stack A [[OpenIG]] implementation (WordPress + WhoAmI) using 4 sources — subagent security-reviewer, subagent code-reviewer, Codex security review, Codex code review. Full evidence file: `docs/reviews/2026-03-14-stack-a-openig-code-security-review.md`.

## Verdict

Core JWT validation and route ordering are strong. But the surrounding contract — secrets, revocation, transport — has the same structural gaps seen in Stack B and C. These are gateway-pattern defects, not stack-local bugs.

> [!warning] Material issues (HIGH)
> 1. Hardcoded `sharedSecret` + OIDC `clientSecret` in `config.json` and route files
> 2. Redis blacklist TTL (3600s) shorter than `JwtSession.sessionTimeout` (8h) → revocation bypass after 1h
> 3. `SessionBlacklistFilter` fail-open on Redis errors → revoked sessions pass through
> 4. Logout URL logged with full `id_token_hint` → token leakage in logs

## Findings

### F1. Hardcoded secrets

- [[config.json]] — `sharedSecret` hardcoded in plaintext
- Route files — OIDC `clientSecret` hardcoded
- Impact: filesystem or repo access exposes gateway signing material and OIDC credentials

### F2. Revocation fail-open on Redis errors

- [SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy) — catch block calls `next.handle()` → request continues downstream on Redis failure
- Impact: any Redis outage turns SLO enforcement into best-effort

### F3. Revocation TTL mismatch

- [BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy) — writes `blacklist:<sid>` with `EX 3600`
- [config.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/config.json) — `sessionTimeout: 28800` (8h)
- Impact: browser cookie remains valid after Redis entry expires → session revocation window = 1h, not 8h

### F4. Logout URL logged with id_token_hint

- SloHandler logs full redirect URL including `id_token_hint` token value
- Impact: token leakage into log files; Stack C does this correctly (logs only metadata)

### F5. Host-derived redirects

- SloHandler and SessionBlacklistFilter derive redirect targets from inbound `Host` header
- Impact: open redirect risk if nginx does not normalize Host before reaching OpenIG

## Confirmed strengths

- `BackchannelLogoutHandler` JWT validation: RS256, JWKS kid lookup, `iss`/`aud`/`events`/`iat`/`exp` — strong
- Route ordering correct: backchannel/logout routes in `00-*` files before app routes
- WordPress credential injection self-heals on 401

## Review method comparison

| Source | Unique findings |
|---|---|
| Subagent security | WordPress synthetic login failure → unauthenticated proxying risk |
| Subagent code | — |
| Codex security | Plaintext HTTP (OIDC, Vault, JWKS), Redis socket timeouts missing |
| Codex code | Duplicate `wordpress_*` cookie collision; unsafe-method retry drops request body |

**Codex > subagent** in coverage — subagent caught 1 unique finding (synthetic login fail-open), Codex caught 3 additional.

## Files reviewed

- [stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy)
- [stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy)
- [stack-a/openig_home/scripts/groovy/SloHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SloHandler.groovy)
- [stack-a/openig_home/scripts/groovy/VaultCredentialFilter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/VaultCredentialFilter.groovy)
- [stack-a/openig_home/config/config.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/config.json)

> [!tip] Follow-up
> See `docs/reviews/2026-03-14-stack-a-openig-code-security-review.md` for full evidence and source comparison table.
> Fix priority: F2 (fail-open) → F1 (secrets) → F3 (TTL) → F4 (log redaction) → F5 (pinned origins)
