# OpenIG 6 SSO/SLO Final Audit Report v2
**Date:** 2026-03-20

16 claims evaluated: 11 confirmed, 5 refuted. This v2 report is self-contained: it consolidates the two primary-source audits, adds exact class/method/line citations plus inline snippets for every verdict, and makes `ACT-1`, `ACT-2`, and `ACT-3` implementable without opening any other file.

## Gap Summary

- No claim from the two primary audits was dropped from verdict coverage, but the prior final report understated several points that matter for implementation quality.
- `B1` needed the `OAuth2Utils.buildUri` nuance: the persisted key is based on URI resolution against the original request, not on blind string concatenation, even though the current `session.keySet()` discovery logic still matches runtime behavior.
- The prior verdict table used accurate evidence themes, but it was still too vague for a planning agent because it omitted exact class/method/line references and 1-3 line source snippets.
- The prior `Confirmed Claims` section had no terminal evidence tags, which made it harder to trace each claim back to a single upstream proof point.
- The prior `B4`, `E1`, and `F2` action items named the right destination files but did not identify the exact section to edit, the exact current text to replace or extend, or the exact replacement text to add.
- The prior `Architecture Implications` bullets were directionally correct but uncited.
- One audit-scope correction from the Codex primary audit was missing entirely: the upstream repo audit should anchor on `JwtSessionManager` and `RouterHandler`, because `JwtSessionFactory` and `Router.java` are not present in the inspected OpenIG repo tree.

## Final Verdict Table

| Claim ID | Priority | Final Verdict | Exact Evidence | Runtime Impact | Action Required |
| --- | --- | --- | --- | --- | --- |
| B1 | CRITICAL | CONFIRM | `OAuth2Utils.buildUri` lines `53-77`: `return routerContext.getOriginalUri().resolve(new URI(uriString));`<br>`OAuth2Utils.sessionKey` lines `154-156`: `return "oauth2:" + clientEndpoint;` | NONE | NONE |
| B2 | CRITICAL | CONFIRM | `OAuth2Session.toJson` lines `97-107`: `putIfNotNullOrEmpty(json, "atr", accessTokenResponse.getObject());` | NONE | NONE |
| B3 | CRITICAL | REFUTE | `OAuth2Session.toJson` lines `97-107`: serialized fields are only `crn`, `ce`, `s`, `arn`, `atr`, `ea`<br>`OAuth2ClientFilter.fillTarget` lines `790-827`: `target.set(bindings(context, null), info)` | NONE | DONE |
| B4 | CRITICAL | REFUTE | `OAuth2ClientFilter.fillTarget` lines `820-825`: `target.set(bindings(context, null), info)`<br>`OAuth2Utils.saveSession` lines `147-152`: `session.put(sessionKey, oAuth2Session.toJson().getObject());` | NONE | DOC-FIX |
| C2 | CRITICAL | CONFIRM | `RouteBuilder.setupRouteHandler` lines `162-165`: `newSessionFilter(sessionManager)`<br>`SessionFilter.filter` lines `53-57`: `thenOnResult(...)`<br>`SessionFilter$1.handleResult` lines `61-67`: `sessionManager.save(...)` | NONE | NONE |
| A1 | HIGH | CONFIRM | `JwtCookieSession.keySet` lines `265-268`: `return new DirtySet<>(super.keySet(), this);` | NONE | NONE |
| A2 | MED | CONFIRM | `JwtCookieSession.clear` lines `260-262`: `dirty = true; super.clear();` | NONE | NONE |
| A3 | MED | CONFIRM | `JwtCookieSession.remove` lines `253-256`: `dirty = true; return super.remove(key);` | NONE | NONE |
| A4 | MED | REFUTE | `JwtCookieSession.save` lines `291-297`: oversize session throws `IOException`<br>`SessionFilter$1.handleResult` lines `61-67`: `Failed to save session` | LOW | DONE |
| A5 | HIGH | CONFIRM | `Keys.<field SESSION_FACTORY_HEAP_KEY>` line `99`: `public static final String SESSION_FACTORY_HEAP_KEY = "Session";`<br>`GatewayHttpApplication.create` lines `163-167`: `heap.get(SESSION_FACTORY_HEAP_KEY, SessionManager.class)` | NONE | NONE |
| D1 | MED | CONFIRM | `ScriptableHandler.handle` lines `46-48`: live `Request` is exposed to scripts<br>`StaticRequestFilterTest.testFormAttributesPropagationWithPostMethod` lines `231-238`: `request.getEntity().getString()` | NONE | NONE |
| D2 | HIGH | CONFIRM | `AbstractScriptableHeapObject.<field scriptGlobals>` line `174`: `new ConcurrentHashMap<>()`<br>`AbstractScriptableHeapObject.enrichBindings` lines `246-254`: `globals` binding exported to script | NONE | NONE |
| E1 | MED | REFUTE | `OAuth2ClientFilter.filter` lines `292-323`: login/callback/logout matching happens inside the filter instance<br>`RouteBuilder.setupRouteHandler` lines `156-192`: filters are installed per route<br>Architect v2 field quote: `private Expression<String> clientEndpoint;` (field line number not available in the primary audits) | NONE | DOC-FIX |
| E2 | MED | CONFIRM | `RouterHandler.<field sorted>` lines `123-124`: `new TreeSet<>(new LexicographicalRouteComparator())`<br>`LexicographicalRouteComparator.compare` line `28`: `first.getId().compareTo(second.getId())` | NONE | NONE |
| F1 | MED | CONFIRM | `JwtCookieSession.buildJwtCookie` lines `332-337`: `.setDomain(cookieDomain)` | NONE | NONE |
| F2 | MED | REFUTE | `JwtCookieSession.buildJwtCookie` lines `324-337`: `.setExpires(new Date(expiryTime.longValue()))`<br>`JwtCookieSession.<field IG_EXP_SESSION_KEY>` lines `79-82`: `_ig_exp`<br>`JwtCookieSession.getNewExpiryTime` lines `370-372`: expiry time is derived from `sessionTimeout` | NONE | DOC-FIX |

