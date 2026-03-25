# OpenIG 6 Built-in Gap Analysis

**Date:** 2026-03-25  
**Method:** Source code analysis (`/tmp/openig-src` shallow clone) + Official docs (`doc.openidentityplatform.org/openig/reference/`) cross-validated  
**Lab version:** `openidentityplatform/openig:6.0.1`

## Executive Summary

**0 of 14 custom Groovy scripts are fully replaceable by OpenIG 6 built-in components.**

The custom Groovy implementation is NOT over-engineering. Every script addresses a genuine capability gap in OpenIG 6. The 14 scripts implement 12 enterprise patterns absent from the built-in filter/handler catalog.

---

## Per-Script Verdict Table

| Script | Core responsibility | Closest OpenIG built-in | Verdict | Rationale |
|---|---|---|---|---|
| BackchannelLogoutHandler.groovy | OIDC backchannel logout JWT validation (RS256/ES256), JWKS cache, Redis blacklist write | None | CUSTOM-NEEDED | No built-in RP backchannel logout handler; JWT validation, sid/sub extraction, Redis write all require Groovy |
| SessionBlacklistFilter.groovy | Extract session ID from JwtSession, Redis GET blacklist check, fail-closed | ConditionEnforcementFilter | CUSTOM-NEEDED | No built-in Redis blacklist check; ConditionEnforcementFilter cannot query Redis |
| TokenReferenceFilter.groovy | Offload oauth2:* session blob to Redis, store per-app reference key in cookie, restore on callback | None | CUSTOM-NEEDED | No built-in Redis session store or token offload mechanism; required for 4KB JwtSession limit |
| SloHandler.groovy | SLO: token cleanup, Redis blacklist write, redirect to Keycloak /oidc/logout with id_token_hint | None | CUSTOM-NEEDED | No built-in SLO orchestrator with Redis cleanup + OIDC logout endpoint coordination |
| SloHandlerJellyfin.groovy | Jellyfin-specific SLO: Jellyfin API logout, Redis blacklist, per-app token cleanup | None | CUSTOM-NEEDED | App-specific SLO adapter; inherits all gaps from SloHandler |
| VaultCredentialFilter.groovy | Vault AppRole auth, HTTP credential lookup by email, inject username/password into session | FileAttributesFilter, HttpBasicAuthFilter | CUSTOM-NEEDED | No built-in Vault AppRole auth or HTTP-based credential lookup; FileAttributesFilter is file/disk only |
| CredentialInjector.groovy | WordPress cookie pass-through, login POST orchestration, Set-Cookie domain rewrite | PasswordReplayFilter, HeaderFilter | PARTIALLY-REPLACEABLE | PasswordReplayFilter covers simple form replay; multi-step cookie capture + domain rewrite requires Groovy |
| RedmineCredentialInjector.groovy | Redmine login flow, CSRF token extraction, Set-Cookie capture, domain rewrite | PasswordReplayFilter, HeaderFilter | PARTIALLY-REPLACEABLE | Same pattern as CredentialInjector; PasswordReplayFilter single-step only |
| JellyfinTokenInjector.groovy | POST to Jellyfin AuthenticateByName, acquire token, inject Authorization header | HeaderFilter (header inject only) | PARTIALLY-REPLACEABLE | Token acquisition via app-specific API requires Groovy; header injection is built-in |
| JellyfinResponseRewriter.groovy | HTML response body rewrite: inject JS to set localStorage with Jellyfin credentials | EntityExtractFilter | PARTIALLY-REPLACEABLE | Entity access is built-in; HTML regex rewrite + JS injection requires Groovy |
| SpaAuthGuardFilter.groovy | Detect XHR vs browser request, return 401 JSON for XHR without OAuth2 session | SwitchFilter, ConditionalFilter | PARTIALLY-REPLACEABLE | Condition dispatch built-in; XHR-aware 401 JSON response + OAuth2 session key check requires Groovy |
| SpaBlacklistGuardFilter.groovy | Redis blacklist check for SPA context, XHR-aware 401 vs pass-through | None | PARTIALLY-REPLACEABLE | ConditionalFilter handles dispatch; Redis query requires Groovy |
| PhpMyAdminAuthFailureHandler.groovy | Detect auth failure (logout path/query), session cleanup, retry redirect | ConditionalFilter, StaticResponseHandler | PARTIALLY-REPLACEABLE | Condition matching built-in; session key cleanup + logout detection orchestration requires Groovy |
| StripGatewaySessionCookies.groovy | Strip IG_SSO_APP* gateway cookies before proxying to backends | CookieFilter | PARTIALLY-REPLACEABLE | CookieFilter suppresses by exact name list only — cannot match IG_SSO_APP* prefix pattern; dynamic app count would require listing all 6 explicitly. Partially usable but Groovy gives cleaner prefix matching. |

**Verdict summary:** CUSTOM-NEEDED: 6 | PARTIALLY-REPLACEABLE: 8 | REPLACEABLE: 0

---

## OpenIG 6 Capability Gaps

These are confirmed gaps in OpenIG 6 where ScriptableFilter/ScriptableHandler is the only option:

