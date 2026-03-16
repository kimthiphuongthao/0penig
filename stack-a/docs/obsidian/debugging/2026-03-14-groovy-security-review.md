---
title: Groovy Security Review for Stack A
tags:
  - openig
  - security-review
  - groovy
  - stack-a
date: 2026-03-14
status: completed
---

# Groovy Security Review for [[Stack A]]

## Context

Reviewed the Groovy scripts under `openig_home/scripts/groovy/` for [[OpenIG]] in Stack A:

- `BackchannelLogoutHandler.groovy`
- `CredentialInjector.groovy`
- `SessionBlacklistFilter.groovy`
- `SessionBlacklistFilterApp2.groovy`
- `SloHandler.groovy`
- `VaultCredentialFilter.groovy`
- `App1ResponseRewriter.groovy`

Corroborating config was checked in `openig_home/config/` to confirm session and transport assumptions for [[Keycloak]] and [[Vault]].

## What Was Confirmed

> [!warning]
> `JwtSession` is enabled in OpenIG config, so values stored in `session[...]` are browser-carried session material unless the deployment is changed.

> [!warning]
> Multiple routes and issuer endpoints still use plain HTTP and `requireHttps: false`, so credentials and tokens are exposed to internal-network interception.

## Findings

### Critical

1. `CredentialInjector.groovy` stores WordPress session cookies in `session['wp_session_cookies']` (required for form injection pattern).
2. ~~`VaultCredentialFilter.groovy` stores Vault client tokens in `session['vault_token']`.~~ **RESOLVED (FIX-09, commit b198e83)**: Vault token now cached in server-side `globals` (ConcurrentHashMap), not JwtSession.
3. ~~Because [[OpenIG]] uses `JwtSession`, both values are leaving the proxy boundary and being embedded in end-user session state.~~ **Partially resolved**: Vault token no longer in cookie. WP session cookies remain (required for injection pattern).

### High

1. `SessionBlacklistFilter.groovy` and `SessionBlacklistFilterApp2.groovy` fail open when Redis lookup fails, allowing revoked sessions to continue.
2. `BackchannelLogoutHandler.groovy`, `CredentialInjector.groovy`, `VaultCredentialFilter.groovy`, and `SloHandler.groovy` send or fetch trust material over HTTP.
3. `SloHandler.groovy` logs the full logout URL including `id_token_hint`.

### Medium

1. Redirect targets are derived from the request `Host` header in logout and blacklist flows.
2. `CredentialInjector.groovy` forwards the browser `Cookie` header together with injected WordPress cookies, which can leak unrelated cookies upstream.

### Low

1. No command execution paths were found.
2. `App1ResponseRewriter.groovy` is empty.
3. No hardcoded secrets were found inside the reviewed Groovy scripts, but supporting config contains hardcoded credentials and secrets.

## Decisions / Recommendations

> [!success]
> Backchannel logout token verification does validate signature, issuer, audience, event type, `iat`, and `exp`. The main issue there is transport security, not missing signature validation.

> [!tip]
> Move WordPress session state and Vault access tokens to a server-side store only. Do not place backend cookies or Vault tokens in `JwtSession`.

> [!tip]
> Change fail-open blacklist checks to fail closed for authenticated routes, or at minimum force re-authentication when Redis is unavailable.

> [!tip]
> Move all OIDC, Vault, and WordPress credential exchanges to HTTPS and enable `requireHttps` on the OpenIG OAuth2 client filters.

## Current State

- Review completed.
- No code changes were applied to the Groovy scripts in this task.
- Obsidian note created for project continuity.

## Files Reviewed

- `openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- `openig_home/scripts/groovy/CredentialInjector.groovy`
- `openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `openig_home/scripts/groovy/SessionBlacklistFilterApp2.groovy`
- `openig_home/scripts/groovy/SloHandler.groovy`
- `openig_home/scripts/groovy/VaultCredentialFilter.groovy`
- `openig_home/scripts/groovy/App1ResponseRewriter.groovy`
