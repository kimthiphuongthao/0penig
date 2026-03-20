# OpenIG Core Audit - Codex - 2026-03-20

Scope:
- Local files read from `stack-a/` and `stack-b/` exactly as requested.
- Upstream source fetched from `OpenIdentityPlatform/OpenIG` `master` via the requested raw GitHub URLs, then expanded with adjacent source files from the same repo for traceability.
- OpenIG delegates session save timing to the HTTP framework `SessionFilter`; that code is not in the OpenIG repo, so I inspected `org/openidentityplatform/commons/http-framework/core/3.0.2/core-3.0.2.jar` locally with `javap -l -c` for claims `[C2]` and `[A4]`.

GitHub search notes:
- No `openig-core` `JwtSessionFactory` file exists in the fetched repo tree.
- No `openig-core` `Router.java` file exists in the fetched repo tree.
- The relevant upstream classes are `org.forgerock.openig.jwt.JwtSessionManager` and `org.forgerock.openig.handler.router.RouterHandler`.

Upstream sources inspected:
- `https://raw.githubusercontent.com/OpenIdentityPlatform/OpenIG/master/openig-core/src/main/java/org/forgerock/openig/jwt/JwtCookieSession.java`
- `https://raw.githubusercontent.com/OpenIdentityPlatform/OpenIG/master/openig-core/src/main/java/org/forgerock/openig/script/AbstractScriptableHeapObject.java`
- `https://raw.githubusercontent.com/OpenIdentityPlatform/OpenIG/master/openig-oauth2/src/main/java/org/forgerock/openig/filter/oauth2/client/OAuth2ClientFilter.java`
- Additional upstream files used for verification: `OAuth2Utils.java`, `OAuth2Session.java`, `ScriptableFilter.java`, `ScriptableHandler.java`, `JwtSessionManager.java`, `RouterHandler.java`, `LexicographicalRouteComparator.java`, `RouteBuilder.java`, `GatewayHttpApplication.java`, `Route.java`, `LeftValueExpressionTest.java`, `GroovyScriptableFilterTest.java`, `StaticRequestFilterTest.java`.

### [B1] OAuth2 session key format
- Verdict: CONFIRM
- Evidence: `org.forgerock.openig.filter.oauth2.client.OAuth2Utils:buildUri:53-77`; `org.forgerock.openig.filter.oauth2.client.OAuth2Utils:sessionKey:154-156`; `org.forgerock.openig.filter.oauth2.client.OAuth2Utils:saveSession:147-152`
- Our code: `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy:135-140` enumerates `session.keySet()` and keeps `oauth2:` keys ending in the configured endpoint. `stack-a/openig_home/scripts/groovy/SloHandler.groovy:17-29` and `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:77-85` reconstruct the full key from host plus `clientEndpoint`.
- Match: YES
- Impact if REFUTE: OAuth2 session discovery would target the wrong slot, breaking logout lookup, blacklist lookup, and Redis restore/offload.

### [B2] `id_token` location under `atr`
- Verdict: CONFIRM
- Evidence: `org.forgerock.openig.filter.oauth2.client.OAuth2Session:extractIdToken:48-59`; `org.forgerock.openig.filter.oauth2.client.OAuth2Session:toJson:97-107`
- Our code: `stack-a/openig_home/scripts/groovy/SloHandler.groovy:26-29`, `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:83-86`, and `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy:67-75` all read `session[key].atr.id_token`.
- Match: YES
- Impact if REFUTE: SLO redirect construction and SID extraction would lose the ID token hint and fail to derive the logout session identifier.

### [B3] `user_info.sub` in the saved OAuth2 session
- Verdict: REFUTE
- Evidence: `org.forgerock.openig.filter.oauth2.client.OAuth2Session:toJson:97-107` serializes only `crn`, `ce`, `s`, `arn`, `atr`, and `ea`; `org.forgerock.openig.filter.oauth2.client.OAuth2ClientFilter:fillTarget:790-827` adds `user_info` only to the runtime `info` map before `target.set(...)`; `org.forgerock.openig.filter.oauth2.client.OAuth2ClientFilter:extractSessionInfo:829-853` builds session-derived info without `user_info`
- Our code: `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:83-86` does not read `user_info.sub`; it reads `atr.id_token`. The `user_info.sub` assumption exists in `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy:69-76`.
- Match: NO
- Impact if REFUTE: any code expecting `session[oauth2Key].user_info.sub` will usually get `null`. In this repo, the Jellyfin logout fallback must rely on `session['jellyfin_user_sub']` or decode `sub` from the ID token.

