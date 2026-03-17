# Stack A OpenIG Code + Security Evidence Review

Date: 2026-03-14  
Scope: Evidence consolidation for Stack A OpenIG login/logout/session/revocation mechanisms (planning input, not final pattern design).

## 1) Executive Summary

Four review outputs were consolidated and deduplicated. The strongest agreement is on three HIGH-risk areas: committed session/crypto secrets, fail-open revocation checks, and revocation TTL shorter than session lifetime. MEDIUM-risk consensus exists for logout token leakage in logs; Host-header redirect construction is supported by two security-focused reviews. Additional Codex-only and subagent-only items are kept separate for follow-up validation.

**[UPDATED 2026-03-17]** Current repo state beyond this historical review:
- F1 resolved in STEP-03 (`b738577`) — secrets moved to `.env` / runtime injection.
- F2 resolved in FIX-03 (`278a29c`) — blacklist checks now fail closed with `500` on Redis errors.
- F3 resolved in live state (`9cbf71a`) — blacklist TTL is now aligned with `JwtSession.sessionTimeout: "30 minutes"`.
- F4 resolved in FIX-13 (`a9d2947`) — `id_token_hint` is redacted from logs.
- F5 resolved in FIX-08 (`7fc73ba`) plus Step 5 env rollout (`aaf66d5`) — redirects now use pinned `CANONICAL_ORIGIN_APP*`.

## 2) Review Sources

- Subagent security review: `/private/tmp/claude-501/-Volumes-OS-claude/3ccda1f9-83b3-4928-8a34-5aa3310485e6/tasks/aa0225745b287c59d.output`
- Subagent code review: `/private/tmp/claude-501/-Volumes-OS-claude/3ccda1f9-83b3-4928-8a34-5aa3310485e6/tasks/a3bd12b64c371b643.output`
- Codex security review: `/private/tmp/claude-501/-Volumes-OS-claude/3ccda1f9-83b3-4928-8a34-5aa3310485e6/tasks/b75w2p0wq.output`
- Codex code review: `/private/tmp/claude-501/-Volumes-OS-claude/3ccda1f9-83b3-4928-8a34-5aa3310485e6/tasks/bsrjw4v1d.output`

## 3) Mechanisms Reviewed

- `JwtSession` configuration and key material in `openig_home/config/config.json`
- RP-initiated logout construction and redirect handling in `SloHandler.groovy`
- Backchannel logout JWT validation and revocation write in `BackchannelLogoutHandler.groovy`
- Request-time revocation enforcement in `SessionBlacklistFilter.groovy` and `SessionBlacklistFilterApp2.groovy`
- Supporting credential/session paths (`VaultCredentialFilter.groovy`, `CredentialInjector.groovy`) where they affect session continuity and revocation behavior

**[UPDATED 2026-03-17]** Status as of Pattern Consolidation Steps 2-4: the `SessionBlacklistFilterApp2.groovy` reference above is historical only. Step 2 deleted that app-specific variant and consolidated Stack A revocation checks into the shared parameterized `SessionBlacklistFilter.groovy`.

## 4) Confirmed Strengths

- Backchannel logout JWT validation is consistently noted as strong in subagent reviews: RS256 pinning, JWKS signature verification, and claims checks (`iss`/`aud`/`events`/`iat`/`exp`) in `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`.
- Secrets are not hardcoded in `VaultCredentialFilter.groovy` app credential flow (noted in subagent code review), though transport security concerns remain in Codex-only findings.

## 5) Confirmed Findings

### F1. Committed session/keystore secrets

- Severity: HIGH (4/4 reviews)
- Files (exact refs from sources):
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/config.json:15,25,29`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/config.json:13-15`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/config.json:23-29`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/config.json#L14`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/config.json#L15`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/config.json#L25`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/config.json#L29`
- Issue: PKCS12 password and JwtSession secret material are in repo-managed config.
- Impact: weakens session integrity/confidentiality boundaries and increases blast radius if config is exposed.
- Minimal fix: move all secrets to injected secret sources (env/secret files/Vault refs), rotate exposed values, invalidate existing `IG_SSO` cookies.

### F2. Revocation enforcement fails open on Redis errors

- Severity: HIGH (4/4 reviews)
- Files (exact refs from sources):
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:95-97`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:96-98`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy:157-159`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy:158-162`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy#L96`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy#L158`
- Issue: request path continues when revocation check cannot be completed.
- Impact: revoked sessions can continue during Redis/network faults.
- Minimal fix: fail closed for authenticated sessions (clear local session + reauth/deny) when blacklist status is indeterminate.

