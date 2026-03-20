# OpenIG 6 SSO/SLO Core Audit Synthesis

This report consolidates four independent audits:
- `docs/audit/2026-03-20-openig-core-audit-codex.md`
- `docs/audit/2026-03-20-openig-core-audit-gemini.md`
- `docs/audit/2026-03-20-openig-core-audit-architect.md`
- `docs/audit/2026-03-20-openig-core-audit-architect-v2.md`

Note: the task text says "15 claims", but the provided claim list contains 16 IDs. This synthesis covers all 16 listed claims.

Method used for consolidation:
- Majority vote wins when 3 or 4 agents align.
- If no 3-agent majority exists, the tie-breaker is the required evidence hierarchy: Architect v2 > Codex > Architect v1 > Gemini.
- `C2` is normalized at the behavior level, because the source audits use inverted wording while agreeing on the actual execution order.

## 1. Per-claim synthesis table

| Claim ID | Priority | Codex | Gemini | Architect v1 | Architect v2 | Final Verdict | Conflict? |
| --- | --- | --- | --- | --- | --- | --- | --- |
| B1 | CRITICAL | CONFIRM | CONFIRM | CONFIRM | REFUTE (partial) | CONFIRM | YES |
| B2 | CRITICAL | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| B3 | CRITICAL | REFUTE | CONFIRM | INCONCLUSIVE | REFUTE | REFUTE | YES |
| B4 | CRITICAL | REFUTE | CONFIRM | CONFIRM | REFUTE | REFUTE | YES |
| C2 | CRITICAL | CONFIRM* | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| A1 | HIGH | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| A2 | MED | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| A3 | MED | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| A4 | MED | REFUTE | CONFIRM | CONFIRM | REFUTE (partial) | REFUTE | YES |
| A5 | HIGH | INCONCLUSIVE | CONFIRM | CONFIRM | CONFIRM | CONFIRM | YES |
| D1 | MED | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| D2 | HIGH | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| E1 | MED | REFUTE | CONFIRM | INCONCLUSIVE | REFUTE | REFUTE | YES |
| E2 | MED | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| F1 | MED | CONFIRM | CONFIRM | CONFIRM | CONFIRM | CONFIRM | NO |
| F2 | MED | REFUTE | CONFIRM | CONFIRM | REFUTE (partial) | REFUTE | YES |

\* `C2` is behavior-normalized. Codex reported `REFUTE` only because it evaluated the inverted wording "after JWT cookie serialization"; its traced execution order agrees that `.then()` mutations are persisted because session save happens later.

## 2. Per-claim detail

### B1
All four audits agree that the OAuth2 session entry is stored under an `oauth2:` prefix tied to the resolved `clientEndpoint` URI, so the current key-discovery approach is fundamentally correct. The only divergence is Architect v2, which partially refutes the simplified wording because `OAuth2Utils.sessionKey()` uses `buildUri()` and URI resolution rather than naive string concatenation. Codex and Architect v1 trace the same code path but accept the shorthand description because the effective key still resolves to the expected full URI. Final verdict: CONFIRM, with the strongest evidence coming from `OAuth2Utils.sessionKey()` plus `OAuth2Utils.buildUri()` as cited by Architect v2 and Codex.

### B2
All four audits agree that the persisted `id_token` lives at `session[oauth2Key].atr.id_token`. There is no substantive disagreement here, only a difference in how explicitly each audit cites the serialization path. Architect v2, Architect v1, and Codex all trace `OAuth2Session.toJson()` to the `atr` object, and Gemini reaches the same conclusion more broadly. Final verdict: CONFIRM, strongest evidence `OAuth2Session.toJson()` writing the access token response map under `atr`.

