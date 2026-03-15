---
# Mẫu Chuẩn Gateway OpenIG cho SSO/SLO
**Version:** 1.0
**Date:** 2026-03-14
**Derived from:** Đánh giá mã nguồn và bảo mật của 3 stack tích hợp (WordPress, Redmine+Jellyfin, Grafana+phpMyAdmin)
**Scope:** OpenIG 6 + Keycloak + Vault + Redis

---

## Tổng quan (Overview)

Mẫu này định nghĩa hợp đồng gateway tham chiếu cho SSO và SLO dựa trên OpenIG khi OpenIG nằm giữa trình duyệt và các ứng dụng downstream không đồng nhất. Tài liệu chuẩn hóa cách xử lý secret, lưu trữ session, thu hồi phiên (revocation), thứ tự logout, bảo mật truyền tải, toàn vẹn redirect và wiring adapter để gateway vẫn đúng ngay cả khi ứng dụng dùng cơ chế đăng nhập khác nhau. Derived from: Cross-Stack Summary Universal Findings; Stack A `§5 F1-F5`; Stack B `F1-F11`; Stack C `§4 F1-F9`.

Ba stack được đánh giá bao phủ năm cơ chế đăng nhập mà một mẫu gateway tái sử dụng phải hỗ trợ: OIDC chuẩn không inject credential xuống downstream (Stack A), inject credential downstream và inject token vào ứng dụng API-first (Stack B), và trusted-header cộng với HTTP Basic Auth injection (Stack C). Các lỗi lặp lại giữa các stack không phải lỗi riêng ứng dụng; đó là lỗi hợp đồng gateway quanh secret, revocation, transport và xử lý origin. Derived from: Cross-Stack Summary "Login Mechanism Pattern Risk Matrix"; Stack A `§3`; Stack B "Summary" and `F5-F8`; Stack C `§5`.

Tài liệu này mô tả mẫu đúng (correct pattern), không phải trạng thái hiện tại của các lab stack. Nơi các stack đã đánh giá thể hiện đúng hình dạng cơ chế thì giữ lại; nơi có hành vi không an toàn thì mẫu này thay bằng control có tính bắt buộc. Derived from: Cross-Stack Summary "Recommended Standard Pattern"; Stack A `§4` and `§6`; Stack B "Confirmed Strengths"; Stack C `§3` and `§6`.

## Phạm vi Cơ chế Đăng nhập (Login Mechanism Coverage)

Derived from: Cross-Stack Summary "Login Mechanism Pattern Risk Matrix"; Stack B "Summary"; Stack C `§5`.

| Pattern | Representative App Type | Session Entry Point | Session Exit Point |
|---|---|---|---|
| OIDC Standard | Ứng dụng form-based hoặc redirect-based, không có downstream credential injection (WordPress, WhoAmI) | Trình duyệt được `OAuth2ClientFilter` redirect tới Keycloak; ứng dụng downstream dựa vào session OpenIG đã được thiết lập | Handler logout khởi tạo từ RP (RP-initiated) cộng với cưỡng chế backchannel logout ở các request kế tiếp |
| Credential Injection | Ứng dụng server-rendered mà đăng nhập native yêu cầu username/password (Redmine) | OpenIG hoàn tất OIDC, lấy downstream credential, và đăng nhập vào app thay mặt người dùng | RP-initiated logout cộng với dọn dẹp downstream session theo adapter-specific và cưỡng chế revocation |
| Token Injection + browser storage | Ứng dụng API-first kỳ vọng trạng thái bearer token ở phía trình duyệt (Jellyfin) | OpenIG hoàn tất OIDC, lấy token material downstream, và adapter bridge sang session ứng dụng phía trình duyệt | RP-initiated logout cộng với backchannel logout; trạng thái token trên trình duyệt phải được xóa theo hợp đồng adapter |
| Trusted Header Injection | Ứng dụng tin cậy identity header do gateway cung cấp (Grafana) | OpenIG hoàn tất OIDC và inject identity vào trusted header trên các request proxy | Logout tại gateway và revocation; định danh downstream kết thúc khi trusted-header injection dừng |
| HTTP Basic Auth Injection | Ứng dụng xác thực bằng header `Authorization: Basic` (phpMyAdmin) | OpenIG hoàn tất OIDC, lấy downstream credential, và inject `Authorization: Basic` trên các request proxy | Logout tại gateway và revocation cộng với đồng bộ cookie downstream khi app cũng phát hành cookie riêng |

