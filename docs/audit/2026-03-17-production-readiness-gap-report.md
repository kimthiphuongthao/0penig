# Production Readiness Gap Report — SSO Lab

Date: 2026-03-17  
Scope: All 3 stacks (Stack A/B/C)  
Status: NOT READY

## Executive Summary

This report captures the 2026-03-17 production-readiness gap assessment for SSO Lab, cross-referencing the 2026-03-16 pre-packaging audit against the current codebase. The current state is **NOT READY** for release as a production reference solution: **17 findings remain open, 7 are partial, and 57 are resolved** out of 81 checks.

Resolved work now covers 57 findings, and one additional finding is now explicitly documented as a partial macOS lab exception. The resolved work includes the JWKS cache race, `SloHandler` try-catch hardening, TTL unit standardization, consolidation of `SessionBlacklistFilter` / `BackchannelLogoutHandler` / `SloHandler`, `vault/keys/` repo hygiene, Redmine direct-port exposure removal, Stack C nginx buffer alignment, `CANONICAL_ORIGIN_*` environment variables, dead-code cleanup, Stack C OIDC client secret rotation, STEP-03 secret externalization plus OpenIG image pinning, STEP-04 Redis authentication hardening, STEP-05 Keycloak URL externalization, STEP-06 Stack C compose parity plus Stack A OpenIG healthchecks, STEP-07 Vault `502` alignment, STEP-08 EOF fail-closed behavior, STEP-09 Base64 URL decoding simplification, STEP-10 Stack C timeout alignment, STEP-11 Linux `extra_hosts` portability, STEP-12 nginx baseline security headers, STEP-13 nginx cookie `SameSite=Lax` flags, the validated Phase 1+2 `JwtSession` production restore, `BackchannelLogoutHandler` `ES256` / EC support, and explicit Keycloak shared-dependency guidance in the deliverables.

Operational follow-up after the scorecard: Stack C Grafana SSO/SLO was re-validated successfully on 2026-03-18. The earlier APP5 padding theory was superseded; the verified root cause was OpenIG `OAuth2ClientFilter` not URL-encoding `client_secret`, so APP5 now uses a strong alphanumeric-only secret and the recreated Stack C OpenIG containers are confirmed working. Additional follow-up on 2026-03-19 validated the full Phase 1+2 `JwtSession` production pattern on all three stacks, including Redis token-reference offload and `BackchannelLogoutHandler` support for `RS256` and `ES256` logout tokens.

The remaining 17 open findings are concentrated in three categories that matter for a reusable reference solution:

- Security: the `Secure` cookie flag remains deferred until TLS is enabled, and OpenIG non-root remains partially blocked by macOS host-mount constraints.
- Architecture and documentation: the documented lab exceptions must remain visible so the HTTP and Vault deferments are not mistaken for production defaults.
- Code quality and operational consistency: several low-effort cleanup items remain open, including hardcoded Redis literals, Groovy log-prefix consistency, Jellyfin-specific logout/device-ID edge cases, and duplicated Vault AppRole logic.

The most important conclusion is that the lab now demonstrates a strong reference pattern shape, but it is not yet publishable as a copy-paste production reference. The blocking work is concentrated in gateway-side assets only: `docker-compose.yml`, OpenIG route JSON, Groovy scripts, and `nginx.conf`.

## Must Fix (Blocking for production reference solution)

These items block production-reference status because they either leave an active security gap open or prevent one of the three stacks from acting as a faithful implementation of the consolidated pattern.

