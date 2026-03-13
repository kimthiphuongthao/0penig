# Consolidated Security & Code Review Report

**Project:** SSO Lab (OpenIG 6 + Keycloak 24)  
**Branch:** security/h8-jwt-validation  
**Date:** 2025-03-13  
**Scope:** 3 stacks (A/B/C) — JWT validation, session management, SLO

## Executive Summary

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| Security | 2 | 3 | 2 | 0 |
| Code Quality | 2 | 5 | 4 | 6 |
| Total | 4 | 8 | 6 | 6 |

**Risk Level:** HIGH — Multiple critical security issues requiring immediate attention

## Critical Issues (Fix Immediately)

### 1. Hardcoded Keystore Password [SECURITY]
- Location: stack-a/config.json:15, stack-c/config.json:15
- Issue: JwtKeyStore.password: openig123 in plaintext
- Blast Radius: JWT token forgery across all stacks
- Fix: Move to ${sys:openig.keystore.password} + Vault injection

### 2. Hardcoded JwtSession sharedSecret [SECURITY]
- Location: All 3 stacks config.json
- Issue: Base64 secrets decode trivially
- Blast Radius: Session hijacking, auth bypass
- Fix: openssl rand -base64 32 → env var → Vault

### 3. Inconsistent JWKS Cache Logic [CODE]
- Location: BackchannelLogoutHandler.groovy (all stacks)
- Issue: Stack A/B use SECONDS, Stack C uses MILLISECONDS
- Fix: Standardize to seconds across all stacks

### 4. Redis Fail-Open on Connection Failure [CODE]
- Location: SessionBlacklistFilter.groovy (all stacks)
- Issue: If Redis down → blacklisted sessions bypass security
- Fix: Configurable fail-closed behavior for SLO-critical paths

## High Priority Issues

| # | Issue | Location | Security Risk |
|---|-------|----------|---------------|
| 5 | OAuth2 client secrets in plaintext | All route JSONs | Token impersonation |
| 6 | requireHttps: false on all routes | All OAuth2ClientFilter | Token interception |
| 7 | HTTP Keycloak endpoints (no TLS) | All JWKS calls | MITM on token validation |
| 8 | Inconsistent audience validation | Stack A/B only single audience | Backchannel logout rejection |
| 9 | Duplicate SessionBlacklistFilter variants | 6 nearly-identical files | Maintenance burden |
| 10 | Missing id_token_hint Stack A | SloHandler.groovy:24 | Keycloak logout rejection |
| 11 | Stack B missing JwtKeyStore | stack-b/config.json | Unsigned session cookies |

## Remediation Plan

### Phase 1: Emergency (Today)
- Rotate all exposed secrets
- Move secrets to environment variables + Vault

### Phase 2: Critical (This Week)
- Consolidate SessionBlacklistFilter → single parameterized implementation
- Add Redis fail-closed with circuit breaker
- Standardize JWKS cache TTL (seconds)
- Add multi-audience support Stacks A/B

### Phase 3: Security Hardening (Next Week)
- Enable requireHttps: true + TLS termination
- HTTPS for internal Keycloak communication
- Add Stack B JwtKeyStore config

## Positive Findings

- BackchannelLogoutHandler: Strong JWT validation (RS256 only, full claim validation, JWKS caching)
- Stack Independence: Proper isolation (separate Redis/Vault per stack, unique secrets)
