# Legacy Authentication Mechanisms Reference for Non-SSO Apps

Date: 2026-03-12
Scope: Ứng dụng legacy chưa hỗ trợ SSO/OIDC native, dùng làm baseline để đối chiếu hiện trạng triển khai.

## Q1. Authentication Mechanisms

| Mechanism | Cơ chế | Flow điển hình | Session storage | Ưu điểm | Nhược điểm / rủi ro |
|---|---|---|---|---|---|
| Form-based Authentication | User nhập `username/password` qua form POST (`/login`) | Browser -> app login page -> POST credential -> app tạo session và set cookie | Server-side session store (memory/DB/redis) + cookie session id ở browser | Phổ biến, dễ retrofit qua gateway | Dễ dính CSRF/session fixation nếu không rotate session ID, khó SSO native |
| HTTP Basic Authentication | App trả `401 + WWW-Authenticate: Basic`, client gửi `Authorization: Basic base64(user:pass)` mỗi request | Request protected -> 401 challenge -> resend kèm Authorization | Stateless ở app (thường không tạo app session) | Đơn giản, tương thích tooling tốt | Credential lặp lại mỗi request; browser logout khó; bắt buộc TLS |
| Header-based Authentication (Pre-auth) | Reverse proxy/gateway xác thực trước rồi inject identity headers (`X-Forwarded-User`, `X-WEBAUTH-USER`, ...) | User auth tại gateway -> gateway forward request + headers -> app trust header | Session có thể nằm ở gateway, app có thể stateless hoặc có local session | Retrofit nhanh cho app legacy không sửa code nhiều | Header spoofing nếu trust boundary sai; cần strip/overwrite header từ client |
| Token-based Authentication (Bearer/JWT) | Client gửi `Authorization: Bearer <token>`; app/resource server validate signature hoặc introspection | Login lấy token -> gọi API bằng bearer -> token expiry/refresh | Token state ở client + server-side state tùy loại token (opaque/introspection/revocation list) | Hợp API/microservice, dễ federation | Token leakage gây impersonation; revoke không tức thời nếu không introspect/blacklist |
| LDAP Authentication (Direct Bind) | App bind trực tiếp tới LDAP/AD để xác thực | User submit cred -> app LDAP bind/search -> map groups/roles | App session cục bộ sau LDAP auth; identity source ở LDAP directory | Tận dụng directory hiện hữu, quản trị tập trung account | Tight coupling LDAP schema/network; chưa phải SSO web; logout toàn cục hạn chế |

### ASCII flow A: Form-based + server session

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

### ASCII flow B: Header-based pre-auth via gateway

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

## Q2. Logout Mechanisms

| Mechanism | Trigger | Server state thay đổi | SLO support | Nhận xét triển khai |
|---|---|---|---|---|
| Local Clear (App Logout URL) | User click logout (`/logout`) | App invalidate local session + clear cookie | Thấp, chỉ local app nếu không gọi IdP | Dễ làm nhất, nhưng còn phiên ở IdP/gateway nếu không sync |
| `401` Challenge Reset (Basic) | App/proxy chủ động trả 401 | Không luôn luôn clear credential cache của browser; chủ yếu buộc re-auth request tiếp theo | Thấp | Không phải logout thật sự; phụ thuộc user-agent behavior |
| Token Revocation (OAuth2) | Client/proxy gọi revocation endpoint | Token bị mark revoked tại AS; resource server cần introspection/cache policy phù hợp | Trung bình | Hiệu quả với token-based stack; cần thiết kế propagation delay |
| Front-channel Logout (OIDC) | Browser redirect/iframe qua OP -> nhiều RP | Mỗi RP nhận logout notification qua browser context | Trung bình, phụ thuộc browser/cookie policy | Dễ bị ảnh hưởng SameSite/3rd-party cookie restrictions |
| Back-channel Logout (OIDC) | OP gửi logout token server-to-server tới RP endpoint | RP terminate session theo `sid`/`sub` không cần browser | Cao | Tin cậy hơn front-channel; yêu cầu endpoint/back-channel validation tốt |

### ASCII flow C: Front-channel logout