## Confirmed Claims (no action needed)

- `B1`: `OAuth2Utils.buildUri()` resolves the configured `clientEndpoint` against the original request URI, and `OAuth2Utils.sessionKey()` persists that resolved URI under the `oauth2:` prefix. The runtime conclusion remains confirmed, but the mechanism should be described as URI resolution rather than raw concatenation. Evidence: `OAuth2Utils.buildUri` lines `53-77`; `OAuth2Utils.sessionKey` lines `154-156`.
- `B2`: `OAuth2Session.toJson()` writes the token response map under `atr`, so `session[oauth2Key].atr.id_token` is the correct persisted lookup path for logout and blacklist flows. Evidence: `OAuth2Session.toJson` lines `97-107`; `OAuth2Session.extractIdToken` lines `48-59`.
- `C2`: `RouteBuilder.setupRouteHandler()` wraps the route with `SessionFilter`, and the save hook runs after downstream promise callbacks complete. That means Groovy `.then { ... }` mutations are persisted by the final cookie save, which is exactly why the Redis token-reference offload pattern works. Evidence: `RouteBuilder.setupRouteHandler` lines `162-165`; `Filters.chainOf` lines `99-108`; `Filters$2.filter` line `127`; `SessionFilter.filter` lines `53-57`; `SessionFilter$1.handleResult` lines `61-67`.
- `A1`: `JwtCookieSession.keySet()` exposes the stored backing-map keys through `DirtySet`, so dynamic discovery of `oauth2:*` entries is supported behavior rather than a Groovy quirk. Evidence: `JwtCookieSession.loadJwtSession` lines `193-198`; `JwtCookieSession.keySet` lines `265-268`.
- `A2`: `JwtCookieSession.clear()` marks the session dirty and empties the backing map, so a full browser-session invalidation is a first-class supported path. Evidence: `JwtCookieSession.clear` lines `260-262`; `JwtCookieSession.save` lines `280-305`; `JwtCookieSession.buildExpiredJwtCookie` lines `320-322`.
- `A3`: `JwtCookieSession.remove()` is implemented directly and marks the session dirty before removing the key, so per-key cleanup is safe for adapter-specific markers and token references. Evidence: `JwtCookieSession.remove` lines `253-256`.
- `A5`: The primary evidence confirms the non-negotiable rule that the auto-wired session heap key is `Session`, and that this naming controls whether OpenIG uses the configured session manager or falls back to container session behavior. No extra Tomcat-specific claim is required to preserve the implementation rule. Evidence: `Keys.<field SESSION_FACTORY_HEAP_KEY>` line `99`; `GatewayHttpApplication.create` lines `163-167`.
- `D1`: Scripts receive the live `Request`, and the framework test proves that form-urlencoded bodies can be read with `request.entity.getString()`. That validates the backchannel logout body-parsing path. Evidence: `ScriptableHandler.handle` lines `46-48`; `StaticRequestFilterTest.testFormAttributesPropagationWithPostMethod` lines `231-238`.
- `D2`: `globals` is backed by `ConcurrentHashMap`, and the binding is exported to scripts, so `globals.compute(...)` is a valid concurrency-safe cache pattern for JWKS lookups. Evidence: `AbstractScriptableHeapObject.<field scriptGlobals>` line `174`; `AbstractScriptableHeapObject.enrichBindings` lines `246-254`; `GroovyScriptableFilterTest.testGlobalsPersistedBetweenInvocations` lines `253-267`.
- `E2`: Route order is determined by lexicographic comparison of filename-derived route IDs stored in `RouterHandler`'s sorted set, so filename prefixes remain the correct route-precedence control. Evidence: `RouterHandler.onAddedFile` lines `394-423`; `RouterHandler.<field sorted>` lines `123-124`; `LexicographicalRouteComparator.compare` line `28`; `RouterHandler.handle` lines `347-355`.
- `F1`: `JwtCookieSession.buildJwtCookie()` maps `cookieDomain` directly to the cookie `Domain` attribute, which is why the current `.sso.local` cross-subdomain scope works. Evidence: `JwtCookieSession.buildJwtCookie` lines `332-337`; `JwtCookieSession.buildExpiredJwtCookie` lines `320-322`.