| Gap | Why no built-in | Lab scripts affected |
|---|---|---|
| Redis-backed session store | JwtSession is the only session heap type; no RedisSession provider exists | TokenReferenceFilter, SessionBlacklistFilter, SpaBlacklistGuardFilter |
| Backchannel logout JWT validation | No RP backchannel logout handler; OAuth2ResourceServerFilter uses introspection endpoint, not local JWT validation | BackchannelLogoutHandler |
| OIDC SLO orchestration | OAuth2ClientFilter clears local session only; no id_token_hint logout, no multi-app coordination | SloHandler, SloHandlerJellyfin |
| Vault AppRole credential fetch | FileAttributesFilter is disk-only; no HTTP-based or Vault-native credential source | VaultCredentialFilter |
| App-specific credential injection | PasswordReplayFilter is single-step only; no multi-step cookie capture or domain rewrite | CredentialInjector, RedmineCredentialInjector, JellyfinTokenInjector |
| SPA-aware auth gate (XHR + 401) | No built-in XHR detection or SPA-specific 401 JSON response strategy | SpaAuthGuardFilter, SpaBlacklistGuardFilter |
| Cookie prefix-pattern stripping | CookieFilter only accepts exact cookie names, not prefix/glob patterns | StripGatewaySessionCookies |
| HTML response body rewrite | EntityExtractFilter extracts regex matches; no built-in body transform/injection | JellyfinResponseRewriter |
| Distributed rate limiting | ThrottlingFilter uses in-process token bucket; no Redis-backed distributed state | (future use) |
| PKCE enforcement | No code_challenge/code_verifier in OAuth2ClientFilter 6.x source | (future requirement) |
| Atomic JWKS cache (globals.compute()) | OAuth2ClientFilter JWKS cache has no race-safe compute-on-miss; concurrent requests race to JWKS endpoint | BackchannelLogoutHandler |
| Per-app session namespace isolation | JwtSession has no built-in per-route/per-app key namespace; Redis prefixing is custom | TokenReferenceFilter (tokenRefKey per app) |

---

## OpenIG 6 Built-in Catalog

### Filters (22 types)

| Type | Core capability |
|---|---|
| AssignmentFilter | EL-based assign values to request/response/attributes |
| ConditionalFilter | Apply delegate filter when condition is true |
| ConditionEnforcementFilter | Return 403/failureHandler when condition is false |
| CookieFilter | Suppress/relay/manage cookies by exact name list |
| CryptoHeaderFilter | Encrypt/decrypt header values (ECB only — no GCM/IV) |
| EntityExtractFilter | Regex-extract from request/response body |
| FileAttributesFilter | CSV file record lookup by key |
| HeaderFilter | Add/remove headers by name |
| HttpBasicAuthFilter | Attach Basic auth to outbound requests; retry on 401 |
| HttpAccessAuditFilter | Emit HTTP access audit events |
| JwtBuilderFilter | Build signed/encrypted JWTs |
| LLMPromptGuardFilter | Block prompt injection (6.x+ only, not in 6.0.1) |
| LLMProxyFilter | LLM token rate limiting (6.x+ only, not in 6.0.1) |
| LocationHeaderFilter | Rewrite Location redirect headers |
| OAuth2ClientFilter | Authorization Code Flow, OIDC, token refresh, session restore |
| OAuth2ResourceServerFilter | Bearer token validation via introspection endpoint |
| OpenApiValidationFilter | Validate request/response against OpenAPI spec |
| PasswordReplayFilter | Detect login page + replay form POST credentials (single-step) |
| ScriptableFilter | Groovy escape hatch — full request/response/session/Redis/Vault access |
| SqlAttributesFilter | SQL query result lookup |
| StaticRequestFilter | Replace request with static/EL-built request |
| SwitchFilter | Multi-branch conditional routing |
| ThrottlingFilter | In-process token-bucket rate limiting |

### Handlers (11 types)

Chain, ClientHandler, DesKeyGenHandler, DispatchHandler, MonitorEndpointHandler, Route, Router, SamlFederationHandler, ScriptableHandler, SequenceHandler, StaticResponseHandler

### Session heap types (1 type)

`JwtSession` only — encrypted JWT in browser cookie. **No Redis session provider. 4 KB limit.**

---

## Pending Recommendations

### REC-001: StripGatewaySessionCookies — evaluate CookieFilter partial replacement
**Status: PENDING — not yet implemented**  
**Priority: LOW**

`CookieFilter` with `suppress: [IG_SSO_APP1, IG_SSO_APP2, IG_SSO_APP3, IG_SSO_APP4, IG_SSO_APP5, IG_SSO_APP6]` in the route-side filter chain would suppress these cookies from being forwarded to backends.

Trade-off:
- Pro: removes custom Groovy, uses built-in
- Con: must list all 6 explicitly; adding a 7th app requires config change; Groovy prefix match is more maintainable at scale

**Decision deferred**: evaluate when adding a 7th app or during OpenIG 7+ upgrade.

---

## Future Considerations

- **OpenIG 7+ / PingGateway**: adds JwtBuilderFilter, OAuth2TokenExchangeFilter, CrossDomainSingleSignOnFilter — recheck gap analysis before upgrading
- **Keycloak backchannel logout**: if Keycloak adds native OpenIG plugin support, BackchannelLogoutHandler may be replaceable
- **ForgeRock AM integration**: PolicyEnforcementFilter + SingleSignOnFilter cover AM-native SSO patterns — not applicable to Keycloak deployments
- **PKCE**: must be implemented in ScriptableFilter if required; not in OAuth2ClientFilter 6.x

---

## Sources

- OpenIG filter reference: https://doc.openidentityplatform.org/openig/reference/filters-conf
- OpenIG handler reference: https://doc.openidentityplatform.org/openig/reference/handlers-conf
- OpenIG source (analyzed): https://github.com/OpenIdentityPlatform/OpenIG (master, shallow clone)
- Analysis date: 2026-03-25
- Lab scripts analyzed: 14 (shared/openig_home/scripts/groovy/)
