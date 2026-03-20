---
title: OpenIG final report v2 ACT anchor verification
tags:
  - openig
  - audit
  - debugging
  - documentation
date: 2026-03-20
status: completed
---

# OpenIG final report v2 ACT anchor verification

Context:
- Follow-up to `docs/audit/2026-03-20-openig-final-report-v2.md` to verify the `ACT-1` / `ACT-2` / `ACT-3` source anchors against the live repo files.
- Goal was to make the planning anchors grepable and line-accurate without changing any [[OpenIG]] route JSON, Groovy filters, [[Keycloak]] config, or [[Vault]] runtime wiring.
- Read the full contents of the final audit report plus the three referenced source documents before editing the report.

> [!success] Verified result
> `docs/audit/2026-03-20-openig-final-report-v2.md` now names exact section headings, exact searchable source text, and verified source line numbers for all three ACT items.

## What was verified

- `ACT-1`: `docs/deliverables/legacy-auth-patterns-definitive.md` contains `## Template-Based Integration` at line `329`; the exact `Validated session note...` sentence exists at line `337`. The prior report string was not an exact match because it dropped the source-file backticks.
- `ACT-2`: `.claude/rules/architecture.md` contains `## clientEndpoint namespace (MỖI app trong cùng OpenIG instance PHẢI unique)` at line `25`, but the prior report used descriptive prose instead of literal source text. The stable grep anchor is the table header `| Stack | App | clientEndpoint | Keycloak client |` at line `27`.
- `ACT-3`: `docs/deliverables/standard-gateway-pattern.md` needs two anchors, not one narrative description. Anchor 1 is `### 1. Revocation Contract` at line `74` with the `What it is: Redis blacklist TTL...` paragraph at line `77`. Anchor 2 is `### Session and revocation` at line `259` with the checklist bullet ``- [ ] `BackchannelLogoutHandler` writes ...`` at line `261`.

## Decisions

> [!tip] Anchor format
> Planning-grade documentation anchors should always include both the exact heading text and a literal grep target from the current file. Narrative location descriptions are too loose for handoff.

- Kept all insertion and replacement text unchanged; only the location anchors in `## Open Action Items` were corrected.
- Left the earlier `Refuted Claims - Action Required` prose untouched because the task scope was limited to the planning anchors in the `ACT-*` section.

## Current state

- `docs/audit/2026-03-20-openig-final-report-v2.md` now carries verified anchors for `ACT-1`, `ACT-2`, and `ACT-3`.
- The target documentation files themselves remain unchanged.
- No runtime configuration or gateway implementation files were modified.

## Next steps

> [!warning] Remaining work
> The deliverable documents still need the actual `ACT-1`, `ACT-2`, and `ACT-3` content changes. This task only fixed the planning references.

- Apply `ACT-1` in `docs/deliverables/legacy-auth-patterns-definitive.md`
- Apply `ACT-2` in `.claude/rules/architecture.md`
- Apply `ACT-3` in `docs/deliverables/standard-gateway-pattern.md`

## Files changed

- `docs/audit/2026-03-20-openig-final-report-v2.md`
- `docs/obsidian/debugging/2026-03-20-openig-final-report-v2-act-anchor-verification.md`
