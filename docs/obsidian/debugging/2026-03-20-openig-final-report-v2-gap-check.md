---
title: OpenIG final report v2 gap check
tags:
  - openig
  - audit
  - debugging
  - documentation
date: 2026-03-20
status: completed
---

# OpenIG final report v2 gap check

Context:
- Follow-up to the three-file comparison between the Codex primary audit, the architect v2 primary audit, and the existing final report.
- Goal was to make the [[OpenIG]] audit output self-sufficient for planning work on the SSO/SLO path in front of [[Keycloak]].
- No runtime stack, [[Vault]], route JSON, or Groovy files were modified in this task.

> [!success] Output
> Wrote `docs/audit/2026-03-20-openig-final-report-v2.md` with exact class/method/line citations, 1-3 line inline snippets, precise `ACT-1` / `ACT-2` / `ACT-3` doc edits, and a final evidence index.

## What changed

- Added a `Gap Summary` section to capture what the previous final report missed or understated.
- Expanded the `Final Verdict Table` so every claim now names the exact OpenIG class, method, line range, and inline snippet.
- Upgraded the `Confirmed Claims` section so every entry ends with an explicit evidence reference.
- Reworked the `B4`, `E1`, and `F2` action items into implementation-ready doc tasks with exact target file, section, current text or insertion point, replacement text, and evidence.
- Added an `Evidence Index` so a follow-on agent can trace any claim without reopening the upstream audit notes.

## Decisions

> [!tip] Documentation baseline
> Use `docs/audit/2026-03-20-openig-final-report-v2.md` as the planning baseline for future [[OpenIG]] documentation work, not the older final report.

- Kept the verdict count unchanged at 11 confirmed and 5 refuted; this pass hardened evidence and task precision rather than re-adjudicating the claims.
- Kept `B1` as `CONFIRM`, but made the `OAuth2Utils.buildUri()` URI-resolution nuance explicit so future docs do not misdescribe the key derivation as naive string concatenation.
- Anchored `E1` on route-local `OAuth2ClientFilter` matching and route order, and explicitly noted that the architect field quote `private Expression<String> clientEndpoint;` has no line number in the primary audits.

## Current state

- `docs/audit/2026-03-20-openig-final-report-v2.md` is now self-contained for planning and evidence lookup.
- `docs/audit/2026-03-20-openig-final-report.md` was left unchanged, per task instructions.
- The next documentation pass can execute `ACT-1`, `ACT-2`, and `ACT-3` without reopening the two primary audit files.

## Next steps

> [!warning] Remaining work
> The target docs named by `ACT-1`, `ACT-2`, and `ACT-3` are still unmodified. This task only produced the planning-grade v2 report.

- Update `docs/deliverables/legacy-auth-patterns-definitive.md`
- Update `.claude/rules/architecture.md`
- Update `docs/deliverables/standard-gateway-pattern.md`

## Files changed

- `docs/audit/2026-03-20-openig-final-report-v2.md`
- `docs/obsidian/debugging/2026-03-20-openig-final-report-v2-gap-check.md`

