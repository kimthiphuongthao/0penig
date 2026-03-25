---
title: Shared Jellyfin inbound auth header strip
tags:
  - openig
  - jellyfin
  - security
  - debugging
  - shared-infra
date: 2026-03-25
status: fixed
---

# Shared Jellyfin inbound auth header strip

Context: [[OpenIG]] shared Jellyfin traffic uses `shared/openig_home/scripts/groovy/JellyfinTokenInjector.groovy` to obtain and inject a gateway-managed Jellyfin token before proxying to [[Jellyfin]].

> [!warning]
> Root cause: when no gateway-managed Jellyfin token existed and the request was non-HTML, the script returned `next.handle(context, request)` without first removing client-supplied `Authorization`, `X-Emby-Authorization`, or `X-MediaBrowser-Token` headers. That allowed direct client authentication headers to reach the upstream Jellyfin service and bypass the intended gateway-managed auth path.

## Fix applied

> [!success]
> `JellyfinTokenInjector.groovy` now strips `Authorization`, `X-Emby-Authorization`, and `X-MediaBrowser-Token` immediately on script entry before any branching.

Additional hardening kept in place for the vulnerable branch:

- The non-HTML pass-through path strips the same three headers again immediately before `return next.handle(context, request)`.
- HTML requests still authenticate through the gateway-managed flow and then inject the gateway-generated `Authorization` header for upstream use.

## Files changed

- `shared/openig_home/scripts/groovy/JellyfinTokenInjector.groovy`

## Current state

- Security fix committed as `05252ea`
- Non-HTML pass-through behavior remains intact, but inbound client auth headers are removed before proxying
- No runtime proxy test was executed in this session

> [!tip]
> Keep inbound auth-header stripping at the top of request-mutating gateway filters so fallback branches cannot accidentally leak client credentials to upstream apps or services such as [[Vault]]-backed integrations.

## Next steps

- Reload the shared [[OpenIG]] instance that serves this script
- Verify with a non-HTML Jellyfin API request carrying forged auth headers that the upstream request no longer receives those headers