### H-4/S-2: Redis no authentication [RESOLVED]
- Files: `stack-a/docker-compose.yml`, `stack-b/docker-compose.yml`, `stack-c/docker-compose.yml`, `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`, `stack-b/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`, `stack-c/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`, `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- Finding: RESOLVED on 2026-03-18 — all three Redis services now enforce `requirepass`, and the Groovy revocation read/write paths authenticate with `AUTH` before issuing `GET` or `SET`.
- Impact: Redis blacklist tampering from other containers on the Docker network is no longer a default path in the reference stacks.
- Fix approach: Completed on 2026-03-18. Keep Redis passwords externalized via environment variables, require `AUTH` on every revocation connection, and retain the verified SSO/SLO regression check across all six apps.
- Effort: MEDIUM

### H-5/S-3: Live secrets in docker-compose committed to git [RESOLVED]
- Files: `stack-a/docker-compose.yml`, `stack-b/docker-compose.yml`, `stack-c/docker-compose.yml`
- Finding: RESOLVED — Secrets externalized to `.env` files (gitignored). `.env.example` committed. Image pinned to `6.0.1`.
- Impact: Live secrets no longer ship in versioned compose files, and the gateway no longer depends on the mutable `latest` tag that broke OpenIG 6 startup.
- Fix approach: Completed. Keep `.env.example` as the committed contract, never commit `.env`, and always pin OpenIG image versions explicitly.
- Effort: MEDIUM

### H-7/A-1: Stack C docker-compose parity gap vs Stack A/B reference [RESOLVED]
- Files: `stack-a/docker-compose.yml`, `stack-c/docker-compose.yml`
- Finding: RESOLVED on 2026-03-18 — Stack C now carries the same compose baseline controls as the Stack A/B reference pattern, including explicit `container_name`, `restart`, `platform`, `OPENIG_NODE_NAME`, `KEYCLOAK_INTERNAL_URL`, and `/openig/api/info` healthchecks. The same batch also aligned Stack A OpenIG services to the same healthcheck contract.
- Impact: Stack C is now a faithful instance of the consolidated reference pattern, and all three stacks share the same OpenIG health probe shape for operations and smoke testing.
- Fix approach: Completed on 2026-03-18. Keep future Stack C compose drift intentional and documented, and retain the shared `/openig/api/info` healthcheck baseline across all OpenIG services.
- Effort: MEDIUM

### M-5/S-9: Stack C weak OIDC client secrets [RESOLVED]
- Files: `stack-c/docker-compose.yml`
- Finding: RESOLVED — Stack C clients were rotated away from the trivially guessable value `secret-c` in STEP-02, and APP5 was re-rotated on 2026-03-18 to a strong alphanumeric-only secret after confirming OpenIG `OAuth2ClientFilter` does not URL-encode `client_secret`.
- Impact: Stack C no longer ships trivially guessable OIDC client credentials in a production-readiness reference path.
- Fix approach: Completed. Keep the strong-random secret generation requirement documented in the gateway pattern, and require alphanumeric-only values whenever OpenIG `OAuth2ClientFilter` consumes the secret.
- Effort: LOW

### A-6/A-7/M-13/S-17: Keycloak URLs hardcoded in Stack A and Stack C routes [RESOLVED]
- Files: `stack-a/openig_home/config/routes/01-wordpress.json`, `stack-a/openig_home/config/routes/02-app2.json`, `stack-a/openig_home/config/routes/00-backchannel-logout-app1.json`, `stack-c/openig_home/config/routes/10-grafana.json`, `stack-c/openig_home/config/routes/11-phpmyadmin.json`, `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json`, `stack-c/openig_home/config/routes/00-backchannel-logout-app6.json`
- Finding: RESOLVED on 2026-03-18 — Stack A and Stack C route JSON now externalize browser-facing Keycloak values through `KEYCLOAK_BROWSER_URL` and server-to-server token, userinfo, and JWKS calls through `KEYCLOAK_INTERNAL_URL`. The hardcoded route literals and route-level `endSession` wiring were removed.
- Impact: Keycloak endpoint changes are now environment-only updates rather than multi-file route edits, and all three stacks share the same env-driven route pattern.
- Fix approach: Completed on 2026-03-18. Keep `issuer` and browser-facing authorize/logout semantics on `KEYCLOAK_BROWSER_URL`, and keep `tokenEndpoint`, `userInfoEndpoint`, and `jwksUri` on `KEYCLOAK_INTERNAL_URL`.
- Effort: MEDIUM

### L-5: PhpMyAdminCookieFilter.groovy dead code [RESOLVED]
- Files: `stack-c/openig_home/scripts/groovy/PhpMyAdminCookieFilter.groovy`
- Finding: RESOLVED — the unused script was deleted in the L-5 fix, and no live route references remain.
- Impact: The misleading dead-code control is no longer shipped in Stack C.
- Fix approach: Completed. Keep the WONT_FIX phpMyAdmin CSRF decision documented in the report and integration docs.
- Effort: LOW

## Should Fix (Production quality)

These items are not the primary blockers, but they should be completed before calling the lab production-quality and operationally consistent.

### M-11: readRespLine does not throw on EOF in BackchannelLogoutHandler [RESOLVED]
- Files: `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- Finding: RESOLVED on 2026-03-18 — `readRespLine` now throws `IOException("EOF")` on unexpected Redis socket closure in all three `BackchannelLogoutHandler` copies.
- Impact: Backchannel logout now fails closed on unexpected connection termination instead of swallowing the failure, which improves revocation correctness and operator visibility.
- Fix approach: Completed on 2026-03-18. Keep EOF handling identical to the hardened `SessionBlacklistFilter.groovy` behavior.
- Effort: LOW

