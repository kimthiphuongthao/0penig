---
title: Stack C Grafana Runtime Env Mismatch
tags:
  - debugging
  - stack-c
  - openig
  - grafana
  - oidc
date: 2026-03-18
status: complete
---

# Stack C Grafana Runtime Env Mismatch

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Requested scope: Stack C only.
- User reported Grafana SSO still failing after `stack-c/.env` was updated with correct 44-character OIDC secrets and Stack C OpenIG containers were recreated.
- Requested output: report only, no fix implementation.

## Findings

- `stack-c/.env` contains valid-looking 44-character values for both `OIDC_CLIENT_SECRET_APP5` and `OIDC_CLIENT_SECRET_APP6`.
- Grafana SSO is wired to `OIDC_CLIENT_SECRET_APP5`, not `APP6`.
- Mounted OpenIG runtime logs show `10-grafana.json` failed to load because `clientSecret` resolved to no value at runtime.
- Later startup logs also show the OpenIG JWT keystore password resolved to no value.
- This points to a runtime environment propagation problem for Stack C OpenIG, not a bad secret length in the repository file.

> [!warning]
> The file on disk is correct, but the running OpenIG process did not consistently see the corresponding environment variables.

## Evidence

- `stack-c/.env`
  - `OIDC_CLIENT_SECRET_APP5` length `44`
  - `OIDC_CLIENT_SECRET_APP6` length `44`
- `stack-c/openig_home/config/routes/10-grafana.json`
  - route uses `clientId` `openig-client-c-app5`
  - route reads `clientSecret` from `env['OIDC_CLIENT_SECRET_APP5']`
- `stack-c/openig_home/logs/route-system.log`
  - route unload/reload at `09:58:12` failed with `JsonValueException: /heap/1/config/clientSecret: Expecting a value`
  - later startup at `09:59:48` failed with `JsonValueException: /heap/2/config/password: Expecting a value`
- `stack-c/openig_home/logs/route-10-grafana.log`
  - earlier Grafana-route requests also hit `SessionBlacklistFilter` Redis timeouts and returned fail-closed `500`

> [!success]
> The Grafana route file itself clearly expects `OIDC_CLIENT_SECRET_APP5`, so the Stack C `.env` update targeted the correct logical client secret.

## Root Cause

- Most likely cause: Stack C OpenIG was started or reloaded without the expected runtime environment values, so the route saw `OIDC_CLIENT_SECRET_APP5` as empty even though `stack-c/.env` on disk contains the correct 44-character secret.
- Secondary issue: Grafana route requests also encountered Redis connectivity failures in `SessionBlacklistFilter`, which can independently return `500`.

## Current State

- Repository config file state: consistent
- Runtime OpenIG environment state: inconsistent with repository `.env`
- Grafana SSO: still blocked
- Fix implementation in this session: none

## Next Steps

1. Verify Stack C OpenIG containers are recreated from the `stack-c/` compose project context so `stack-c/.env` is actually loaded.
2. Inspect live container env for `OIDC_CLIENT_SECRET_APP5` and `KEYSTORE_PASSWORD`.
3. Recheck Grafana after route load succeeds, then separately validate Redis reachability from OpenIG because the blacklist filter is failing closed.

## Files Changed

- `docs/obsidian/debugging/2026-03-18-stack-c-grafana-runtime-env-mismatch.md`