### [B4] `target = ${attributes.openid}` writes to both session and attributes
- Verdict: REFUTE
- Evidence: `org.forgerock.openig.filter.oauth2.client.OAuth2ClientFilter$Heaplet:create:927-929` wires the configured target expression; `org.forgerock.openig.filter.oauth2.client.OAuth2ClientFilter:fillTarget:820-825` writes only via `target.set(...)`; `org.forgerock.openig.filter.oauth2.client.OAuth2Utils:saveSession:147-152` is the separate session write path; `org.forgerock.openig.el.LeftValueExpressionTest:setAttribute:31-43` shows an `attributes.*` target updates the attributes context
- Our code: `stack-a/openig_home/config/routes/01-wordpress.json:32-46` sets `"target": "${attributes.openid}"`. `stack-a/openig_home/scripts/groovy/SloHandler.groovy:17-29` reads `session[oauth2Key].atr.id_token`, which comes from `saveSession(...)`, not from `attributes.openid`.
- Match: NO
- Impact if REFUTE: request-scoped `attributes.openid` still works, and `session[oauth2Key].atr.id_token` still works. The real breakage is anything expecting `target` data, especially `user_info`, to be mirrored into session.

### [C2] ScriptableFilter `.then { ... }` runs after JWT cookie serialization
- Verdict: REFUTE
- Evidence: `org.forgerock.openig.handler.router.RouteBuilder:setupRouteHandler:162-165` adds `newSessionFilter(sessionManager)` before route filters; `org.forgerock.http.filter.Filters:chainOf:99-108`; `org.forgerock.http.filter.Filters$2:filter:127` makes the first filter outermost; `org.forgerock.http.filter.SessionFilter:filter:53-57` attaches `thenOnResult(...)` only after downstream handling returns; `org.forgerock.http.filter.SessionFilter$1:handleResult:61-67` calls `sessionManager.save(...)` in that result hook
- Our code: `stack-a/openig_home/config/routes/01-wordpress.json:116-120` places `TokenReferenceFilterApp1` ahead of `OidcFilter`, and `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy:236-269` mutates `session` inside `next.handle(...).then { ... }`.
- Match: NO
- Impact if REFUTE: nothing breaks on this point. The opposite order is what the current Redis offload needs, and the source shows the offload mutation happens before JWT session save.

### [A1] `JwtCookieSession.keySet()` returns stored keys
- Verdict: CONFIRM
- Evidence: `org.forgerock.openig.jwt.JwtCookieSession:loadJwtSession:193-198`; `org.forgerock.openig.jwt.JwtCookieSession:keySet:265-268`
- Our code: `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy:137-140` and `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy:191-199` depend on enumerating session keys.
- Match: YES
- Impact if REFUTE: the `oauth2:*` discovery logic would miss stored entries and break Redis offload/restore.

### [A5] Default session heap name is `Session`
- Verdict: INCONCLUSIVE
- Evidence: `org.forgerock.openig.heap.Keys:<field SESSION_FACTORY_HEAP_KEY>:99` sets the default session heap key to `Session`; `org.forgerock.openig.http.GatewayHttpApplication:create:163-167` only auto-wires that heap key to override the container session; `org.forgerock.openig.handler.router.RouteBuilder:setupRouteHandler:162-165` uses only an explicit route `"session"` reference. OpenIG source confirms fallback to the container session, but not the container brand.
- Our code: `stack-a/openig_home/config/config.json:19-30` correctly declares `"name": "Session", "type": "JwtSession"`.
- Match: PARTIAL
- Impact if REFUTE: naming the heap object `JwtSession` without also wiring `"session": "JwtSession"` would leave the gateway on the container session implementation instead of the JWT-cookie session manager.

### [D2] `globals` is a `ConcurrentHashMap` and supports atomic `compute()`
- Verdict: CONFIRM
- Evidence: `org.forgerock.openig.script.AbstractScriptableHeapObject:<field scriptGlobals>:174`; `org.forgerock.openig.script.AbstractScriptableHeapObject:enrichBindings:246-254`; `org.forgerock.openig.filter.GroovyScriptableFilterTest:testGlobalsPersistedBetweenInvocations:253-267`
- Our code: `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:117-128` uses `globals.compute('jwks_cache', ...)` for JWKS caching.
- Match: YES
- Impact if REFUTE: concurrent backchannel logout requests could race and corrupt or duplicate the JWKS cache.