### M-12: base64UrlDecode manual padding in BackchannelLogoutHandler [RESOLVED]
- Files: `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-b/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`, `stack-c/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
- Finding: RESOLVED on 2026-03-18 — all three `BackchannelLogoutHandler` copies now rely directly on `java.util.Base64.getUrlDecoder().decode(input)` without manual padding logic.
- Impact: The logout-token parsing path is simpler and lower-risk while preserving the same Java 8+ decoding behavior.
- Fix approach: Completed on 2026-03-18. Keep the helper thin and rely on the standard URL-safe decoder rather than duplicating padding logic in gateway code.
- Effort: LOW

### M-3/S-7: No security response headers on nginx [RESOLVED]
- Files: `stack-a/nginx/nginx.conf`, `stack-b/nginx/nginx.conf`, `stack-c/nginx/nginx.conf`
- Finding: RESOLVED on 2026-03-18 — all three nginx configs now set `X-Frame-Options: SAMEORIGIN`, `X-Content-Type-Options: nosniff`, and `Referrer-Policy: strict-origin-when-cross-origin`. `Content-Security-Policy` remains intentionally omitted pending app-specific tuning, and HSTS remains deferred until TLS exists.
- Impact: The reference gateway now includes the baseline browser hardening headers that are safe in the current HTTP lab without shipping a misleading shared CSP policy.
- Fix approach: Completed on 2026-03-18. Keep these headers in the nginx `http` block, add CSP only per application after validation, and enable HSTS only when real TLS termination is in place.
- Effort: LOW

### M-4/S-8: JwtSession cookies lack Secure and SameSite flags [RESOLVED]
- Files: `stack-a/nginx/nginx.conf`, `stack-b/nginx/nginx.conf`, `stack-c/nginx/nginx.conf`
- Finding: RESOLVED on 2026-03-18 — all three nginx configs now apply `proxy_cookie_flags` to the OpenIG session cookies (`IG_SSO`, `IG_SSO_B`, `IG_SSO_C`) with `SameSite=Lax`. `Secure` remains intentionally deferred until TLS because browsers ignore `Secure` cookies on plain HTTP.
- Impact: The lab now models the intended `SameSite` baseline without breaking the current HTTP-only cookie flow.
- Fix approach: Completed on 2026-03-18. Keep `SameSite=Lax` at nginx, and add `Secure` only when TLS termination is enabled.
- Effort: LOW

### M-6/S-10: OpenIG runs as root in Stack A and Stack B [PARTIAL]
- Files: `stack-a/docker-compose.yml`, `stack-b/docker-compose.yml`, `stack-c/docker-compose.yml`
- Finding: PARTIAL on 2026-03-18 — `user: root` remains on all six OpenIG services, but the compose files now carry an explicit macOS lab-exception comment. Non-root is not currently viable with the mounted Vault/AppRole files on macOS (`-rw-------` owner-only), and Stack C still mutates `config.json` in place at startup.
- Impact: Least privilege is still not achieved in the lab, but the constraint and the production requirement are now explicit in the deployment contract.
- Fix approach: Partial on 2026-03-18. Keep `user: root` only as a documented macOS lab exception. Production should move to non-root with copied writable config plus injected secrets instead of host mounts.
- Effort: LOW

### M-9/Code-M6: Vault error status inconsistency [RESOLVED]
- Files: `stack-a/openig_home/scripts/groovy/VaultCredentialFilter.groovy`, `stack-b/openig_home/scripts/groovy/VaultCredentialFilterRedmine.groovy`, `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
- Finding: RESOLVED on 2026-03-18 — Stack B and Stack C Vault credential filters now return `502 BAD_GATEWAY` for Vault auth/read upstream failures, matching Stack A.
- Impact: Vault dependency failures now yield one consistent gateway contract across all three stacks, which improves troubleshooting and pattern reuse.
- Fix approach: Completed on 2026-03-18. Keep Vault auth/read failures mapped to `502 BAD_GATEWAY` everywhere the gateway depends on Vault as an upstream service.
- Effort: LOW

