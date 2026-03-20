# OpenIG Conflict Verification - 2026-03-20

Scope:
- Verify only disputed claims `B4`, `A4`, and `F2`.
- `B4` and `F2` use the exact raw GitHub file paths requested by the task.
- `A4` could not be verified from the requested OpenIG path because `SessionFilter` is not in the OpenIG repo; the definitive check was done against the locally cached CHF artifact `org.openidentityplatform.commons.http-framework:core:3.0.2`, which is the framework class OpenIG actually uses.

Notes:
- This sandbox cannot directly re-fetch GitHub raw content. For `B4` and `F2`, line ranges come from the existing direct-source audit already in this repo: `docs/audit/2026-03-20-openig-core-audit-codex.md`.
- For `A4`, the requested path is absent from OpenIG, so the bytecode for `org.forgerock.http.filter.SessionFilter$1` was inspected locally with `javap -l -c`.

### B4 — REFUTED
**Source file**: https://raw.githubusercontent.com/OpenIdentityPlatform/OpenIG/master/openig-oauth2/src/main/java/org/forgerock/openig/filter/oauth2/client/OAuth2ClientFilter.java
**Relevant method**: `fillTarget()` lines `790-827`; write site `820-825`
**Evidence** (exact code quote):
```java
target.set(bindings(context, null), info);
```
**Source file**: https://raw.githubusercontent.com/OpenIdentityPlatform/OpenIG/master/openig-oauth2/src/main/java/org/forgerock/openig/filter/oauth2/client/OAuth2Utils.java
**Relevant method**: `saveSession()` lines `147-152`
**Evidence** (exact code quote):
```java
session.put(sessionKey, oAuth2Session.toJson().getObject());
```
**Verdict**: `target=${attributes.openid}` writes only to the target expression (`attributes.openid` here); session persistence is a separate `saveSession()` path, so `target=` does not cause a dual-store write.

### A4 — REFUTED
**Source file**: requested OpenIG path `https://raw.githubusercontent.com/OpenIdentityPlatform/OpenIG/master/openig-core/src/main/java/org/forgerock/http/filter/SessionFilter.java` is not present in OpenIG; verified from local CHF artifact `org.openidentityplatform.commons.http-framework:core:3.0.2` at `/Users/duykim/.m2/repository/org/openidentityplatform/commons/http-framework/core/3.0.2/core-3.0.2.jar`
**Relevant method**: `SessionFilter$1.handleResult(Response)` source lines `61-67` from the class `LineNumberTable`
**Evidence** (exact bytecode quote):
```java
35: astore_2
36: getstatic     #10                 // Field org/forgerock/http/filter/SessionFilter.logger:Lorg/slf4j/Logger;
39: ldc           #11                 // String Failed to save session
41: aload_2
42: invokeinterface #12,  3           // InterfaceMethod org/slf4j/Logger.error:(Ljava/lang/String;Ljava/lang/Throwable;)V
47: aload_0
48: getfield      #2                  // Field val$sessionContext:Lorg/forgerock/http/session/SessionContext;
51: aload_0
52: getfield      #3                  // Field val$oldSession:Lorg/forgerock/http/session/Session;
55: invokevirtual #8                  // Method org/forgerock/http/session/SessionContext.setSession:(Lorg/forgerock/http/session/Session;)Lorg/forgerock/http/session/SessionContext;
58: pop
59: goto          77
77: return
```
**Verdict**: when `sessionManager.save(...)` throws `IOException`, `SessionFilter` catches it, logs `"Failed to save session"`, restores the old session, and returns the original downstream response instead of propagating a hard HTTP 500.

### F2 — REFUTED
**Source file**: https://raw.githubusercontent.com/OpenIdentityPlatform/OpenIG/master/openig-core/src/main/java/org/forgerock/openig/jwt/JwtCookieSession.java
**Relevant method**: `buildJwtCookie()` lines `324-337`; related expiry computation `getNewExpiryTime()` lines `370-372`
**Evidence** (exact code quote):
```java
.setExpires(new Date(expiryTime.longValue()))
```
**Verdict**: `sessionTimeout` participates in OpenIG session expiry, but the cookie builder uses `Expires`, not `Max-Age`; the claim is false as stated.
