---
title: LAB-EXCEPTION doc coverage for JwtSession and Vault hardening
tags:
  - debugging
  - openig
  - vault
  - documentation
  - tracking
date: 2026-03-23
status: done
---

# LAB-EXCEPTION doc coverage for JwtSession and Vault hardening

Context: closed the 4 remaining `LAB-EXCEPTION` backlog rows in `docs/fix-tracking/master-backlog.md`: `C-2/S-1`, `S-11/S-12/M-7`, `M-8/S-13`, and `M-7/S-11`. This was a documentation-only task for [[OpenIG]], [[Keycloak]], and [[Vault]]; the runtime lab behavior is unchanged.

## What changed

> [!success] JwtSession transport exception documented
> Updated `docs/deliverables/standard-gateway-pattern.md` and `docs/deliverables/standalone-legacy-app-integration-guide.md` so the browser `JwtSession` / `IG_SSO*` cookie is explicitly marked as an HTTP-only lab exception. Both docs now state that production must complete nginx TLS termination before enabling `Secure` cookies and `requireHttps: true`.

> [!success] Vault hardening plan made explicit
> Updated `docs/reference/vault-hardening-gaps.md` with explicit production actions for Vault listener TLS, Vault UI restriction or disablement, CIDR-bound AppRole, and Raft HA. Updated `docs/deliverables/legacy-app-team-checklist.md` with a Vault production-readiness subsection covering the same controls plus full bootstrap secret rotation.

> [!success] Lab seed warning added to bootstrap scripts
> Added a comment block near the top of `stack-a`, `stack-b`, and [[Stack C]] `vault-bootstrap.sh` scripts marking the embedded passwords as deterministic lab seed data that must be rotated before production.

## Decision

> [!warning] Closing the rows does not mean the lab is production-ready
> The backlog rows are `DONE` because the exception and the required production hardening actions are now explicit in the deliverables. No TLS, Vault UI, CIDR, or Raft implementation change happened in this task.

> [!tip] Keep LAB-EXCEPTION rows documentation-complete
> A `LAB-EXCEPTION` item is only actually closed when the risk, the production action, and the readiness checklist entry all exist in the deliverable docs.

## Current state

- The 4 backlog rows were moved from `OPEN` to `DONE`.
- Requested atomic commit created: `daba0ae`.
- The committed backlog rows still have empty `Commit hash` cells because the final Git object ID cannot be embedded inside the same commit that defines it.

## Next steps

1. If `docs/fix-tracking/master-backlog.md` must literally carry a commit ID, add `daba0ae` in a follow-up tracking-only edit.
2. When HTTPS is implemented, remove the `JwtSession` lab-exception wording instead of leaving stale exceptions in the deliverables.
3. When [[Vault]] is moved toward production shape, replace the readiness bullets with concrete runbook or completed implementation references.

## Files changed

- `docs/deliverables/standard-gateway-pattern.md`
- `docs/deliverables/standalone-legacy-app-integration-guide.md`
- `docs/reference/vault-hardening-gaps.md`
- `docs/deliverables/legacy-app-team-checklist.md`
- `stack-a/vault/init/vault-bootstrap.sh`
- `stack-b/vault/init/vault-bootstrap.sh`
- `stack-c/vault/init/vault-bootstrap.sh`
- `docs/fix-tracking/master-backlog.md`
- `docs/obsidian/debugging/2026-03-23-lab-exception-doc-coverage-tls-vault-hardening.md`