## Kiến trúc Mẫu (Pattern Architecture)

Derived from: Cross-Stack Summary "Recommended Standard Pattern"; Stack B `F4`, `F7`, `F9-F10`; Stack C `§3`, `§4 F4`, `§4 F6-F9`; Stack A `§4`, `§5 F2-F5`, `§6`.

Triển khai chuẩn đặt nginx phía trước OpenIG và xem OpenIG là enforcement point cho session, revocation, logout và logic adapter. Keycloak giữ vai trò nhà cung cấp OIDC dùng chung và nguồn phát backchannel logout. Vault là nguồn runtime cho secret của gateway và downstream. Redis là kho revocation, và phải có giới hạn rõ ràng để không âm thầm làm sai logout hoặc làm gateway bị treo. Các stack đã đánh giá cho thấy các mối quan tâm này phải được thiết kế như một hợp đồng thống nhất, không phải các script rời rạc.

Text diagram:

```text
Browser
  |
  v
nginx
  - TLS termination
  - sticky routing cho triển khai HA (suy luận từ phạm vi HA 2 node của Stack B)
  - loại bỏ hoặc chuẩn hóa Host inbound và trusted identity headers
  |
  v
OpenIG
  - SessionBlacklistFilter
  - adapter filters theo từng app
  - proxy handler
  |
  v
Downstream App

Keycloak <---- OIDC / end_session / backchannel logout ---- OpenIG
Vault    <---- runtime secret retrieval ------------------- OpenIG
Redis    <---- blacklist read/write ----------------------- OpenIG
```

Thành phần chính và vai trò:

- `nginx`: kết thúc TLS, chuẩn hóa định tuyến trước OpenIG, và loại bỏ trusted identity/header inbound mà downstream app chỉ được chấp nhận từ gateway. Ghi chú sticky-routing cho HA là suy luận từ topology HA 2 node đã đánh giá ở Stack B, không phải finding trực tiếp. Derived from: Stack B "Scope" and "Summary"; Stack B `F4`, `F7`; Stack C `§4 F4`, `§4 F9`.
- Chuỗi filter `OpenIG`: cưỡng chế revocation trước, sau đó chạy adapter-specific filters cần cho cơ chế đăng nhập đã chọn trước khi proxy. App cleanup và logout helper chỉ là một phần của route contract khi chúng thực sự được wiring vào chain. Derived from: Stack A `§5 F2-F5`, `§6`; Stack B `F5`; Stack C `§4 F6`.
- `Vault`: cung cấp secret runtime cho gateway crypto, OIDC client và downstream credential thay vì literal nằm trong repo. Derived from: Stack A `§5 F1`; Stack B `F1`; Stack C `§4 F1` and `§3`.
- `Redis`: lưu trạng thái revocation với TTL ít nhất bằng gateway session lifetime và hành vi socket có giới hạn. Derived from: Stack A `§5 F2-F3`, `§6`; Stack B `F2-F3`, `F9-F10`, `F11`; Stack C `§4 F2-F3`, `§4 F7-F8`.
- `Keycloak`: đóng vai trò IdP dùng chung, OIDC issuer, và bộ phát backchannel logout. Các điểm mạnh đã được xác nhận cho thấy OpenIG phải validate đầy đủ logout token trước khi ghi trạng thái revocation. Derived from: Stack A `§4`; Stack B "Confirmed Strengths"; Stack C `§3`.

## Controls Bắt buộc (Required Controls - MUST)

### 1. Externalization Secret (Secret Externalization)
[Derived from: A F1, B F1, C F1]

Nội dung: Tất cả `JwtSession.sharedSecret`, OIDC `clientSecret`, và mật khẩu keystore BẮT BUỘC lấy từ Vault hoặc environment tại runtime. KHÔNG ĐƯỢC xuất hiện trong `config.json`, route JSON, hoặc mã Groovy.