### A-3/S-14: Stack C nginx proxy timeout missing [RESOLVED]
- Files: `stack-c/nginx/nginx.conf`
- Finding: RESOLVED on 2026-03-18 — Stack C now explicitly sets `proxy_connect_timeout 3s`, `proxy_read_timeout 60s`, and `proxy_send_timeout 60s` on the same user-facing and backchannel proxy paths as the other stacks.
- Impact: Stack C now uses the same standardized gateway timeout profile as the reference baseline instead of relying on nginx defaults.
- Fix approach: Completed on 2026-03-18. Keep Stack C timeout values aligned with Stack A/B unless a stack-specific variance is documented and tested.
- Effort: LOW

### A-4: host.docker.internal not portable on Linux [RESOLVED]
- Files: `stack-a/docker-compose.yml`, `stack-c/docker-compose.yml`, `stack-a/openig_home/config/routes/00-backchannel-logout-app1.json`, `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json`, `stack-c/openig_home/config/routes/00-backchannel-logout-app6.json`, `stack-c/openig_home/config/routes/10-grafana.json`, `stack-c/openig_home/config/routes/11-phpmyadmin.json`
- Finding: RESOLVED on 2026-03-18 — all six OpenIG services now declare `extra_hosts: host.docker.internal:host-gateway`, so Stack A and Stack C no longer depend on Docker Desktop-only hostname magic for internal Keycloak reachability.
- Impact: The reference solution is now portable across Linux and Docker Desktop hosts without route or compose rewrites.
- Fix approach: Completed on 2026-03-18. Keep the host-gateway mapping on every OpenIG service that uses `host.docker.internal` in env-backed route configuration.
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
| L-2 | Redis TTL `28800` still hardcoded in route args | `stack-a/openig_home/config/routes/00-backchannel-logout-app1.json`, `stack-b/openig_home/config/routes/00-backchannel-logout-app3.json`, `stack-b/openig_home/config/routes/00-backchannel-logout-app4.json`, `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json`, `stack-c/openig_home/config/routes/00-backchannel-logout-app6.json` | LOW |
| Code-M3 | `VaultCredentialFilter` copies still duplicate AppRole login/read logic | `stack-a/openig_home/scripts/groovy/VaultCredentialFilter.groovy`, `stack-b/openig_home/scripts/groovy/VaultCredentialFilterRedmine.groovy`, `stack-b/openig_home/scripts/groovy/VaultCredentialFilterJellyfin.groovy`, `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy` | MEDIUM |

