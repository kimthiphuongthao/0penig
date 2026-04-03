# OpenIG Best Practices Compliance Evaluation

**Date:** 2026-04-02
**Scope:** shared-infra deployment (6 apps: WordPress, WhoAmI, Redmine, Jellyfin, Grafana, phpMyAdmin)
**Evaluator:** Claude Sonnet 4.6 (via oh-my-claudecode:critic agent)
**Verification:** Pending Codex review

---

## Executive Summary

| Metric | Result |
|--------|--------|
| **Overall Score** | 52/80 (65%) — MEDIUM |
| **Categories COMPLIANT** | 1/8 (Error Handling) |
| **Categories PARTIAL** | 6/8 |
| **Categories NON-COMPLIANT** | 1/8 (Observability) |
| **Production Ready** | NO — 6 findings require remediation |

---

## Compliance Scorecard

| Category | Score | Status |
|----------|-------|--------|
| Session Management | 6/10 | PARTIAL |
| OAuth2/OIDC | 3/10 | PARTIAL |
| Secret Management | 7/10 | PARTIAL |
| Error Handling | 9/10 | COMPLIANT |
| Security Controls | 6/10 | PARTIAL |
| Performance | 5/10 | PARTIAL |
| Observability | 3/10 | NON-COMPLIANT |
| Code Quality | 6/10 | PARTIAL |

---

## Critical Findings

| ID | Finding | Severity | Evidence |
|----|---------|----------|----------|
| OAUTH-001 | PKCE not implemented on any OAuth2ClientFilter | MAJOR | All route JSON files lack `pkce` config. Note: These are confidential server-side clients with clientSecret. PKCE is recommended hardening per OAuth 2.1, not a blocking requirement for confidential clients. |
| OAUTH-002 | Refresh tokens disabled | UNVERIFIED | Not evidenced in gateway code — may reflect Keycloak external state only; not verifiable from route configs |
| SESS-001 | JwtSession cookies lack explicit Secure flag | MAJOR | No secureCookie config in config.json or routes; nginx only adds SameSite=Lax. HttpOnly absence not confirmed by code inspection. |
| SECRET-001 | Redis stores plaintext OAuth2 token payloads | MAJOR | TokenReferenceFilter.groovy:350 |
| PERF-001 | No circuit breaker for Redis/Vault dependencies | MAJOR | Direct socket connections, no fallback |
| OBS-001 | No structured audit logging configured | MAJOR | No audit handler in config.json or routes; Groovy uses ad-hoc logger calls only. No evidence of token values in logs. |

---

## What's Done Right (Strengths)

| Area | Evidence |
|------|----------|
| Fail-closed pattern | SessionBlacklistFilter, TokenReferenceFilter, CredentialInjector return 500 on error |
| HTTP semantics | 400 for client errors, 500 for infra errors |
| JWKS caching | BackchannelLogoutHandler: 1-hour cache with stale-key fallback |
| Vault token caching | globals cache with expiry check |
| Security headers | X-Frame-Options, X-Content-Type-Options, Referrer-Policy in nginx.conf |
| Canonical origins | Redirect URLs use `CANONICAL_ORIGIN_APP*` env vars, not raw `Host` |
| Session timeout | 30 minutes (acceptable for lab) |
| 4KB JWT limit handled | TokenReferenceFilter offloads oauth2:* to Redis |
| Per-app isolation | 6 cookies, 6 heaps, 6 Redis users, 6 Vault AppRoles |

---

## Lab Exceptions (Accepted)

| Exception | Justification |
|-----------|---------------|
| HTTP-only transport | Lab environment; Phase 7b planned |
| `requireHttps: false` on all routes | Acceptable for internal testing |
| Manual Vault `secret_id` rotation | 72h TTL with runbook |
| No circuit breaker | Single-site lab; blast radius contained |

---

## Production Requirements (Phase 2)

| Priority | Action | Rationale |
|----------|--------|-----------|
| 1 | Implement PKCE (`AuthorizationCodeOAuth2ClientFilter`) | OAuth 2.1 draft mandates PKCE for all public clients |
| 2 | Enable TLS + Secure cookie flags | OWASP Session Management requirement |
| 3 | Encrypt Redis token payloads (Vault Transit) | Protect sensitive data at rest |
| 4 | Enable structured audit logging | Compliance requirement |
| 5 | Implement circuit breaker | Prevent cascade failures |
| 6 | Re-enable refresh tokens with rotation | Reduce re-auth frequency |

---

## Clarification: OAuth 2.0 vs OIDC

**Important:** OIDC **is** OAuth 2.0 (extended). PKCE (RFC 7636) is an OAuth 2.0 extension that applies to OIDC flows.

```
OIDC = OAuth 2.0 + ID Token + UserInfo Endpoint

OIDC with PKCE = OAuth 2.0 Authorization Code Flow + PKCE extension
```

Current lab uses:
- OAuth2ClientFilter (supports OIDC)
- Authorization Code Flow
- **WITHOUT PKCE** (design choice for simplicity)

Production should use:
- AuthorizationCodeOAuth2ClientFilter with PKCE enabled
- Per OAuth 2.1 draft recommendation

---

## References

- OpenIG 6 Documentation: https://backstage.pingidentity.com/docs/ig/
- HashiCorp Vault AppRole Best Practices: https://developer.hashicorp.com/vault/tutorials/auth-methods/approle-best-practices
- OWASP Session Management: https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
- RFC 7636 (PKCE): https://datatracker.ietf.org/doc/html/rfc7636
- OAuth 2.0 Best Practices: https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics

---

## Verification Request for Codex

**Task:** Review this evaluation and validate findings.

**Specific questions:**
1. Is PKCE implementation required for this lab's security model?
2. Are the 6 critical findings accurate and properly severity-rated?
3. Are there any false positives in this assessment?
4. What additional OpenIG-specific best practices should be considered?

**Files to review:**
- `shared/openig_home/config/routes/*.json` (OAuth2ClientFilter configs)
- `shared/openig_home/config/config.json` (JwtSession config)
- `shared/openig_home/scripts/groovy/*.groovy` (error handling, caching)
- `shared/nginx/nginx.conf` (security headers, cookie flags)