Lý do: Cả ba stack đều lộ secret gateway hoặc OIDC trong config quản lý bởi repo, biến lỗi cục bộ thành lỗi gateway có thể tái sử dụng và mở rộng blast radius của việc mất cookie hoặc lộ config. Derived from: Stack A `§5 F1`; Stack B `F1`; Stack C `§4 F1`; Cross-Stack Summary Universal Findings.

Cách triển khai trong OpenIG: Dùng nguồn secret runtime kiểu `VaultCredentialFilter` và inject giá trị thu được vào cấu hình route/filter mà không serialize chúng vào `JwtSession`. Mẫu triển khai suy luận từ adapter dùng Vault đã đánh giá là: lấy secret lúc startup, cache có TTL, và refresh trước khi hết hạn thay vì lưu secret đã lấy vào session gắn với trình duyệt. Derived from: Stack A `§4`; Stack C `§3`; Stack C `§4 F5`.

### 2. Hợp đồng Revocation (Revocation Contract)
[Derived from: A F2/F3, B F2/F3, C F2/F3, B F11]

Nội dung: TTL blacklist trong Redis BẮT BUỘC lớn hơn hoặc bằng `JwtSession.sessionTimeout`. Khi Redis lookup lỗi, gateway BẮT BUỘC fail closed cho session đã xác thực bằng cách trả `503` hoặc buộc re-authentication; KHÔNG ĐƯỢC proxy tiếp request. Khi Redis write lỗi trong backchannel logout, handler BẮT BUỘC trả `5xx`, không phải `4xx`. Cùng một key `sid` BẮT BUỘC dùng ở cả đường ghi và đường đọc.

Lý do: Các stack đã đánh giá lặp lại hai failure mode giống nhau: trạng thái revocation hết hạn trước session trình duyệt, và kiểm tra revocation vẫn tiếp tục khi Redis lỗi. Stack B còn cho thấy lệch `sid`/`sub` có thể làm hỏng enforcement dù cả hai đường đều tồn tại. Derived from: Stack A `§5 F2-F3`; Stack B `F2-F3`, `F11`; Stack C `§4 F2-F3`; Cross-Stack Summary Universal Findings.

Cách triển khai trong OpenIG: `BackchannelLogoutHandler` phải validate logout token trước khi ghi `blacklist:<sid>` vào Redis với TTL khớp session lifetime, và `SessionBlacklistFilter` phải đọc đúng key `sid` đó trên mọi request đã xác thực. Bộ validate logout token BẮT BUỘC kiểm tra `alg=RS256`, resolve signing key từ JWKS theo `kid`, và validate `iss`, `aud`, `events`, `iat`, `exp` trước khi ghi trạng thái revocation. Derived from: Stack A `§4`; Stack B "Confirmed Strengths"; Stack C `§3`.

### 3. Bảo mật Truyền tải (Transport Security)
[Derived from: B F4, C F4, A §6]

Nội dung: Toàn bộ OIDC flows, Vault API calls, và downstream app proxying BẮT BUỘC dùng HTTPS trong production. Cookie `JwtSession` BẮT BUỘC có cờ `Secure`, và mọi `OAuth2ClientFilter` BẮT BUỘC đặt `requireHttps: true`.

Lý do: Stack B và Stack C cho phép plaintext HTTP cho OIDC, logout, Vault và session traffic; phần review bổ sung của Stack A cũng ghi nhận vấn đề tương tự ở đường Vault, JWKS, logout và credential. Điều này biến nguy cơ token theft, credential interception và cookie interception thành một phần của thiết kế gateway, không còn là lỗi triển khai riêng lẻ. Derived from: Stack B `F4`; Stack C `§4 F4`; Stack A `§6` Codex-only additions; Cross-Stack Summary Universal Findings.

Cách triển khai trong OpenIG: Dùng giá trị endpoint và `baseURI` dạng HTTPS trong route config, đặt `requireHttps: true` trên `OAuth2ClientFilter`, và chỉ phát hành cookie `Secure` từ `JwtSession`. Nếu lab scaffolding vẫn HTTP-only thì không phải reference implementation. Derived from: Stack B `F4`; Stack C `§4 F4`.

### 4. Ranh giới Lưu trữ Session (Session Storage Boundaries)
[Derived from: B F6, B F8, C F5]