## Fix Plan Input — Priority Table

| Priority | ID | Finding | Effort |
|----------|----|---------|--------|
| P1-MUST | H-4/S-2 | Redis authentication [RESOLVED 2026-03-18] | MEDIUM |
| P1-MUST | H-5/S-3 | Secrets out of docker-compose to `.env` + OpenIG image pin [RESOLVED] | MEDIUM |
| P1-MUST | H-7/A-1 | Stack C docker-compose parity + Stack A healthcheck baseline [RESOLVED 2026-03-18] | MEDIUM |
| P1-MUST | A-6/A-7 | Keycloak URL externalization Stack A+C [RESOLVED 2026-03-18] | MEDIUM |
| P1-MUST | M-5/S-9 | Stack C weak OIDC secrets [RESOLVED] | LOW |
| P1-MUST | L-5 | `PhpMyAdminCookieFilter` dead code [RESOLVED 2026-03-17] | LOW |
| P2-SHOULD | M-11 | `readRespLine` EOF in `BackchannelLogoutHandler` [RESOLVED 2026-03-18] | LOW |
| P2-SHOULD | M-12 | `base64UrlDecode` manual padding [RESOLVED 2026-03-18] | LOW |
| P2-SHOULD | M-3/S-7 | nginx security headers [RESOLVED 2026-03-18] | LOW |
| P2-SHOULD | M-4/S-8 | Cookie `Secure` / `SameSite` flags [RESOLVED 2026-03-18] | LOW |
| P2-SHOULD | M-6/S-10 | OpenIG non-root user [PARTIAL 2026-03-18] | LOW |
| P2-SHOULD | M-9 | Vault error status `502` consistency [RESOLVED 2026-03-18] | LOW |
| P2-SHOULD | A-3 | Stack C nginx timeouts [RESOLVED 2026-03-18] | LOW |
| P2-SHOULD | A-4 | `host.docker.internal` Linux portability [RESOLVED 2026-03-18] | LOW |
| P3-NICE | L-1, L-2, L-3, L-4, L-6 | Low severity improvements | LOW |
| P3-NICE | Code-M3 | `VaultCredentialFilter` consolidation | MEDIUM |

## Resolved Findings Summary (Pattern Consolidation Phase)

