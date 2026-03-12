# Legacy Application Authentication Mechanisms Reference

Tài liệu này tổng hợp các authentication mechanisms phổ biến trong các ứng dụng legacy, phục vụ cho việc thiết kế các giải pháp SSO Wrapper hoặc Identity Gateway (như OpenIG).

---

## 1. Authentication Mechanisms

### 1.1 Form-based Authentication
Đây là phương thức phổ biến nhất. Người dùng gửi tên đăng nhập và mật khẩu qua một biểu mẫu HTML (HTTP POST).

*   **Cách thức hoạt động:**
    1. Người dùng truy cập trang bảo mật.
    2. Server trả về mã 302 redirect hoặc trang 200 kèm biểu mẫu đăng nhập.
    3. Người dùng gửi thông tin (POST).
    4. Server xác thực và trả về session cookie (ví dụ: `JSESSIONID`, `PHPSESSID`).

*   **Sơ đồ luồng (ASCII Flow):**
    ```text
    User (Browser)          Identity Gateway          Legacy App
          |                        |                      |
          |---(1) GET /index ----->|                      |
          |                        |---(2) GET /index --->|
          |                        |                      |
          |                        |<--(3) 302 (Login) ---|
          |<--(4) 302 (Login) -----|                      |
          |                        |                      |
          |---(5) POST /login ---->|                      |
          |   (user/pass)          |                      |
          |                        |---(6) POST /login -->|
          |                        |   (user/pass)        |
          |                        |                      |
          |                        |<--(7) 302 (Success)--|
          |                        |   Set-Cookie: ID=123 |
          |<--(8) 302 (Success) ---|                      |
          |   Set-Cookie: ID=123   |                      |
    ```

*   **Ví dụ curl:**
    ```bash
    curl -v -X POST http://legacy.local/login \
         -d "username=admin&password=password123" \
         -c cookies.txt
    ```

*   **Security considerations:** Dễ bị tấn công Brute-force, CSRF. Cần sử dụng HTTPS.

### 1.2 HTTP Basic Authentication (RFC 7617)
Cơ chế thách thức-phản hồi đơn giản gửi thông tin trong header `Authorization` dưới dạng mã hóa Base64 của `user:pass`.

*   **Sơ đồ luồng (ASCII Flow):**
    ```text
    User (Browser)          Identity Gateway          Legacy App
          |                        |                      |
          |---(1) GET /resource -->|                      |
          |                        |---(2) GET /resource->|
          |                        |                      |
          |                        |<--(3) 401 Unauth ----|
          |                        |   WWW-Authenticate   |
          |<--(4) 401 Unauth ------|                      |
          |   WWW-Authenticate     |                      |
          |                        |                      |
          |---(5) GET /resource -->|                      |
          |   Auth: Basic <B64>    |                      |
          |                        |---(6) GET /resource->|
          |   Auth: Basic <B64>    |                      |
    ```

*   **Request Example:**
    ```bash
    curl -v -u admin:password123 http://legacy.local/resource
    # Header: Authorization: Basic YWRtaW46cGFzc3dvcmQxMjM=
    ```

### 1.3 Header-based Authentication (Pre-Auth)
Ứng dụng tin tưởng hoàn toàn vào các HTTP header được chèn bởi một proxy an toàn. Ứng dụng không tự thực hiện xác thực.

*   **Headers phổ biến:** `X-Remote-User`, `X-Forwarded-User`, `X-User-Roles`.
*   **Sử dụng thực tế:** Grafana (`GF_AUTH_PROXY_ENABLED`), phpMyAdmin (`config.user.inc.php`).

*   **Sơ đồ luồng (ASCII Flow):**
    ```text
    User (Browser)          Identity Gateway          Legacy App
          |                        |                      |
          |---(1) Auth with SSO -->|                      |
          |                        |                      |
          |                        |---(2) GET /app ----->|
          |                        |   X-Remote-User: bob |
          |                        |   X-User-Roles: admin|
    ```

---

## 2. Logout Mechanisms

### 2.1 Local Logout
Ứng dụng cung cấp URL đăng xuất (ví dụ: `/logout`) để xóa session cục bộ và cookie.

*   **Flow:**
    ```text
    User (Browser)          Gateway (OpenIG)          Legacy App
          |                        |                      |
          |---(1) GET /logout ---->|                      |
          |                        |---(2) GET /logout -->|
          |                        |   (App clears ses)   |
          |                        |<--(3) 200 OK --------|
          |<--(4) Redirect to IDP--|                      |
          |   (Clear Global Ses)   |                      |
    ```

### 2.2 Single Logout (SLO) Patterns
*   **Front-Channel:** Logout qua trình duyệt (Redirect/Iframe) đến nhiều ứng dụng cùng lúc.
*   **Back-Channel:** Gateway gọi trực tiếp API logout của App (Server-to-Server, không qua browser).

---

## 3. Gateway Integration Patterns

### 3.1 Credential Injection
Gateway tự động điền thông tin xác thực cho backend:
- **Form Injection:** Chèn dữ liệu vào thân yêu cầu POST.
- **Basic Auth Injection:** Thêm header `Authorization: Basic ...`.

### 3.2 Session Translation
Chuyển đổi phiên làm việc hiện đại (OIDC ID Token hoặc JWT Session) thành Cookie cục bộ của ứng dụng cũ.