## Refuted Claims - Already Fixed

### B3

The claim said the persisted OAuth2 session blob exposes `session[oauth2Key].user_info.sub`. The primary audits refute that directly: `OAuth2Session.toJson()` serializes only `crn`, `ce`, `s`, `arn`, `atr`, and `ea`, while `OAuth2ClientFilter.fillTarget()` writes live `user_info` into the configured target expression instead of the persisted session blob. That is why the old `user_info` session read in `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy` was dead code rather than a working fallback. Evidence: `OAuth2Session.toJson` lines `97-107`; `OAuth2ClientFilter.fillTarget` lines `790-827`; `OAuth2Utils.saveSession` lines `147-152`.

That dead code was removed in commit `b53c239`, which deleted the stale `user_info` session read from `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`. No further action is required.

### A4

The claim said a `JwtCookieSession` over 4096 bytes produces a hard HTTP 500. The primary audits show a different failure mode: `JwtCookieSession.save()` throws `IOException`, but `SessionFilter$1.handleResult(Response)` catches it, logs `Failed to save session`, restores the old session, and returns the original downstream response. That makes overflow an operability problem that looks like silent state loss to the browser, not a fail-closed 500. Evidence: `JwtCookieSession.save` lines `291-297`; `SessionFilter$1.handleResult` lines `61-67`.

That wording error was already corrected in commit `b53c239`, which updated `.claude/rules/gotchas.md` to describe the failure mode as silent state loss rather than a hard 500. No further action is required.

## Refuted Claims - Action Required

### B4

