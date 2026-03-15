---
title: Cross-Stack OpenIG Fix Plan
tags:
  - openig
  - sso
  - planning
  - security
date: 2026-03-15
status: ready
---

# Cross-Stack OpenIG Fix Plan

Context: built a consolidated execution plan from the three review inputs for [[OpenIG]] gateway hardening across [[Stack A]], [[Stack B]], and [[Stack C]].

Primary output: `.omc/plans/test-codex-comparison-2.md`

## What Was Done

- Read and merged the review findings for Stack A, Stack B, and Stack C.
- Normalized the work into standalone fix packages so each item can be executed in one conversation without hidden prior context.
- Grouped fixes by `CRITICAL -> HIGH -> MEDIUM`, with exact gateway-side file paths, required changes, and independently verifiable acceptance criteria.
- Added execution ordering based on dependency chains between revocation, session-state boundaries, transport, and adapter-specific cleanup.

> [!success] Confirmed direction
> The plan keeps all remediation strictly on the gateway side: OpenIG routes, Groovy scripts, `nginx/nginx.conf`, and secret/bootstrap wiring only.

## Key Decisions Captured

- Revocation correctness comes first: TTL alignment, fail-closed reads, Redis timeouts, and correct backchannel status codes must land before adapter-specific cleanup.
- The reference pattern must cover all gateway adapter styles:
  - form injection
  - token injection
  - header injection
  - HTTP Basic Auth injection
- Trust-boundary violations were treated as first-class plan items:
  - committed secrets
  - browser-stored backend credentials/tokens
  - inbound `Host`-derived redirects
  - plaintext browser-facing URLs

> [!warning] User input still required
> The plan cannot finish implementation safely without decisions on secret source, canonical public origins, TLS scope, fail-closed response semantics, and the server-side store for privileged adapter state.

## Current State

- Consolidated plan is ready for execution.
- No app-side changes are required by the plan.
- Several retained findings are marked validation-first for their execution conversations, especially around Stack A WordPress adapter behavior and Stack B browser token handling.

## Next Steps

- Get user decisions for secret source, fail-closed behavior, public origins, TLS scope, and server-side state storage.
- Start with the revocation batch before any transport or adapter polish.
- Execute the stack-specific fixes for Jellyfin logout and phpMyAdmin cookie reconciliation immediately after the revocation foundation.

> [!tip] Recommended execution order
> `H2 -> H3 -> M2 -> M3 -> M6`, then `H4 -> H7 -> M4`, then `H1 -> H6 -> M5`, and only then `H5 -> M1 -> M7/M8/M9`.

## Files Changed

- `.omc/plans/test-codex-comparison-2.md`
