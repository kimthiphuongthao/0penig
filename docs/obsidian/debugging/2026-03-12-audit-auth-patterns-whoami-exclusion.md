---
title: "Audit Auth Patterns - Exclude WhoAmI Diagnostic Utility"
tags:
  - sso-lab
  - audit
  - documentation
  - debugging
date: 2026-03-12
status: completed
---

# Context

Updated `docs/audit-auth-patterns.md` to treat WhoAmI as a diagnostic utility, not a production app in audit scoring.

Related systems: [[OpenIG]], [[Keycloak]], [[Vault]], [[Stack C]]

# What Changed

- Per-App Inventory: added explicit note that WhoAmI is a diagnostic utility and out of scope.
- Control Score Matrix: replaced WhoAmI scoring row with `N/A — diagnostic tool, not scored`.
- Gap Summary: removed all WhoAmI gap rows.
- Audit Questions: replaced WhoAmI gap-oriented entries with `WhoAmI excluded — diagnostic utility`.
- Overall Score: recalculated to exclude WhoAmI from denominator and totals (`31 / 50`).

> [!success] Confirmed Working
> The document now excludes WhoAmI from scoring and gap reporting while keeping all other app entries intact.

> [!warning] Scope Boundary
> This is a documentation scope correction only. No OpenIG route or Groovy runtime behavior was changed.

> [!tip] Best Practice
> Keep diagnostic and test utilities explicitly marked out of production audit scope to avoid skewing control maturity scores.

# Decision Rationale

- WhoAmI (`whoami-a.sso.local`) is used for failover and identity header inspection.
- Including it in production control scoring diluted comparative risk posture for production-target apps.
- Excluding it keeps matrix and gaps aligned with deployable business apps.

# Current State

- `docs/audit-auth-patterns.md` reflects WhoAmI as out of scope for scoring.
- Overall score now reflects five scored apps only.

# Files Changed

- `docs/audit-auth-patterns.md`