- What the claim said: `target = ${attributes.openid}` causes OIDC data to be written to both session and attributes.
- What the source code actually shows: `OAuth2ClientFilter.fillTarget()` writes only to the configured target expression, while `OAuth2Utils.saveSession()` separately persists the compact OAuth2 session blob into `session[oauth2Key]`.
- Evidence:
- `OAuth2ClientFilter.fillTarget` lines `820-825` - `target.set(bindings(context, null), info)`
- `OAuth2Utils.saveSession` lines `147-152` - `session.put(sessionKey, oAuth2Session.toJson().getObject());`
- `OAuth2Session.toJson` lines `97-107` - persisted fields are only `crn`, `ce`, `s`, `arn`, `atr`, `ea`
- Files in this repo to update: `docs/deliverables/legacy-auth-patterns-definitive.md:337`, paragraph `Validated session note` in `Template-Based Integration`.
- Exact change:
- Replace the current sentence `Validated session note: when routes use browser-bound JwtSession, place TokenReferenceFilter.groovy immediately after OAuth2ClientFilter so the heavy oauth2:* entry is offloaded to Redis and the browser cookie keeps only a per-app token reference key (token_ref_id_appN on shared-cookie stacks, fallback token_ref_id) plus small identity markers.`
- With this replacement paragraph: `Validated session note: when routes use browser-bound JwtSession, place TokenReferenceFilter.groovy immediately after OAuth2ClientFilter so the heavy oauth2:* entry is offloaded to Redis and the browser cookie keeps only a per-app token reference key (token_ref_id_appN on shared-cookie stacks, fallback token_ref_id) plus small identity markers. OIDC data-model note: target = ${attributes.openid} is request-scoped output written by OAuth2ClientFilter.fillTarget(); it does not mirror data into session. Persisted session[oauth2Key] state is written separately by OAuth2Utils.saveSession(), so lookups such as session[oauth2Key].atr.id_token must use the persisted blob, while live user_info belongs under attributes.openid.`

### E1

- What the claim said: `clientEndpoint` is registered globally at the server level.
- What the source code actually shows: `OAuth2ClientFilter.filter()` handles login, callback, and logout by matching URIs inside the filter instance, and `RouteBuilder.setupRouteHandler()` installs filters per route. The architect primary audit also quotes `private Expression<String> clientEndpoint;`; that field quote has no line number in the primary audits, so the line-numbered proof point for the implementation claim remains the filter and route-builder methods.
- Evidence:
- `OAuth2ClientFilter.filter` lines `292-323` - route-local login/callback/logout matching happens inside the filter instance
- `RouteBuilder.setupRouteHandler` lines `156-192` - filters are installed per route
- `RouterHandler.handle` lines `347-355` - sorted routes are evaluated in route order
- `LexicographicalRouteComparator.compare` line `28` - `first.getId().compareTo(second.getId())`
- Architect v2 field quote - `private Expression<String> clientEndpoint;` (line number not available in the primary audits)
- Files in this repo to update: `.claude/rules/architecture.md:25`, section `## clientEndpoint namespace (MỖI app trong cùng OpenIG instance PHẢI unique)`.
- Exact change:
- Insert this paragraph immediately after the heading and before the endpoint table: `Why unique: OpenIG does not keep a server-global clientEndpoint registry. Each OAuth2ClientFilter instance matches its own login, callback, and logout URIs inside the route-local filter chain, so collisions happen when more than one route can match the same clientEndpoint and lexicographic route order selects the wrong route first.`

### F2

- What the claim said: `sessionTimeout` controls JWT `exp` and cookie `Max-Age`.
- What the source code actually shows: `JwtCookieSession.buildJwtCookie()` writes cookie expiry with `.setExpires(...)`, `JwtCookieSession` tracks session expiry under `_ig_exp`, and `getNewExpiryTime()` derives that expiry from the configured timeout. The primary audits do not show any `Max-Age` write path.
- Evidence:
- `JwtCookieSession.buildJwtCookie` lines `324-337` - `.setExpires(new Date(expiryTime.longValue()))`
- `JwtCookieSession.<field IG_EXP_SESSION_KEY>` lines `79-82` - `_ig_exp`
- `JwtCookieSession.getNewExpiryTime` lines `370-372` - expiry time is computed from the configured timeout
- `JwtSessionManager$Heaplet.create` lines `199-214` - `sessionTimeout` is wired into the session manager
- Files in this repo to update:
- `docs/deliverables/standard-gateway-pattern.md:77`, section `### 1. Revocation Contract`
- `docs/deliverables/standard-gateway-pattern.md:259-266`, section `### Session and revocation`
- Exact change:
- Insert this sentence after the paragraph at line `77`: `For OpenIG 6 JwtSession, sessionTimeout is enforced through the JWT _ig_exp claim plus the cookie Expires attribute; JwtCookieSession does not emit Max-Age, so tests and runbooks must validate Expires or decoded JWT expiry instead of asserting Max-Age.`
- Insert this checklist bullet after line `261`: `- [ ] For OpenIG 6 JwtSession, session lifetime checks validate cookie Expires or JWT _ig_exp rather than Max-Age, because JwtCookieSession.buildJwtCookie() writes Expires only.`

