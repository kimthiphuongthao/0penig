# Tại sao cần Redis để thực hiện SLO?

> Update 2026-03-17: Pattern Consolidation Steps 1-5 are complete. Step 5 verification confirmed Redis blacklist writes for all 5 OIDC clients, and Step 6 is the current document-sync pass.

> Câu hỏi: Tôi chưa hiểu rõ hết vì sao lại cần đến Redis, tại sao dùng thêm Redis thì lại SLO được?

## Vấn đề gốc: Keycloak không thể 'vào tận nơi' xóa session

Khi user logout, Keycloak hủy **SSO session** của mình. Nhưng OpenIG đang giữ **JSESSIONID** (Tomcat session) chứa tokens — Keycloak không có cách nào trực tiếp xóa cái đó đi.

```
Keycloak:  'Tao đã xóa session rồi'
OpenIG:    'Tao vẫn còn JSESSIONID của nó, user vào được bình thường'
```


## Tại sao Redis giải quyết được?

Redis đóng vai trò **'bảng thông báo'** dùng chung:

```
1. User logout
        ↓
2. Keycloak POST đến /openid/app1/backchannel_logout
   (BackchannelLogoutHandler nhận)
        ↓
3. Handler ghi vào Redis:
   SET blacklist:abc-session-id '1' EX 1800
        ↓
4. User vào app1 lần sau → SessionBlacklistFilter chạy
   GET blacklist:abc-session-id → có! → xóa JSESSIONID → redirect login
```

Redis là **tầng trung gian** vì:
- Keycloak không biết OpenIG đang lưu session ở đâu (JSESSIONID local)
- OpenIG không biết Keycloak đã logout ai (2 hệ thống tách biệt)
- Redis là nơi cả 2 phía đều có thể đọc/ghi

Step 5 verification (2026-03-17) reconfirmed điều này trên toàn bộ lab: 5/5 clients đều ghi được `blacklist:<sid>` vào Redis khi logout.

## Cross-stack hoạt động vì Keycloak gửi đến TẤT CẢ client

```
User logout từ app3 (Stack B)
        ↓
Keycloak hủy SSO session
        ↓
Keycloak POST đến openig-client-b  → redis-b → kick app3
Keycloak POST đến openig-client    → redis-a → kick app1, app2
```

Keycloak gửi back-channel logout đến **mọi client** trong realm có backchannelLogoutUrl — không phân biệt stack. Đó là lý do cross-stack hoạt động dù redis-a và redis-b là 2 instance tách biệt.

## Tóm lại

| Không có Redis | Có Redis |
|----------------|----------|
| Keycloak logout → chỉ Keycloak session bị xóa | Keycloak logout → signal lan đến OpenIG qua Redis |
| OpenIG vẫn còn JSESSIONID → user vào được | OpenIG check Redis → phát hiện bị revoke → kick |
| Cross-stack: không có cơ chế đồng bộ | Cross-stack: Keycloak gửi POST → mỗi stack ghi Redis riêng → đồng loạt kick |

---
*Ghi chú: Đây là giải pháp custom cho OpenIG 6. Nếu nâng cấp lên OpenIG/IG 7.1+, có thể dùng built-in back-channel logout support thay thế.*

---

## SessionBlacklistFilter là gì trong OpenIG?

> Câu hỏi: SessionBlacklistFilter ở đây là code trên OpenIG à? Nó được gọi là gì — plugin hay module hay gì?

Nó là một **ScriptableFilter** — tức là một Groovy script được OpenIG load và chạy như 1 filter trong chain. Không phải plugin hay module riêng — chỉ là đoạn code Groovy bạn viết, OpenIG cung cấp runtime để chạy nó.

```json
{
  "name": "SessionBlacklistFilter",
  "type": "ScriptableFilter",
  "config": {
    "type": "application/x-groovy",
    "file": "SessionBlacklistFilter.groovy"
  }
}
```

OpenIG có sẵn các type built-in: `ScriptableFilter`, `ScriptableHandler`, `HeaderFilter`, `OAuth2ClientFilter`... `ScriptableFilter` là cách để viết logic tùy ý bằng Groovy.

---

