# Production Readiness Gap Report — SSO Lab

Date: 2026-03-17  
Scope: All 3 stacks (Stack A/B/C)  
Status: NOT READY

## Executive Summary

This report captures the 2026-03-17 production-readiness gap assessment for SSO Lab, cross-referencing the 2026-03-16 pre-packaging audit against the current codebase. The current state is **NOT READY** for release as a production reference solution: **37 findings remain open, 6 are partial, and 38 are resolved** out of 81 checks.

Resolved work now covers 38 findings and materially improved the gateway baseline. The resolved work includes the JWKS cache race, `SloHandler` try-catch hardening, TTL unit standardization, consolidation of `SessionBlacklistFilter` / `BackchannelLogoutHandler` / `SloHandler`, `vault/keys/` repo hygiene, Redmine direct-port exposure removal, Stack C nginx buffer alignment, `CANONICAL_ORIGIN_*` environment variables, dead-code cleanup, Stack C OIDC client secret rotation, and STEP-03 secret externalization plus OpenIG image pinning.

Operational follow-up after the scorecard: Stack C Grafana SSO/SLO was re-validated successfully on 2026-03-18. The earlier APP5 padding theory was superseded; the verified root cause was OpenIG `OAuth2ClientFilter` not URL-encoding `client_secret`, so APP5 now uses a strong alphanumeric-only secret and the recreated Stack C OpenIG containers are confirmed working.

The remaining 37 open findings are concentrated in three categories that matter for a reusable reference solution:

- Security: Redis revocation state is unauthenticated, and security headers and cookie flags are incomplete.
- Architecture: Stack C is not yet parity-aligned with the Stack A/B reference pattern, Keycloak endpoint configuration is still hardcoded in Stack A/C routes, and Linux portability remains incomplete because `host.docker.internal` is assumed.
- Code quality and operational consistency: several low-effort Groovy and nginx fixes remain open, including EOF handling, Base64 decoding simplification, and inconsistent upstream error semantics.

The most important conclusion is that the lab now demonstrates a strong reference pattern shape, but it is not yet publishable as a copy-paste production reference. The blocking work is concentrated in gateway-side assets only: `docker-compose.yml`, OpenIG route JSON, Groovy scripts, and `nginx.conf`.

## Must Fix (Blocking for production reference solution)

These items block production-reference status because they either leave an active security gap open or prevent one of the three stacks from acting as a faithful implementation of the consolidated pattern.

