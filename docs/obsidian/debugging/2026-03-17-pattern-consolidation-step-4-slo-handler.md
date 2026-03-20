---
title: Pattern consolidation step 4 SloHandler
tags:
  - debugging
  - openig
  - keycloak
  - stack-a
  - stack-b
  - stack-c
  - pattern-consolidation
  - slo
date: 2026-03-17
status: done
---

# Pattern consolidation step 4 SloHandler

Context: consolidated the standard logout handler for [[OpenIG]] across [[Stack A]], [[Stack B]], and [[Stack C]] while preserving the special-case Jellyfin logout flow and keeping the old per-app handlers as backups where requested.

## Root cause

- [[Stack A]], [[Stack B]], and [[Stack C]] still had multiple SLO Groovy variants with duplicated lookup and redirect logic.
- The Stack C Grafana and phpMyAdmin handlers did not have the defensive `try/catch` wrapper, which left the logout flow exposed to unhandled runtime failures.
- Several handlers hardcoded Keycloak browser URL or client-specific values instead of using route-driven parameters.

## Changes made

- Replaced [stack-a/openig_home/scripts/groovy/SloHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SloHandler.groovy) with the new parameterized template using ScriptableHandler `args`.
- Created [stack-b/openig_home/scripts/groovy/SloHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandler.groovy) from the same template and repointed the Redmine logout route to it.
- Created [stack-c/openig_home/scripts/groovy/SloHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/SloHandler.groovy) from the same template and repointed the Grafana and phpMyAdmin logout routes plus the inline phpMyAdmin `failureHandler`.
- Added route-level `clientEndpoint`, `clientId`, `canonicalOrigin`, and `postLogoutPath` args so logout redirects are per-app without needing extra Groovy copies.
- Left [stack-b/openig_home/scripts/groovy/SloHandlerRedmine.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandlerRedmine.groovy), [stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy), [stack-c/openig_home/scripts/groovy/SloHandlerGrafana.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/SloHandlerGrafana.groovy), and [stack-c/openig_home/scripts/groovy/SloHandlerPhpMyAdmin.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/SloHandlerPhpMyAdmin.groovy) in place as requested backups.

> [!success] Consolidation verification
> Local verification confirmed the three new `SloHandler.groovy` files are byte-identical and all targeted routes now point at the shared template with the correct args blocks.

> [!warning] Runtime verification gap
> `docker restart` and `docker logs` could not be executed in this session because the sandbox cannot access `/Users/duykim/.docker/run/docker.sock`.

> [!tip] Logout behavior
> The consolidated handler now follows the safer [[Keycloak]] browser logout path across the standard apps: route-driven client metadata, host-aware OAuth2 session key lookup, env-driven Keycloak URL, and a user-facing HTTP 500 response on failure.

## Current state

- [[Stack A]] WordPress logout uses the shared `SloHandler.groovy` with app1 args.
- [[Stack B]] Redmine logout uses the shared `SloHandler.groovy` with app3 args.
- [[Stack B]] Jellyfin still uses its dedicated logout handler because its flow includes an application-specific logout API call and `/web/index.html` redirect.
- [[Stack C]] Grafana and phpMyAdmin logout now use the shared `SloHandler.groovy` with app5 and app6 args.
- [[Stack C]] phpMyAdmin inline `failureHandler` now points to the shared template instead of the dedicated backup file.
- Runtime restart and route-load log validation still need to be executed from a Docker-enabled environment.

## Files changed

- [[OpenIG]]
  File: [stack-a/openig_home/scripts/groovy/SloHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/SloHandler.groovy)
- [[OpenIG]]
  File: [stack-a/openig_home/config/routes/00-wp-logout.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/routes/00-wp-logout.json)
- [[OpenIG]]
  File: [stack-b/openig_home/scripts/groovy/SloHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/scripts/groovy/SloHandler.groovy)
- [[OpenIG]]
  File: [stack-b/openig_home/config/routes/00-redmine-logout.json](/Volumes/OS/claude/openig/sso-lab/stack-b/openig_home/config/routes/00-redmine-logout.json)
- [[OpenIG]]
  File: [stack-c/openig_home/scripts/groovy/SloHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/scripts/groovy/SloHandler.groovy)
- [[OpenIG]]
  File: [stack-c/openig_home/config/routes/00-grafana-logout.json](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/config/routes/00-grafana-logout.json)
- [[OpenIG]]
  File: [stack-c/openig_home/config/routes/00-phpmyadmin-logout.json](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/config/routes/00-phpmyadmin-logout.json)
- [[OpenIG]]
  File: [stack-c/openig_home/config/routes/11-phpmyadmin.json](/Volumes/OS/claude/openig/sso-lab/stack-c/openig_home/config/routes/11-phpmyadmin.json)
