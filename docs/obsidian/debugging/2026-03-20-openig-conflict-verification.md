---
title: OpenIG conflict verification for B4 A4 F2
tags:
  - openig
  - audit
  - debugging
  - source-verification
date: 2026-03-20
status: completed
---

# OpenIG conflict verification for B4 A4 F2

Context:
- Follow-up to conflicting audits about [[OpenIG]] claim IDs `B4`, `A4`, and `F2`.
- Goal was to anchor the verdicts to upstream source paths where possible, and to the actual CHF runtime class for `A4` because `SessionFilter` is not in the OpenIG repo tree.

> [!success] Final verdict
> `B4`, `A4`, and `F2` are all **REFUTED**.

## What was verified

### B4
- `OAuth2ClientFilter.fillTarget()` writes only through `target.set(bindings(context, null), info)`.
- `OAuth2Utils.saveSession()` writes the persisted OAuth2 blob through `session.put(sessionKey, oAuth2Session.toJson().getObject())`.
- Result: `target = ${attributes.openid}` does not itself mirror data into session. Attributes and session are separate write paths.

### A4
- Requested OpenIG path for `SessionFilter.java` is absent because the class lives in the CHF dependency, not in [[OpenIG]].
- Verified from local Maven artifact `org.openidentityplatform.commons.http-framework:core:3.0.2` with `javap -l -c`.
- `SessionFilter$1.handleResult(Response)` catches `IOException`, logs `Failed to save session`, restores the old session, and returns. No hard 500 is thrown from that path.

### F2
- `JwtCookieSession.buildJwtCookie()` uses `.setExpires(new Date(expiryTime.longValue()))`.
- Prior raw-source trace in the repo also ties expiry to `_ig_exp` and `getNewExpiryTime()`.
- Result: `sessionTimeout` affects expiry semantics, but the cookie attribute is `Expires`, not `Max-Age`.

## Decision

> [!tip] Audit baseline
> Treat `B4`, `A4`, and `F2` as settled in future reviews unless upstream OpenIG or CHF code changes.

## Current state

- Focused report written to `docs/audit/2026-03-20-openig-conflict-verification.md`.
- Existing broader audits remain useful as provenance, especially the prior direct-source trace in `docs/audit/2026-03-20-openig-core-audit-codex.md`.

## Files changed

- `docs/audit/2026-03-20-openig-conflict-verification.md`
- `docs/obsidian/debugging/2026-03-20-openig-conflict-verification.md`
