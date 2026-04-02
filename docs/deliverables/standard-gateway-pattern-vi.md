---
# Mẫu Gateway OpenIG Chuẩn cho SSO/SLO
**Phiên bản:** 1.4
**Ngày:** 2026-04-02
**Rút ra từ:** Xác thực shared-infra trên WordPress, WhoAmI, Redmine, Jellyfin, Grafana và phpMyAdmin
**Phạm vi:** OpenIG 6 + Keycloak + Vault + Redis

> Cập nhật 2026-03-24: runtime lab đang hoạt động hiện là `shared/`: một nginx, hai node OpenIG, một Redis và một Vault phục vụ cả 6 app trên cổng 80 qua định tuyến theo hostname. Tính cô lập của shared-infra được cưỡng chế bằng các heap `JwtSession` cục bộ theo route (`SessionApp1..6`), cookie chỉ theo host (`IG_SSO_APP1..APP6`), Redis ACL user theo từng app (`openig-app1..6`), prefix key Redis theo từng app (`app1:*..app6:*`), và Vault AppRole theo từng app (`openig-app1..6`).

---

## Tổng quan

Tài liệu này định nghĩa hợp đồng gateway tham chiếu cho SSO và SLO dựa trên OpenIG khi một runtime gateway dùng chung đứng phía trước nhiều ứng dụng downstream không đồng nhất. Gateway chịu trách nhiệm điều phối login, lưu trữ session, revocation, lan truyền logout, truy xuất secret, toàn vẹn redirect và wiring adapter. Các app downstream giữ nguyên cơ chế login native của chúng.

Hình dạng runtime đang hoạt động là:

- `shared-nginx` kết thúc lưu lượng trình duyệt trên cổng 80 và định tuyến theo hostname
- `shared-openig-1` và `shared-openig-2` chạy toàn bộ bộ route
- `shared-redis` lưu trạng thái revocation và token-reference
- `shared-vault` lưu credential downstream và secret của gateway
- Keycloak vẫn là IdP dùng chung tại `http://auth.sso.local:8080`

## Hợp đồng triển khai shared-infra

| Tầng | Baseline shared-infra hiện tại |
|------|-------------------------------|
| Điểm vào của trình duyệt | Định tuyến theo hostname trên cổng 80 |
| Mô hình session | `JwtSession` cục bộ theo route cho từng app |
| Cookie trình duyệt | `IG_SSO_APP1..APP6` |
| Cô lập Redis | `openig-app1..6` với prefix ACL `~appN:*` |
| Cô lập Vault | `openig-app1..6` AppRole với policy giới hạn theo path |
| Redirect base | env var `CANONICAL_ORIGIN_APP1..6` |
| OpenIG image | `openidentityplatform/openig:6.0.1` |

## Trạng thái control bảo mật

| Control | Trạng thái shared-infra | Ghi chú |
|---------|------------------------|---------|
| Cookie `JwtSession` cục bộ theo route | Đã triển khai | `SessionApp1..6` với `IG_SSO_APP1..APP6` |
| Redis ACL theo từng app | Đã triển khai | `openig-app1..6`, bộ lệnh tối thiểu |
| Cô lập Vault AppRole theo từng app | Đã triển khai | `openig-app1..6`, policy giới hạn theo path |
| Loại bỏ cookie session của gateway trước khi proxy | Đã triển khai | `StripGatewaySessionCookies.groovy` trên mọi app route |
| Backchannel logout với JWT validation | Đã triển khai | Hỗ trợ `RS256` và `ES256` |
| TLS giữa các thành phần | Ngoại lệ lab - hoãn sang production | Lab hiện tại vẫn chỉ dùng HTTP |

## Phạm vi cơ chế đăng nhập

| Mẫu | Loại app đại diện | Hành động của gateway |
|------|-------------------|-----------------------|
| Form login injection | WordPress, Redmine | Hoàn tất OIDC, lấy credential app từ Vault, gửi luồng login native |
| Token injection | Jellyfin | Hoàn tất OIDC, lấy trạng thái token downstream, bridge trạng thái đó vào hợp đồng của app |
| Trusted header injection | Grafana, WhoAmI | Hoàn tất OIDC, inject identity header sau khi auth và kiểm tra blacklist |
| HTTP Basic injection | phpMyAdmin | Hoàn tất OIDC, lấy credential từ Vault, inject `Authorization: Basic` |
| LDAP | Future pattern | Cần đánh giá theo từng app; không thuộc baseline 6-app đã được xác thực |

