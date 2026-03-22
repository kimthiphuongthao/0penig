---
title: IG_SSO cookie forwarding investigation
tags:
  - debugging
  - openig
  - nginx
  - cookies
  - sso
date: 2026-03-22
status: done
---

# IG_SSO cookie forwarding investigation

Context: browser requests against [[OpenIG]] fronting [[Stack A]], [[Stack B]], and [[Stack C]] currently forward `IG_SSO*` cookies downstream to backend apps.

## Findings

> [!success] Route JSONs do not strip `IG_SSO*`
> Searches for `"Cookie"` in `stack-a/openig_home/config/routes/`, `stack-b/openig_home/config/routes/`, and `stack-c/openig_home/config/routes/` returned no matches. The only request `HeaderFilter remove` currently present is `X-WEBAUTH-USER` in `stack-c/openig_home/config/routes/10-grafana.json`.

> [!success] nginx does not strip request cookies
> `stack-a/nginx/nginx.conf`, `stack-b/nginx/nginx.conf`, and `stack-c/nginx/nginx.conf` contain `proxy_cookie_flags IG_SSO* samesite=lax`, but no `proxy_hide_header`, no `proxy_set_header Cookie`, and no request-cookie rewrite logic. This means nginx leaves request `Cookie` untouched and only adjusts response `Set-Cookie` attributes.

> [!warning] Two Groovy injectors rewrite `Cookie`, but only for app cookies
> `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy` strips only WordPress cookies (`wordpress_*`, `wordpress_logged_in_*`, `wp-settings-*`, `wp_woocommerce_*`) before reinjecting browser or freshly issued app cookies.
> `stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy` strips only `_redmine_session` and known Redmine cookie names before reinjecting them.
> Neither script references `IG_SSO`, `IG_SSO_B`, or `IG_SSO_C`.

> [!success] SSO/SLO logic depends on OpenIG session state, not backend visibility of `IG_SSO*`
> `TokenReferenceFilter.groovy` restores and offloads `oauth2:*` entries from `session[...]`.
> `SessionBlacklistFilter.groovy` reads cached `sid` or decodes it from `session[...]`.
> `SloHandler.groovy`, `SloHandlerJellyfin.groovy`, and `PhpMyAdminAuthFailureHandler.groovy` pull `id_token` from `session[...]` to build Keycloak end-session redirects.
> Backchannel logout uses `logout_token` and writes `blacklist:<sid>` into Redis.

## Evidence

### Routes

- Stack A:
  - `stack-a/openig_home/config/routes/01-wordpress.json`
  - `stack-a/openig_home/config/routes/02-app2.json`
- Stack B:
  - `stack-b/openig_home/config/routes/01-jellyfin.json`
  - `stack-b/openig_home/config/routes/02-redmine.json`
- Stack C:
  - `stack-c/openig_home/config/routes/10-grafana.json`
  - `stack-c/openig_home/config/routes/11-phpmyadmin.json`

Only route-level header removal found:

```json
"remove": [
  "X-WEBAUTH-USER"
]
```

from `stack-c/openig_home/config/routes/10-grafana.json`.

### nginx

- `stack-a/nginx/nginx.conf`
- `stack-b/nginx/nginx.conf`
- `stack-c/nginx/nginx.conf`

Observed pattern:

```nginx
proxy_cookie_flags IG_SSO samesite=lax;
proxy_cookie_flags IG_SSO_B samesite=lax;
proxy_cookie_flags IG_SSO_C samesite=lax;
```

No `proxy_hide_header` or request-cookie rewrite directives were found.

### Groovy

- `stack-a/openig_home/scripts/groovy/CredentialInjector.groovy`
- `stack-b/openig_home/scripts/groovy/RedmineCredentialInjector.groovy`
- `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-b/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-c/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
- `stack-a/openig_home/scripts/groovy/SloHandler.groovy`
- `stack-b/openig_home/scripts/groovy/SloHandler.groovy`
- `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`
- `stack-c/openig_home/scripts/groovy/SloHandler.groovy`
- `stack-c/openig_home/scripts/groovy/PhpMyAdminAuthFailureHandler.groovy`

## Git history

Relevant commits from:

```bash
git log --oneline --all -- '**/nginx.conf' '**/CredentialInjector*' '**/TokenReferenceFilter*'
```

- `78e2128` / `fdbffdc`: Stack A `CredentialInjector` cookie pass-through
- `9b2d109`: add `TokenReferenceFilter` to offload `oauth2:*` session data to Redis
- `47cbab9`: dynamic session key discovery in `TokenReferenceFilter`
- `4ab4865`: Stack C `TokenReferenceFilter` caches `sid` into `oidc_sid_app5/app6`
- `eb7b8ba` / `9a7b855`: nginx `proxy_cookie_flags IG_SSO* ...`

Additional check for Stack B Redmine injector:

- `895e401` / `cdb5425`: add Redmine cookie pass-through and `stripRedmineCookies`

No commit in the inspected history introduces stripping of `IG_SSO*` itself. The cookie-stripping commits are app-cookie specific.

## Impact assessment

> [!tip] Safe place to strip `IG_SSO*`
> Strip only on the outbound request to backend, after OpenIG has already restored session state and after app-specific injectors have run.

> [!warning] Unsafe place to strip `IG_SSO*`
> Do not strip in nginx before OpenIG, and do not strip before `TokenReferenceFilter`, `OAuth2ClientFilter`, `SessionBlacklistFilter`, or SLO handlers. Those components rely on OpenIG session restoration from the browser session cookie.

Backend auth paths currently in use:

- [[Stack A]] app2: `X-Authenticated-User`
- [[Stack A]] WordPress: WordPress app cookies injected by `CredentialInjector.groovy`
- [[Stack B]] Jellyfin: `Authorization` header injected by `JellyfinTokenInjector.groovy`
- [[Stack B]] Redmine: Redmine app cookies injected by `RedmineCredentialInjector.groovy`
- [[Stack C]] Grafana: `X-WEBAUTH-USER`
- [[Stack C]] phpMyAdmin: `HttpBasicAuthFilter` credentials from `VaultCredentialFilter.groovy`

So backend apps do not appear to require `IG_SSO*` in downstream requests. The risk is to OpenIG-side SSO/SLO processing if stripping happens before session restoration.

## Next steps

1. If the goal is to stop leaking `IG_SSO*` to apps, add a final outbound request filter immediately before each `ClientHandler` that removes only `IG_SSO*`.
2. Keep logout intercept routes and OpenIG entry handling unchanged.
3. Validate with one login + one front-channel logout + one backchannel logout per stack after the change.