## Architecture Implications

- OpenIG maintains two distinct OIDC data planes: request-scoped `attributes.openid` from `OAuth2ClientFilter.fillTarget()` and persisted `session[oauth2Key]` state from `OAuth2Utils.saveSession()`. Evidence: `OAuth2ClientFilter.fillTarget` lines `820-825`; `OAuth2Utils.saveSession` lines `147-152`; `OAuth2Session.toJson` lines `97-107`.
- The Redis token-reference pattern is source-aligned, not accidental, because Groovy `.then { ... }` callbacks run before `SessionFilter` saves the session cookie. Evidence: `RouteBuilder.setupRouteHandler` lines `162-165`; `Filters.chainOf` lines `99-108`; `SessionFilter.filter` lines `53-57`; `SessionFilter$1.handleResult` lines `61-67`.
- `JwtSession` overflow is an operability problem, not a fail-closed 500 path; if the cookie grows past the limit, session state can be silently dropped while the downstream response still succeeds. Evidence: `JwtCookieSession.save` lines `291-297`; `SessionFilter$1.handleResult` lines `61-67`.
- `clientEndpoint` uniqueness is still mandatory, but the reason is route-local filter matching plus lexicographic route order, not a server-global registration table. Evidence: `OAuth2ClientFilter.filter` lines `292-323`; `RouteBuilder.setupRouteHandler` lines `156-192`; `RouterHandler.handle` lines `347-355`; `LexicographicalRouteComparator.compare` line `28`.
- Session lifetime semantics in OpenIG 6 are carried by JWT expiry state plus cookie `Expires`, while the heap object name `Session` remains a non-negotiable wiring constraint for `JwtSession`. Evidence: `Keys.<field SESSION_FACTORY_HEAP_KEY>` line `99`; `GatewayHttpApplication.create` lines `163-167`; `JwtCookieSession.<field IG_EXP_SESSION_KEY>` lines `79-82`; `JwtCookieSession.buildJwtCookie` lines `324-337`; `JwtCookieSession.getNewExpiryTime` lines `370-372`.
- Audit plans should reference `JwtSessionManager` and `RouterHandler`, not non-existent `JwtSessionFactory` or `Router.java`, when tracing session wiring and route order in this upstream tree. Evidence: Codex primary audit scope notes, lines `8-17`, naming the real upstream classes inspected.

## Open Action Items (for planning)

### ACT-1

- Exact file path: `docs/deliverables/legacy-auth-patterns-definitive.md`
- Verified section heading: `## Template-Based Integration` (heading line `329`)
- Verified current text to search: ``Validated session note: when routes use browser-bound `JwtSession`, place `TokenReferenceFilter.groovy` immediately after `OAuth2ClientFilter` so the heavy `oauth2:*` entry is offloaded to Redis and the browser cookie keeps only a per-app token reference key (`token_ref_id_appN` on shared-cookie stacks, fallback `token_ref_id`) plus small identity markers.``
- Verified line number: `337`
- Correct replacement: `Validated session note: when routes use browser-bound JwtSession, place TokenReferenceFilter.groovy immediately after OAuth2ClientFilter so the heavy oauth2:* entry is offloaded to Redis and the browser cookie keeps only a per-app token reference key (token_ref_id_appN on shared-cookie stacks, fallback token_ref_id) plus small identity markers. OIDC data-model note: target = ${attributes.openid} is request-scoped output written by OAuth2ClientFilter.fillTarget(); it does not mirror data into session. Persisted session[oauth2Key] state is written separately by OAuth2Utils.saveSession(), so lookups such as session[oauth2Key].atr.id_token must use the persisted blob, while live user_info belongs under attributes.openid.`
- Evidence citation: `OAuth2ClientFilter.fillTarget` lines `820-825`; `OAuth2Utils.saveSession` lines `147-152`; `OAuth2Session.toJson` lines `97-107`.
- Priority: MED

### ACT-2

