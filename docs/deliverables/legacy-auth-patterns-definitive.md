# Legacy Authentication/Logout Mechanisms - Definitive Reference
**Date:** 2026-03-12
**Purpose:** Tài liệu reference để đối chiếu với hiện trạng triển khai và tìm gaps
**Sources:** Claude (Exa MCP) + Codex (web search) + Gemini (deep research)

> Update 2026-03-17: Pattern Consolidation Steps 1-6 are complete. The live lab now uses consolidated SessionBlacklistFilter / BackchannelLogoutHandler / SloHandler templates; STEP-02 rotated Stack C OIDC secrets; STEP-03 moved compose secrets into gitignored `.env` files and pinned OpenIG to `6.0.1`. Operational follow-up 2026-03-18: Stack C Grafana re-validation passed after rotating APP5 to an alphanumeric-only secret because OpenIG `OAuth2ClientFilter` does not URL-encode `client_secret`. Validation follow-up 2026-03-19: the Phase 1+2 `JwtSession` production pattern is now fully validated across all three stacks, with `TokenReferenceFilter.groovy` offloading `oauth2:*` state and `BackchannelLogoutHandler.groovy` supporting both `RS256` and `ES256`.

---

## Executive Summary

Sau khi đối chứng cả 3 research sources, đây là các **authentication mechanisms cốt lõi** đã được verify:

### Authentication Mechanisms - Verified Consensus
| Mechanism | Claude | Codex | Gemini | Verdict |
|-----------|--------|-------|--------|---------|
| Form-based | ✅ | ✅ | ✅ | **CONFIRMED** |
| HTTP Basic | ✅ | ✅ | ✅ | **CONFIRMED** |
| Header-based | ✅ | ✅ | ✅ | **CONFIRMED** |
| Token-based | ✅ | ✅ | ✅ | **CONFIRMED** |
| LDAP | ✅ | ✅ | ✅ | **CONFIRMED** |

### Logout Mechanisms - Verified Consensus
| Mechanism | Claude | Codex | Gemini | Verdict |
|-----------|--------|-------|--------|---------|
| Local Clear | ✅ | ✅ | ✅ | **CONFIRMED** |
| 401 Challenge | ✅ | ✅ | ✅ | **CONFIRMED** |
| Token Revocation | ✅ | ✅ | ✅ | **CONFIRMED** |
| Front-channel | ✅ | ✅ | ✅ | **CONFIRMED** |
| Back-channel | ✅ | ✅ | ✅ | **CONFIRMED** |

### Security Risks - Verified Consensus
| Risk | Claude | Codex | Gemini | Verdict |
|------|--------|-------|--------|---------|
| Header Spoofing | ✅ | ✅ | ✅ | **CRITICAL - CONFIRMED** |
| Session Fixation | ✅ | ✅ | ✅ | **HIGH - CONFIRMED** |
| Logout Sync Gap | ✅ | ✅ | ✅ | **HIGH - CONFIRMED** |
| Token Leakage | ✅ | ✅ | ✅ | **CRITICAL - CONFIRMED** |

---

## Q1. Authentication Mechanisms (Definitive)

### Mechanism 1: Form-based Authentication

| Aspect | Details |
|--------|---------|
| **Cơ chế** | POST `username/password` → server tạo session + `Set-Cookie` |
| **Flow** | Browser → login page → POST credentials → 302 + cookie → subsequent requests với cookie |
| **Session Storage** | Server-side (memory/DB/Redis) + cookie reference ở browser |
| **Khi nào dùng** | Ứng dụng web legacy có trang login HTML |
| **Pros** | Phổ biến, dễ implement, user experience quen thuộc |
| **Cons** | CSRF risk, session fixation nếu không rotate ID, khó SLO |

**ASCII Flow:**
```text
Browser                Legacy App
   | GET /app              |
   |---------------------->|
   |<----- 302 /login -----|
   | GET /login            |
   |---------------------->|
   |<---- 200 login form --|
   | POST /login (u,p)     |
   |---------------------->|
   |<-- 302 /app + Set-Cookie: SID=abc
   | GET /app (Cookie SID) |
   |---------------------->|
   |<------- 200 OK -------|
```

**SSO Integration:** Gateway intercept form, inject credentials từ Vault, forward session cookie

---

### Mechanism 2: HTTP Basic Authentication