## Các control bắt buộc

### 1. Cô lập session

Mỗi app trong runtime dùng chung MUST có:

- Một `clientEndpoint` duy nhất
- Một heap session cục bộ theo route duy nhất (`SessionAppN`)
- Một tên cookie duy nhất (`IG_SSO_APPN`)
- Một `tokenRefKey` duy nhất
- Một namespace Redis và Redis user duy nhất
- Một Vault AppRole duy nhất khi dùng Vault

Ma trận định tuyến và cô lập shared-runtime đang hoạt động:

| Ứng dụng | Hostname | clientEndpoint | Keycloak client | Session heap | Tên cookie |
|----------|----------|----------------|-----------------|--------------|-------------|
| WordPress | `http://wp-a.sso.local` | `/openid/app1` | `openig-client` | `SessionApp1` | `IG_SSO_APP1` |
| WhoAmI | `http://whoami-a.sso.local` | `/openid/app2` | `openig-client` | `SessionApp2` | `IG_SSO_APP2` |
| Redmine | `http://redmine-b.sso.local` | `/openid/app3` | `openig-client-b` | `SessionApp3` | `IG_SSO_APP3` |
| Jellyfin | `http://jellyfin-b.sso.local` | `/openid/app4` | `openig-client-b-app4` | `SessionApp4` | `IG_SSO_APP4` |
| Grafana | `http://grafana-c.sso.local` | `/openid/app5` | `openig-client-c-app5` | `SessionApp5` | `IG_SSO_APP5` |
| phpMyAdmin | `http://phpmyadmin-c.sso.local` | `/openid/app6` | `openig-client-c-app6` | `SessionApp6` | `IG_SSO_APP6` |

Heap `Session` global dự phòng trong `shared/openig_home/config/config.json` không phải là mô hình session đang hoạt động cho các app route. Các shared-infra route ghi đè rõ ràng lên nó.

### 2. Hợp đồng revocation và token-reference của Redis

Redis chịu trách nhiệm cho:

- Trạng thái revocation kiểu `blacklist:<sid>`
- Trạng thái token-reference để giữ các blob `oauth2:*` nặng nằm ngoài cookie trình duyệt

Quy tắc:

- Blacklist TTL MUST lớn hơn hoặc bằng `JwtSession.sessionTimeout`
- Lỗi đọc Redis trên một request đã xác thực MUST fail closed
- Lỗi ghi Redis trong backchannel logout MUST trả về `5xx`, không phải `4xx`
- OpenIG MUST xác thực bằng `AUTH <username> <password>`
- Redis ACL user MUST vẫn chỉ giới hạn ở `SET`, `GET`, `DEL`, `EXISTS`, `PING`
- Redis key MUST vẫn được giới hạn theo app (`app1:*..app6:*`)

Mapping Redis ACL theo từng app hiện tại:

