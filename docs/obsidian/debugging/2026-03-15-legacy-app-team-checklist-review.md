---
title: Legacy App Team Checklist Review
tags:
  - sso-lab
  - docs
  - review
  - debugging
date: 2026-03-15
status: open
---

# Legacy App Team Checklist Review

## Context

Reviewed `docs/deliverables/legacy-app-team-checklist.md` as the primary app-team deliverable for legacy SSO onboarding into [[OpenIG]], [[Keycloak]], [[Vault]], and Redis-backed logout handling.

Cross-referenced against:

- `docs/deliverables/legacy-survey-checklist.md`
- `docs/reference/legacy-app-code-changes.md`
- `stack-a/openig_home/config/routes/*.json`
- `stack-b/openig_home/config/routes/*.json`
- `stack-c/openig_home/config/routes/*.json`
- `stack-a/openig_home/scripts/groovy/*.groovy`
- `stack-b/openig_home/scripts/groovy/*.groovy`
- `stack-c/openig_home/scripts/groovy/*.groovy`
- `stack-a/docker-compose.yml`
- `stack-b/docker-compose.yml`
- `stack-c/docker-compose.yml`

## What Was Confirmed

- The checklist does cover the 4 verified integration patterns: Form, Token, Header, Basic.
- Exact forbidden internal terms such as `OAuth2ClientFilter`, `SessionBlacklistFilter`, `JwtSession`, `Groovy`, `RESP`, `Redis blacklist`, and `backchannel` do not appear verbatim in the deliverable.
- Header-mode and Basic-mode app-side config claims match the lab:
  - Grafana requires auth proxy env vars.
  - phpMyAdmin requires mounted config file with `auth_type=http`.

> [!warning]
> The document still contains gateway-internal concepts in plain language, especially around logout internals and gateway operations. It is cleaner than gateway-team docs, but not yet fully app-team-native.

## Key Findings

### 1. SLO explanation overpromises behavior

- The checklist says logout is "broadcast" to all apps and has no delay.
- Actual flow is: app logout clears local session, redirects to Keycloak logout, then other apps are forced out on their next request when logout state is detected.
- This matters because testers will otherwise expect tab B to die instantly without refresh.

### 2. Credential handoff instructions are too generic

- The checklist asks app teams to send `username:password`.
- Actual mapping varies by app:
  - WordPress maps by `preferred_username`
  - Redmine lookup is keyed by email
  - Jellyfin lookup is keyed by email but login uses `preferred_username`
  - phpMyAdmin derives lookup key from token claims
- App teams cannot prepare Vault-ready data correctly from the current instructions alone.

### 3. Subpath guidance is too optimistic

- The checklist frames subpath as mainly "tell app its base path".
- Lab evidence shows some apps need infrastructure/image work as well:
  - WordPress needed Apache `Alias` plus entrypoint change.
  - Redmine subpath required `config.ru` wrapping because the standard image did not support the subpath case directly.

### 4. Login questionnaire loses important edge cases

- The simplified checklist removed fields present in the gateway survey that are still relevant to app teams:
  - extra login fields
  - multi-step login
  - trusted header restrictions
  - user creation / password policy constraints
- This will cause rework during kickoff for anything beyond a simple username/password form.

## Verdict

> [!warning]
> Verdict: `NEEDS_WORK`
>
> Good direction and mostly correct pattern coverage, but not yet reliable enough as the primary zero-gateway-knowledge onboarding checklist.

## Recommended Changes

> [!tip]
> Keep the file app-team-facing by replacing mechanism detail with concrete asks:
> pattern, login URL, logout URL, claim-to-account mapping, provisioning method, required app config, and expected effort band.

- Rewrite the SLO section to describe user-visible behavior only.
- Replace the generic credential handoff step with a small mapping table:
  - Keycloak identity field used
  - app account identifier
  - password source/reset method
  - users pre-created or auto-created
- Update subpath guidance to say some apps require image/config changes, not just base-path config.
- Add risk flags from the survey doc for extra fields, multi-step login, and password policy.
- Add FAQ entries for:
  - "Do app accounts need to exist first?"
  - "Should mapping use username or email?"
  - "What if we cannot bulk reset passwords?"

## Current State

- No production config changed.
- No app or gateway code changed.
- Review only.