Nội dung: `JwtSession` KHÔNG ĐƯỢC lưu Vault token, downstream app credential, downstream app session cookie, hoặc bearer token. Trạng thái adapter nhạy cảm BẮT BUỘC dùng lưu trữ phía server với tham chiếu session dạng opaque. Bearer token KHÔNG ĐƯỢC inject vào `localStorage` của trình duyệt hoặc bất kỳ vùng lưu trữ nào JavaScript truy cập được. Nếu không thể tránh lưu token ở phía trình duyệt, BẮT BUỘC dùng cookie `httpOnly`, `Secure`.

Lý do: Stack B lưu Vault token và downstream session material trong gateway session gắn với trình duyệt, đồng thời để lộ Jellyfin access token trong `localStorage`. Stack C lưu Vault token và phpMyAdmin credential bên trong `JwtSession` gắn với trình duyệt. Các finding này cho thấy sự tiện lợi của adapter có thể làm sụp ranh giới giữa trạng thái identity của gateway và dữ liệu backend đặc quyền. Derived from: Stack B `F6`, `F8`; Stack C `§4 F5`; Cross-Stack Summary Stack-Specific Findings.

Cách triển khai trong OpenIG: Chỉ giữ tham chiếu identity/session dạng opaque trong `JwtSession`; giữ material adapter đặc quyền ở phía server và rehydrate trong chuỗi filter adapter khi cần. Với token delivery ra trình duyệt, ưu tiên cookie `httpOnly`, `Secure` thay vì rewrite response vào vùng lưu trữ JavaScript nhìn thấy được. Derived from: Stack B `F6`, `F8`; Stack C `§4 F5`.

### 5. Pin Origin và Toàn vẹn Redirect (Pinned Origins and Redirect Integrity)
[Derived from: A F5, B F7, C F9, B F5]

Nội dung: Tất cả redirect base URL, post-logout target và OAuth2 session namespace root BẮT BUỘC pin bằng hằng cấu hình tĩnh. Gateway KHÔNG ĐƯỢC suy ra redirect target hoặc session namespace root từ header inbound như `Host` hay `X-Forwarded-Host`. Namespace client của `OAuth2ClientFilter` dùng trong lưu trữ session BẮT BUỘC khớp tuyệt đối với client registration mà route sử dụng.

Lý do: Stack A, Stack B và Stack C đều suy ra hành vi redirect hoặc session-resolution từ dữ liệu host inbound, và Stack B có thêm lỗi toàn vẹn khi logout handler đọc sai OIDC namespace làm RP-initiated logout âm thầm thất bại. Derived from: Stack A `§5 F5`; Stack B `F5`, `F7`; Stack C `§4 F9`; Cross-Stack Summary Universal Findings and Stack-Specific Findings.

Cách triển khai trong OpenIG: Định nghĩa canonical public origin hằng cho từng route và dùng chúng để dựng redirect, `post_logout_redirect_uri`, và lookup OIDC session-key. Xác minh namespace mà SLO handler dùng trùng với client ID của `OAuth2ClientFilter` đã đăng ký trong route, và cấu hình nginx loại bỏ hoặc chuẩn hóa header liên quan host inbound trước khi request vào OpenIG. Derived from: Stack B `F5`, `F7`; Stack C `§4 F9`; Stack A `§5 F5`.

### 6. Hành vi Dependency có Giới hạn (Bounded Dependency Behavior)
[Derived from: B F9, C F7, B F10, C F8]

Nội dung: Mọi thao tác socket Redis BẮT BUỘC đặt `connectTimeout` và `soTimeout` tường minh; khuyến nghị đã review là connect `200ms` và read `500ms`. `BackchannelLogoutHandler` BẮT BUỘC chỉ trả `400` cho logout token malformed/invalid và BẮT BUỘC trả `500` cho lỗi Redis, JWKS hoặc runtime. Redis unavailability NÊN được bao bằng circuit-breaker hoặc cơ chế bounded-failure tương đương.

Lý do: Stack B và Stack C đều có hành vi socket Redis không giới hạn, đồng thời hạ lỗi backchannel nội bộ xuống `400`, có thể làm IdP ngừng retry gửi logout. Đây không chỉ là lỗi correctness; đây còn là lỗi latency và delivery contract. Derived from: Stack B `F9-F10`; Stack C `§4 F7-F8`; Cross-Stack Summary "Recommended Standard Pattern".