| Ứng dụng | Redis user | Key prefix | Lệnh được phép |
|----------|------------|------------|-----------------|
| WordPress | `openig-app1` | `~app1:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| WhoAmI | `openig-app2` | `~app2:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| Redmine | `openig-app3` | `~app3:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| Jellyfin | `openig-app4` | `~app4:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| Grafana | `openig-app5` | `~app5:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |
| phpMyAdmin | `openig-app6` | `~app6:*` | `SET`, `GET`, `DEL`, `EXISTS`, `PING` |

Quy tắc của `TokenReferenceFilter.groovy`:

- Đặt nó ngay sau `OAuth2ClientFilter`
- Dùng một `tokenRefKey` duy nhất cho từng app (`token_ref_id_app1..6`)
- Bỏ qua Redis restore trên `<clientEndpoint>/callback`
- Bỏ qua Redis offload khi namespace OAuth2 chưa có dữ liệu token thực sự
- Chỉ xóa các key OAuth2 được phát hiện của app hiện tại khỏi trạng thái session

### 3. Mô hình secret của Vault

Vault chịu trách nhiệm cho:

- Material crypto của gateway và secret runtime
- OIDC client secret khi áp dụng
- Credential app downstream cho các pattern form/basic/token injection

Quy tắc:

- Secret MUST đến từ Vault hoặc environment tại runtime
- Secret MUST NOT được hardcode trong route JSON, Groovy hoặc `.env` đã commit
- Mỗi app có AppRole riêng: `openig-app1..6`
- Mỗi AppRole chỉ được giới hạn vào secret path của chính nó
- File AppRole tách biệt theo từng app: `/vault/file/openig-appN-role-id`, `/vault/file/openig-appN-secret-id`

Mapping Vault AppRole theo từng app hiện tại:

| Ứng dụng | AppRole | Policy | Phạm vi secret path |
|----------|---------|--------|-------------------|
| WordPress | `openig-app1` | `openig-app1-policy` | `secret/data/wp-creds/*` |
| WhoAmI | `openig-app2` | `openig-app2-policy` | `secret/data/dummy/*` |
| Redmine | `openig-app3` | `openig-app3-policy` | `secret/data/redmine-creds/*` |
| Jellyfin | `openig-app4` | `openig-app4-policy` | `secret/data/jellyfin-creds/*` |
| Grafana | `openig-app5` | `openig-app5-policy` | `secret/data/grafana-creds/*` |
| phpMyAdmin | `openig-app6` | `openig-app6-policy` | `secret/data/phpmyadmin/*` |

Ghi chú vận hành:

- `secret_id_ttl` là `72h` trong lab, vì vậy việc tạo lại là một bước vận hành bình thường sau thời gian dừng dài

### 4. Toàn vẹn redirect và logout

Quy tắc:

- Redirect và logout target MUST dùng `CANONICAL_ORIGIN_APP1..6`
- Không suy ra redirect base từ `Host` inbound
- RP-initiated logout MUST đọc đúng namespace OIDC cho route đó
- Backchannel logout MUST validate `alg`, `kid`, signature, `iss`, `aud`, `events`, `iat` và `exp`

### 5. Thứ tự inject header và credential

Trusted header và credential downstream MUST chỉ được inject sau khi:

1. Xác thực OIDC thành công
2. Kiểm tra revocation thành công
3. Cookie session của gateway đã được loại bỏ khỏi header `Cookie` gửi lên upstream

Điều này ngăn identity giả mạo hoặc đã bị thu hồi đi tới backend.

### 6. Vệ sinh secret và image

Quy tắc:

- Giữ secret runtime trong `.env` được gitignore hoặc runtime injection dựa trên Vault
- Commit `.env.example`, không bao giờ commit `.env`
- OpenIG `OAuth2ClientFilter` client secret phải là giá trị mạnh chỉ gồm ký tự chữ và số
- Không dùng `openidentityplatform/openig:latest`
- Pin `openidentityplatform/openig:6.0.1`

## Luồng SLO

### RP-initiated logout

1. Trình duyệt gọi logout handler dành riêng cho route
2. `SloHandler` đọc `id_token` của route đó
3. `SloHandler` dựng `end_session` của Keycloak với `id_token_hint` và `post_logout_redirect_uri` đã pin
4. Session cục bộ của route bị invalidate
5. Trình duyệt được redirect tới logout của Keycloak
6. Keycloak gửi backchannel logout tới các endpoint gateway đã đăng ký

### Backchannel logout

1. Keycloak gửi `logout_token` đã ký
2. `BackchannelLogoutHandler.groovy` validate JWT và các claim
3. Handler ghi trạng thái blacklist vào namespace Redis của app đó
4. Request đã xác thực kế tiếp đi qua `SessionBlacklistFilter.groovy`
5. Session bị blacklist fail closed và yêu cầu xác thực lại

## Mẫu sai cần tránh

| Mẫu sai | Rủi ro | Cách làm đúng |
|---------|--------|----------------|
| Hardcoded secret trong route hoặc script | Lộ repo sẽ thành lộ credential | Externalize sang Vault hoặc runtime env |
| Dùng chung Redis password cho mọi app | Truy cập revocation và token-reference xuyên app | Dùng ACL user theo từng app |
| Dùng chung Vault AppRole cho mọi app | Một route có thể đọc secret của route khác | Dùng AppRole theo từng app |
| Một cookie trình duyệt dùng chung cho mọi app trong shared runtime hiện tại | Blast radius xuyên app và khó debug | Dùng cookie cục bộ theo route `IG_SSO_APP1..APP6` |
| Redirect base suy ra từ host | Open redirect hoặc sai logout origin | Dùng canonical origin đã pin |
| Chuyển tiếp cookie của gateway xuống downstream | Backend nhận trạng thái chỉ dành cho gateway | Loại bỏ `IG_SSO_APP*` trước khi proxy |
| Restore token reference trên callback | Trạng thái OAuth2 pending bị ghi đè | Bỏ qua restore trên callback |
| Xóa mọi namespace `oauth2:*` | Một app có thể phá login pending của app khác | Chỉ xóa các key được phát hiện của app hiện tại |
| Dùng OpenIG image `latest` | Runtime drift và lỗi khi khởi động | Pin `6.0.1` |

## Checklist tích hợp mới

### Session và revocation

- [ ] Route có `clientEndpoint` duy nhất
- [ ] Route có `SessionAppN` và `IG_SSO_APPN` duy nhất
- [ ] Route có `tokenRefKey` duy nhất
- [ ] Route dùng Redis user và prefix giới hạn theo app
- [ ] `TokenReferenceFilter.groovy` được gắn ngay sau `OAuth2ClientFilter`
- [ ] `SessionBlacklistFilter.groovy` kiểm tra cùng namespace `sid` mà backchannel logout ghi vào

### Vault và credential

- [ ] AppRole là duy nhất cho app đó
- [ ] Policy chỉ giới hạn vào secret path của app đó
- [ ] Không credential downstream hoặc Vault token nào được serialize vào `JwtSession`
- [ ] Tài liệu hóa chủ sở hữu của việc xoay vòng credential

### Logout

- [ ] RP-initiated logout đọc đúng namespace OIDC
- [ ] URL backchannel logout đã được đăng ký
- [ ] Backchannel logout validate JWT trước khi ghi trạng thái Redis
- [ ] Post-logout redirect target đã được pin

### Ranh giới proxy

- [ ] Cookie session do gateway sở hữu được loại bỏ trước khi proxy
- [ ] Trusted identity header bị loại bỏ khỏi input của client và chỉ được gateway inject
- [ ] Adapter-specific filters được khai báo rõ ràng trong route chain

### Transport

> Ngoại lệ lab: shared infra hiện tại vẫn chỉ dùng HTTP. Lab này xác thực integration pattern, không phải việc hardening transport cho production.

- [ ] Triển khai production dùng TLS cho trình duyệt, Vault, Redis và lưu lượng control-plane nội bộ
- [ ] `requireHttps: true` được bật trong production
- [ ] Cookie `JwtSession` là `Secure` trong production
- [ ] Có network segmentation giữa các đường app, trình duyệt và admin/control-plane

## Kiến trúc template được tham số hóa

Shared runtime dùng một bản sao của mỗi gateway Groovy template và cấu hình hành vi theo từng route bằng JSON `args`.

Các template đã được xác thực:

| Template | Mục đích |
|----------|----------|
| `TokenReferenceFilter.groovy` | Offload `oauth2:*` sang Redis và giữ cookie nhỏ |
| `SessionBlacklistFilter.groovy` | Cưỡng chế SLO revocation trên mọi request |
| `BackchannelLogoutHandler.groovy` | Validate logout JWT và ghi trạng thái Redis blacklist |
| `SloHandler.groovy` | Xử lý RP-initiated logout cho các route chuẩn |
| `SloHandlerJellyfin.groovy` | Helper logout riêng cho Jellyfin |
| `VaultCredentialFilter.groovy` | Lấy credential downstream từ Vault bằng route args |

Quy tắc bind `args` cho OpenIG 6.0.1:

- Key `args` của route trở thành biến bind Groovy ở mức top-level
- Dùng `binding.hasVariable('name')`
- Không dựa vào `args.name` hoặc `(args as Map).name`

## Các điều chỉnh triển khai (2026-03-31)

Baseline shared-runtime hiện tại bao gồm các điều chỉnh triển khai sau đã được xác thực sau snapshot tài liệu v1.3 ban đầu:

- `BUG-002`: nginx đã tắt `proxy_next_upstream` trên cả sáu callback path (`/openid/app1/callback` đến `/openid/app6/callback`) để ngăn duplicate OIDC code exchange trong lúc retry upstream.
- `AUD-003`: `BackchannelLogoutHandler.groovy` hiện giữ JWKS cache null-safe và áp dụng failure backoff `60s` sau khi JWKS fetch thất bại để tránh hammering Keycloak.
- `DOC-007`: Hành vi fail-closed của `TokenReferenceFilter.groovy` chỉ áp dụng trên callback path, không áp dụng trên mọi request đã xác thực, để tránh phản hồi `500` sai trên lưu lượng hợp lệ.
- `AUD-009`: `SloHandler.groovy` và `SloHandlerJellyfin.groovy` không còn dùng legacy hostname fallback và hiện fail closed với `500` nếu thiếu `OPENIG_PUBLIC_URL` hoặc `CANONICAL_ORIGIN_APP4`.

## Triển khai production trên Kubernetes

Đối với triển khai production trên Kubernetes, sử dụng phương thức Kubernetes authentication của Vault thay vì AppRole. Điều này phù hợp với best practices của HashiCorp về trusted third-party authentication.

> **Ghi chú production:** Mẫu AppRole của lab hiện tại không production-ready cho các Kubernetes workload chạy dài hạn. Khi `secret_id` 72h hết hạn, `VaultCredentialFilter.groovy` không còn có thể lấy một Vault token mới sau khi cached Vault token của nó hết hạn, làm hỏng tất cả các Vault-backed downstream login flows cho đến khi operator xoay vòng AppRole material.

**Tài liệu tham khảo:**
- HashiCorp Vault Docs: [Kubernetes Auth Method](https://developer.hashicorp.com/vault/docs/auth/kubernetes)
- HashiCorp Vault API: [Kubernetes Auth API](https://developer.hashicorp.com/vault/api-docs/auth/kubernetes)
- HashiCorp Tutorial: [AppRole Best Practices](https://developer.hashicorp.com/vault/tutorials/auth-methods/approle-best-practices)

### So sánh phương thức authentication

| Aspect | Lab (Docker Compose) | Production (Kubernetes) |
|--------|---------------------|-------------------------|
| Phương thức auth | AppRole (`role_id` + `secret_id`) | Kubernetes auth (ServiceAccount JWT) [^1] |
| Phân phối credential | Thủ công (file mount từ `/vault/file/`) | Tự động (K8s tự mount SA token) [^1] |
| Rotation credential | Thủ công (admin regenerate `secret_id` mỗi 72h) | Thời hạn ServiceAccount token được cấu hình theo cluster/pod (không có mặc định cố định 1h). Không tài liệu hóa giá trị này như một universal value. OpenIG phải renew hoặc chạy lại auth/kubernetes/login khi Vault token hết hạn. |
| Vault config | `POST /auth/approle/login` | `POST /auth/kubernetes/login` [^2] |
| Thay đổi code OpenIG | Không | Cập nhật `VaultCredentialFilter.groovy` dùng endpoint K8s auth |

[^1]: HashiCorp Vault Docs: [Kubernetes Auth Method](https://developer.hashicorp.com/vault/docs/auth/kubernetes)
[^2]: HashiCorp Vault API: [Kubernetes Auth API](https://developer.hashicorp.com/vault/api-docs/auth/kubernetes)
[^3]: HashiCorp Tutorial: [AppRole Best Practices](https://developer.hashicorp.com/vault/tutorials/auth-methods/approle-best-practices)

> **Khuyến nghị HashiCorp:** *"If another platform method of authentication is available via a trusted third-party authenticator, the best practice is to use that instead of AppRole."* [^3]

### Cấu hình Vault cho Kubernetes

Commands cấu hình từ HashiCorp official documentation [^1][^2]:

```bash
# Enable Kubernetes auth method
vault auth enable kubernetes

# Cấu hình Vault giao tiếp với Kubernetes API
# Khi Vault chạy trong K8s, nó tự động discovery các giá trị này
vault write auth/kubernetes/config \
    kubernetes_host=https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT

# Tạo per-app roles (thay thế AppRole pattern)
vault write auth/kubernetes/role/openig-app1 \
    bound_service_account_names=openig-sa \
    bound_service_account_namespaces=sso \
    policies=openig-app1-policy \
    ttl=1h
```

### Yêu cầu Kubernetes

Yêu cầu từ HashiCorp official documentation [^1]:

| Component | Cấu hình |
|-----------|----------|
| ServiceAccount | Tạo `openig-sa` trong namespace `sso` cho OpenIG pods |
| ClusterRoleBinding | Grant `system:auth-delegator` cho ServiceAccount của Vault để gọi TokenReview API |
| Network | Vault phải reach được Kubernetes API server (`$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT`) |
| OpenIG pod spec | Mount ServiceAccount token (tự động mount tại `/var/run/secrets/kubernetes.io/serviceaccount/token`) |

### Thay đổi code OpenIG

**Ghi chú triển khai:** Code sau là đề xuất dựa trên Vault Kubernetes Auth API specification [^2]. Code này **CHƯA được test** trong lab này (vì lab dùng Docker Compose). Test trong Kubernetes staging environment trước khi deploy production.

Cập nhật `VaultCredentialFilter.groovy` để dùng Kubernetes auth endpoint:

```groovy
// Hiện tại (AppRole) - Lab implementation:
String payload = JsonOutput.toJson([role_id: roleId, secret_id: secretId])
connection = new URL("${vaultAddr}/v1/auth/approle/login").openConnection()

// Production (Kubernetes) - Proposed implementation:
String saToken = new File('/var/run/secrets/kubernetes.io/serviceaccount/token').text.trim()
String payload = JsonOutput.toJson([jwt: saToken, role: 'openig-app1'])
connection = new URL("${vaultAddr}/v1/auth/kubernetes/login").openConnection()
```

### Mẫu production được khuyến nghị

Ưu tiên Vault Agent Sidecar hoặc Vault Agent Injector với Kubernetes auth cho các OpenIG pod chạy dài hạn. Agent thực hiện Kubernetes auth, renew hoặc re-authenticate Vault token, và refresh rendered secrets mà không cần file AppRole `secret_id`. OpenIG đọc rendered files hoặc một local agent endpoint và không tự sở hữu vòng đời Vault token.

Nếu OpenIG tự xác thực trực tiếp với Vault (không dùng Vault Agent), nó MUST:
- dùng projected ServiceAccount token với `audience: vault` nếu Vault role cưỡng chế audience validation
- đọc ServiceAccount JWT từ disk ở mỗi lần gọi `auth/kubernetes/login`; không cache Kubernetes JWT
- renew Vault token hoặc chạy lại `auth/kubernetes/login` khi cached Vault token hết hạn

### Best practices bảo mật cho production

Giá trị từ HashiCorp official documentation examples [^1][^2]:

| Setting | Giá trị khuyến nghị | Mục đích |
|---------|--------------------|----------|
| Token TTL | `1h` | Token short-lived giảm blast radius (từ docs example) |
| Bound service accounts | Bắt buộc | Prevent token reuse xuyên workloads [^1] |
| Bound namespaces | Bắt buộc | Namespace isolation cho multi-tenant clusters [^1] |
| Audience claim | `vault` | Prevent JWT reuse cho mục đích khác [^1] |
| CIDR binding | Tùy chọn | Giới hạn source IP authentication [^1] |

### Checklist migration

**Lưu ý:** Checklist này được derived từ HashiCorp documentation [^1][^2][^3] và **chưa được validate** trong lab này.

- [ ] Deploy Vault trong Kubernetes cluster (khuyến nghị cho local SA token rotation)
- [ ] Enable Kubernetes auth method và cấu hình kết nối K8s API
- [ ] Tạo ServiceAccount `openig-sa` trong namespace mục tiêu
- [ ] Tạo ClusterRoleBinding cho Vault để access TokenReview API
- [ ] Tạo per-app Kubernetes roles (một role per app, thay thế AppRole)
- [ ] Cập nhật `VaultCredentialFilter.groovy` của OpenIG để dùng `/auth/kubernetes/login`
- [ ] Test authentication flow với app non-production trước
- [ ] Migrate apps từng cái một, giữ AppRole làm fallback trong quá trình transition
