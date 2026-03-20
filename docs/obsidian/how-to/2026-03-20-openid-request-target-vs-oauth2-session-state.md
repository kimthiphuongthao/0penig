---
title: OpenID Request Target vs OAuth2 Session State
tags:
  - openig
  - oidc
  - oauth2
  - session
  - documentation
date: 2026-03-20
status: done
---

# OpenID Request Target vs OAuth2 Session State

Context: ACT-1 updated the definitive legacy auth patterns deliverable to make the OpenIG OIDC data model explicit for browser-bound `JwtSession` routes.

## What Changed

- Expanded the validated session note in `docs/deliverables/legacy-auth-patterns-definitive.md` under `## Template-Based Integration`.
- Kept the existing `TokenReferenceFilter.groovy` placement guidance and appended the missing data-model distinction between request attributes and persisted session state.

> [!success] Confirmed behavior
> `target = ${attributes.openid}` is request-scoped output produced by `OAuth2ClientFilter.fillTarget()`. It is valid for live request data such as `user_info`.

> [!warning] Gotcha
> `attributes.openid` does not mirror into session. Persisted OIDC state is written separately by `OAuth2Utils.saveSession()`, so token lookups such as `session[oauth2Key].atr.id_token` must read from the saved `session[oauth2Key]` blob.

> [!tip] Best practice
> For browser-bound `JwtSession`, keep `TokenReferenceFilter.groovy` immediately after `OAuth2ClientFilter` so the large `oauth2:*` payload is offloaded to Redis. Use `attributes.openid` for request-scoped identity data and `session[oauth2Key]` for persisted token material.

## Lab Mapping

- [[OpenIG]]: source of `OAuth2ClientFilter.fillTarget()` request output and the persisted `oauth2Key` session blob behavior.
- [[Keycloak]]: upstream OIDC provider; no behavior change required, but the note avoids misreading returned user profile data.
- [[Vault]]: unaffected by this documentation fix, but still the source of OIDC client secret material for the surrounding pattern.
- [[Stack C]]: shared-cookie stacks continue using per-app token reference keys such as `token_ref_id_appN`.

## Files Changed

- `docs/deliverables/legacy-auth-patterns-definitive.md`
