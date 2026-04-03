---
title: Standalone legacy app integration guide shared runtime rewrite
tags:
  - openig
  - documentation
  - shared-infra
  - sso
  - slo
  - session
date: 2026-04-02
status: done
---

# Standalone legacy app integration guide shared runtime rewrite

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Redis]] [[Stack C]]

## Context

Task: update `docs/deliverables/standalone-legacy-app-integration-guide.md` architecture sections so they match `.claude/rules/architecture.md` and the active `shared/` runtime, without touching the auth mechanism taxonomy, Part 2 legacy app discovery checklist, or FAQ.

## What Done

- Rewrote the intro and overview sections to state that `shared/` is the active runtime: `shared-nginx`, `shared-openig-1/2`, `shared-redis`, `shared-vault`, and all 6 apps on port 80 via hostname routing.
- Replaced per-stack Redis language with one `shared-redis` model using per-app ACL users `openig-app1..6` and prefixes `appN:*`.
- Replaced the old dual-layer `JwtSession` + `JSESSIONID` description with route-local `SessionApp1..6`, host-only `IG_SSO_APP1..APP6`, and Redis-backed token-reference offload via `TokenReferenceFilter.groovy`.
- Rewrote SLO and blacklist sections to use `appN:blacklist:*`, `SessionBlacklistFilter.groovy`, and `CANONICAL_ORIGIN_APP1..6` redirects.
- Added a `Shared-runtime controls` subsection covering `StripGatewaySessionCookies.groovy`, canonical origin construction, and unique `token_ref_id_appN`.
- Fixed the reversed App3/App4 labels so App3 = Redmine and App4 = Jellyfin.
- Committed the guide rewrite as `2e953dd`.

> [!success]
> The guide’s architecture narrative now matches the active shared runtime instead of the historical 3-stack deployment.

> [!warning]
> The auth taxonomy, Part 2 discovery checklist, and FAQ were intentionally preserved so the rewrite stayed within the requested scope.

## Decision Rationale

- Keep the rewrite scoped to architecture-facing sections so deployment, session, and SLO descriptions stop drifting without rewriting the stable legacy-auth taxonomy.
- Use `CANONICAL_ORIGIN_APP1..6` and Redis-backed token references as the shared-runtime contract because that is the source of truth in `.claude/rules/architecture.md`.

> [!tip]
> When updating deliverables that still mention `stack-a/`, `stack-b/`, or `stack-c/`, treat those directories as rollback references unless the document is explicitly historical.

## Current State

- `docs/deliverables/standalone-legacy-app-integration-guide.md` now describes `shared/` as the active runtime.
- Auth taxonomy, Part 2 legacy app discovery checklist, and FAQ remain unchanged.
- The requested commit covers only the deliverable guide; this Obsidian note is a separate follow-up artifact.

## Files Changed

- `docs/deliverables/standalone-legacy-app-integration-guide.md`
- `docs/obsidian/how-to/2026-04-02-standalone-legacy-app-integration-guide-shared-runtime-rewrite.md`
