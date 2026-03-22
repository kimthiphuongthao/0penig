---
title: Stack C SEC COOKIE STRIP
tags:
  - stack-c
  - openig
  - cookie
  - security
date: 2026-03-22
status: fixed
---

# Stack C SEC COOKIE STRIP

Related: [[Stack C]] [[OpenIG]] [[Keycloak]] [[Vault]]

## Root cause

`[[OpenIG]]` restores `JwtSession` from `IG_SSO_C` before Groovy filters execute, but Stack C still forwarded gateway session cookies to Grafana and phpMyAdmin upstreams.
That exposed `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C` to backends that do not need gateway session cookies.

## Fix

Updated `stack-c/openig_home/config/routes/10-grafana.json`:

- Added `StripGatewaySessionCookiesApp5` immediately after `SessionBlacklistFilterApp5`.
- Kept `GrafanaUserHeader` and the backend proxy downstream of the strip point.

Updated `stack-c/openig_home/config/routes/11-phpmyadmin.json`:

- Added `StripGatewaySessionCookiesApp6` immediately after `SessionBlacklistFilterApp6`.
- Kept `VaultCredentialFilter` and `PhpMyAdminBasicAuth` downstream of the strip point.

Added `stack-c/openig_home/scripts/groovy/StripGatewaySessionCookies.groovy`:

- Removes exact-match cookie names `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C` from the outbound `Cookie` header.
- Deletes the `Cookie` header entirely when those are the only cookies present.

> [!success]
> Stack C now strips OpenIG session cookies on both app5 and app6 request paths after blacklist/session checks and before backend forwarding.

## Decision rationale

The strip point is safe because framework-level session restoration has already happened before Groovy filters run.
Placing the filter after `SessionBlacklistFilter` preserves session-based enforcement while preventing backend cookie leakage.

> [!tip]
> Keep stripping on exact cookie names only so unrelated application cookies remain intact.

## Current state

- Route JSON syntax validated with `jq empty`.
- `StripGatewaySessionCookies.groovy` matches the known-good helper already used in Stack A.

> [!warning]
> Requested runtime verification could not run from Codex because Docker socket access is blocked by the sandbox: `dial unix /Users/duykim/.docker/run/docker.sock: connect: operation not permitted`.

## Files changed

- `stack-c/openig_home/config/routes/10-grafana.json`
- `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- `stack-c/openig_home/scripts/groovy/StripGatewaySessionCookies.groovy`