### B3
The audits agree that the implementation still has a viable way to derive the subject for Jellyfin logout, but they do not agree that `user_info` is serialized into the saved OAuth2 session blob. Gemini confirms the claim broadly and Architect v1 leaves it inconclusive while leaning on runtime behavior, whereas Codex and Architect v2 both trace `OAuth2Session.toJson()` and show that `user_info` is absent from the persisted session entry. Those two stronger audits also separate `OAuth2ClientFilter.fillTarget()` from `OAuth2Utils.saveSession()`, explaining why `attributes.openid.user_info` can exist even when `session[oauth2Key].user_info` does not. Final verdict: REFUTE, with the strongest evidence in Architect v2's exact serialization-path split backed by Codex.

### B4
All four audits agree on the observable outcome that OIDC data is available in request attributes during the filter chain and that session-resident OAuth2 data remains available for later handlers. They diverge on mechanism: Gemini and Architect v1 call that effective dual availability a confirmation, while Codex and Architect v2 refute the claim because `target = ${attributes.openid}` itself writes only to attributes and the session write comes from a separate `saveSession()` path. The disagreement is therefore about causal wording, not whether both locations can contain useful data. Final verdict: REFUTE, with Architect v2 providing the strongest evidence by quoting both `OAuth2ClientFilter.fillTarget()` and `OAuth2Utils.saveSession()`.

### C2
All four audits agree on the actual behavior that matters: mutations made in the Groovy `.then()` callback are included in the final saved session, so the Redis token-reference offload remains valid. The apparent disagreement is purely wording, because Codex refuted a version of the claim that said `.then()` ran after serialization, while Architect v1 and v2 state directly that `.then()` runs before `SessionFilter` saves the session. Gemini describes the same response-phase behavior without tracing the filter chain as deeply. Final verdict: CONFIRM at the behavior level, with the strongest evidence coming from the `RouteBuilder` plus `SessionFilter` execution order traced by Codex and Architect v2.

### A1
All four audits agree that `JwtCookieSession` exposes stored keys through `keySet()` and that Groovy can enumerate them safely. There is no substantive divergence beyond depth of citation. Architect v1, Architect v2, and Codex all point directly to `JwtCookieSession.keySet()`, while Gemini relies on the class's `Map` behavior more generally. Final verdict: CONFIRM, strongest evidence the explicit `keySet()` implementation returning a `DirtySet` over `super.keySet()`.

### A2
All four audits agree that `session.clear()` empties the `JwtCookieSession` contents. The only variation is explanatory detail: Codex and the Architect reports connect `clear()` to the later expired-cookie save path, while Gemini stays at the `Map`-semantics level. The conclusion is still uniform across all four audits. Final verdict: CONFIRM, strongest evidence `JwtCookieSession.clear()` together with the empty-session branch in `save()`.

### A3
All four audits agree that `session.remove(key)` is supported directly on `JwtCookieSession`. There is no substantive disagreement. Codex and both Architect passes cite the concrete `remove()` override, while Gemini treats it as inherited `Map` behavior. Final verdict: CONFIRM, strongest evidence the explicit `JwtCookieSession.remove()` implementation.

### A4
All four audits agree that OpenIG does not truncate an oversized JWT session cookie and that crossing the 4096-character threshold is a failure path. They diverge on client-visible behavior because Gemini and Architect v1 stop at `JwtCookieSession.save()` and infer a hard HTTP 500, while Codex traces the underlying CHF `SessionFilter` and shows the `IOException` is caught before the original downstream response is returned. Architect v2 accepts that deeper trace and independently aligns with Codex, making this a clear evidence-quality split rather than a genuine stalemate. Final verdict: REFUTE, with the strongest evidence in Codex's direct inspection of `org.forgerock.http.filter.SessionFilter.handleResult()`.

### A5
All four audits agree operationally that the heap object must be named `Session` for `JwtSession` to be auto-wired as the default session manager. Codex is the only outlier because it marks the claim inconclusive at the edge of the fallback behavior, distinguishing "the heap key is `Session`" from "the fallback implementation is a specific container brand." Gemini and both Architect passes flatten that nuance into a straight confirmation because the project-relevant requirement is still the same. Final verdict: CONFIRM, strongest evidence Architect v2's pairing of `Keys.SESSION_FACTORY_HEAP_KEY = "Session"` with `GatewayHttpApplication.create()`.

