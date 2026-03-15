---
title: Planning workflow uses Planner plus architecture references before Codex execution
tags:
  - decision
  - workflow
  - planner
  - codex
date: 2026-03-15
status: accepted
---

# Planning workflow uses Planner plus architecture references before Codex execution

Applies to planning and execution flow across [[OpenIG]], [[Keycloak]], [[Vault]], and [[Stack C]] work in this repo.

> [!success] Confirmed change
> `.claude/rules/workflow.md` now requires Planner prompts to include both review findings and architecture references, then pass through a read-only Critic loop before implementation.

## Context

The previous planning section was too thin for enterprise gateway work. It did not require architecture references, did not define how Critic output must be normalized, and did not give an execution prompt template for pre-confirmed fixes.

## Decision

1. Planner must receive two inputs: review findings and architecture references (`architecture.md`, `standard-gateway-pattern.md`, `gotchas.md`).
2. Standard flow is `Claude -> Planner -> Critic -> Planner loop until ACCEPT -> Current Task update`.
3. High-stakes cross-validation with Codex is reserved for large or non-reversible plans only.
4. Critic output must be converted into structured findings before being returned to Planner.
5. Pre-confirmed execution tasks must use explicit intent labels and direct implementation prompts.
6. Post-batch documentation updates are mandatory when architecture, controls, or gotchas change.

> [!warning] Gotcha
> Forwarding raw Critic text back to Planner creates noisy revisions. Claude must extract severity, affected step, rationale, and evidence first.

## Files changed

- `/.claude/rules/workflow.md`

## Current state

The planning workflow section has been replaced with the new Planner/Critic/Codex process, and the duplicate numbered line in the later workflow rules block was updated exactly as requested in the edit.

> [!tip] Operational pattern
> Use the execution prompt template only for pre-confirmed fixes that already exist in `.omc/plans/*.md` with acceptance criteria. Investigation tasks should stay outside that template.

## Next steps

1. Use the new workflow wording when generating the next `.omc/plans/*.md` file.
2. If numbering in the later ordered list is meant to be visually normalized in source as well, do a follow-up edit with explicit desired numbering for all items in that block.