| ID | Finding | Resolution |
|----|---------|------------|
| C-1 | JWKS cache race condition | `globals.compute()` atomic cache (commit `4d8f065`) |
| H-1 | `SloHandler` missing try-catch | Consolidated `SloHandler` template (commit `3b8a6d8`) |
| H-2 | `vault/keys/` not in `.gitignore` | Added `**/vault/keys/` (commit `5ae657e`) |
| H-3 | Redmine port `3000` exposed | Removed `3000:3000` mapping (commit `f86c7eb`) |
| H-4/S-2 | Redis authentication | Redis `requirepass` enabled in all three stacks and OpenIG revocation sockets now send `AUTH` before blacklist `GET`/`SET` (2026-03-18) |
| H-5/S-3 | Secrets externalized from compose files | `.env` files are gitignored, `.env.example` files are committed, and all three stacks pin `openidentityplatform/openig:6.0.1` (commit `b738577`) |
| H-6 | JWKS TTL unit inconsistency | Standardized to seconds (commit `4d8f065`) |
| H-8 | `SessionBlacklistFilterApp2` divergent Base64 | File deleted in consolidation (commit `832bbae`) |
| H-9 | Stack C nginx proxy buffer missing | Added `proxy_buffer_size 128k` (commit `f86c7eb`) |
| H-7/A-1 | Stack C compose parity + Stack A healthcheck baseline | Stack C compose now matches the shared OpenIG baseline for names, restart policy, platform, node identity, and healthchecks; Stack A OpenIG now uses the same `/openig/api/info` healthcheck contract (2026-03-18) |
| A-6/A-7/M-13/S-17 | Keycloak URLs externalized in Stack A/C | Routes now use `KEYCLOAK_BROWSER_URL` for browser-facing issuer/authorize semantics and `KEYCLOAK_INTERNAL_URL` for token, userinfo, and JWKS calls (2026-03-18) |
| M-2 | `CANONICAL_ORIGIN_*` env vars missing A/B | Added to docker-compose A+B (commit `aaf66d5`) |
| M-5/S-9 | Stack C weak OIDC client secrets | Rotated away from `secret-c` in STEP-02 (`37672ed`); APP5 re-validated 2026-03-18 with an alphanumeric-only secret compatible with OpenIG (`a403b3d`) |
| M-9/Code-M6 | Vault upstream failures standardized to `502` | Stack B/C Vault credential filters now return `502 BAD_GATEWAY` for Vault auth/read failures, matching Stack A (2026-03-18) |
| M-11 | EOF handling in `readRespLine` | Unexpected Redis EOF now throws `IOException` and fails closed in all three backchannel handlers (2026-03-18) |
| M-12 | Base64 URL decoder simplification | Manual padding logic removed; Java's standard URL decoder now handles the logout-token decode path (2026-03-18) |
| M-4 | Stack A `SloHandler` hardcoded Keycloak URL | Parameterized via `KEYCLOAK_BROWSER_URL` env (commit `3b8a6d8`) |
| M-4/S-8 | Cookie `SameSite` flags via nginx | `proxy_cookie_flags` now sets `SameSite=Lax` on `IG_SSO`, `IG_SSO_B`, and `IG_SSO_C`; `Secure` remains deferred until TLS so the HTTP lab cookie flow keeps working (2026-03-18) |
| M-14 | `App1ResponseRewriter.groovy` dead code | Deleted (commit `f86c7eb`) |
| A-3/S-14 | Stack C nginx timeouts aligned | Stack C now uses the standardized `3s` connect and `60s` read/send timeout profile on user-facing and backchannel proxy paths (2026-03-18) |
| A-4 | Linux portability for `host.docker.internal` | `extra_hosts: host.docker.internal:host-gateway` added to all six OpenIG services (2026-03-18) |
| M-3/S-7 | nginx security headers | Added `X-Frame-Options`, `X-Content-Type-Options`, and `Referrer-Policy`; CSP intentionally omitted pending app-specific tuning, HSTS deferred until TLS (2026-03-18) |
| BUG-JWTSESSION-4KB | `JwtSession` production pattern restore | Heap renamed to `Session`, Stack A/B app cookies moved to browser pass-through, `TokenReferenceFilter.groovy` now offloads `oauth2:*` state to Redis, and full login+logout validation passed across all three stacks on 2026-03-19 (`0454796`, `78e2128`, `895e401`, `9b2d109`, `47cbab9`) |
| Post-audit | `BackchannelLogoutHandler` `ES256` / EC support | Logout-token validation now accepts `RS256` and `ES256`, reconstructs EC `P-256` keys from JWKS, and verifies ECDSA signatures after raw `R||S` -> DER conversion (`646a45a`, `d2eb8e9`) |
| A-10 | Keycloak single-point-of-failure guidance | Deliverables now call out Keycloak as a shared dependency that needs explicit HA/availability planning for new integrations (2026-03-19) |
| Pattern | `SessionBlacklistFilter` 6 copies -> 1 template | Three per-stack parameterized copies via args (commits `a76e194`, `832bbae`) |
| Pattern | `BackchannelLogoutHandler` 3 copies -> 1 template | Three per-stack parameterized copies via args (commit `4d8f065`) |
| Pattern | `SloHandler` 5 copies -> 2 templates | Standard + Jellyfin-specific (commit `3b8a6d8`) |