### 3.3 Credential Mapping
- **Static Mapping:** Người dùng SSO `alice` → User App `admin`.
- **Dynamic Retrieval:** Gateway lấy mật khẩu từ HashiCorp Vault dựa trên định danh SSO.

---

## 4. Security Considerations

| Nguy cơ | Biện pháp giảm thiểu |
| :--- | :--- |
| **Header Spoofing** | App chỉ chấp nhận traffic từ IP Gateway. Gateway xóa sạch các header xác thực cũ từ request của người dùng. |
| **Session Fixation** | Gateway phải cấp Session ID mới sau khi đăng nhập thành công. |
| **Replay Attacks** | Sử dụng nonces, timestamps và token có thời hạn ngắn. |
| **CSRF** | Triển khai thuộc tính cookie SameSite và bộ lọc Anti-CSRF tại Gateway. |

---

## 5. Tham chiếu
- RFC 7617: HTTP Basic Authentication - https://tools.ietf.org/html/rfc7617
- OWASP Authentication Cheat Sheet - https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
- OpenIG Documentation - https://backstage.forgerock.com/docs/ig/7.1/guide/index.html

---

## Appendix: Research Comparison (2026-03-12)

### Codex Research Results (with web search)
**Auth Mechanisms Discovered:**
| Mechanism | How to discover | Logout/401 pattern | SSO retrofit fit |
|-----------|-----------------|-------------------|------------------|
| Form + session cookie | `/login`, `Set-Cookie`, CSRF | Logout GET/POST with CSRF | Gateway or native OIDC/SAML |
| HTTP Basic | `WWW-Authenticate: Basic` | 401 directly | Gateway/sidecar |
| Bearer/API token | `Authorization: Bearer` | 401/403 on expiry | Native OIDC/JWT |
| Trusted header auth | `X-Forwarded-User` | Proxy+IdP concern | Best for legacy |
| SAML/OIDC app-native | IdP metadata/config | App logout may not terminate IdP | Preferred when available |

**Application Matrix (15 apps with sources):**
- WordPress: Cookie/session, logout via `wp-login.php?action=logout` (nonce)
- Jenkins: Form + API token, header SSO via reverse-proxy plugin
- GitLab: OmniAuth (OIDC/SAML), `/users/sign_out`, auto-signin gap
- Grafana: Auth proxy + OIDC/SAML, SAML SLO supported
- phpMyAdmin: `auth_type: cookie/http/signon`, signon mode for SSO
- Confluence: Seraph authenticators, secure admin session (10min timeout)
- Jira: Native auth + SAML fallback
- Mattermost: OIDC/SAML/LDAP, `POST /api/v4/users/logout`
- Nextcloud: LDAP + OIDC app, `session_lifetime`, `auto_logout`
- Jellyfin: Local users + LDAP plugin, token-based API
- Vault: Token auth with TTL, `POST /auth/token/revoke-self`
- SonarQube: HTTP header auth mode for reverse-proxy SSO
- Nexus: RUT (Remote User Token) for proxy integration
- Kibana: Multiple providers, `idleTimeout`/`lifespan` controls
- Prometheus: Basic auth only, no rich logout semantics

**Sources:** 38 official docs (wordpress.org, jenkins.io, docs.gitlab.com, grafana.com, etc.)

---

### Gemini Research Results
**Auth Patterns Taxonomy:**
| Pattern | Credential Exchange | Session Storage | Common Apps |
|---------|--------------------|-----------------|-------------|
| Basic Auth | `Authorization: Basic <base64>` | Stateless | Prometheus, Jenkins API |
| Form-Based | POST user/pass | Server-side (DB/Files) | WordPress, Drupal, Joomla |
| Header-Based | `REMOTE_USER` trusted header | Proxy-side | SonarQube, Nexus RUT |
| LDAP/RADIUS | Direct bind | Directory Server | Zabbix, GitLab CE, Vault |

**Logout Patterns:**
| Pattern | Trigger | Server State | Challenge |
|---------|---------|--------------|-----------|
| Local Clear | Click logout | Remains until TTL | Orphaned sessions |
| Front-Channel | Redirect/iframe | Invalidated via request | SameSite restrictions |
| Back-Channel | POST from IdP | Invalidated via API/DB | App must expose API |
| TTL | Inactivity | Expired by GC | No immediate termination |
| Blacklist | Proxy-to-Redis | Blacklisted at Gateway | Requires shared state |

**Integration Strategies:**
| Strategy | Prerequisites | Trade-offs |
|----------|---------------|------------|
| Gateway/Proxy | App supports `REMOTE_USER` | Simple; header spoofing risk |
| Sidecar | Container environment | High isolation; resource intensive |
| Plugins/Modules | Extensible architecture | Better UX; high maintenance |
| Database Sync | DB access + hashing | Direct control; complex |
| Form Injection | Stable DOM/endpoints | Brittle; needs credential storage |

**Security Checklist:**
- [ ] Header spoofing: Only trust headers from gateway IP
- [ ] Session fixation: Regenerate session ID after SSO
- [ ] Logout sync: Front-channel or back-channel Redis blacklist
- [ ] Token leakage: Strip auth headers before internet
- [ ] Cookie security: HttpOnly, Secure, SameSite

**References:** Okta blog, OWASP, Microsoft AAD docs, Grafana/GitLab/Jenkins official docs
