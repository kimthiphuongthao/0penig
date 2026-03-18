---
title: Stack C Grafana SSO Cleanup And Gotchas
tags:
  - debugging
  - stack-c
  - grafana
  - openig
  - keycloak
  - secrets
date: 2026-03-18
status: done
---

# Stack C Grafana SSO Cleanup And Gotchas

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack C]]

## Context

- Follow-up after the Stack C Grafana SSO recovery work.
- Runtime fix was already in place: `stack-c/docker/openig/docker-entrypoint.sh` now renders `config.json` from the mounted `/opt/openig` path that OpenIG actually reads.
- Remaining repository issue: because `/opt/openig/config/config.json` is a bind-mounted host file, container startup replaced placeholders in the committed file with live secret values.
- Second confirmed gotcha: OpenIG `OAuth2ClientFilter` does not URL-encode `client_secret` in the token request body, so `+` is decoded by Keycloak as space.

## Cleanup Performed

- Restored `stack-c/openig_home/config/config.json` to placeholders:
  - `JwtSession.sharedSecret` -> `__JWT_SHARED_SECRET__`
  - `JwtKeyStore.password` -> `__KEYSTORE_PASSWORD__`
  - `JwtSession.password` -> `__KEYSTORE_PASSWORD__`
- Added two entries to `.claude/rules/gotchas.md`:
  - mounted host file mutation from `sed -i` in `docker-entrypoint.sh`
  - `client_secret` URL-encoding limitation in OpenIG

> [!warning]
> If Stack C OpenIG containers have been started locally, always check `stack-c/openig_home/config/config.json` before commit. A dirty file here can silently leak real runtime secrets into git.

> [!warning]
> For OpenIG OIDC clients, treat secret format as a compatibility requirement, not just a strength requirement. Base64-looking secrets containing `+`, `/`, or `=` are unsafe for this path.

> [!success]
> The committed Stack C `config.json` is back to a template-safe state and the team gotchas now document both failure modes.

## Decision

- Keep the repository copy of `config.json` templated at all times.
- Until the entrypoint is reworked to render from a temporary copy again, placeholder restoration is a required pre-commit cleanup step.
- Generate OpenIG-facing OIDC client secrets as alphanumeric-only values to avoid Keycloak token endpoint parsing issues.

## Current State

- Stack C Grafana SSO fix remains documented across code and notes.
- Repository copy no longer contains the live `JwtSession` secret or keystore password.
- `.claude/rules/gotchas.md` now captures both the bind-mount mutation trap and the `client_secret` encoding trap.

## Files Changed

- `stack-c/openig_home/config/config.json`
- `.claude/rules/gotchas.md`
- `docs/obsidian/debugging/2026-03-18-stack-c-grafana-sso-cleanup-and-gotchas.md`
