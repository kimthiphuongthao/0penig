---
title: M-4b legacy fallback URL removal
tags:
  - debugging
  - openig
  - shared-infra
  - jellyfin
  - redmine
date: 2026-03-25
status: done
---

# M-4b legacy fallback URL removal

Context: removed legacy fallback origins from the shared [[OpenIG]] Groovy handlers for [[Jellyfin]] and [[Redmine]] so they now depend on the shared-infra `CANONICAL_ORIGIN_*` variables instead of silently falling back to stale `:9080` endpoints.

## Root cause

- [shared/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy](/Volumes/OS/claude/openig/sso-lab/shared/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy) built the injected Jellyfin server address from the request `Host` header and fell back to `http://jellyfin-b.sso.local:9080`.
- [shared/openig_home/scripts/groovy/RedmineCredentialInjector.groovy](/Volumes/OS/claude/openig/sso-lab/shared/openig_home/scripts/groovy/RedmineCredentialInjector.groovy) hardcoded a `CANONICAL_ORIGIN_APP3` fallback of `http://redmine-b.sso.local:9080` and separately hardcoded `redmine-b.sso.local` for cookie domain rewriting.
- Those fallbacks allowed old shared-infra ports to survive in gateway-side behavior even after the canonical origins moved to port 80.

## Changes made

- Added a `requireEnv` helper to the Jellyfin response rewriter and changed the injected `serverAddress` to require `CANONICAL_ORIGIN_APP4`.
- Changed Jellyfin pre-handler configuration failures to return `500` fail-closed instead of passing the request through.
- Added a `requireEnv` helper to the Redmine credential injector and changed it to require `CANONICAL_ORIGIN_APP3`.
- Derived the Redmine cookie domain from `CANONICAL_ORIGIN_APP3` via `URI.host` instead of leaving a hardcoded hostname in the script.
- Added a dedicated `IllegalStateException` branch in the Redmine script so missing or invalid canonical-origin configuration returns `500` fail-closed.

> [!success] Verification
> `git diff --check` passed after the edits, and the two target scripts no longer contain `:9080`, `:18080`, `openiga`, or `openigb`.

> [!warning] Runtime gap
> The local shell does not have the `groovy` CLI installed, so I did not run a syntax compile or live OpenIG runtime validation in this session.

> [!tip] Pattern alignment
> Requiring `CANONICAL_ORIGIN_APP3` and `CANONICAL_ORIGIN_APP4` keeps these scripts aligned with the fail-closed configuration pattern already used by [[OpenIG]] session-handling filters.

## Current state

- [[Jellyfin]] response rewriting now depends on `CANONICAL_ORIGIN_APP4` and no longer falls back to the legacy `jellyfin-b.sso.local:9080` origin.
- [[Redmine]] credential injection now depends on `CANONICAL_ORIGIN_APP3`, rewrites cookie domains from the configured canonical origin, and no longer falls back to `redmine-b.sso.local:9080`.

## Next steps

- Reload the shared OpenIG instance in a Docker-enabled environment.
- Validate Jellyfin login still seeds `localStorage.jellyfin_credentials` with the port-80 canonical origin.
- Validate Redmine login, redirect retry, and cookie rewrite flows with `CANONICAL_ORIGIN_APP3` populated.

## Files changed

- [shared/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy](/Volumes/OS/claude/openig/sso-lab/shared/openig_home/scripts/groovy/JellyfinResponseRewriter.groovy)
- [shared/openig_home/scripts/groovy/RedmineCredentialInjector.groovy](/Volumes/OS/claude/openig/sso-lab/shared/openig_home/scripts/groovy/RedmineCredentialInjector.groovy)
