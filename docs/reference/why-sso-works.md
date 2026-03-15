# Tại sao SSO hoạt động — dù khác Stack?

> Câu hỏi: Tại sao SSO được? Tại sao dù khác stack nhưng vẫn SSO được?

## SSO là gì về mặt kỹ thuật?

SSO = user chỉ cần login **1 lần**, các app còn lại **tự nhận ra** mà không hỏi lại.

Cơ chế là **Keycloak SSO session** — khi user login lần đầu, Keycloak tạo 1 session và lưu vào **browser cookie** tên `KEYCLOAK_SESSION` (domain `auth.sso.local`).

---

## Lần đầu login (app1)

```
Browser → app1 → OpenIG → 'chưa có JSESSIONID' → redirect Keycloak
Keycloak: 'chưa có KEYCLOAK_SESSION' → hiện form login
User nhập alice/alice123
Keycloak: tạo SSO session, set cookie KEYCLOAK_SESSION, redirect về OpenIG callback
OpenIG: nhận token, lưu vào JSESSIONID → cho vào app1 ✓
```

---

## Sang app3 (Stack B) — KHÔNG hỏi lại

```
Browser → app3 → OpenIG-B → 'chưa có JSESSIONID' → redirect Keycloak
Keycloak: 'ĐÃ có KEYCLOAK_SESSION cookie!' → KHÔNG hiện form login
          → tự động cấp token mới → redirect về OpenIG-B callback
OpenIG-B: nhận token, lưu vào JSESSIONID-B → cho vào app3 ✓
```

User không thấy form login — nhưng thực ra vẫn có 1 redirect đi về Keycloak rất nhanh (trong suốt với user).

---

## Tại sao cross-stack vẫn SSO được?

Vì **Keycloak là shared** — cả 2 stack đều trỏ về cùng 1 Keycloak (`auth.sso.local:8080`). Cookie `KEYCLOAK_SESSION` nằm trên browser, không phân biệt app nào hỏi.

```
Stack A (openiga.sso.local)  ──┐
                               ├──→ Keycloak (auth.sso.local:8080)
Stack B (openigb.sso.local)  ──┘        ↑
                                   KEYCLOAK_SESSION
                                   (cookie trên browser)
```

Stack A và Stack B **không cần biết nhau** — cả hai chỉ cần biết Keycloak. Keycloak là 'trung tâm nhớ' ai đã login.

---

## So sánh SSO vs SLO

| | SSO | SLO |
|--|-----|-----|
| **Nhờ vào** | `KEYCLOAK_SESSION` cookie (browser ↔ Keycloak) | Redis blacklist (Keycloak → OpenIG) |
| **Ai điều phối** | Keycloak (tự động, có sẵn) | BackchannelLogoutHandler (custom code) |
| **App cần làm gì** | Redirect đến Keycloak khi chưa có session | Check Redis mỗi request |
| **Cross-stack** | Tự nhiên — vì dùng chung Keycloak | Cần Redis vì OpenIG session tách biệt |

**SSO dễ hơn SLO** — SSO là tính năng có sẵn của Keycloak, chỉ cần mọi app đều dùng chung 1 Keycloak là xong. SLO mới phức tạp vì phải đồng bộ ngược lại từ Keycloak về từng OpenIG node.

---
*Xem thêm: [why-redis-slo.md](./why-redis-slo.md) — giải thích tại sao SLO cần Redis.*
