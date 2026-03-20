---
title: 2026-03-20 OpenIG Core Audit Synthesis
tags:
  - debugging
  - audit
  - openig
  - sso
  - slo
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-20
status: complete
---

# 2026-03-20 OpenIG Core Audit Synthesis

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

> [!success]
> Consolidated `docs/audit/2026-03-20-openig-core-audit-synthesis.md` from four independent audit reports and resolved all substantive verdict splits with the required evidence hierarchy.

## Context

- Task: read four independent OpenIG 6 audit reports line by line, cross-reference them, and produce one final synthesis report.
- Inputs:
  - `docs/audit/2026-03-20-openig-core-audit-codex.md`
  - `docs/audit/2026-03-20-openig-core-audit-gemini.md`
  - `docs/audit/2026-03-20-openig-core-audit-architect.md`
  - `docs/audit/2026-03-20-openig-core-audit-architect-v2.md`
- Scope: compare verdicts for the listed claim set (`B1`..`F2`), normalize wording inversions, and resolve ties with evidence weight.

## What was done

- Read all four audit reports in full before drafting output.
- Built a per-claim comparison matrix across Codex, Gemini, Architect v1, and Architect v2.
- Normalized `C2` at the behavior level because all four audits agreed on actual execution order even though one report used inverted wording.
- Resolved non-majority cases with the required hierarchy:
  - Architect v2
  - Codex
  - Architect v1
  - Gemini
- Wrote consolidated report:
  - `docs/audit/2026-03-20-openig-core-audit-synthesis.md`

## Key findings

> [!warning]
> `B3` is the main correctness gap in the original claim set: `user_info` is not serialized into the saved OAuth2 session blob, so `session[oauth2Key].user_info.sub` is a dead-path assumption.

- Final synthesis covered 16 claim IDs, not 15. The task text said 15 claims, but the supplied list contained 16 entries.
- Strongest negative conclusion: `B3` is `REFUTE` based on `OAuth2Session.toJson()` plus the split between `fillTarget()` and `saveSession()`.
- Strongest operability conclusion: `A4` is `REFUTE`; oversized JWT session save is silent state-loss, not a guaranteed hard HTTP 500.
- Strongest architecture clarification: `B4` is `REFUTE`; `target = ${attributes.openid}` writes attributes, while session persistence happens on a separate path.
- Strongest positive conclusion: `C2` is behaviorally confirmed; Redis token-reference offload mutations happen before session save and therefore persist correctly.

> [!tip]
> Future audits should separate "observable behavior is correct" from "the stated mechanism is correct." `B4`, `E1`, and `F2` all split on that distinction.

## Current state

- Consolidated audit report exists and includes:
  - per-claim synthesis table
  - per-claim detail paragraphs
  - conflict register
  - critical findings summary
  - open actions
- No source audit files were modified.
- No gateway config or Groovy code was changed during this task.

## Next steps

1. Remove or annotate dead `session[oauth2Key].user_info.sub` reads in the Jellyfin flow.
2. Update docs/runbooks so `target = ${attributes.openid}` is described as a separate attributes write, not session mirroring.
3. Document `A4` as silent session-save loss and `F2` as `Expires` rather than `Max-Age`.
4. Keep `clientEndpoint` uniqueness and heap name `Session` documented as non-negotiable OpenIG constraints.

## Files changed

- `docs/audit/2026-03-20-openig-core-audit-synthesis.md`
- `docs/obsidian/debugging/2026-03-20-openig-core-audit-synthesis.md`