| Aspect | Details |
|--------|---------|
| **Cơ chế** | `WWW-Authenticate: Basic` → client gửi `Authorization: Basic base64(user:pass)` |
| **Flow** | Request → 401 challenge → resend với Auth header → 200 OK |
| **Session Storage** | Stateless (không session server), browser cache credentials |
| **Khi nào dùng** | APIs, monitoring tools, admin interfaces |
| **Pros** | Đơn giản, universal support, dễ test |
| **Cons** | Credential gửi mỗi request, no logout mechanism, TLS bắt buộc |

**RFC Reference:** RFC 7617, RFC 9110 (401 challenge)

**SSO Integration:** Gateway terminate Basic Auth, map sang IdP session

---

### Mechanism 3: Header-based Authentication (Pre-auth)

| Aspect | Details |
|--------|---------|
| **Cơ chế** | Gateway inject headers (`X-Remote-User`, `X-Forwarded-User`, `X-WEBAUTH-USER`) → app tin tưởng |
| **Flow** | User auth at IdP → gateway validates → forward request + identity headers |
| **Session Storage** | Session tại gateway, app có thể stateless |
| **Khi nào dùng** | Apps có chế độ "trusted proxy" hoặc "auth proxy enabled" |
| **Pros** | Retrofit nhanh, app không cần sửa nhiều, gateway control hoàn toàn |
| **Cons** | **Header spoofing risk** nếu trust boundary sai |

**ASCII Flow:**
```text
Browser           Gateway/Proxy                Legacy App
   |                    |                           |
   | 1) Access /app     |                           |
   |------------------->|                           |
   | 2) Redirect to IdP |                           |
   |<-------------------|                           |
   | 3) Return with SSO |                           |
   |------------------->|                           |
   |                    | 4) GET /app + X-User     |
   |                    |-------------------------->|
   |                    |<--------- 200 ------------|
   |<--------- 200 -----|                           |
```

**Security Critical:** Strip incoming headers từ client, chỉ inject tại gateway, trust boundary = gateway IP only

**Examples:** Grafana (`GF_AUTH_PROXY_ENABLED`), SonarQube header auth mode

---

### Pattern 4: Token-based (Bearer/JWT/API Key)

| Aspect | Details |
|--------|---------|
| **Cơ chế** | Client gửi `Authorization: Bearer <token>` hoặc `X-API-Key` |
| **Flow** | Login → token issuance → API calls với bearer token → expiry/refresh |
| **Session Storage** | Token tại client + server-side validation (signature/introspection/revocation) |
| **Khi nào dùng** | REST APIs, service-to-service, automation |
| **Pros** | Stateless, dễ federation, hợp microservices |
| **Cons** | Token leakage = impersonation, revoke không tức thời |

**RFC Reference:** RFC 6750 (OAuth 2.0 Bearer), RFC 7009 (Token Revocation)

**SSO Integration:** Gateway map OIDC token → app token, store trong session, auto-refresh

---

### Mechanism 5: LDAP Authentication (Direct Bind)

| Aspect | Details |
|--------|---------|
| **Cơ chế** | App bind trực tiếp vào LDAP/AD để verify credentials |
| **Flow** | User submit creds → app LDAP bind/search → map groups/roles → local session |
| **Session Storage** | App session cục bộ sau LDAP auth |
| **Khi nào dùng** | Enterprise apps có LDAP integration native |
| **Pros** | Tận dụng directory hiện hữu, central account management |
| **Cons** | Tight coupling LDAP schema, network dependency, SLO hạn chế |

**RFC Reference:** RFC 4513 (LDAP Authentication)

**Security:** LDAPS/StartTLS bắt buộc, bind account least privilege

---

## Q2. Logout Mechanisms (Definitive)

### Mechanism 1: Local Clear (App Logout URL)

| Aspect | Details |
|--------|---------|
| **Trigger** | User click logout → GET/POST `/logout` |
| **Server State** | App invalidate local session + clear cookie |
| **SLO Support** | ❌ LOW - chỉ local app, IdP session còn nguyên |
| **Challenge** | Orphan session ở IdP/gateway, user auto-login lại |

**Use case:** WordPress `wp-login.php?action=logout`, Grafana `/logout`

---

### Mechanism 2: 401 Challenge Reset

| Aspect | Details |
|--------|---------|
| **Trigger** | App/proxy trả về `401 Unauthorized` |
| **Server State** | Không clear credential, chỉ buộc client re-auth |
| **SLO Support** | ❌ LOW - browser cache behavior không predictable |
| **Challenge** | Không phải true logout, phụ thuộc user-agent |

