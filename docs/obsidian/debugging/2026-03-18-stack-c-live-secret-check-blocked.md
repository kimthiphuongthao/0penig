---
title: Stack C Live Secret Check Blocked
tags:
  - debugging
  - stack-c
  - keycloak
  - openig
  - grafana
date: 2026-03-18
status: blocked
---

# Stack C Live Secret Check Blocked

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Requested scope: Stack C only.
- Requested action: compare `stack-c/.env` values for `OIDC_CLIENT_SECRET_APP5` and `OIDC_CLIENT_SECRET_APP6` against live Keycloak values, then update only `stack-c/.env` if needed and recreate Stack C OpenIG containers.

## Findings

- `stack-c/.env` already contains 44-character secrets for both Stack C OIDC clients.
- `OIDC_CLIENT_SECRET_APP5` already ends with `=`.
- `OIDC_CLIENT_SECRET_APP6` already ends with `=`.
- No local edit was required in `stack-c/.env` based on repository state.

> [!warning]
> The original diagnosis for this session claimed `OIDC_CLIENT_SECRET_APP5` was missing trailing `=`. Current repo state does not match that claim.

## Evidence

- `stack-c/.env`
  - `OIDC_CLIENT_SECRET_APP5=CVZ1...ce4=` length `44`
  - `OIDC_CLIENT_SECRET_APP6=NXti.../M=` length `44`
- [[2026-03-17-stack-c-grafana-invalid-client-credentials]]
  - prior debugging already recorded the strong padded app5 secret in `stack-c/.env`
- [[stack-c]]
  - current stack status note says both Stack C client secrets were rotated to strong 44-character values and synced on `2026-03-17`

> [!tip]
> The repo includes [`keycloak/sync-stack-c-oidc-secrets.sh`](../../../keycloak/sync-stack-c-oidc-secrets.sh), which treats `stack-c/.env` as the source of truth and syncs Keycloak to match it.

## Blockers

- This Codex sandbox cannot connect to `http://localhost:8080` or `http://127.0.0.1:8080`.
- This Codex sandbox cannot access the Docker daemon socket at `/Users/duykim/.docker/run/docker.sock`.
- Because of that, the following requested live checks could not be completed here:
  - read actual Keycloak client secrets
  - compare live Keycloak values against `stack-c/.env`
  - recreate `openig-c1` and `openig-c2`
  - inspect OpenIG logs for loaded routes or runtime errors

> [!warning]
> Remaining uncertainty is runtime-only. The local file state is consistent; live Keycloak state and container state were not observable from this session.

## Current State

- `stack-c/.env`: unchanged
- Keycloak live secret verification: blocked
- Stack C OpenIG recreate: blocked
- Route load verification: blocked

## Next Steps

1. Run the Keycloak admin API queries on a host that can reach `localhost:8080`.
2. If Keycloak does not match `stack-c/.env`, use `keycloak/sync-stack-c-oidc-secrets.sh` or manually set the client secrets to the `.env` values.
3. Recreate only Stack C OpenIG containers and recheck logs for route load lines and auth errors.

## Files Changed

- `docs/obsidian/debugging/2026-03-18-stack-c-live-secret-check-blocked.md`
