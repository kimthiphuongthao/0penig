---
title: Board presentation architecture source inventory
tags:
  - openig
  - documentation
  - architecture
  - presentation
  - sso
  - slo
date: 2026-04-02
status: done
---

# Board presentation architecture source inventory

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Redis]] [[Stack C]]

## Context

Task: search `docs/deliverables`, `docs/obsidian`, `docs/audit`, `docs/reference`, and `docs/historical` for files that describe the overall SSO/SLO solution architecture in a form usable for stakeholder or defense presentation.

Evaluation criteria:

- executive summary
- architecture overview or component description
- integration patterns across legacy apps
- business-value framing
- suitability for a board or stakeholder deck

## What Done

- Read the current architecture-facing deliverables, audit summaries, reference docs, and Obsidian stack/decision notes.
- Compared active shared-runtime docs against older 3-stack and migration-oriented documents.
- Ranked the files by usefulness for a stakeholder presentation instead of by technical depth alone.

> [!success]
> Best current architecture baseline: `docs/deliverables/standard-gateway-pattern.md`. It is the only strong candidate that matches the active shared runtime and explains the core gateway contract across all 6 apps.

> [!warning]
> Strongest Vietnamese narrative sources are not current. `docs/deliverables/standard-gateway-pattern-vi.md` and `docs/deliverables/standalone-legacy-app-integration-guide.md` still describe the older 3-stack model, so they should not be used directly for a defense presentation without rewrite.

## Key Findings

- `docs/deliverables/standard-gateway-pattern.md`
  - Best source for the active solution architecture: shared nginx, 2 OpenIG nodes, shared Redis, shared Vault, shared Keycloak, per-app session and secret isolation, login pattern coverage, and SLO flow.
- `docs/deliverables/standard-gateway-pattern-vi.md`
  - Best Vietnamese structure for presentation narrative, but outdated topology and controls relative to the shared runtime.
- `docs/deliverables/standalone-legacy-app-integration-guide.md`
  - Clearest text diagram and component listing, but tied to historical 3-stack deployment details.
- `docs/audit/2026-03-25-production-readiness-audit.md`
  - Strong executive-state summary for current maturity and open risks; better for a readiness slide than an architecture slide.
- `docs/audit/2026-03-16-pre-packaging-audit/00-executive-summary.md`
  - Concise leadership summary, but historical and still 3-stack oriented.
- `docs/deliverables/sso-workflow-security.md`
  - Useful hop-by-hop workflow diagram and component-role table; security framing is strong, business framing is weak.
- `docs/reference/why-sso-works.md` and `docs/reference/why-redis-slo.md`
  - Good explainer notes for simple SSO/SLO slides, but not sufficient as the main architecture source.
- `docs/obsidian/stacks/stack-shared.md`
  - Useful status note for the active shared runtime, but not polished enough for stakeholder presentation.

## Decisions

- Use `standard-gateway-pattern.md` as the primary technical source if building slides now.
- Use audit executive summaries only as supporting evidence for readiness, not as the main architecture narrative.
- Do not use the Vietnamese or integration-guide documents as-is until they are aligned to the shared runtime.

> [!tip]
> If a Vietnamese defense deck is needed quickly, the fastest safe path is to rewrite `standard-gateway-pattern-vi.md` from the current English shared-runtime baseline, then add one board-level page for business value, one page for risk posture, and one page for rollout scope.

## Current State

- The repo has enough material to assemble a strong technical presentation.
- The repo does not have one single board-ready document that is both current and management-oriented.
- Business-value framing is still thin inside the searched subdirectories; most files explain correctness, controls, and risks rather than organizational value, adoption path, or operating model impact.

## Next Steps

- Create one current Vietnamese architecture summary derived from `standard-gateway-pattern.md`.
- Add a board-facing executive summary slide/note covering problem, solution shape, benefits, risks, and rollout recommendation.
- Separate clearly between active shared-runtime documents and historical 3-stack references.

## Files Changed

- `docs/obsidian/how-to/2026-04-02-board-presentation-architecture-source-inventory.md`