**Use case:** Basic Auth apps, Prometheus

---

### Mechanism 3: Token Revocation

| Aspect | Details |
|--------|---------|
| **Trigger** | Client/proxy gọi revocation endpoint (`POST /revoke`, `POST /logout`) |
| **Server State** | Token marked revoked tại Authorization Server |
| **SLO Support** | ⚠️ MEDIUM - hiệu quả nếu RS introspect hoặc có blacklist |
| **Challenge** | Propagation delay, RS cache policy |

**RFC Reference:** RFC 7009 (OAuth 2.0 Token Revocation)

**Use case:** Vault (`POST /auth/token/revoke-self`), GitLab, Mattermost API

---

### Mechanism 4: Front-channel Logout

| Aspect | Details |
|--------|---------|
| **Trigger** | Browser redirect/iframe qua OP → nhiều RP logout URLs |
| **Server State** | Mỗi RP nhận logout notification qua browser context |
| **SLO Support** | ⚠️ MEDIUM - phụ thuộc browser cookie policy |
| **Challenge** | SameSite restrictions, 3rd-party cookie blocking |

**ASCII Flow:**
```text
Browser            OP/IdP                RP1            RP2
   | Logout click     |                    |              |
   |----------------->|                    |              |
   |<-- redirects ----|                    |              |
   |----> RP1 logout endpoint (browser) -->|              |
   |----> RP2 logout endpoint (browser) ----------------->|
   |<------------------- final redirect to post-logout ---|
```

**Spec Reference:** OpenID Connect Front-Channel Logout 1.0

---

### Mechanism 5: Back-channel Logout

| Aspect | Details |
|--------|---------|
| **Trigger** | OP gửi logout token server-to-server tới RP endpoint |
| **Server State** | RP terminate session theo `sid`/`sub` không cần browser |
| **SLO Support** | ✅ HIGH - reliable, không phụ thuộc browser |
| **Challenge** | App phải expose back-channel logout endpoint |

**ASCII Flow:**
```text
Browser              OP/IdP                       RP
   | logout request     |                         |
   |------------------->|                         |
   |                    | POST logout_token       |
   |                    |------------------------>|
   |                    |<------- 200/204 --------|
   |<------ logged out confirmation --------------|
```

**Spec Reference:** OpenID Connect Back-Channel Logout 1.0

---

## Q3. SSO Integration Strategies (Definitive)

**Lưu ý thuật ngữ:** "Integration Strategies" là cách tích hợp SSO vào app legacy, không phải authentication mechanisms. Đây là các chiến lược ở gateway level.

| Strategy | How It Works | Prerequisites | Trade-offs | Best For |
|----------|--------------|---------------|------------|----------|
| **Gateway Reverse Proxy** | Gateway handle OIDC/SAML, app nằm sau protected upstream | Control toàn bộ ingress, TLS termination | Centralized, single choke point | Most legacy apps |
| **Header Injection** | Gateway inject identity headers, app trust headers | App supports `REMOTE_USER` mode, trust boundary rõ | Fast retrofit, spoofing risk if misconfigured | Grafana, SonarQube, Nexus RUT |
| **Sidecar Auth Proxy** | Auth proxy chạy cùng pod/VM với app | Platform supports sidecar, traffic qua sidecar | Good isolation, operational overhead | Kubernetes workloads |
| **Credential Injection** | Gateway map SSO user → legacy creds, submit form/basic thay | Vault credentials, stable login endpoint | Works for non-modifiable apps, secret management complexity | WordPress, Jenkins form auth |

---

## Q4. Security Checklist (Severity-Rated)

| Risk | Severity | Detection | Mitigation | Status |
|------|----------|-----------|------------|--------|
| **Header Spoofing** | CRITICAL | Crafted request test | Strip headers at edge, trust gateway IP only, mTLS | ✅ Consensus |
| **Token Leakage** | CRITICAL | Log sampling, URL inspection | TLS mandatory, no token in URL, log redaction | ✅ Consensus |
| **Session Fixation** | HIGH | Cookie before/after auth test | Regenerate session ID post-auth | ✅ Consensus |
| **Logout Sync Gap** | HIGH | Logout test matrix | Back-channel preferred, front-channel fallback | ✅ Consensus |
| **CSRF on Login/Logout** | HIGH | CSRF token presence check | CSRF token + SameSite/Origin checks | ✅ Consensus |
| **Over-trust Forwarded Headers** | HIGH | Header injection test | Explicit trusted proxy list | ✅ Consensus |
| **Weak Cookie Flags** | MEDIUM | Cookie inspection | Secure, HttpOnly, SameSite | ✅ Consensus |
| **LDAP Channel Security** | MEDIUM | LDAP config audit | LDAPS/StartTLS, least privilege bind | ✅ Consensus |

