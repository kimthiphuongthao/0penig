---
title: All Stacks Security Review
tags:
  - sso-lab
  - security-review
  - openig
  - nginx
date: 2026-03-16
status: completed
---

# All Stacks Security Review

Reviewed [[OpenIG]] routes, Groovy scripts, nginx, and OpenIG entrypoints for [[Stack A]], [[Stack B]], and [[Stack C]].

## Scope

- `stack-a/openig_home/`
- `stack-b/openig_home/`
- `stack-c/openig_home/`
- `stack-a/nginx/nginx.conf`
- `stack-b/nginx/nginx.conf`
- `stack-c/nginx/nginx.conf`
- `stack-a/docker/openig/docker-entrypoint.sh`
- `stack-b/docker/openig/docker-entrypoint.sh`
- `stack-c/docker/openig/docker-entrypoint.sh`

## Confirmed Findings

> [!warning] Plain HTTP for OIDC, session transport, and logout
> All three stacks expose OpenIG on `listen 80`, set `requireHttps: false`, and use `http://` Keycloak endpoints. This leaves OIDC redirects, session cookies, and `id_token_hint` on cleartext transport.

> [!warning] Browser-held [[OpenIG]] `JwtSession` stores upstream session material
> Active code stores backend session state in the stateless gateway session cookie:
> - [[Stack A]] caches WordPress cookies in `session['wp_session_cookies']`
> - [[Stack B]] caches Redmine cookies and the Jellyfin access token in `session[...]`
> This expands the blast radius of a stolen `IG_SSO` cookie from gateway access to backend app access.

> [!warning] OpenIG admin surface appears externally reachable
> `admin.json` enables the `/openig` prefix in all stacks, and nginx proxies all paths to OpenIG without blocking `/openig/*`. [[Stack A]] and [[Stack C]] also set admin `mode: EVALUATION`, which is a materially worse exposure if reachable from outside the container network.

> [!warning] Backchannel logout trusts JWKS over HTTP
> Each `BackchannelLogoutHandler.groovy` validates signatures, issuer, audience, `events`, `iat`, and `exp`, but the JWKS is fetched from Keycloak over plain HTTP. Signature verification is therefore only as trustworthy as that internal network hop.

> [!warning] Host header remains a trust boundary
> nginx forwards `$http_host` unchanged and multiple routes match hostnames with loose regexes such as `foo.sso.local.*`. Groovy filters also use `Host` to derive OAuth2 session keys and logout behavior. Unknown or malformed hosts should be rejected earlier.

> [!warning] Jellyfin response rewriting injects bearer material into inline JavaScript
> The response rewriter embeds the Jellyfin access token, user ID, and host-derived server address into an inline `<script>` and persists them into `localStorage`. The escaping is incomplete for JavaScript/HTML contexts, so any related injection issue becomes token theft immediately.

> [!warning] App2 trusted identity header is appended, not stripped/replaced
> `App2HeaderFilter` adds `X-Authenticated-User` but does not remove a client-supplied copy first. If the backend trusts the wrong value when duplicates are present, identity spoofing is possible.

## Findings Not Confirmed

> [!success] Backchannel logout claim validation is present
> The three backchannel handlers do verify JWT structure, `alg`, signature, issuer, audience, `events`, `iat`, and `exp`.

> [!success] Redis and Vault failures are generally fail-closed
> The blacklist filters and Vault credential filters mostly return `500`/`502` rather than proxying through on infrastructure failure.

> [!success] No committed hardcoded secrets found
> Secrets are referenced through env vars and mounted files. The Docker entrypoints materialize them into runtime config under `/tmp/openig`, but no literal shared secrets or Vault tokens were committed in the reviewed files.

## Inactive Or Unwired Code

- `stack-a/openig_home/scripts/groovy/App1ResponseRewriter.groovy` is empty.
- `stack-b/openig_home/scripts/groovy/DotnetCredentialInjector.groovy` and `DotnetSloHandler.groovy` are marked dead code.
- `stack-c/openig_home/scripts/groovy/PhpMyAdminCookieFilter.groovy` was reviewed but is not wired into `11-phpmyadmin.json`.

## Next Steps

1. Terminate TLS at nginx and switch all Keycloak/OIDC/JWKS/logout URLs to `https://`.
2. Move backend session state and bearer tokens out of `JwtSession`; store opaque references server-side instead.
3. Disable or firewall `/openig/*`; remove `EVALUATION` mode outside local-only debugging.
4. Normalize and allowlist `Host` at nginx, then anchor route host regexes.
5. Remove client-side Jellyfin token injection or replace it with an HttpOnly server-managed session design.