### D1
All four audits agree that `request.entity.getString()` works for form-urlencoded request bodies in scripts and handlers. The only difference is source depth: Codex cites a specific framework test, the Architect reports rely on the framework API behavior, and Gemini gives a higher-level confirmation with a usage caveat for large bodies. None of the audits dispute the implementation assumption. Final verdict: CONFIRM, strongest evidence Codex's citation of the form-body propagation test plus the live `Request` binding.

### D2
All four audits agree that `globals` is backed by a `ConcurrentHashMap` and therefore supports atomic `compute()` usage. There is no substantive disagreement. The strongest audits all point to `AbstractScriptableHeapObject` creating `scriptGlobals` as a `ConcurrentHashMap`, and Gemini reaches the same conclusion more briefly. Final verdict: CONFIRM, strongest evidence `AbstractScriptableHeapObject` plus the script binding path.

### E1
All four audits agree on the practical guardrail that `clientEndpoint` values must stay unique to avoid callback or logout collisions. They diverge on mechanism: Gemini describes a global registration, Architect v1 says there is no formal registry but the first matching route still creates the same effect, and Codex plus Architect v2 trace `OAuth2ClientFilter` as a per-route filter instance with no server-level registry at all. That makes this a wording and source-path disagreement, not an operational disagreement about uniqueness. Final verdict: REFUTE, with the strongest evidence in Architect v2's direct inspection of the `clientEndpoint` instance field and absence of any global table, corroborated by Codex's route-local analysis.

### E2
All four audits agree that route evaluation order follows lexicographic filename-derived route IDs. There is no substantive conflict here. Codex and both Architect passes cite `RouterHandler` and `LexicographicalRouteComparator` directly, while Gemini summarizes the same behavior using the older `Router` label. Final verdict: CONFIRM, strongest evidence the `TreeSet` plus comparator path in `RouterHandler`.

### F1
All four audits agree that `cookieDomain` becomes the cookie `Domain` attribute in `Set-Cookie`. The only variation is how much code each audit cites. Codex and Architect v2 point directly to the cookie builder, Architect v1 traces the manager-to-cookie flow, and Gemini states the mapping directly. Final verdict: CONFIRM, strongest evidence `JwtCookieSession.buildJwtCookie().setDomain(cookieDomain)`.

### F2
All four audits agree that `sessionTimeout` controls session lifetime semantics, but they diverge on the exact cookie attribute used to express that lifetime. Gemini and Architect v1 describe the browser lifetime generically and therefore confirm the claim, while Codex and Architect v2 trace `JwtCookieSession.buildJwtCookie()` and show it sets `Expires`, not `Max-Age`, while also driving the JWT expiry state. This is another evidence-depth split where the strongest code-level traces outweigh the broader summaries. Final verdict: REFUTE, with the strongest evidence in Architect v2's exact `setExpires(...)` snippet backed by Codex's `JwtSessionManager` and `_ig_exp` tracing.

## 3. Conflict register

### B1
Divergence: Architect v2 says the claim is only partially correct because the key is produced by URI resolution in `buildUri()`, while the other three audits accept the shorthand description `oauth2:<full-request-URL>/<clientEndpoint>`. The strongest evidence is Architect v2, because it cites both `OAuth2Utils.sessionKey()` and `OAuth2Utils.buildUri()` and therefore explains the mechanism, not just the resulting string shape. Recommended action: ACCEPTED.

### B3
Divergence: Gemini says the `user_info` path is confirmed, Architect v1 says it works in practice but leaves source-level persistence inconclusive, and Codex plus Architect v2 say `user_info` is never serialized into the saved OAuth2 session blob. The strongest evidence is Architect v2, because it separates `OAuth2Session.toJson()`, `OAuth2ClientFilter.fillTarget()`, and `OAuth2Utils.saveSession()` with exact snippets, and Codex independently reaches the same result. Recommended action: CODE-CLEANUP.

