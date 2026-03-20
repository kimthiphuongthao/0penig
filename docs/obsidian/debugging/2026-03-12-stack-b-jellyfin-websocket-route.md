---
title: Stack B Jellyfin WebSocket route investigation
tags:
  - openig
  - jellyfin
  - websocket
  - stack-b
  - debugging
date: 2026-03-12
status: investigated
---

# Stack B Jellyfin WebSocket route investigation

Context: [[OpenIG]] in [[Stack B]] proxies Jellyfin through `stack-b/openig_home/config/routes/01-jellyfin.json`. Browser traffic works for normal HTTP pages, but WebSocket upgrade requests fail with `jakarta.websocket.DeploymentException: The scheme [http] is not supported. The supported schemes are ws and wss`.

> [!warning]
> Root cause is in the gateway route design, not in the Jellyfin app. The current route uses a single top-level `baseURI` of `http://jellyfin:8096` for both normal HTTP traffic and WebSocket upgrade traffic.

## Findings

1. `01-jellyfin.json` currently applies `baseURI: http://jellyfin:8096` to every request for `jellyfin-b.sso.local`, including Upgrade requests.
2. `JellyfinResponseRewriter.groovy` only rewrites `text/html` responses after `next.handle(...)` returns. It does not detect Upgrade requests, does not rewrite WebSocket URLs, and cannot correct a proxy-side `http` to `ws` mismatch.
3. The current Jellyfin filter chain is HTTP-centric:
   - `VaultCredentialFilterJellyfin.groovy` fetches credentials from [[Vault]]
   - `JellyfinTokenInjector.groovy` authenticates against Jellyfin over HTTP and injects an HTTP `Authorization` header
   - `JellyfinResponseRewriter.groovy` injects browser-side localStorage bootstrap into HTML

## Proposed gateway-side fix

Create a dedicated WebSocket route ahead of `01-jellyfin.json` for Jellyfin Upgrade traffic:

- Match `Host: jellyfin-b.sso.local` plus `Upgrade: websocket`
- Use `baseURI: ws://jellyfin:8096`
- Keep only the filters required for OpenIG session enforcement
- Do not run the HTML response rewriter on this route

Then narrow the existing `01-jellyfin.json` HTTP route so it excludes WebSocket upgrade requests and continues using `http://jellyfin:8096` for HTML/API traffic.

> [!tip]
> This keeps the change fully inside [[OpenIG]] route/groovy config and preserves the repo rule that target apps must not be modified.

## Current state

- Investigation complete
- No config change applied yet
- Ready for route-only implementation after user confirmation
