---
title: Legacy Auth and Logout SSO Integration Research
tags:
  - how-to
  - authentication
  - logout
  - sso
  - oidc
date: 2026-03-12
status: done
---

# Context

Research scope: legacy enterprise apps and self-hosted tools where SSO/OIDC support is missing, partial, plugin-based, or inconsistently implemented.

Related systems: [[OpenIG]], [[Keycloak]], [[Vault]], [[Stack C]]

# What Was Done

- Collected vendor documentation for authentication methods, token/cookie/session behavior, and logout capabilities.
- Compared discovery patterns for form login, HTTP Basic, API tokens, auth proxy/header trust, and delegated identity.
- Mapped practical integration patterns: gateway brokering, header injection, and sidecar enforcement.
- Built an app-level matrix for:
  - WordPress
  - Jenkins
  - GitLab
  - Grafana
  - phpMyAdmin
  - Confluence
  - Jira
  - Mattermost
  - Nextcloud
  - Jellyfin
  - Vault
  - SonarQube
  - Nexus
  - Kibana
  - Prometheus

> [!success] Confirmed Working Pattern
> For apps with native auth-proxy or header-based modes (Grafana Auth Proxy, SonarQube HTTP header auth, Nexus Remote User Token), gateway-mediated SSO with strict header sanitation is the fastest path to production.

# Key Decisions

1. Prefer **gateway/token exchange** when app session semantics are complex or logout/SLO is weak.
2. Use **header injection** only when the app has explicit trusted-proxy/trusted-header controls.
3. Treat logout as **best-effort across legacy apps**: enforce gateway-side token revocation and local session kill even when upstream IdP SLO is unavailable.

> [!warning] Gotcha
> Many apps do not implement full front-channel/back-channel SLO. Logging out from the app may not terminate IdP session (and vice versa).

# Current State

- Research complete and ready to convert into OpenIG route design and per-app adapters.
- Evidence set is documentation-backed; some endpoint details remain version-specific and must be validated during integration tests.

# Next Steps

1. Create per-app OpenIG route templates (login, callback, logout, session-check).
2. Add automated probes:
   - unauthenticated request behavior (`302` vs `401/403`)
   - CSRF requirements for logout POST
   - idle timeout and absolute timeout
3. Add security hardening checklist to deployment runbook.

> [!tip] Best Practice
> Keep a per-app "auth contract" document with exact headers, cookies, logout URL/method, timeout values, and failure semantics before go-live.

# Files Changed

- `docs/obsidian/how-to/2026-03-12-legacy-auth-logout-sso-integration-research.md`
