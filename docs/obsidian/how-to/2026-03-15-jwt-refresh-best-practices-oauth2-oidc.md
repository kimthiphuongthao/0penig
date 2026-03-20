---
title: JWT Refresh Token Best Practices (OAuth2/OIDC)
tags:
  - oauth2
  - oidc
  - jwt
  - refresh-token
  - security
date: 2026-03-15
status: done
---

# JWT Refresh Token Best Practices (OAuth2/OIDC)

Context: research cho luong refresh token trong OAuth2/OIDC, uu tien nguon chinh thuc (IETF RFC, OIDC Core) va GitHub examples chinh chu.

## Core Guidance

> [!success] Confirmed baseline
> Dung Authorization Code + PKCE cho public clients. Khong su dung implicit flow cho use case can refresh token.

> [!warning] High-risk pitfall
> Refresh token cho public clients phai co co che chong replay: sender-constrained (DPoP/mTLS) hoac refresh token rotation + reuse detection.

> [!tip] Practical rule
> Access token nen short-lived; refresh token long-lived hon nhung co inactivity timeout, absolute lifetime, va revoke path ro rang.

## Best-Practice Checklist

1. Chi cap refresh token khi can thiet (risk-based issuance).
2. Voi public clients (SPA/mobile): bat buoc PKCE; khuyen nghi rotation + reuse detection.
3. Neu co the, sender-constrain refresh token (DPoP/mTLS).
4. Luu refresh token an toan:
   - Native: secure enclave/keystore.
   - Web: tranh localStorage; uu tien BFF + secure session cookie.
5. Thiet lap expiration:
   - inactivity timeout;
   - absolute max lifetime;
   - revoke khi logout/password reset/compromise.
6. Khi refresh:
   - xac thuc client theo loai client;
   - khong mo rong scope vuot qua consent ban dau.
7. Neu rotate refresh token:
   - cap token moi moi lan refresh;
   - vo hieu hoa token cu;
   - neu phat hien reuse => revoke token family/grant.
8. Co endpoint revocation va (neu can) introspection cho ecosystem can kiem tra online.
9. Logging/monitoring bat buoc:
   - refresh success/fail;
   - reuse detection events;
   - anomaly theo device/IP/geo.
10. Neu refresh token la JWT:
    - phai co integrity protection (signature/MAC),
    - include grant/family identifier (jti/family_id),
    - tranh de lo claim nhay cam.

## Official References

- OAuth 2.0 Framework: [RFC 6749](https://www.rfc-editor.org/rfc/rfc6749)
- OAuth 2.0 Security BCP: [RFC 9700](https://www.rfc-editor.org/rfc/rfc9700)
- OAuth 2.0 Token Revocation: [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009)
- OAuth 2.0 DPoP: [RFC 9449](https://www.rfc-editor.org/rfc/rfc9449)
- OAuth 2.0 for Native Apps (PKCE context): [RFC 8252](https://www.rfc-editor.org/rfc/rfc8252)
- OpenID Connect Core 1.0 (offline_access, refresh usage): [OIDC Core](https://openid.net/specs/openid-connect-core-1_0.html)

## GitHub Examples (Official Repos)

- [[OpenID]] AppAuth-iOS: `performActionWithFreshTokens` pattern  
  https://github.com/openid/AppAuth-iOS
- openid-client (Node): refresh grant usage (`client.refresh(...)`)  
  https://github.com/panva/openid-client
- Spring Authorization Server (official): refresh token support and token settings in source/sample  
  https://github.com/spring-projects/spring-authorization-server

## SSO-Lab Mapping

- [[OpenIG]]: uu tien BFF pattern cho web clients de giam token exposure tren browser.
- [[Keycloak]]: bat rotation/revocation policy, review offline token settings.
- [[Vault]]: luu client secrets/signing keys, rotate dinh ky.
- [[Stack C]]: follow-up test cases cho refresh replay/revocation scenarios.