```text
Browser            OP/IdP                RP1            RP2
   | Logout click     |                    |              |
   |----------------->|                    |              |
   |<-- redirects ----|                    |              |
   |----> RP1 logout endpoint (browser) -->|              |
   |----> RP2 logout endpoint (browser) ----------------->|
   |<------------------- final redirect to post-logout ---|
```

### ASCII flow D: Back-channel logout

```text
Browser              OP/IdP                       RP
   | logout request     |                         |
   |------------------->|                         |
   |                    | POST logout_token       |
   |                    |------------------------>|
   |                    |<------- 200/204 --------|
   |<------ logged out confirmation --------------|
```

## Q3. SSO integration strategies for legacy apps

| Strategy | How it works | Prerequisites | Trade-offs |
|---|---|---|---|
| Gateway reverse proxy (central auth) | Gateway handle OIDC/SAML, app nằm sau protected upstream | Khống chế toàn bộ ingress path, TLS termination chuẩn, route ổn định | Triển khai tập trung, ít sửa app; tạo single choke point và cần HA |
| Header injection (trusted pre-auth) | Gateway inject identity headers, app tin tưởng headers đó | App hỗ trợ remote-user/header auth; network trust boundary rõ (allowlist IP/mTLS) | Retrofit nhanh nhất; rủi ro spoofing cao nếu quên strip incoming headers |
| Sidecar auth proxy | Auth proxy chạy cùng pod/VM với app, intercept local traffic | Platform hỗ trợ sidecar/service mesh; app traffic đi qua sidecar | Cô lập tốt theo workload; vận hành phức tạp hơn (resource, observability, policy drift) |
| Credential injection (form/basic bridge) | Gateway map user SSO -> legacy credential rồi submit form/basic thay user | Credential vault + mapping policy, login flow ổn định, endpoint/form fields ít thay đổi | Hữu dụng cho app không hỗ trợ pre-auth; nhưng rủi ro lưu giữ secret và dễ gãy khi UI đổi |

## Q4. Security checklist (severity-rated)

| Check | Severity | Audit question | Expected control |
|---|---|---|---|
| Header spoofing | Critical | App có chặn trực tiếp internet và chỉ nhận header auth từ gateway trusted addresses không? | Strip + overwrite auth headers tại gateway; mTLS/IP allowlist giữa gateway-app |
| Session fixation | High | Sau login/privilege change có rotate session ID không? | Regenerate session ID sau auth, invalidate pre-auth session |
| Logout sync gap | High | Logout tại app có terminate session ở IdP/gateway và ngược lại không? | RP-initiated + back-channel/front-channel coordination, session index mapping |
| Token leakage | Critical | Bearer/basic credentials có xuất hiện ở URL, logs, referer, browser storage không phù hợp không? | TLS bắt buộc, không để token trong URL, log redaction, short TTL + revocation |
| CSRF on login/logout | High | Endpoint login/logout có CSRF protection phù hợp với cookie-based flow không? | CSRF token + SameSite/Origin checks |
| Over-trust forwarded headers | High | Có trust toàn bộ `X-Forwarded-*` từ nguồn không xác thực không? | Chỉ trust proxy đã cấu hình; explicit trusted proxy list |
| Weak cookie flags | Medium | Session cookie có `Secure`, `HttpOnly`, `SameSite` phù hợp không? | Bật đầy đủ flags theo risk profile |
| LDAP channel/security | Medium | LDAP bind có dùng LDAPS/StartTLS, principle of least privilege và timeout/retry control không? | TLS to LDAP, bind account hạn quyền, lockout/rate-limit |

## Gap analysis framework (for field audit)

### Step 1: Inventory (per app)

| Field | What to capture |
|---|---|
| App name/version | Product, version, deployment mode |
| Current authentication mechanism | Form-based/HTTP Basic/Header-based/Token-based/LDAP/hybrid |
| Session artifact | Cookie name, TTL, storage backend, rotation behavior |
| Logout mechanism | Local URL, global logout linkage, timeout policy |
| Proxy/gateway topology | Direct access path, trusted proxies, header policy |
| Identity dependencies | IdP, LDAP/AD, vault, token services |

### Step 2: Control scoring