- Exact file path: `.claude/rules/architecture.md`
- Verified section heading: `## clientEndpoint namespace (MỖI app trong cùng OpenIG instance PHẢI unique)` (heading line `25`)
- Verified current text to search: `| Stack | App | clientEndpoint | Keycloak client |`
- Verified line number: `27`
- Correct insertion: `Why unique: OpenIG does not keep a server-global clientEndpoint registry. Each OAuth2ClientFilter instance matches its own login, callback, and logout URIs inside the route-local filter chain, so collisions happen when more than one route can match the same clientEndpoint and lexicographic route order selects the wrong route first.`
- Evidence citation: `OAuth2ClientFilter.filter` lines `292-323`; `RouteBuilder.setupRouteHandler` lines `156-192`; `RouterHandler.handle` lines `347-355`; `LexicographicalRouteComparator.compare` line `28`.
- Priority: MED

### ACT-3

- Exact file path: `docs/deliverables/standard-gateway-pattern.md`
- Verified section heading (anchor 1): `### 1. Revocation Contract` (heading line `74`)
- Verified current text to search (anchor 1): ``What it is: Redis blacklist TTL MUST be greater than or equal to `JwtSession.sessionTimeout`. On Redis lookup failure, the gateway MUST fail closed for authenticated sessions by returning `503` or forcing re-authentication; it MUST NOT proxy the request onward. On Redis write failure during backchannel logout, the handler MUST return `5xx`, not `4xx`. The same `sid` key MUST be used on both the write path and the read path.``
- Verified line number (anchor 1): `77`
- Verified section heading (anchor 2): `### Session and revocation` (heading line `259`)
- Verified current text to search (anchor 2): ``- [ ] `BackchannelLogoutHandler` writes `blacklist:<sid>` with TTL greater than or equal to `JwtSession.sessionTimeout`.``
- Verified line number (anchor 2): `261`
- Correct insertion after line `77`: `For OpenIG 6 JwtSession, sessionTimeout is enforced through the JWT _ig_exp claim plus the cookie Expires attribute; JwtCookieSession does not emit Max-Age, so tests and runbooks must validate Expires or decoded JWT expiry instead of asserting Max-Age.`
- Correct insertion after line `261`: `- [ ] For OpenIG 6 JwtSession, session lifetime checks validate cookie Expires or JWT _ig_exp rather than Max-Age, because JwtCookieSession.buildJwtCookie() writes Expires only.`
- Evidence citation: `JwtCookieSession.buildJwtCookie` lines `324-337`; `JwtCookieSession.<field IG_EXP_SESSION_KEY>` lines `79-82`; `JwtCookieSession.getNewExpiryTime` lines `370-372`; `JwtSessionManager$Heaplet.create` lines `199-214`.
- Priority: LOW

## Evidence Index