**[UPDATED 2026-03-17]** Status as of Pattern Consolidation Steps 2-4: the `SessionBlacklistFilterApp2.groovy` file references in this finding are historical evidence from the 2026-03-14 review. Step 2 deleted that duplicate variant and moved the app2 logic into the shared parameterized `SessionBlacklistFilter.groovy`. The fail-open finding itself remains open unless addressed separately.

### F3. Revocation TTL shorter than OpenIG session lifetime

- Severity: HIGH (3/4 reviews)
- Files (exact refs from sources):
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:305-310`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/config.json:23-29`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/config.json#L24`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy#L305`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy#L310`
- Issue: blacklist key lifetime (`EX 3600`) is shorter than session timeout (`8 hours`).
- Impact: previously revoked session can become usable before session cookie expiry.
- Minimal fix: set revocation TTL >= max session lifetime (or remaining session lifetime + skew).

### F4. Logout URL logging leaks `id_token_hint`

- Severity: MEDIUM (4/4 reviews)
- Files (exact refs from sources):
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SloHandler.groovy:27-32`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SloHandler.groovy:32`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SloHandler.groovy#L31`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SloHandler.groovy#L32`
- Issue: full logout URL containing token hint is logged.
- Impact: token-bearing identity artifacts leak to logs and downstream log systems.
- Minimal fix: never log token-bearing query strings; log only redacted metadata.

### F5. Redirect bases built from inbound `Host`

- Severity: MEDIUM (2/4 reviews)
- Files (exact refs from sources):
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SloHandler.groovy:7-10,24-30`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:85-90`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy:150-155`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SloHandler.groovy#L8`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SloHandler.groovy#L25`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy#L86`
  - `/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy#L151`
- Issue: redirect/public URL construction uses request `Host`.
- Impact: potential open-redirect/domain confusion if boundary host validation is weak.
- Minimal fix: build redirect origins from pinned allowlisted public URL config.

**[UPDATED 2026-03-17]** Status as of Pattern Consolidation Steps 2-4: the `SessionBlacklistFilterApp2.groovy` references above are historical only. Step 2 removed that duplicate file as part of the shared `SessionBlacklistFilter.groovy` consolidation; the Host-derived redirect finding remains open.

## 6) Cross-Review Comparison

- 4/4 agreement: F1 hardcoded secrets, F2 fail-open revocation check, F4 logout token logging.
- 3/4 agreement: F3 revocation TTL mismatch.
- 2/4 agreement: F5 Host-derived redirect bases.

### Codex-only additions (for validation)

- HIGH: plaintext HTTP used for Vault/JWKS/logout/WordPress credential paths (`VaultCredentialFilter.groovy#L26,#L55,#L90`, `BackchannelLogoutHandler.groovy#L18,#L19`, `SloHandler.groovy#L24`, `CredentialInjector.groovy#L54`) from `b75w2p0wq.output`.
- HIGH: app1 blacklist filter lacks socket timeout (`SessionBlacklistFilter.groovy#L76`) from `bsrjw4v1d.output`.
- MEDIUM: possible duplicate `wordpress_*` cookie collision (`CredentialInjector.groovy#L121`) from `bsrjw4v1d.output`.
- MEDIUM: retry redirect on unsafe methods can drop original request body (`CredentialInjector.groovy#L155`) from `bsrjw4v1d.output`.

### Subagent-only findings (for validation)

- MEDIUM: WordPress synthetic login failure path can degrade into unauthenticated proxying (`CredentialInjector.groovy:82-105,120-151,155-167`) from `a3bd12b64c371b643.output`.

## 7) Fix Priority for Stack A

1. P0: close revocation gaps first (F2 + F3) so logout/revocation guarantees hold under fault and over full session lifetime.
2. P0: remove and rotate committed session/keystore secrets (F1) and expire existing sessions.
3. P1: remove token-bearing logout URL logging (F4).
4. P1: pin redirect origin to trusted config, not request headers (F5).
5. P2: validate Codex-only and subagent-only additions before implementation batching.

## 8) Notes for Cross-Stack Comparison Later

- Compare revocation model consistency across stacks: blacklist write TTL, read-path fail policy, and Redis timeout/availability behavior.
- Compare secret handling maturity: repo-managed secrets vs runtime secret injection and rotation procedure.
- Compare logout hygiene: token logging, redirect-origin construction, and backchannel JWT validation parity.
- Keep WordPress/CredentialInjector findings secondary unless the same mechanism exists in other stacks.
