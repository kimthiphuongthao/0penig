---
title: K8s gateway pattern documentation fix
tags:
  - docs
  - debugging
  - vault
  - kubernetes
  - openig
  - deliverables
date: 2026-04-02
status: completed
---

# K8s gateway pattern documentation fix

Related: [[OpenIG]] [[Vault]] [[Keycloak]] [[Stack C]]

## Context

Updated the Kubernetes production section in both deliverable docs after the prior review note identified a missing token-lifecycle explanation around Kubernetes auth and AppRole `secret_id` expiry.

Files updated:

- `docs/deliverables/standard-gateway-pattern.md`
- `docs/deliverables/standard-gateway-pattern-vi.md`

## Root Cause

The v1.4 text was directionally correct about preferring Kubernetes auth over AppRole, but it still left three production gaps:

- it documented ServiceAccount token lifetime as a fixed `1h`
- it did not state the concrete failure mode after AppRole `secret_id` expiry
- it did not recommend Vault Agent Sidecar or Vault Agent Injector as the preferred long-lived production pattern

> [!warning]
> Direct `auth/kubernetes/login` from [[OpenIG]] still leaves Vault token renewal and re-authentication inside application-owned code unless a Vault Agent pattern is used.

## What Changed

- Replaced the table row that implied a universal `1h` ServiceAccount token TTL.
- Added an explicit production note describing how expired AppRole `secret_id` breaks Vault-backed downstream login flows once the cached Vault token ages out.
- Added a `Recommended production pattern` subsection in EN and VI covering Vault Agent Sidecar or Vault Agent Injector, projected ServiceAccount tokens with `audience: vault`, per-login JWT rereads from disk, and Vault token renew or re-login requirements.

> [!success]
> The deliverable docs now describe the token lifecycle boundary more accurately for Kubernetes production deployments.

## Decision

Prefer Vault Agent Sidecar or Vault Agent Injector for long-lived Kubernetes deployments where [[OpenIG]] should consume rendered secrets instead of owning the Vault token lifecycle.

> [!tip]
> Keep the lab AppRole model documented only as a Docker Compose or lab exception, not as a steady-state Kubernetes pattern.

## Current State

- EN and VI deliverable docs are updated consistently.
- The docs-only commit requested by the task stages only the two deliverable files.
- This note was written separately for project continuity.
