---
title: Kubernetes Vault auth secret_id rotation review
tags:
  - docs
  - audit
  - vault
  - kubernetes
  - openig
  - shared-infra
date: 2026-04-02
status: completed
---

# Kubernetes Vault auth secret_id rotation review

Related: [[OpenIG]] [[Vault]] [[Keycloak]] [[Stack C]]

## Context

Reviewed the `Production deployment on Kubernetes` section in `docs/deliverables/standard-gateway-pattern.md` v1.4 against the active lab hardening documented in `.claude/rules/architecture.md`.

Reference lab values:

- `token_ttl=1h`
- `token_max_ttl=4h`
- `secret_id_ttl=72h`

The production review focused on the failure mode where `VaultCredentialFilter.groovy` must re-authenticate to Vault after its cached Vault token expires, but the mounted AppRole `secret_id` has already aged out.

> [!warning]
> The current lab AppRole model is not production-safe for long-running Kubernetes workloads. After `secret_id_ttl=72h`, Vault-backed downstream login flows can no longer obtain a fresh Vault token until an operator rotates the AppRole material.

## What Was Checked

- `docs/deliverables/standard-gateway-pattern.md`
- `.claude/rules/architecture.md`
- `shared/openig_home/scripts/groovy/VaultCredentialFilter.groovy`

## Findings

- The v1.4 section does acknowledge the lab gap by stating that AppRole credential rotation is manual and tied to `secret_id` regeneration every `72h`.
- The section proposes direct Kubernetes auth from [[OpenIG]] to [[Vault]] by replacing `/auth/approle/login` with `/auth/kubernetes/login`.
- That change removes `secret_id` from the authentication path, so it addresses the narrow AppRole expiry problem.
- The section is still incomplete for production because it does not document the full token lifecycle:
  - it treats Kubernetes ServiceAccount token rotation as a fixed `1h` behavior
  - it does not explain that [[OpenIG]] must still renew or re-authenticate when the Vault token expires
  - it recommends `audience: vault` without also requiring a projected ServiceAccount token volume carrying that audience
- The example keeps one ServiceAccount `openig-sa` for all app roles. That preserves per-role Vault policies, but it is not equivalent to distinct per-app workload identities.

> [!tip]
> If the goal is to eliminate application-managed Vault login and token renewal, prefer Vault Agent Sidecar or Vault Agent Injector with Kubernetes auth, and let [[OpenIG]] consume rendered secrets instead of implementing the Vault auth lifecycle itself.

> [!success]
> The production direction is correct at a high level: do not carry the lab AppRole `secret_id` pattern into Kubernetes.

## Recommended Doc Corrections

- Explicitly state that AppRole `secret_id` rotation is a lab-only operational exception and must not be the steady-state Kubernetes pattern.
- Replace the `Credential rotation` wording so it says Kubernetes auth removes `secret_id`, but ServiceAccount token lifetime is cluster or pod configured rather than a fixed `1h`.
- Add a requirement for a projected ServiceAccount token with `audience: vault` if the role enforces that audience.
- Add one operational note that direct `auth/kubernetes/login` from [[OpenIG]] still requires Vault token renewal or re-login on expiry.
- Add a stronger production recommendation for Vault Agent Sidecar or Injector when the team wants automatic auth and secret refresh without custom [[OpenIG]] Vault-auth code.

## Current State

- No gateway routes, Groovy scripts, or runtime files were modified.
- Review output is a documentation correction recommendation only.

## Files Changed

- Added this note only: `docs/obsidian/debugging/2026-04-02-k8s-vault-auth-secret-id-rotation-review.md`