---

## Gap Analysis Framework

### Step 1: Inventory (per app)

| Field | What to capture |
|-------|-----------------|
| App name/version | Product, version, deployment mode |
| Current authentication mechanism | Form-based/HTTP Basic/Header-based/Token-based/LDAP/hybrid |
| Session artifact | Cookie name, TTL, storage backend, rotation behavior |
| Logout behavior | Local URL, global logout linkage, timeout policy |
| Proxy/gateway topology | Direct access path, trusted proxies, header policy |
| Identity dependencies | IdP, LDAP/AD, vault, token services |

### Step 2: Control Scoring

Score per control: `0 = missing`, `1 = partial`, `2 = adequate`

| Control domain | 0 | 1 | 2 |
|----------------|---|---|---|
| Auth trust boundary | Direct header spoof possible | Partial filtering | Strict strip/overwrite + network trust |
| Session hardening | No rotation/weak cookie flags | Some controls | Rotation + secure flags + timeout |
| Logout coherence | Local only | Partial sync | Bi-directional sync with IdP/gateway |
| Token/credential hygiene | Secret leakage risk | Partially redacted | Redaction + short TTL + revocation |
| Observability/audit | No auth/logout traceability | Incomplete logs | Correlated logs + alert rules |

### Step 3: Audit Questions (Checklist)

1. **Login đang xảy ra ở app, gateway hay cả hai?** Evidence: HTTP trace và redirect chain.
2. **Sau login thành công, session ID có đổi không?** Evidence: cookie before/after auth.
3. **User có thể bypass gateway để gọi app trực tiếp không?** Evidence: network path + ingress rules.
4. **App có tin tưởng `X-Forwarded-*`/`X-Remote-User` từ client không?** Evidence: crafted request test.
5. **Logout từ app có làm mất session tại IdP/gateway không?** Evidence: logout test matrix.
6. **Logout từ IdP có invalidate local session app không?** Evidence: back-channel/front-channel logs.
7. **Token/credential có bị log lộ ở reverse proxy/app logs không?** Evidence: log sampling.
8. **Có policy revoke/blacklist cho token và propagation SLA không?** Evidence: AS config + runtime test.
9. **LDAP auth có dùng TLS và giới hạn bind permissions không?** Evidence: LDAP config/certs.
10. **Có rate limit/lockout cho login endpoint không?** Evidence: WAF/gateway policy.

---

## Recommended Evidence Pack

1. HAR/network capture cho login + logout flows
2. Gateway route/policy export (header strip/inject, authn filters)
3. App auth config snapshot (session, cookie, logout endpoint)
4. IdP client config (logout URIs, front/back-channel flags)
5. Security test results: spoofing, fixation, logout sync, token leakage

---

## Template-Based Integration

As of 2026-03-17 (Pattern Consolidation Steps 1-6), all gateway Groovy scripts follow a parameterized template architecture. New app integrations should copy these templates and configure via route JSON args.

Runtime note: pin OpenIG images to `openidentityplatform/openig:6.0.1`. Do not use the mutable `latest` tag, because `latest=6.0.2` is currently broken in this lab while `6.0.1` is the known-good OpenIG 6 runtime tag.

OpenIG compatibility note: when `OAuth2ClientFilter` consumes an OIDC `clientSecret`, generate a strong random alphanumeric-only value. Avoid Base64 secrets containing `+`, `/`, or `=` because OpenIG 6 sends `client_secret` without URL-encoding in the token request body.

Validated session note: when routes use browser-bound `JwtSession`, place `TokenReferenceFilter.groovy` immediately after `OAuth2ClientFilter` so the heavy `oauth2:*` entry is offloaded to Redis and the browser cookie keeps only a per-app token reference key (`token_ref_id_appN` on shared-cookie stacks, fallback `token_ref_id`) plus small identity markers. OIDC data-model note: `target = ${attributes.openid}` is request-scoped output written by `OAuth2ClientFilter.fillTarget()`; it does not mirror data into session. Persisted `session[oauth2Key]` state is written separately by `OAuth2Utils.saveSession()`, so lookups such as `session[oauth2Key].atr.id_token` must use the persisted blob, while live `user_info` belongs under `attributes.openid`.

