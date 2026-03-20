# OpenIG 6 SSO/SLO Final Audit Report
**Date:** 2026-03-20

16 claims evaluated: 11 confirmed, 5 refuted, and all conflicts resolved. `docs/audit/2026-03-20-openig-conflict-verification.md` is the final tie-breaker for `B4`, `A4`, and `F2`.

## Final Verdict Table

| Claim ID | Priority | Final Verdict | Evidence Source | Runtime Impact | Action Required |
| --- | --- | --- | --- | --- | --- |
| B1 | CRITICAL | CONFIRM | `OAuth2Utils.buildUri()` + `OAuth2Utils.sessionKey()` -> `return "oauth2:" + clientEndpoint;` | NONE | NONE |
| B2 | CRITICAL | CONFIRM | `OAuth2Session.toJson()` -> `putIfNotNullOrEmpty(json, "atr", accessTokenResponse.getObject());` | NONE | NONE |
| B3 | CRITICAL | REFUTE | `OAuth2Session.toJson()` persists only `crn`, `ce`, `s`, `arn`, `atr`, `ea`; no `user_info` field | NONE | DONE |
| B4 | CRITICAL | REFUTE | `OAuth2ClientFilter.fillTarget()` -> `target.set(bindings(context, null), info);`; `OAuth2Utils.saveSession()` -> `session.put(sessionKey, oAuth2Session.toJson().getObject());` | NONE | DOC-FIX |
| C2 | CRITICAL | CONFIRM | `RouteBuilder.setupRouteHandler()` adds `newSessionFilter(sessionManager)` before route filters; `SessionFilter$1.handleResult(Response)` saves after downstream completion | NONE | NONE |
| A1 | HIGH | CONFIRM | `JwtCookieSession.keySet()` -> `return new DirtySet<>(super.keySet(), this);` | NONE | NONE |
| A2 | MED | CONFIRM | `JwtCookieSession.clear()` -> `dirty = true; super.clear();` | NONE | NONE |
| A3 | MED | CONFIRM | `JwtCookieSession.remove()` -> `dirty = true; return super.remove(key);` | NONE | NONE |
| A4 | MED | REFUTE | `JwtCookieSession.save()` throws `IOException`; CHF `SessionFilter$1.handleResult(Response)` bytecode lines `35-59` logs `Failed to save session`, restores old session, returns | LOW | DONE |
| A5 | HIGH | CONFIRM | `Keys.SESSION_FACTORY_HEAP_KEY` = `"Session"`; `GatewayHttpApplication.create()` resolves `heap.get("Session", SessionManager.class)` | NONE | NONE |
| D1 | MED | CONFIRM | `ScriptableHandler.handle()` exposes `Request`; `StaticRequestFilterTest.testFormAttributesPropagationWithPostMethod()` proves `request.getEntity().getString()` on form bodies | NONE | NONE |
| D2 | HIGH | CONFIRM | `AbstractScriptableHeapObject` field `private final Map<String, Object> scriptGlobals = new ConcurrentHashMap<>();` | NONE | NONE |
| E1 | MED | REFUTE | `OAuth2ClientFilter` instance field `private Expression<String> clientEndpoint;`; `filter()` handles callback/login/logout inside the filter instance; `RouteBuilder.setupRouteHandler()` installs filters per route | NONE | DOC-FIX |
| E2 | MED | CONFIRM | `RouterHandler` field `new TreeSet<>(new LexicographicalRouteComparator())`; `LexicographicalRouteComparator.compare()` -> `first.getId().compareTo(second.getId())` | NONE | NONE |
| F1 | MED | CONFIRM | `JwtCookieSession.buildJwtCookie()` -> `.setDomain(cookieDomain)` | NONE | NONE |
| F2 | MED | REFUTE | `JwtCookieSession.buildJwtCookie()` -> `.setExpires(new Date(expiryTime.longValue()))`; no `Max-Age` write path | NONE | DOC-FIX |

## Confirmed Claims (no action needed)