## Ai xóa JSESSIONID? Ai xóa key trong Redis?

> Câu hỏi: Chính OpenIG (SessionBlacklistFilter) sẽ thấy blacklist rồi xóa nó đi à?

**SessionBlacklistFilter KHÔNG xóa JSESSIONID**. Nó xóa **session data** (nội dung bên trong), còn JSESSIONID (cookie) vẫn tồn tại trên browser nhưng rỗng tuếch:

```groovy
session.clear()   // xóa toàn bộ data trong Tomcat session
                  // (tokens, wp_cookies, vault_token...)
```

Sau đó redirect → browser gửi request mới → OidcFilter thấy session rỗng → redirect đến Keycloak login.

**Không ai xóa key Redis thủ công** — Redis tự xóa sau TTL:

```
SET blacklist:abc-session-id "1" EX 1800
                                    ↑
                              tự hết hạn sau 1 giờ
```

TTL 1800s hiện được giữ đồng bộ với `JwtSession.sessionTimeout = 30 minutes` trong lab hiện tại. Sau khi hết hạn, key tự biến mất.

---

## Flow đầy đủ (mọi request authenticated)

```
Mọi request authenticated
        ↓
SessionBlacklistFilter chạy (luôn luôn)
        ↓
   ┌─ Redis: không có key → cho qua bình thường
   │
   └─ Redis: có key blacklist:{sid}
              ↓
         session.clear()  ← xóa data trong Tomcat
              ↓
         redirect về chính URL đó
              ↓
         OidcFilter thấy session rỗng → redirect Keycloak
              ↓
         User phải login lại
```

---

## Bảng phân công trách nhiệm

| Việc | Ai làm | Bằng cách nào |
|------|--------|---------------|
| Ghi blacklist | `BackchannelLogoutHandler.groovy` (ScriptableHandler) | Redis `SET blacklist:{sid} 1 EX 1800` |
| Đọc blacklist | `SessionBlacklistFilter.groovy` (ScriptableFilter) | Redis `GET blacklist:{sid}` |
| Xóa blacklist | Redis tự xóa | TTL hết hạn sau 1800s |
| Xóa session data | `SessionBlacklistFilter.groovy` | `session.clear()` |
| Force re-login | OidcFilter (built-in OpenIG) | Thấy session rỗng → redirect Keycloak |

---

## Keycloak cần cấu hình gì để SLO hoạt động với legacy app?

> Câu hỏi: Keycloak sẽ phải cấu hình những gì, cấu hình như thế nào để có thể SLO được cho legacy app khi dùng giải pháp này?

Tại Keycloak Admin → Clients → [client-id] → Settings:

**1. Backchannel logout URL**

```
Backchannel logout URL: http://<openig-host>/openid/<app-id>/backchannel_logout
```

OpenIG phải expose endpoint này qua một route với BackchannelLogoutHandler.

**2. Backchannel logout session required: ON**

Bắt buộc — Keycloak đính kèm `sid` (session ID) vào `logout_token`. Không có `sid` thì không thể blacklist đúng session.

**3. Front channel logout: OFF**

Legacy app không có JS để handle front-channel logout. Dùng backchannel (server-to-server POST).

**4. Valid post logout redirect URIs**

```
http://<openig-host>/*
```

Keycloak từ chối redirect sau logout nếu URI không match — trả về "Invalid redirect uri".

**5. Keycloak chỉ POST đến client đang có active session**

Nếu user chưa từng login qua client đó trong session hiện tại → không nhận POST backchannel logout.

Với 100 client: nếu user chỉ SSO vào 3 app → chỉ 3 OpenIG nhận POST → chỉ 3 Redis ghi blacklist.

## Nginx phải cấu hình riêng cho backchannel_logout

Path backchannel_logout phải có location block riêng — tránh retry làm mất body POST:

```nginx
location ~ ^/openid/(app1|app2)/backchannel_logout {
    proxy_pass http://openig_pool;
    proxy_request_buffering off;
    proxy_next_upstream off;
}
```

Nếu thiếu `proxy_next_upstream off`: nginx retry POST sang node khác → body bị mất → OpenIG nhận request rỗng → "logout_token is not a JWT".