### H-4/S-2: Redis no authentication
- Files: `stack-a/docker-compose.yml`, `stack-b/docker-compose.yml`, `stack-c/docker-compose.yml`, `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`, `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`, `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`, `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- Finding: Redis runs without `requirepass`, and the Groovy revocation read/write paths do not issue `AUTH` before blacklist commands. Any container on the Docker network can read or modify revocation entries.
- Impact: Session blacklist state can be tampered with. An attacker who can reach Redis from the Docker network can delete or overwrite `blacklist:{sid}` entries and effectively un-revoke sessions.
- Fix approach: Add Redis password protection in all three compose stacks, pass the password through environment variables, and update all revocation Groovy templates to authenticate before issuing Redis commands.
- Effort: MEDIUM

### H-5/S-3: Live secrets in docker-compose committed to git [RESOLVED]
- Files: `stack-a/docker-compose.yml`, `stack-b/docker-compose.yml`, `stack-c/docker-compose.yml`
- Finding: RESOLVED — Secrets externalized to `.env` files (gitignored). `.env.example` committed. Image pinned to `6.0.1`.
- Impact: Live secrets no longer ship in versioned compose files, and the gateway no longer depends on the mutable `latest` tag that broke OpenIG 6 startup.
- Fix approach: Completed. Keep `.env.example` as the committed contract, never commit `.env`, and always pin OpenIG image versions explicitly.
- Effort: MEDIUM

### H-7/A-1: Stack C docker-compose parity gap vs Stack A/B reference
- Files: `stack-c/docker-compose.yml`
- Finding: Stack C is still materially behind Stack A/B in production-shape compose controls. It is missing items such as `platform`, health checks, `KEYCLOAK_INTERNAL_URL`, `OPENIG_NODE_NAME`, explicit `container_name`, and restart-policy consistency.
- Impact: Stack C is not a faithful instance of the consolidated reference pattern. That breaks the core goal of SSO Lab as a reusable copy-paste reference across multiple legacy-integration styles.
- Fix approach: Align `stack-c/docker-compose.yml` with `stack-a/docker-compose.yml` as the reference baseline, then keep only the stack-specific service differences that are intentional.
- Effort: MEDIUM

### M-5/S-9: Stack C weak OIDC client secrets [RESOLVED]
- Files: `stack-c/docker-compose.yml`
- Finding: RESOLVED — Stack C clients were rotated away from the trivially guessable value `secret-c` in STEP-02, and APP5 was re-rotated on 2026-03-18 to a strong alphanumeric-only secret after confirming OpenIG `OAuth2ClientFilter` does not URL-encode `client_secret`.
- Impact: Stack C no longer ships trivially guessable OIDC client credentials in a production-readiness reference path.
- Fix approach: Completed. Keep the strong-random secret generation requirement documented in the gateway pattern, and require alphanumeric-only values whenever OpenIG `OAuth2ClientFilter` consumes the secret.
- Effort: LOW

### A-6/A-7/M-13/S-17: Keycloak URLs hardcoded in Stack A and Stack C routes
- Files: `stack-a/openig_home/config/routes/01-wordpress.json`, `stack-a/openig_home/config/routes/02-app2.json`, `stack-a/openig_home/config/routes/00-backchannel-logout-app1.json`, `stack-c/openig_home/config/routes/10-grafana.json`, `stack-c/openig_home/config/routes/11-phpmyadmin.json`, `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json`, `stack-c/openig_home/config/routes/00-backchannel-logout-app6.json`
- Finding: `issuer`, `jwksUri`, `authorize`, `token`, `userinfo`, and `endSession` endpoints remain hardcoded in Stack A and Stack C route JSON. Stack B already uses the env-driven reference pattern.
- Impact: Keycloak endpoint changes still require multi-file route edits in two stacks. That undermines the parameterized reference pattern and makes rollout, reuse, and portability error-prone.
- Fix approach: Align Stack A and Stack C with the Stack B route pattern by sourcing browser-facing and internal Keycloak endpoints from `KEYCLOAK_BROWSER_URL` and `KEYCLOAK_INTERNAL_URL` route args or environment-backed expressions.
- Effort: MEDIUM

### L-5: PhpMyAdminCookieFilter.groovy dead code [RESOLVED]
- Files: `stack-c/openig_home/scripts/groovy/PhpMyAdminCookieFilter.groovy`
- Finding: RESOLVED — the unused script was deleted in the L-5 fix, and no live route references remain.
- Impact: The misleading dead-code control is no longer shipped in Stack C.
- Fix approach: Completed. Keep the WONT_FIX phpMyAdmin CSRF decision documented in the report and integration docs.
- Effort: LOW

## Should Fix (Production quality)

These items are not the primary blockers, but they should be completed before calling the lab production-quality and operationally consistent.

### M-11: readRespLine does not throw on EOF in BackchannelLogoutHandler
- Files: `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- Finding: The `readRespLine` helper returns silently on `-1` instead of throwing `IOException` when Redis closes the connection unexpectedly.
- Impact: Backchannel logout can fail silently on connection drops, which weakens revocation correctness and makes failures harder to detect.
- Fix approach: Throw `new IOException("EOF")` on `-1`, matching the already-hardened behavior in `SessionBlacklistFilter.groovy`.
- Effort: LOW

### M-12: base64UrlDecode manual padding in BackchannelLogoutHandler
- Files: `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- Finding: The helper manually calculates Base64 URL padding before decoding.
- Impact: The current code works, but the extra logic is unnecessary and increases maintenance risk in a security-sensitive parsing path.
- Fix approach: Replace the helper implementation with `java.util.Base64.getUrlDecoder().decode(input)` and rely on the standard decoder behavior.
- Effort: LOW

### M-3/S-7: No security response headers on nginx
- Files: `stack-a/nginx/nginx.conf`, `stack-b/nginx/nginx.conf`, `stack-c/nginx/nginx.conf`
- Finding: nginx does not set security headers such as HSTS, `X-Frame-Options`, `X-Content-Type-Options`, or `Content-Security-Policy`.
- Impact: The reference gateway omits basic browser hardening controls that production consumers will expect as table-stakes.
- Fix approach: Add `add_header` directives in the nginx `http` block and document which headers are lab-safe over HTTP versus production-required with TLS.
- Effort: LOW

### M-4/S-8: JwtSession cookies lack Secure and SameSite flags
- Files: `stack-a/openig_home/config/config.json`, `stack-b/openig_home/config/config.json`, `stack-c/openig_home/config/config.json`
- Finding: OpenIG session cookies are not assigned `Secure` or `SameSite` flags.
- Impact: Browser session handling is weaker than the intended production pattern and does not model the final cookie posture expected for a reference deployment.
- Fix approach: Add cookie flag handling with `proxy_cookie_flags` in nginx. `Secure` remains a documented lab exception until TLS is enabled, but the production requirement must be explicit.
- Effort: LOW

### M-6/S-10: OpenIG runs as root in Stack A and Stack B
- Files: `stack-a/docker-compose.yml`, `stack-b/docker-compose.yml`
- Finding: `user: root` is explicitly configured for the OpenIG containers.
- Impact: The current setup violates least privilege and weakens the credibility of the stack as a production reference, even if the current lab justification is host-volume compatibility on macOS.
- Fix approach: Remove `user: root` or switch to a dedicated non-root UID after validating mounted-volume and entrypoint compatibility with the OpenIG image.
- Effort: LOW

### M-9/Code-M6: Vault error status inconsistency
- Files: `stack-a/openig_home/scripts/groovy/VaultCredentialFilter.groovy`, `stack-b/openig_home/scripts/groovy/VaultCredentialFilterRedmine.groovy`, `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
- Finding: Stack A returns `502 BAD_GATEWAY` when Vault auth fails, while Stack B and Stack C return `500`.
- Impact: The same upstream dependency failure yields different API contracts across stacks, which makes troubleshooting and pattern reuse inconsistent.
- Fix approach: Standardize all Vault auth/read upstream failures to `502 BAD_GATEWAY`.
- Effort: LOW

