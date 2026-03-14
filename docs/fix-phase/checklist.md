# Fix Phase Checklist — OpenIG Gateway GAPs

> ⚠️ **SUPERSEDED** — This file is a high-level tracking reference only.
> Primary implementation plan (file paths, code changes, acceptance criteria, execution batches):
> **`.omc/plans/fix-phase-openig-gaps.md`**
> Maintain this file for progress tracking (Status column + Fix Log). Do not use as implementation guide.



**Based on:** `docs/standard-gateway-pattern.md` v1.1
**Source findings:** `docs/reviews/2026-03-14-cross-stack-review-summary.md` → Next Steps 1–7
**Started:** 2026-03-14
**Status legend:** `[ ]` pending · `[~]` in progress · `[x]` done · `[!]` blocked

---

## Group 1 — Revocation Contract 🔴 HIGHEST PRIORITY
> Stacks: A, B, C | Fixes: A F2/F3, B F2/F3/F9/F10/F11, C F2/F3/F7/F8

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 1a | BackchannelLogoutHandler.groovy — TTL 3600s → 28800s | A, B, C | [ ] | |
| 1b | SessionBlacklistFilter + variants — catch block fail-open → fail-closed (503/redirect login) | A, B, C | [ ] | Stack A: SessionBlacklistFilter + SessionBlacklistFilterApp2; Stack B: +App3 +App4 |
| 1c | Redis socket timeouts — connectTimeout=200ms, soTimeout=500ms (BackchannelLogoutHandler + SessionBlacklistFilter) | A, B, C | [ ] | |
| 1d | BackchannelLogoutHandler — catch Exception → 500 (không phải 400) cho infra faults | B, C | [ ] | B F10, C F8 |
| 1e | Verify sid/sub consistency — BackchannelLogoutHandler write vs SessionBlacklistFilter* read | B | [ ] | B F11 — 1/4 reviewers, investigate first |

---

## Group 2 — Secret Externalization 🟠 HIGH
> Stacks: A, B, C | Fixes: A F1, B F1, C F1

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 2a | config.json — sharedSecret + PKCS12 password → Vault/env | A | [ ] | |
| 2b | config.json + 01-jellyfin.json + 02-redmine.json — sharedSecret + clientSecrets → Vault/env | B | [ ] | |
| 2c | config.json + 10-grafana.json + 11-phpmyadmin.json — sharedSecret + clientSecrets → Vault/env | C | [ ] | |
| 2d | Rotate tất cả exposed secrets + invalidate existing sessions | A, B, C | [ ] | Thực hiện sau 2a/2b/2c |

---

## Group 3 — Transport + Origin Integrity 🟠 HIGH
> Stacks: A, B, C | Fixes: A §6, A F5, B F4/F7, C F4/F9

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 3a | SloHandler*.groovy + SessionBlacklistFilter*.groovy — pin redirect base URLs, không dùng inbound Host | A, B, C | [ ] | A F5, B F7, C F9 |
| 3b | requireHttps: false → true (config.json + route files) | B | [ ] | B F4 |
| 3c | requireHttps: false → true (config.json + route files) | C | [ ] | C F4 |
| 3d | Validate Codex-only HTTP findings: VaultCredentialFilter, BackchannelLogoutHandler, SloHandler, CredentialInjector | A | [ ] | A §6 Codex-only — confirm scope trước khi fix |

---

## Group 4 — Session Storage Boundaries 🟡 MEDIUM
> Stacks: B, C | Fixes: B F6/F8, C F5

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 4a | Remove Vault token + downstream session material khỏi JwtSession → server-side store | B | [ ] | B F6 — 1/4 reviewers, design cần careful |
| 4b | Jellyfin access token — localStorage → httpOnly Secure cookie | B | [ ] | B F8 |
| 4c | Remove vault_token + phpmyadmin_username/password khỏi JwtSession → server-side store | C | [ ] | C F5 |

---

## Group 5 — RP-Initiated Logout + Observability 🔴 URGENT (5a)
> Stacks: A, B | Fixes: A F4, B F5

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 5a | SloHandlerJellyfin.groovy — namespace 'app3' → 'app4' | B | [ ] | **URGENT** — logout đang silently fail; B F5 priority #1 Stack B |
| 5b | SloHandler.groovy — không log full logout URL chứa id_token_hint | A | [ ] | A F4 |

---

## Group 6 — Adapter Contract 🟡 MEDIUM
> Stacks: A, C | Fixes: A §6, C F6

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 6a | Wire PhpMyAdminCookieFilter vào route chain 11-phpmyadmin.json | C | [ ] | C F6 — verify không gây Token mismatch trước khi wire |
| 6b | CredentialInjector.groovy — validate synthetic login failure → fail closed (không proxy unauthenticated) | A | [ ] | A §6 Subagent-only |
| 6c | Validate adapter cleanup hooks wired đúng vào route chain | A | [ ] | A §6 Codex-only |

---

## Group 7 — Unsafe Method Reauth 🟡 MEDIUM
> Stack: A | Fix: A §6 Codex-only

| ID | Task | Stack | Status | Notes |
|----|------|-------|--------|-------|
| 7a | CredentialInjector.groovy — POST/PUT/PATCH/DELETE expired session → 401 thay vì redirect (mất body) | A | [ ] | A §6 Codex-only |

---

## Progress Summary

| Group | Total | Done | In Progress | Pending |
|-------|-------|------|-------------|---------|
| 1 — Revocation | 5 | 0 | 0 | 5 |
| 2 — Secrets | 4 | 0 | 0 | 4 |
| 3 — Transport/Origin | 4 | 0 | 0 | 4 |
| 4 — Session Storage | 3 | 0 | 0 | 3 |
| 5 — Logout/Observability | 2 | 0 | 0 | 2 |
| 6 — Adapter Contract | 3 | 0 | 0 | 3 |
| 7 — Unsafe Method | 1 | 0 | 0 | 1 |
| **Total** | **22** | **0** | **0** | **22** |

---

## Fix Log

| Date | ID | Action | Result | Commit |
|------|----|--------|--------|--------|
| — | — | — | — | — |
