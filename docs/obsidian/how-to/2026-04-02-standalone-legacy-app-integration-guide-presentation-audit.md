---
title: Standalone legacy app integration guide presentation audit
tags:
  - openig
  - documentation
  - presentation
  - audit
  - sso
  - slo
date: 2026-04-02
status: done
---

# Standalone legacy app integration guide presentation audit

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Redis]] [[Stack C]]

## Context

Task: audit `docs/deliverables/standalone-legacy-app-integration-guide.md` for stakeholder-presentation readiness against the current shared runtime baseline.

Reference sources used:

- `.claude/rules/architecture.md`
- `CLAUDE.md` Roadmap -> `Đã hoàn thành`
- `docs/deliverables/standard-gateway-pattern.md` v1.4 dated 2026-04-02

## What Done

- Read the integration guide in full.
- Compared its architecture framing, SLO/session model, and operational details against the current shared-infra baseline.
- Separated findings into outdated statements, missing current-state controls, and sections that still describe validated behavior correctly.

> [!warning]
> The guide is not presentation-ready as a current-state architecture source. Its top-level narrative still describes the historical 3-stack model instead of the active shared runtime.

> [!success]
> Several app-integration patterns remain technically useful: legacy app public URL ownership, app-specific `clientEndpoint`, auth mechanism taxonomy, and legacy login-flow discovery steps.

## Key Findings

- The introduction and architecture sections still say the guide reflects `stack-a`, `stack-b`, and `stack-c` as the running deployment. The active lab deployment is `shared/` with one nginx, two OpenIG nodes, one Redis, and one Vault for all 6 apps.
- Cross-stack SLO is described through separate Redis instances per stack. Current shared infra uses `shared-redis` with per-app ACL isolation and app-scoped key namespaces.
- The session section still describes a `JwtSession` plus server-side `JSESSIONID` dual-layer token model. Current shared runtime uses route-local `JwtSession` plus `TokenReferenceFilter.groovy` to offload heavyweight `oauth2:*` state into Redis.
- The guide omits current shared-runtime controls that matter in stakeholder review: per-app `tokenRefKey`, token-reference offload, `StripGatewaySessionCookies.groovy`, and the fact that `stack-a/`, `stack-b/`, and `stack-c/` are rollback assets rather than the active deployment.
- Some concrete examples are internally inconsistent or presentation-risky, including reversed App3/App4 labels in the URL section and duplicated redirect/logout URI examples without the `:80` variants they claim to show.

## Decisions

- Do not present `standalone-legacy-app-integration-guide.md` as the current-state architecture document.
- Use `docs/deliverables/standard-gateway-pattern.md` as the primary current baseline and keep the integration guide only as a pattern appendix after rewrite.

> [!tip]
> Fastest safe path: rewrite the guide intro, architecture, session/SLO, and environment sections from the shared-runtime baseline, then keep the app-auth taxonomy and discovery appendix with light cleanup.

## Current State

- The guide still contains valuable pattern knowledge for legacy integration.
- Its deployment model and several controls are out of date relative to the validated shared runtime.
- Without rewrite, stakeholders could leave with the wrong mental model about topology, isolation, and revocation flow.

## Next Steps

- Replace all remaining 3-stack framing with the `shared/` runtime baseline.
- Add a short deployment contract section covering `shared-nginx`, `shared-openig-1/2`, `shared-redis`, `shared-vault`, route-local cookies, per-app Redis ACL, per-app Vault AppRole, and `CANONICAL_ORIGIN_APP1..6`.
- Update the session/SLO sections to describe token-reference offload and shared Redis isolation.
- Keep the auth-pattern taxonomy and Part 2 legacy app discovery checklist after correcting examples and app numbering.

## Files Changed

- `docs/obsidian/how-to/2026-04-02-standalone-legacy-app-integration-guide-presentation-audit.md`