- `B1`: `OAuth2Utils.buildUri()` resolves the configured `clientEndpoint` against the original request URI, and `OAuth2Utils.sessionKey()` stores that resolved URI under the `oauth2:` prefix. This confirms the current `session.keySet()` plus suffix-match discovery logic is aligned with upstream behavior.
- `B2`: `OAuth2Session.toJson()` writes the token response map under `atr`, so `session[oauth2Key].atr.id_token` is the correct lookup path. That is why the current SLO and blacklist handlers can recover the ID token reliably from persisted OAuth2 session state.
- `C2`: `RouteBuilder.setupRouteHandler()` wraps the route with `SessionFilter`, and the save happens after downstream promise callbacks complete. That confirms the Redis token-reference offload works because the `.then { ... }` session mutations are persisted in the final cookie.
- `A1`: `JwtCookieSession.keySet()` returns a `DirtySet` over the stored backing-map keys. That makes dynamic discovery of `oauth2:*` entries a supported behavior, not a Groovy accident.
- `A2`: `JwtCookieSession.clear()` marks the session dirty and empties the backing map. That is the correct primitive for logout and blacklist enforcement when the entire browser session must be invalidated.
- `A3`: `JwtCookieSession.remove()` is implemented directly and marks the session dirty before removing the key. That confirms per-key cleanup logic is safe for adapter-specific state such as Jellyfin token markers.
- `A5`: `Keys.SESSION_FACTORY_HEAP_KEY` is hardcoded to `"Session"`, and `GatewayHttpApplication.create()` resolves that heap name. That is why the lab must keep the heap object named `Session` to stay on `JwtSession` instead of falling back to the container session.
- `D1`: `ScriptableHandler.handle()` exposes the live `Request`, and the framework test proves form-urlencoded bodies can be read with `request.entity.getString()`. That validates the backchannel logout handler's request-body parsing path.
- `D2`: `globals` is backed by a `ConcurrentHashMap`, so `compute()` is atomic. That makes the current JWKS cache pattern correct under concurrent logout traffic.
- `E2`: `RouterHandler` keeps routes in a `TreeSet` ordered by `LexicographicalRouteComparator`, which compares route IDs derived from filenames. That confirms filename prefixes remain the right way to control route precedence.
- `F1`: `JwtCookieSession.buildJwtCookie()` maps `cookieDomain` directly to the cookie `Domain` attribute. That is why the current `.sso.local` cross-subdomain session scope works as intended.

## Refuted Claims — Already Fixed

### B3

The claim said the persisted OAuth2 session blob exposes `session[oauth2Key].user_info.sub`. `OAuth2Session.toJson()` proves otherwise because it serializes only `crn`, `ce`, `s`, `arn`, `atr`, and `ea`, so the `user_info` read path in `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy` was dead code.

That dead code was removed in commit `b53c239`, which deleted the stale `user_info` session read from `stack-b/openig_home/scripts/groovy/SloHandlerJellyfin.groovy`. No further action is required.

### A4

The claim said a `JwtCookieSession` over 4096 bytes produces a hard HTTP 500. `JwtCookieSession.save()` does throw `IOException`, but the definitive CHF bytecode check of `SessionFilter$1.handleResult(Response)` shows the framework catches it, logs `Failed to save session`, restores the old session, and returns the original downstream response.

That wording error was already corrected in commit `b53c239`, which updated `.claude/rules/gotchas.md` to describe the failure mode as silent state loss rather than a hard 500. No further action is required.

## Refuted Claims — Action Required

### B4

- What the claim said: `target = ${attributes.openid}` causes OIDC data to be written to both session and attributes.
- What the source code actually shows: `OAuth2ClientFilter.fillTarget()` writes only to the configured target expression with `target.set(bindings(context, null), info);`, while `OAuth2Utils.saveSession()` persists the compact OAuth2 session blob separately with `session.put(sessionKey, oAuth2Session.toJson().getObject());`.
- Current state: the runtime code already uses the two-path model correctly. `stack-a/openig_home/config/routes/01-wordpress.json` sets `"target": "${attributes.openid}"`, while `stack-a/openig_home/scripts/groovy/SloHandler.groovy` reads the persisted session blob at `session[oauth2Key].atr.id_token`; the conflicting statement survives only in historical source audits such as `docs/audit/2026-03-20-openig-core-audit-gemini.md` and `docs/audit/2026-03-20-openig-core-audit-architect.md`.
- Required action: update `docs/deliverables/legacy-auth-patterns-definitive.md` to add a short OIDC data-model note near the validated `TokenReferenceFilter` session pattern: `attributes.openid` is request-scoped output from `fillTarget()`, and persisted `session[oauth2Key]` state comes from `OAuth2Utils.saveSession()`. Do not rewrite the raw audit input files.