### B4
Divergence: Gemini says OIDC data is mirrored into both session and attributes, Architect v1 says "Data IS stored to both," and Codex plus Architect v2 say both locations are populated by two separate write paths, not by the `target` expression itself. The strongest evidence is Architect v2, because it quotes both the attribute write and the session write and therefore resolves the causal claim precisely. Recommended action: CODE-CLEANUP.

### A4
Divergence: Gemini and Architect v1 treat the 4096-byte overflow as a hard HTTP 500, while Codex and Architect v2 say the framework catches the exception and returns the original response without updating the session cookie. The strongest evidence is Codex, because it is the only audit that explicitly inspected the underlying CHF `SessionFilter` implementation outside the OpenIG repo and traced the exception to client-visible behavior. Recommended action: ACCEPTED.

### A5
Divergence: Codex marks the claim inconclusive because it distinguishes the proven `"Session"` heap key from the unproven exact container-brand fallback, while Gemini and both Architect passes collapse the project-relevant requirement into a plain confirmation. The strongest evidence is Architect v2, because it ties `Keys.SESSION_FACTORY_HEAP_KEY` directly to the `GatewayHttpApplication.create()` lookup path. Recommended action: ACCEPTED.

### E1
Divergence: Gemini describes a server-global `clientEndpoint` registration, Architect v1 says there is no formal registry but the operational collision still exists, and Codex plus Architect v2 describe the behavior as route-local matching plus route-order/path collisions. The strongest evidence is Architect v2, because it shows `clientEndpoint` as an instance field and identifies the absence of any static or global registry mechanism. Recommended action: CODE-CLEANUP.

### F2
Divergence: Gemini and Architect v1 say `sessionTimeout` controls both JWT `exp` and cookie `Max-Age`, while Codex and Architect v2 say it controls JWT expiry and cookie `Expires` instead. The strongest evidence is Architect v2, because it quotes `JwtCookieSession.buildJwtCookie().setExpires(...)`, and Codex independently confirms there is no `Max-Age` write path. Recommended action: CODE-CLEANUP.

## 4. Critical findings summary

- `B3` is the most important negative finding: `user_info` is not persisted in the OAuth2 session blob, so `session[oauth2Key].user_info.sub` is a dead-path assumption. Risk: LOW current runtime risk because ID-token fallback exists, but MEDIUM maintenance risk because the dead read obscures the true data model.
- `A4` is the highest operability risk: oversized JWT sessions do not fail as a visible hard 500, they fail as silent session-save loss while the response still reaches the client. Risk: MEDIUM, because this is harder to diagnose than an explicit error even though the current Redis offload pattern largely prevents it.
- `B4` is the main architectural wording trap: `target = ${attributes.openid}` does not itself mirror data into session, and conflating those two paths can send future debugging to the wrong source of truth. Risk: MEDIUM design/debugging risk, LOW immediate runtime risk.
- `E1` is operationally important but lower risk: `clientEndpoint` collisions come from route-local matching and route order, not from a server-global registry. Risk: LOW as long as endpoint paths remain unique, but the documentation should say the right thing.
- `C2` is the strongest positive finding: all four audits support the same execution order, confirming that Redis token-reference offload mutations happen before session save and are therefore persisted. Risk: LOW, and it materially strengthens confidence in the current OpenIG 6 pattern.

## 5. Open actions

- Remove or clearly annotate any dead `session[oauth2Key].user_info.sub` read paths in the Jellyfin logout flow, and standardize on `attributes.openid` during-request plus ID-token fallback out-of-band.
- Update project documentation to say that `target = ${attributes.openid}` and OAuth2 session persistence are separate write paths, not a single mirrored write.
- Add a gotcha or runbook note that JWT session overflow leads to silent session-save loss, not a guaranteed hard 500.
- Correct any documentation or test expectations that mention cookie `Max-Age`; OpenIG 6 uses cookie `Expires` for this session path.
- Keep the `Session` heap name and unique `clientEndpoint` values documented as non-negotiable configuration constraints for future route additions.