### [A2] `session.clear()` fully clears `JwtCookieSession`
- Verdict: CONFIRM
- Evidence: `org.forgerock.openig.jwt.JwtCookieSession:clear:260-262`; `org.forgerock.openig.jwt.JwtCookieSession:save:280-305`; `org.forgerock.openig.jwt.JwtCookieSession:buildExpiredJwtCookie:320-322`
- Our code: `stack-a/openig_home/scripts/groovy/SloHandler.groovy:34`, `stack-a/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:139-145`, `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy:114-115`, and `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy:203-207` rely on `session.clear()`.
- Match: YES
- Impact if REFUTE: logout, blacklist redirect, and token-reference stripping would leave stale keys behind.

### [A3] `session.remove(key)` is supported
- Verdict: CONFIRM
- Evidence: `org.forgerock.openig.jwt.JwtCookieSession:remove:253-256`
- Our code: `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy:92-99` and `stack-b/openig_home/scripts/groovy/JellyfinTokenInjector.groovy:125-135` remove multiple Jellyfin session keys on 401.
- Match: YES
- Impact if REFUTE: Jellyfin token cleanup would leak stale session state and cause repeated invalid-token reuse.

### [A4] `JwtCookieSession` over 4096 bytes returns hard HTTP 500
- Verdict: REFUTE
- Evidence: `org.forgerock.openig.jwt.JwtCookieSession:save:291-297` throws `IOException` when the JWT cookie value exceeds 4096 chars; `org.forgerock.http.filter.SessionFilter$1:handleResult:61-67` catches `IOException`, logs `Failed to save session`, restores the old session, and returns without replacing the response
- Our code: `stack-a/openig_home/scripts/groovy/TokenReferenceFilter.groovy:253-256` offloads the OAuth2 blob before session save to avoid hitting the cookie-size limit.
- Match: NO
- Impact if REFUTE: oversize session failures are silent from the client perspective. The response still goes out, but the session is not saved, which is harder to diagnose than a hard 500.

### [D1] `request.entity.getString()` works for form-urlencoded request bodies in scripts
- Verdict: CONFIRM
- Evidence: `org.forgerock.openig.handler.ScriptableHandler:handle:46-48` exposes the live `Request` object to scripts; `org.forgerock.openig.filter.StaticRequestFilterTest:testFormAttributesPropagationWithPostMethod:231-238` shows a `Content-Type: application/x-www-form-urlencoded` request body is readable via `request.getEntity().getString()`
- Our code: `stack-a/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:328-337` parses `logout_token` from `request.entity?.getString()`.
- Match: YES
- Impact if REFUTE: backchannel logout body parsing would fail before JWT validation even starts.

### [E1] `clientEndpoint` registers globally at server level
- Verdict: REFUTE
- Evidence: `org.forgerock.openig.filter.oauth2.client.OAuth2ClientFilter:filter:292-323` handles `login`, `callback`, and `logout` by matching URIs inside the filter itself; `org.forgerock.openig.filter.oauth2.client.OAuth2ClientFilter$Heaplet:create:881-953` only constructs the filter and does not register a global endpoint; `org.forgerock.openig.handler.router.RouteBuilder:setupRouteHandler:156-192` installs filters per route
- Our code: `stack-a/openig_home/config/routes/01-wordpress.json:32-46` declares `clientEndpoint` inside the route-local `OidcFilter`.
- Match: NO
- Impact if REFUTE: endpoint behavior depends on route matching and filter placement, not on a server-global registration table. Route conditions and ordering still matter.

### [E2] Route evaluation order is filename alphabetical order
- Verdict: CONFIRM
- Evidence: `org.forgerock.openig.handler.router.RouterHandler:onAddedFile:394-423` derives `routeId` from the filename without `.json`; `org.forgerock.openig.handler.router.RouterHandler:<field sorted>:123-124` stores routes in a `TreeSet` with `LexicographicalRouteComparator`; `org.forgerock.openig.handler.router.LexicographicalRouteComparator:compare:28`; `org.forgerock.openig.handler.router.RouterHandler:handle:347-355` iterates that sorted set
- Our code: the route file itself is named `stack-a/openig_home/config/routes/01-wordpress.json`, which uses filename prefixing for order control.
- Match: YES
- Impact if REFUTE: route precedence assumptions based on numeric filename prefixes would be unreliable.