Cách triển khai trong OpenIG: Thay socket thô không giới hạn bằng timeout connect/read tường minh cho cả đường đọc và ghi revocation, và ánh xạ lớp lỗi để lỗi validate token trả `400` còn lỗi hạ tầng/runtime trả `5xx`. Thêm circuit-breaker hoặc cơ chế short-circuit tương đương quanh Redis nếu lỗi lặp lại có thể ghim worker thread. Derived from: Stack B `F9-F10`; Stack C `§4 F7-F8`.

### 7. Cưỡng chế Hợp đồng Adapter (Adapter Contract Enforcement)
[Derived from: C F6, A §6, B F5]

Nội dung: Mỗi adapter theo cơ chế đăng nhập BẮT BUỘC định nghĩa đầy đủ các thành phần filter-chain cần thiết như phần tử hợp đồng route không tùy chọn. App-specific cleanup controls, downstream cookie expiry và hook invalidation session BẮT BUỘC wiring vào route chain thay vì để ở helper script. Hook logout của adapter BẮT BUỘC được kiểm chứng end-to-end, bao gồm xác nhận `id_token_hint` không null trước khi dựng URL end-session.

Lý do: Stack C cho thấy có safeguard filter tồn tại trong code nhưng không có trong route chain. Stack B cho thấy SLO handler có thể lệch khỏi OAuth2 namespace đang hoạt động và âm thầm ngừng chạy. Ghi chú review bổ sung từ Stack A cho thấy login/retry ở helper có thể thoái hóa thành hành vi không an toàn khi route contract không cưỡng chế. Derived from: Stack C `§4 F6`; Stack B `F5`; Stack A `§6` Codex-only additions and Subagent-only findings.

Cách triển khai trong OpenIG: Xem route JSON và script Groovy là một đơn vị adapter: route phải khai báo tường minh mọi cleanup và identity filter bắt buộc, và logout handler phải validate namespace OIDC cùng token kỳ vọng tồn tại trước khi redirect sang Keycloak. Việc script xuất hiện trong `scripts/` không chứng minh control đang hoạt động. Derived from: Stack C `§4 F6`; Stack B `F5`; Stack A `§6`.

## Controls Khuyến nghị (Recommended Controls - SHOULD)

### 8. Quan sát Logout (Logout Observability)
[Derived from: A F4]

Logout handler KHÔNG NÊN log full redirect URL có chứa `id_token_hint` hoặc bất kỳ token value nào. NÊN chỉ log timestamp, session identifier dạng opaque, logout type (`RP-initiated` hoặc `backchannel`), và kết quả (`success` hoặc `failure`).

Lý do: Stack A log toàn bộ logout URL, làm lộ `id_token_hint` vào log. Stack C là đối chứng tích cực vì log metadata request và cảnh báo thiếu token mà không log URL đã lắp token. Derived from: Stack A `§5 F4`; Stack C `§7` Cross-Stack Comparison Anchors.

### 9. An toàn Re-auth cho Unsafe Method (Unsafe Method Reauth Safety)
[Derived from: A §6]

Nếu request khi session đã hết hạn dùng `POST`, `PUT`, `PATCH`, hoặc `DELETE`, gateway KHÔNG NÊN redirect âm thầm và làm mất request body. NÊN hoặc trả `401` kèm gợi ý re-authentication, hoặc bảo toàn đủ ngữ cảnh request để replay sau khi xác thực lại.

Lý do: Finding review bổ sung của Stack A cho thấy logic retry dựa trên redirect có thể làm mất body request gốc. Đây là rủi ro ngữ nghĩa request trong các adapter giả định mọi request hết session đều an toàn để redirect. Derived from: Stack A `§6` Codex-only additions.

### 10. Failure Mode của Adapter (Adapter Failure Mode)
[Derived from: A §6]

Nếu credential injection của adapter thất bại do Vault không truy cập được, tra cứu credential thất bại, hoặc synthetic login downstream không hoàn tất, gateway NÊN fail closed và trả `503` thay vì proxy request ở trạng thái chưa xác thực.