### A-3/S-14: Stack C nginx proxy timeout missing
- Files: `stack-c/nginx/nginx.conf`
- Finding: Stack C does not explicitly set `proxy_connect_timeout` or `proxy_read_timeout`, unlike Stack A/B.
- Impact: Stack C depends on nginx defaults instead of the standardized gateway tuning already present in the other stacks.
- Fix approach: Add timeout directives matching Stack A/B.
- Effort: LOW

### A-4: host.docker.internal not portable on Linux
- Files: `stack-a/docker-compose.yml`, `stack-c/docker-compose.yml`, `stack-a/openig_home/config/routes/00-backchannel-logout-app1.json`, `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json`, `stack-c/openig_home/config/routes/00-backchannel-logout-app6.json`, `stack-c/openig_home/config/routes/10-grafana.json`, `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- Finding: Stack A and Stack C route or compose wiring assumes `host.docker.internal`, which resolves automatically on Docker Desktop but not on Linux without explicit host-gateway mapping.
- Impact: The reference solution is not portable across the most common Docker host environments.
- Fix approach: Add `extra_hosts: host.docker.internal:host-gateway` to the relevant compose services and call the Linux portability requirement out in the integration guide.
- Effort: LOW

## Lab Exceptions (Acceptable with documentation)

These items remain acceptable only as explicitly documented lab constraints. They must not be presented as production-ready defaults.

### C-2/S-1: App session tokens in JwtSession over HTTP
- Finding: `CredentialInjector`, `RedmineCredentialInjector`, and `JellyfinTokenInjector` store downstream session material in `JwtSession`, and the cookie is transmitted over HTTP in the current lab.
- Why lab exception: This is a transport-security gap caused by Phase 7b TLS deferral, not a pattern-shape failure. The integration model is still valid once TLS is enabled.
- Documentation required: Add an explicit `PRODUCTION REQUIREMENT` note to `docs/deliverables/standard-gateway-pattern.md` and the integration guide stating that TLS is mandatory before using this pattern outside the lab.

### S-11/S-12/M-7: Vault TLS disabled + UI enabled
- Why lab exception: This is a deliberate lab-convenience tradeoff for bring-up and troubleshooting.
- Documentation required: Keep the warning in `vault-hardening-gaps.md` and confirm that the production checklist also states that Vault TLS must be enabled and the UI disabled before real deployment.

### M-8/S-13: Hardcoded passwords in vault-bootstrap.sh
- Why lab exception: The bootstrap script seeds deterministic test credentials for the lab. Production bootstrap would use externally injected secrets or an operator-driven secret-seeding process.
- Documentation required: Add a warning comment in the bootstrap script and keep the same caveat in the production checklist.

### M-7/S-11: Vault TLS across stacks
- Why lab exception: This remains intentionally deferred under Vault Hardening Phase 3 together with Raft work.
- Documentation required: Keep the deferment visible in the hardening plan and reference it from the readiness checklist so consumers do not interpret the current lab posture as acceptable production guidance.

## Nice to Have (Low priority)

| ID | Finding | File(s) | Effort |
|----|---------|---------|--------|
| L-1 | Redis port `6379` hardcoded literal | `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`, `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy` | LOW |
| L-3 | Log prefix naming inconsistent | All Groovy files under `stack-a/openig_home/scripts/groovy/`, `stack-b/openig_home/scripts/groovy/`, `stack-c/openig_home/scripts/groovy/` | LOW |
| L-4 | `SloHandlerJellyfin` skips Keycloak logout when no `id_token` | `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy` | LOW |
| L-6 | Jellyfin device ID derived from `session.hashCode()` is not stable | `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy`, `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy` | LOW |
| A-10 | Keycloak single point of failure not documented | `docs/deliverables/` | LOW |
| L-2 | Redis TTL `28800` still hardcoded in route args | `stack-a/openig_home/config/routes/00-backchannel-logout-app1.json`, `stack-b/openig_home/config/routes/00-backchannel-logout-app3.json`, `stack-b/openig_home/config/routes/00-backchannel-logout-app4.json`, `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json`, `stack-c/openig_home/config/routes/00-backchannel-logout-app6.json` | LOW |
| Code-M3 | `VaultCredentialFilter` copies still duplicate AppRole login/read logic | `stack-a/openig_home/scripts/groovy/VaultCredentialFilter.groovy`, `stack-b/openig_home/scripts/groovy/VaultCredentialFilterRedmine.groovy`, `stack-b/openig_home/scripts/groovy/VaultCredentialFilterJellyfin.groovy`, `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy` | MEDIUM |

## Fix Plan Input — Priority Table

| Priority | ID | Finding | Effort |
|----------|----|---------|--------|
| P1-MUST | H-4/S-2 | Redis authentication | MEDIUM |
| P1-MUST | H-5/S-3 | Secrets out of docker-compose to `.env` + OpenIG image pin [RESOLVED] | MEDIUM |
| P1-MUST | H-7/A-1 | Stack C docker-compose parity | MEDIUM |
| P1-MUST | A-6/A-7 | Keycloak URL externalization Stack A+C | MEDIUM |
| P1-MUST | M-5/S-9 | Stack C weak OIDC secrets [RESOLVED] | LOW |
| P1-MUST | L-5 | `PhpMyAdminCookieFilter` dead code | LOW |
| P2-SHOULD | M-11 | `readRespLine` EOF in `BackchannelLogoutHandler` | LOW |
| P2-SHOULD | M-12 | `base64UrlDecode` manual padding | LOW |
| P2-SHOULD | M-3/S-7 | nginx security headers | LOW |
| P2-SHOULD | M-4/S-8 | Cookie `Secure` / `SameSite` flags | LOW |
| P2-SHOULD | M-6/S-10 | OpenIG non-root user | LOW |
| P2-SHOULD | M-9 | Vault error status `502` consistency | LOW |
| P2-SHOULD | A-3 | Stack C nginx timeouts | LOW |
| P2-SHOULD | A-4 | `host.docker.internal` Linux portability | LOW |
| P3-NICE | L-1, L-2, L-3, L-4, L-6, A-10 | Low severity improvements | LOW |
| P3-NICE | Code-M3 | `VaultCredentialFilter` consolidation | MEDIUM |

## Resolved Findings Summary (Pattern Consolidation Phase)

| ID | Finding | Resolution |
|----|---------|------------|
| C-1 | JWKS cache race condition | `globals.compute()` atomic cache (commit `4d8f065`) |
| H-1 | `SloHandler` missing try-catch | Consolidated `SloHandler` template (commit `3b8a6d8`) |
| H-2 | `vault/keys/` not in `.gitignore` | Added `**/vault/keys/` (commit `5ae657e`) |
| H-3 | Redmine port `3000` exposed | Removed `3000:3000` mapping (commit `f86c7eb`) |
| H-5/S-3 | Secrets externalized from compose files | `.env` files are gitignored, `.env.example` files are committed, and all three stacks pin `openidentityplatform/openig:6.0.1` (commit `b738577`) |
| H-6 | JWKS TTL unit inconsistency | Standardized to seconds (commit `4d8f065`) |
| H-8 | `SessionBlacklistFilterApp2` divergent Base64 | File deleted in consolidation (commit `832bbae`) |
| H-9 | Stack C nginx proxy buffer missing | Added `proxy_buffer_size 128k` (commit `f86c7eb`) |
| M-2 | `CANONICAL_ORIGIN_*` env vars missing A/B | Added to docker-compose A+B (commit `aaf66d5`) |
| M-5/S-9 | Stack C weak OIDC client secrets | Rotated away from `secret-c` in STEP-02 (`37672ed`); APP5 re-validated 2026-03-18 with an alphanumeric-only secret compatible with OpenIG (`a403b3d`) |
| M-4 | Stack A `SloHandler` hardcoded Keycloak URL | Parameterized via `KEYCLOAK_BROWSER_URL` env (commit `3b8a6d8`) |
| M-14 | `App1ResponseRewriter.groovy` dead code | Deleted (commit `f86c7eb`) |
| Pattern | `SessionBlacklistFilter` 6 copies -> 1 template | Three per-stack parameterized copies via args (commits `a76e194`, `832bbae`) |
| Pattern | `BackchannelLogoutHandler` 3 copies -> 1 template | Three per-stack parameterized copies via args (commit `4d8f065`) |
| Pattern | `SloHandler` 5 copies -> 2 templates | Standard + Jellyfin-specific (commit `3b8a6d8`) |
