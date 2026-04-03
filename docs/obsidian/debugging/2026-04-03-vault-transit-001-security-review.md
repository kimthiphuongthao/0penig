---
title: VAULT-TRANSIT-001 security review
tags:
  - debugging
  - vault
  - openig
  - security
  - transit
  - review
date: 2026-04-03
status: completed
---

# VAULT-TRANSIT-001 security review

Related: [[OpenIG]] [[Vault]]

## Context

- Requested task: security-review the `VAULT-TRANSIT-001` implementation in read-only mode.
- Scope:
  - `shared/vault/init/vault-bootstrap.sh`
  - `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
  - 11 OpenIG route JSON files that pass Transit/AppRole args into `TokenReferenceFilter.groovy`
- Review method: static inspection only. No gateway, Vault, route, or application files were modified.

## What Done

- Checked all 30 requested items across bootstrap, Groovy, route wiring, and Vault best-practice controls.
- Verified Transit enablement is idempotent and creates `app1-key` through `app6-key` as `aes256-gcm96`.
- Verified per-app Vault policies keep Transit access scoped to each app's own key only.
- Verified `TokenReferenceFilter.groovy` uses AppRole token caching, 403 retry-once behavior, base64 handling for Vault Transit, and fail-closed request handling.
- Verified all 11 routes include the required Transit/AppRole args with the correct app-to-key and app-to-file mappings.
- Identified one configuration gap: `VAULT_ADDR` is not fully environment-driven in the reviewed implementation.

> [!success]
> 29 of 30 checklist items passed. Transit key scoping, route wiring, and fail-closed request behavior are all aligned with the requested implementation contract.

> [!warning]
> `shared/vault/init/vault-bootstrap.sh` hardcodes `VAULT_ADDR=http://127.0.0.1:8200`, and `TokenReferenceFilter.groovy` still carries a hardcoded fallback of `http://vault:8200` when `VAULT_ADDR` is unset. This fails the strict "environment-driven only" requirement from the review checklist.

> [!tip]
> Keep the current one-AppRole-to-one-Transit-key model. It is the main control that preserves per-app token-reference isolation when Redis payloads are shared through one runtime.

## Finding

- `shared/vault/init/vault-bootstrap.sh:4`
  - Exports a fixed `VAULT_ADDR` instead of reading it from the environment.
- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy:237`
  - Reads `VAULT_ADDR`, but falls back to hardcoded `http://vault:8200` for AppRole login.
- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy:267`
  - Reads `VAULT_ADDR`, but falls back to hardcoded `http://vault:8200` for Transit encrypt/decrypt calls.

## Decision

- Treat the review result as "secure enough to continue lab validation" but "not yet fully compliant with the stated environment-driven address rule".
- Do not weaken the current fail-closed behavior in `TokenReferenceFilter.groovy`; only adjust address sourcing.

## Current State

- Transit mount creation and key creation are idempotent.
- App policies are least-privilege for Transit operations and scoped to their own keys.
- The logout/SLO path intentionally degrades to warning-only on decrypt failure while normal request flow remains fail-closed.
- Route wiring is consistent for apps 1 through 6, including the WhoAmI route on `app2`.

## Next Steps

- Change bootstrap to honor `VAULT_ADDR` from the environment instead of overwriting it.
- Remove the hardcoded `http://vault:8200` fallback from `TokenReferenceFilter.groovy`, or make the fallback explicitly match the deployment contract if strict env-only behavior is not required.
- Add a regression check that fails when Transit/AppRole code is reachable without an explicit `VAULT_ADDR`.

## Files Changed

- `docs/obsidian/debugging/2026-04-03-vault-transit-001-security-review.md`