Lý do: Ghi chú review bổ sung của Stack A chỉ ra đường lỗi synthetic login có thể thoái hóa thành proxy không xác thực. Hệ quả ở mức pattern là lỗi adapter phải giữ nguyên đảm bảo xác thực, không được bypass. Derived from: Stack A `§6` Subagent-only findings.

## Luồng SLO — Trình tự Chuẩn (SLO Flow — Standard Sequence)

Trình tự này chuẩn hóa cả RP-initiated và backchannel logout để tính đúng đắn của logout không phụ thuộc script đặc thù từng stack hoặc hành vi Redis best-effort. Derived from: Stack A `§4`, `§5 F2-F5`; Stack B "Confirmed Strengths", `F2-F5`, `F10-F11`; Stack C `§3`, `§4 F2-F3`, `§4 F7-F9`.

### RP-Initiated Logout (người dùng bấm logout)

Derived from: Stack A `§5 F4-F5`; Stack B `F5`, `F7`; Stack C `§4 F9`.

1. Trình duyệt gọi `SloHandler` của OpenIG.
2. `SloHandler` đọc `id_token` từ `JwtSession`, và namespace được đọc BẮT BUỘC khớp với client ID của `OAuth2ClientFilter` cấu hình cho route đó.
3. `SloHandler` xây URL `end_session` của Keycloak với `id_token_hint` và `post_logout_redirect_uri`, và redirect target này BẮT BUỘC lấy từ config đã pin, không lấy từ `Host` inbound.
4. `SloHandler` invalidate `JwtSession` cục bộ để trình duyệt không thể tiếp tục trên session cục bộ còn sống trong khi logout từ xa đang diễn ra.
5. Trình duyệt được redirect tới endpoint `end_session` của Keycloak.
6. Keycloak kích hoạt backchannel logout tới mọi OpenIG client đã đăng ký.

### Backchannel Logout (Keycloak khởi tạo)

Derived from: Stack A `§4`, `§5 F2-F3`; Stack B "Confirmed Strengths", `F2-F3`, `F10-F11`; Stack C `§3`, `§4 F2-F3`, `§4 F7-F8`.

1. Keycloak gửi `POST /backchannel_logout` kèm logout token đã ký.
2. `BackchannelLogoutHandler` validate token: `alg=RS256`, JWKS lookup theo `kid`, verify chữ ký, và kiểm tra `iss`, `aud`, `events`, `iat`, `exp`.
3. Khi token hợp lệ, handler ghi `blacklist:<sid>` vào Redis với TTL bằng `sessionTimeout`.
4. Handler trả `200` cho Keycloak.
5. Ở request đã xác thực kế tiếp, `SessionBlacklistFilter` kiểm tra Redis cho `sid` đó.
6. Nếu `sid` bị blacklist, gateway fail closed: xóa trạng thái session cục bộ và redirect tới login hoặc từ chối truy cập.
7. Nếu Redis không truy cập được, gateway fail closed bằng `503` hoặc re-authentication; KHÔNG ĐƯỢC cho request đi qua như đã xác thực.

## Mẫu Sai cần Tránh (Anti-Patterns - MUST NOT)

Derived from: Cross-Stack Summary Universal Findings and Stack-Specific Findings; Stack A `§5 F1-F5`, `§6`; Stack B `F1-F11`; Stack C `§4 F1-F9`.