Use score per control: `0 = missing`, `1 = partial`, `2 = adequate`.

| Control domain | 0 | 1 | 2 |
|---|---|---|---|
| Auth trust boundary | Direct header spoof possible | Partial filtering | Strict strip/overwrite + network trust |
| Session hardening | No rotation/weak cookie flags | Some controls | Rotation + secure flags + timeout |
| Logout coherence | Local only | Partial sync | Bi-directional sync with IdP/gateway |
| Token/credential hygiene | Secret leakage risk | Partially redacted | Redaction + short TTL + revocation |
| Observability/audit | No auth/logout traceability | Incomplete logs | Correlated logs + alert rules |

### Step 3: Audit questions (checklist)

1. Login đang xảy ra ở app, gateway hay cả hai? Evidence: HTTP trace và redirect chain.
2. Sau login thành công, session ID có đổi không? Evidence: cookie before/after auth.
3. User có thể bypass gateway để gọi app trực tiếp không? Evidence: network path + ingress rules.
4. App có tin tưởng `X-Forwarded-*`/`X-Remote-User` từ client không? Evidence: crafted request test.
5. Logout từ app có làm mất session tại IdP/gateway không? Evidence: logout test matrix.
6. Logout từ IdP có invalidate local session app không? Evidence: back-channel/front-channel logs.
7. Token/credential có bị log lộ ở reverse proxy/app logs không? Evidence: log sampling.
8. Có policy revoke/blacklist cho token và propagation SLA không? Evidence: AS config + runtime test.
9. LDAP auth có dùng TLS và giới hạn bind permissions không? Evidence: LDAP config/certs.
10. Có rate limit/lockout cho login endpoint không? Evidence: WAF/gateway policy.

## Recommended evidence pack

1. HAR/network capture cho login + logout.
2. Gateway route/policy export (header strip/inject, authn filters).
3. App auth config snapshot (session, cookie, logout endpoint).
4. IdP client config (logout URIs, front/back-channel flags).
5. Security test results: spoofing, fixation, logout sync, token leakage.

## Sources (primary)

- RFC 9110: HTTP Semantics (401 challenge + `WWW-Authenticate`) - https://www.rfc-editor.org/rfc/rfc9110
- RFC 7617: Basic HTTP Authentication - https://www.rfc-editor.org/rfc/rfc7617
- RFC 6750: OAuth 2.0 Bearer Token Usage - https://www.rfc-editor.org/rfc/rfc6750
- RFC 7009: OAuth 2.0 Token Revocation - https://www.rfc-editor.org/rfc/rfc7009
- RFC 4513: LDAP Authentication Methods and Security Mechanisms - https://www.rfc-editor.org/rfc/rfc4513
- OpenID Connect Front-Channel Logout 1.0 - https://openid.net/specs/openid-connect-frontchannel-1_0-final.html
- OpenID Connect Back-Channel Logout 1.0 - https://openid.net/specs/openid-connect-backchannel-1_0-final.html
- OpenID Connect RP-Initiated Logout 1.0 - https://openid.net/specs/openid-connect-rpinitiated-1_0-final.html
- OWASP Session Management Cheat Sheet (session fixation/cookie controls) - https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html
- OWASP Authentication Cheat Sheet - https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
- Grafana Auth Proxy (`X-WEBAUTH-USER`, whitelist for spoofing prevention) - https://grafana.com/docs/grafana/latest/setup-grafana/configure-security/configure-authentication/auth-proxy/
- oauth2-proxy integration with NGINX auth_request (header propagation pattern) - https://oauth2-proxy.github.io/oauth2-proxy/configuration/integration
- Keycloak reverse proxy trusted headers / trusted addresses - https://www.keycloak.org/server/reverseproxy
- NGINX `auth_request` module - https://nginx.org/en/docs/http/ngx_http_auth_request_module.html
- Istio external authorization (`CUSTOM`/ext_authz) - https://istio.io/latest/docs/tasks/security/authorization/authz-custom/

## Notes on interpretation

Một số trade-off trong bảng là kết luận tổng hợp từ nhiều chuẩn/tài liệu triển khai (inference), không phải câu chữ nguyên văn của từng source.
