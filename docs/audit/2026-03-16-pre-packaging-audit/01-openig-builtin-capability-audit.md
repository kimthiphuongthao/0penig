# Task 1A: OpenIG 6.0.2 Built-in Capability Audit

**Agent:** document-specialist (Sonnet)
**Source:** https://github.com/OpenIdentityPlatform/OpenIG (master, tag 6.0.2)
**Date:** 2026-03-16

---

## A. Built-in Filters (Core)

Source: `openig-core/src/main/java/org/forgerock/openig/filter/`

| Filter Class | Purpose | Config-only? | Notes |
|---|---|---|---|
| `AssignmentFilter` | Assign values to request/response expressions | Yes | General-purpose mutator |
| `ChainFilterHeaplet` | Build filter chain (filters + handler) | Yes | Inline chain definitions |
| `ConditionEnforcementFilter` | Enforce boolean condition → 403 | Yes | Gate access on any EL expression |
| `ConditionalFilterHeaplet` | Apply filter only when condition is true | Yes | Conditional decoration |
| `CookieFilter` | Manage/suppress/relay cookies | Yes | MANAGE stores in session |
| `CryptoHeaderFilter` | Encrypt/decrypt HTTP headers | Yes | AES/ECB/PKCS5Padding default |
| `EntityExtractFilter` | Extract values from body via regex | Yes | CSRF tokens, hidden fields |
| `FileAttributesFilter` | Lookup record from CSV/delimiter file | Yes | Lazy evaluation |
| `HeaderFilter` | Add/remove headers on request/response | Yes | X-WEBAUTH-USER injection |
| `HttpBasicAuthFilter` | Inject Basic Auth credentials, retry on 401 | Yes | EL-aware: `${attributes.key}` |
| `LocationHeaderFilter` | Rewrite redirect Location headers | Yes | Essential for proxied backends |
| `PasswordReplayFilterHeaplet` | Form-based credential replay | Yes | Login page detection + CSRF extraction |
| `ScriptableFilter` | Execute Groovy script as filter | No (requires script) | Full filter lifecycle |
| `SqlAttributesFilter` | Execute SQL, expose first row as map | Yes | Requires JNDI DataSource |
| `StaticRequestFilter` | Replace request with constructed one | Yes | Form POST injection |
| `SwitchFilter` | Conditional routing to handlers | Yes | First-match wins |

**Throttling sub-package:**
| Class | Purpose | Config-only? |
|---|---|---|
| `ThrottlingFilterHeaplet` | Rate-limit via token bucket | Yes |
| `MappedThrottlingPolicyHeaplet` | Per-group throttling | Yes |
| `ScriptableThrottlingPolicy` | Groovy-defined rate policy | No |

## B. Built-in Handlers

Source: `openig-core/src/main/java/org/forgerock/openig/handler/`

| Handler Class | Purpose | Config-only? | Notes |
|---|---|---|---|
| `ClientHandler` | Outbound HTTP client | Yes | Required terminal handler |
| `DispatchHandler` | Route to handlers by condition | Yes | 404 if no match |
| `ScriptableHandler` | Execute Groovy as handler | No | Produces full response |
| `SequenceHandler` | Chain handlers sequentially | Yes | Multi-step flows (GET→POST) |
| `StaticResponseHandler` | Return static HTTP response | Yes | Blocking, maintenance, fail-closed |
| `RouterHandler` | Dynamic route loading from directory | Yes | Watches for file changes |

## C. Session Management

| Feature | Description |
|---|---|
| `JwtCookieSession` | Stateless encrypted cookie. RSAES_PKCS1_V1_5 + A128CBC_HS256. Max 4096 bytes. |
| `JwtSessionManager` | Config: `keystore`, `alias`, `password`, `cookieName`, `cookieDomain`, `sessionTimeout`, `sharedSecret` |
| **No server-side session store** | No Redis/DB session store built-in |
| **No session blacklist** | No revocation list — requires custom Groovy + external Redis |

## D. OAuth2/OIDC Support

Source: `openig-oauth2/src/main/java/org/forgerock/openig/filter/oauth2/`

| Class | Purpose | Supports logout? | Config-only? |
|---|---|---|---|
| `OAuth2ClientFilter` | Full OIDC RP (login, callback, refresh, userinfo) | **Local only** — NO end_session, NO id_token_hint, NO backchannel | Yes |
| `OAuth2ResourceServerFilter` | Bearer token validation | No | Yes |
| `ClientRegistration` | Client credentials + issuer config | N/A | Yes |
| `Issuer` | OpenID Provider metadata | N/A | Yes — **NO end_session_endpoint or jwks_uri** |

## E. SAML Support

| Class | Purpose | Config-only? |
|---|---|---|
| `SamlFederationHandler` | Full SAML 2.0 SP (SSO + SLO + ACS) | Yes |

## F. What Does NOT Exist in OpenIG 6.0.2

| Feature | Status |
|---|---|
| `SessionInfoFilter` | Does not exist (ForgeRock AM feature) |
| `TokenTransformationFilter` | Does not exist (ForgeRock AM feature) |
| `SingleSignOnFilter` | Does not exist as standalone |
| `openIdEndSessionOnLogout` config | Does not exist |
| `id_token_hint` in OAuth2ClientFilter | Does not exist |
| Backchannel logout (built-in) | Does not exist |
| `end_session_endpoint` in Issuer | Does not exist |
| `jwks_uri` in Issuer | Does not exist |
| Server-side session store | Does not exist (JwtCookieSession only) |
| Session revocation / blacklist | Does not exist |

## G. Decision Guide: Built-in vs Custom Groovy

| Task | Use built-in | Use Groovy |
|---|---|---|
| Inject header | `HeaderFilter` | — |
| Inject Basic Auth | `HttpBasicAuthFilter` | — |
| Inject form POST | `StaticRequestFilter` | — |
| Auto-detect login page + replay | `PasswordReplayFilterHeaplet` | Only if too complex for regex |
| OIDC RP flow | `OAuth2ClientFilter` | — |
| Call IdP end_session | — | `ScriptableHandler` (no built-in) |
| Backchannel logout | — | `ScriptableHandler` (no built-in) |
| Session blacklisting | — | `ScriptableFilter` + Redis |
| Vault AppRole fetch | — | `ScriptableFilter` |
| SAML SSO+SLO | `SamlFederationHandler` | — |
| Rate limiting | `ThrottlingFilterHeaplet` | Only for dynamic rates |

---

**Sources:**
- [Filter directory](https://github.com/OpenIdentityPlatform/OpenIG/tree/master/openig-core/src/main/java/org/forgerock/openig/filter)
- [Handler directory](https://github.com/OpenIdentityPlatform/OpenIG/tree/master/openig-core/src/main/java/org/forgerock/openig/handler)
- [OAuth2 client](https://github.com/OpenIdentityPlatform/OpenIG/tree/master/openig-oauth2/src/main/java/org/forgerock/openig/filter/oauth2/client)
- [SAML handler](https://github.com/OpenIdentityPlatform/OpenIG/tree/master/openig-saml/src/main/java/org/forgerock/openig/handler/saml)
- [Release 6.0.2](https://github.com/OpenIdentityPlatform/OpenIG/releases/tag/6.0.2)