| Evidence ID | OpenIG Class | Method | Lines | Claims | What it proves |
| --- | --- | --- | --- | --- | --- |
| EV-01 | `OAuth2Utils` | `buildUri` | `53-77` | `B1` | `clientEndpoint` is resolved against the original request URI before key generation |
| EV-02 | `OAuth2Utils` | `sessionKey` | `154-156` | `B1` | persisted OAuth2 session key is `oauth2:` plus the resolved URI |
| EV-03 | `OAuth2Session` | `extractIdToken` | `48-59` | `B2` | `id_token` is read from the access-token response model |
| EV-04 | `OAuth2Session` | `toJson` | `97-107` | `B2`, `B3`, `B4` | persisted blob contains `atr` and omits `user_info`; only `crn`, `ce`, `s`, `arn`, `atr`, `ea` are serialized |
| EV-05 | `OAuth2ClientFilter` | `fillTarget` | `790-827` | `B3` | live OIDC data, including `user_info`, is written to the configured target expression rather than the session blob |
| EV-06 | `OAuth2ClientFilter` | `fillTarget` | `820-825` | `B4` | `target.set(...)` writes only to the target expression |
| EV-07 | `OAuth2Utils` | `saveSession` | `147-152` | `B4` | persisted OAuth2 session blob is written through `session.put(...)` as a separate path |
| EV-08 | `RouteBuilder` | `setupRouteHandler` | `162-165` | `C2` | `SessionFilter` wraps the route chain |
| EV-09 | `Filters` | `chainOf` | `99-108` | `C2` | first filter in the chain is outermost |
| EV-10 | `Filters$2` | `filter` | `127` | `C2` | filter-order behavior preserves the outer wrapper semantics |
| EV-11 | `SessionFilter` | `filter` | `53-57` | `C2` | session save is attached as a result callback after downstream handling returns |
| EV-12 | `SessionFilter$1` | `handleResult` | `61-67` | `C2`, `A4` | session save happens in the result hook and overflow is logged/returned, not converted to a new 500 response |
| EV-13 | `JwtCookieSession` | `loadJwtSession` | `193-198` | `A1` | session data is loaded into the backing map before key enumeration |
| EV-14 | `JwtCookieSession` | `keySet` | `265-268` | `A1` | stored keys are exposed through `DirtySet` |
| EV-15 | `JwtCookieSession` | `clear` | `260-262` | `A2` | full session clear is supported and marks the session dirty |
| EV-16 | `JwtCookieSession` | `save` | `280-305` | `A2` | dirty session state is persisted on response |
| EV-17 | `JwtCookieSession` | `buildExpiredJwtCookie` | `320-322` | `A2`, `F1` | empty-session path emits an expired cookie and uses the configured cookie properties |
| EV-18 | `JwtCookieSession` | `remove` | `253-256` | `A3` | per-key removal is implemented directly |
| EV-19 | `JwtCookieSession` | `save` | `291-297` | `A4` | oversize cookie path throws `IOException` |
| EV-20 | `Keys` | `<field SESSION_FACTORY_HEAP_KEY>` | `99` | `A5` | default auto-wired session heap key is `Session` |
| EV-21 | `GatewayHttpApplication` | `create` | `163-167` | `A5` | OpenIG resolves the session manager through the `Session` heap key |
| EV-22 | `ScriptableHandler` | `handle` | `46-48` | `D1` | scripts receive the live `Request` object |
| EV-23 | `StaticRequestFilterTest` | `testFormAttributesPropagationWithPostMethod` | `231-238` | `D1` | form-urlencoded request bodies are readable with `request.getEntity().getString()` |
| EV-24 | `AbstractScriptableHeapObject` | `<field scriptGlobals>` | `174` | `D2` | `globals` is backed by `ConcurrentHashMap` |
| EV-25 | `AbstractScriptableHeapObject` | `enrichBindings` | `246-254` | `D2` | `globals` is exported into script bindings |
| EV-26 | `GroovyScriptableFilterTest` | `testGlobalsPersistedBetweenInvocations` | `253-267` | `D2` | persisted `globals` behavior is exercised by test coverage |
| EV-27 | `OAuth2ClientFilter` | `filter` | `292-323` | `E1` | login, callback, and logout matching happens inside the filter instance |
| EV-28 | `OAuth2ClientFilter$Heaplet` | `create` | `881-953` | `E1` | filter construction happens during route assembly; no server-global registration is shown |
| EV-29 | `RouteBuilder` | `setupRouteHandler` | `156-192` | `E1` | filters are attached per route |
| EV-30 | `RouterHandler` | `handle` | `347-355` | `E1`, `E2` | runtime route evaluation follows the sorted route set |
| EV-31 | `RouterHandler` | `onAddedFile` | `394-423` | `E2` | route IDs are derived from filenames |
| EV-32 | `RouterHandler` | `<field sorted>` | `123-124` | `E2` | routes are stored in a lexicographically ordered `TreeSet` |
| EV-33 | `LexicographicalRouteComparator` | `compare` | `28` | `E1`, `E2` | route precedence is based on route ID string comparison |
| EV-34 | `JwtCookieSession` | `buildJwtCookie` | `324-337` | `F1`, `F2` | `cookieDomain` becomes `Domain` and session expiry is emitted as cookie `Expires` |
| EV-35 | `JwtCookieSession` | `<field IG_EXP_SESSION_KEY>` | `79-82` | `F2` | JWT session expiry state is tracked under `_ig_exp` |
| EV-36 | `JwtCookieSession` | `getNewExpiryTime` | `370-372` | `F2` | expiry time is derived from the configured timeout |
| EV-37 | `JwtSessionManager$Heaplet` | `create` | `199-214` | `F2` | `sessionTimeout` is wired into the JWT session manager |