### Available Templates (per stack)

| Template | File | Args | Purpose |
|----------|------|------|---------|
| TokenReferenceFilter | TokenReferenceFilter.groovy | clientEndpoint, redisHost, redisTtl | Offload heavy `oauth2:*` session state to Redis so `JwtSession` stays under the browser cookie budget |
| SessionBlacklistFilter | SessionBlacklistFilter.groovy | clientEndpoint, sessionCacheKey, canonicalOrigin | Check Redis blacklist on every request; redirect to re-auth if blacklisted |
| BackchannelLogoutHandler | BackchannelLogoutHandler.groovy | audiences (List), redisHost, jwksUri, issuer | Receive Keycloak backchannel logout POST; validate `RS256` / `ES256` JWTs against JWKS; write sid to Redis blacklist |
| SloHandler | SloHandler.groovy | clientEndpoint, clientId, canonicalOrigin, postLogoutPath | Intercept logout request; redirect to Keycloak end_session with id_token_hint |
| SloHandlerJellyfin | SloHandlerJellyfin.groovy | (hardcoded — Jellyfin-specific) | Same as SloHandler + calls Jellyfin /Sessions/Logout API before redirect |

### Template Selection Decision Tree

1. Does the route use `OAuth2ClientFilter` with browser-bound `JwtSession`? -> Add TokenReferenceFilter immediately after the OIDC filter
2. Does the app need SSO session revocation check on every request? -> Add SessionBlacklistFilter to route chain
3. Does Keycloak need to notify this app on global logout? -> Register backchannel logout URL + add BackchannelLogoutHandler route
4. Does the app have a logout URL to intercept? -> Add SloHandler route (or SloHandlerJellyfin for Jellyfin-specific API)
5. Does the app use a dedicated Keycloak client? -> Set matching clientId arg in SloHandler + BackchannelLogoutHandler audiences

### Args Binding Pattern (OpenIG 6.0.x runtime pinned to 6.0.1)

Each key in route JSON args block becomes a top-level Groovy variable.
Example route JSON:
  "type": "ScriptableFilter",
  "config": {
    "file": "SessionBlacklistFilter.groovy",
    "args": { "clientEndpoint": "/openid/app1", "sessionCacheKey": "oidc_sid" }
  }

Access in Groovy: binding.hasVariable('clientEndpoint') ? (clientEndpoint as String) : '/openid/default'
WARNING: Do NOT use args.clientEndpoint or (args as Map).clientEndpoint — these do NOT work in the OpenIG 6.0.x runtime used here.

---

## Sources (Primary)

- RFC 9110: HTTP Semantics - https://www.rfc-editor.org/rfc/rfc9110
- RFC 7617: Basic HTTP Authentication - https://www.rfc-editor.org/rfc/rfc7617
- RFC 6750: OAuth 2.0 Bearer Token - https://www.rfc-editor.org/rfc/rfc6750
- RFC 7009: OAuth 2.0 Token Revocation - https://www.rfc-editor.org/rfc/rfc7009
- RFC 4513: LDAP Authentication - https://www.rfc-editor.org/rfc/rfc4513
- OpenID Connect Front-Channel Logout 1.0 - https://openid.net/specs/openid-connect-frontchannel-1_0-final.html
- OpenID Connect Back-Channel Logout 1.0 - https://openid.net/specs/openid-connect-backchannel-1_0-final.html
- OWASP Session Management Cheat Sheet - https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
- OWASP Authentication Cheat Sheet - https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
- Grafana Auth Proxy - https://grafana.com/docs/grafana/latest/setup-grafana/configure-security/configure-authentication/auth-proxy/
- Keycloak Reverse Proxy - https://www.keycloak.org/server/reverseproxy
- Praetorian Research (Header Attacks, 2026-03-11) - https://www.praetorian.com/blog/reverse-proxy-header-attacks/

---

## Research Comparison

| Agent | Speed | Sources | Strength | Limitation |
|-------|-------|---------|----------|------------|
| **Claude (Exa)** | ~30s | 20+ URLs (2024-2026) | Fresh CVEs, latest content | Less app coverage |
| **Codex** | ~2-3min | 38 official docs | Actionable, specific endpoints | No visual diagrams |
| **Gemini** | ~10min | 6 references | Conceptual depth, diagrams | Slower, fewer citations |

**Definitive = Synthesis of all 3, cross-verified.**