### E1

- What the claim said: `clientEndpoint` is registered globally at the server level.
- What the source code actually shows: `OAuth2ClientFilter` keeps `clientEndpoint` as an instance field (`private Expression<String> clientEndpoint;`), and `OAuth2ClientFilter.filter()` handles login, callback, and logout by matching URIs inside that filter instance. `RouteBuilder.setupRouteHandler()` installs filters per route, so collisions are caused by route-local matching and route order, not by a server-global registry.
- Current state: the running gateway config is already correct because each app has a unique `clientEndpoint`. `.claude/rules/architecture.md` already lists a unique namespace for `/openid/app1` through `/openid/app6`, but it does not yet explain the actual collision mechanism; the incorrect "global registration" description remains only in historical audit prose, chiefly `docs/audit/2026-03-20-openig-core-audit-gemini.md`.
- Required action: update `.claude/rules/architecture.md` under `clientEndpoint namespace` to state explicitly that uniqueness is required because `OAuth2ClientFilter` matching is route-local and route-order dependent, not because OpenIG maintains a server-global `clientEndpoint` registry.

### F2

- What the claim said: `sessionTimeout` controls JWT `exp` and cookie `Max-Age`.
- What the source code actually shows: `JwtCookieSession.buildJwtCookie()` sets cookie expiry with `.setExpires(new Date(expiryTime.longValue()))`, and the session lifetime is tracked through the JWT session expiry state. The claim is false because there is no `Max-Age` write path in `JwtCookieSession`.
- Current state: the runtime configuration is correct because the stacks set `sessionTimeout` and OpenIG enforces expiry, but the exact cookie attribute is easy to misstate. The remaining incorrect wording is in historical audits such as `docs/audit/2026-03-20-openig-core-audit-gemini.md` and `docs/audit/2026-03-20-openig-core-audit-architect.md`.
- Required action: update `docs/deliverables/standard-gateway-pattern.md` in the session-lifetime guidance to say that `JwtSession.sessionTimeout` drives JWT expiry plus cookie `Expires`, and that tests or runbooks must not assert `Max-Age` for this OpenIG 6 session path.

## Architecture Implications

- OpenIG maintains two distinct OIDC data planes: request-scoped `attributes.openid` from `OAuth2ClientFilter.fillTarget()` and persisted `session[oauth2Key]` state from `OAuth2Utils.saveSession()`.
- The Redis token-reference pattern is source-aligned, not accidental, because Groovy `.then { ... }` callbacks run before `SessionFilter` saves the session cookie.
- `JwtSession` overflow is an operability problem, not a fail-closed 500 path; if the cookie grows past the limit, session state can be silently dropped while the downstream response still succeeds.
- `clientEndpoint` uniqueness is still mandatory, but the reason is route-local filter matching plus lexicographic route order, not a server-global registration table.
- Session lifetime semantics in OpenIG 6 are carried by JWT expiry state plus cookie `Expires`, while the heap object name `Session` remains a non-negotiable wiring constraint for `JwtSession`.

## Open Action Items (for planning)

1. `ACT-1`
   File to change: `docs/deliverables/legacy-auth-patterns-definitive.md`
   What to change: add a concise OIDC data-model note stating that `target = ${attributes.openid}` writes request attributes only, while persisted `session[oauth2Key]` data is written separately by `OAuth2Utils.saveSession()`.
   Priority: MED
2. `ACT-2`
   File to change: `.claude/rules/architecture.md`
   What to change: extend the `clientEndpoint namespace` section so it explains the real collision mechanism: route-local `OAuth2ClientFilter` matching plus route order, not a server-global registry.
   Priority: MED
3. `ACT-3`
   File to change: `docs/deliverables/standard-gateway-pattern.md`
   What to change: add a session-lifetime clarification that `sessionTimeout` maps to JWT expiry plus cookie `Expires`, and remove any future `Max-Age` expectations from tests and runbooks.
   Priority: LOW