| Anti-pattern | Risk | Finding ref | Correct approach |
|---|---|---|---|
| Hardcoded secret trong file config hoặc route | Truy cập repo hoặc filesystem có thể làm lộ signing material của gateway và OIDC client credential | `A F1`, `B F1`, `C F1` | Externalize secret gateway và OIDC sang Vault hoặc environment tại runtime |
| TTL revocation ngắn hơn session lifetime | Session trình duyệt đã bị thu hồi có thể hợp lệ trở lại sau khi entry Redis hết hạn | `A F3`, `B F2`, `C F2` | Đặt TTL blacklist ít nhất bằng `JwtSession.sessionTimeout` |
| Fail-open khi Redis lỗi | Redis outage biến enforcement logout thành hành vi best-effort | `A F2`, `B F3`, `C F3` | Fail closed cho session đã xác thực khi trạng thái revocation không xác định |
| Redirect suy ra từ Host | Toàn vẹn redirect và tra cứu session phụ thuộc vào request header có thể bị attacker ảnh hưởng | `A F5`, `B F7`, `C F9` | Pin origin và redirect target trong config tĩnh |
| Vault hoặc app credential trong `JwtSession` | Mất cookie hoặc lộ shared-secret làm lộ backend material đặc quyền | `B F6`, `C F5` | Giữ trạng thái adapter đặc quyền ở phía server sau tham chiếu session opaque |
| Bearer token trong `localStorage` | Bất kỳ JavaScript cùng origin đều có thể đọc và lưu token lâu dài | `B F8` | Dùng cookie `httpOnly`, `Secure` hoặc lưu trữ phía server |
| Script safeguard của adapter không được wiring | Control dọn dẹp dự kiến có trong code nhưng không active trong route chain đang chạy | `C F6` | Biến filter adapter bắt buộc thành phần tử tường minh trong route chain |
| Trả HTTP `400` cho lỗi hạ tầng trong backchannel handler | IdP có thể coi lỗi tạm thời là lỗi vĩnh viễn và dừng retry gửi logout | `B F10`, `C F8` | Chỉ trả `400` cho logout token không hợp lệ và trả `5xx` cho lỗi nội bộ |
| Đọc `id_token_hint` từ sai OIDC namespace | RP-initiated logout âm thầm thất bại vì không tìm thấy OIDC session mong đợi | `B F5` | Gắn logout handler với đúng namespace/client ID của `OAuth2ClientFilter` trong route |
| Thiếu timeout socket Redis | Kết nối Redis chậm hoặc half-open có thể ghim worker thread và làm giảm khả dụng | `A §6`, `B F9`, `C F7` | Đặt timeout connect/read tường minh cho mọi thao tác socket revocation |

## Checklist — Đánh giá Tích hợp Mới

Derived from: Cross-Stack Summary "Recommended Standard Pattern" and "Next Steps"; Stack A `§5 F1-F5`, `§6`; Stack B `F1-F11`; Stack C `§4 F1-F9`.

### Quản lý secret

- [ ] `JwtSession.sharedSecret`, OIDC `clientSecret`, và mật khẩu keystore lấy từ Vault hoặc environment tại runtime và không xuất hiện trong config, route hoặc Groovy.
- [ ] Mọi truy xuất secret từ Vault đều được cache với TTL có giới hạn và refresh trước khi hết hạn, không ghi secret đã lấy vào `JwtSession`.

### Session và revocation

- [ ] `BackchannelLogoutHandler` ghi `blacklist:<sid>` với TTL lớn hơn hoặc bằng `JwtSession.sessionTimeout`.
- [ ] Mọi đường đọc revocation dùng đúng key `sid` được ghi bởi backchannel handler.
- [ ] Nếu Redis lookup lỗi cho session đã xác thực, request fail closed thay vì tiếp tục xuống downstream.
- [ ] Đường đọc và ghi Redis có timeout connect/read tường minh, và lỗi runtime backchannel trả `5xx`.

### Transport

- [ ] Tất cả endpoint OIDC, Vault call và downstream proxy target dùng HTTPS trong production.
- [ ] Mọi `OAuth2ClientFilter` đều có `requireHttps: true`, và cookie `JwtSession` là `Secure`.

### Hợp đồng adapter

- [ ] Route chain tường minh bao gồm mọi adapter filter, cleanup hook và logout handler bắt buộc; không có safeguard chỉ tồn tại ở script chưa wiring.
- [ ] Material đặc quyền theo adapter được lưu phía server, không nằm trong `JwtSession`, `localStorage`, hoặc vùng lưu trữ JavaScript-accessible khác.

### Luồng logout

- [ ] RP-initiated logout đọc đúng OIDC namespace, xác minh `id_token_hint` tồn tại, và dùng post-logout redirect target đã pin.
- [ ] Backchannel logout validate `alg`, `kid`/JWKS, chữ ký, `iss`, `aud`, `events`, `iat`, và `exp` trước khi ghi trạng thái revocation.

### Quan sát vận hành (Observability)

- [ ] Log logout chỉ chứa timestamp, session identifier dạng opaque, loại logout và kết quả; không log URL chứa token hoặc token value.