### [F1] `cookieDomain` maps to the `Domain` attribute in `Set-Cookie`
- Verdict: CONFIRM
- Evidence: `org.forgerock.openig.jwt.JwtCookieSession:buildExpiredJwtCookie:320-322`; `org.forgerock.openig.jwt.JwtCookieSession:buildJwtCookie:332-337`
- Our code: `stack-a/openig_home/config/config.json:23-29` sets `"cookieDomain": ".sso.local"`.
- Match: YES
- Impact if REFUTE: cross-subdomain SSO cookies would not scope correctly across `*.sso.local`.

### [F2] `sessionTimeout` controls JWT `exp` and cookie `Max-Age`
- Verdict: REFUTE
- Evidence: `org.forgerock.openig.jwt.JwtCookieSession:<field IG_EXP_SESSION_KEY>:79-82`; `org.forgerock.openig.jwt.JwtCookieSession:buildJwtCookie:324-337`; `org.forgerock.openig.jwt.JwtCookieSession:getNewExpiryTime:370-372`; `org.forgerock.openig.jwt.JwtSessionManager$Heaplet:create:199-214`. The source uses `_ig_exp` inside the JWT session and `Cookie.setExpires(...)`; there is no `Max-Age` write path in `JwtCookieSession`.
- Our code: `stack-a/openig_home/config/config.json:23-25` sets `"sessionTimeout": "30 minutes"` and expects session lifetime control from the JWT cookie session manager.
- Match: PARTIAL
- Impact if REFUTE: expiry still works, but through JWT `_ig_exp` and cookie `Expires`, not `Max-Age`. Tests or tooling that assert `Max-Age` specifically will be wrong.

## Summary Table

| ID | Verdict | Match | Short note |
| --- | --- | --- | --- |
| B1 | CONFIRM | YES | Session key is `oauth2:` plus the resolved `clientEndpoint` URI |
| B2 | CONFIRM | YES | `id_token` lives under `atr.id_token` in the saved OAuth2 session |
| B3 | REFUTE | NO | `user_info` is not serialized into the `oauth2:` session blob |
| B4 | REFUTE | NO | `target=${attributes.openid}` writes attributes, not a second session copy |
| C2 | REFUTE | NO | Script `.then { ... }` runs before `SessionFilter` saves the session |
| A1 | CONFIRM | YES | `JwtCookieSession.keySet()` exposes the stored map keys |
| A5 | INCONCLUSIVE | PARTIAL | Default key is `Session`; OpenIG source says container session fallback, not Tomcat by name |
| D2 | CONFIRM | YES | `globals` is backed by a `ConcurrentHashMap` |
| A2 | CONFIRM | YES | `session.clear()` empties the JWT session and emits an expired cookie |
| A3 | CONFIRM | YES | `session.remove(key)` is implemented directly |
| A4 | REFUTE | NO | Oversize JWT save is logged and dropped, not converted into a hard 500 |
| D1 | CONFIRM | YES | Form-urlencoded request bodies are readable via `request.entity.getString()` |
| E1 | REFUTE | NO | `clientEndpoint` behavior is route-local, not globally registered |
| E2 | CONFIRM | YES | Route order comes from lexicographic filename-derived route IDs |
| F1 | CONFIRM | YES | `cookieDomain` becomes cookie `Domain` |
| F2 | REFUTE | PARTIAL | `sessionTimeout` drives JWT expiry and cookie `Expires`, not `Max-Age` |

## Critical Findings

- `session[oauth2Key].user_info.sub` is not a valid OpenIG persistence assumption. `OAuth2ClientFilter` only puts `user_info` into the runtime target map; it does not serialize it into the saved OAuth2 session. In this repo, `SloHandlerJellyfin.groovy:69-76` is the risky consumer.
- `target=${attributes.openid}` is not the reason SLO can still read `session[oauth2Key].atr.id_token`. That read works because `OAuth2Utils.saveSession(...)` persists the OAuth2 session separately under the `oauth2:` key.
- The Redis token-reference design is not broken by response timing. `TokenReferenceFilter.groovy:236-269` runs before `SessionFilter` persists the session, so stripping `oauth2:*` keys in `.then { ... }` is captured by the final cookie save.
- Oversized JWT session cookies do not fail closed with a hard 500. The HTTP framework logs `Failed to save session` and returns the original response, which makes oversize-session problems look like silent state loss.
- The upstream repo currently has no `JwtSessionFactory` or `Router.java`. The real classes to audit are `JwtSessionManager` and `RouterHandler`.
