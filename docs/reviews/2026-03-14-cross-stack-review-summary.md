---
# Cross-Stack Review Summary — OpenIG SSO/SLO Gateway
**Date:** 2026-03-14
**Stacks reviewed:** A (WordPress+WhoAmI), B (Redmine+Jellyfin HA), C (Grafana+phpMyAdmin)
**Purpose:** Identify universal pattern-level findings vs stack-specific findings to inform a standard, reusable OpenIG gateway pattern

> Update 2026-03-17: This file remains the 2026-03-14 historical cross-review summary. Since then, STEP-01 deleted `PhpMyAdminCookieFilter.groovy` (`20d523f`), STEP-02 rotated Stack C OIDC secrets (`37672ed`), and STEP-03 moved compose secrets into gitignored `.env` files while pinning OpenIG to `6.0.1` (`b738577`). Use `docs/audit/2026-03-17-production-readiness-gap-report.md` and `docs/fix-tracking/master-backlog.md` for the current open-item list.

---

## Universal Findings (present in ALL 3 stacks)

| Finding | Severity | Stack A ref | Stack B ref | Stack C ref | Pattern Impact |
|---|---|---|---|---|---|
| Repo-managed gateway/OIDC secrets | HIGH | `§5 F1` | `Findings F1` | `§4 F1` | Secret exposure becomes a gateway-pattern flaw, not a stack-local mistake. |
| Revocation TTL shorter than session lifetime | HIGH | `§5 F3` | `Findings F2` | `§4 F2` | Logout correctness expires before the browser session does. |
| Revocation checks fail open on Redis errors | HIGH | `§5 F2` | `Findings F3` | `§4 F3` | Backchannel logout becomes best-effort whenever Redis is unavailable. |
| Redirect/public URL behavior depends on inbound `Host` | MEDIUM | `§5 F5` | `Findings F7` | `§4 F9` | Redirect integrity and session namespace resolution depend on request headers instead of pinned config. |

Also present in all 3 review files, but only validation-only in Stack A: plaintext HTTP (`Stack A §6 Codex-only additions`; `Stack B Findings F4`; `Stack C §4 F4`) and missing Redis socket timeouts (`Stack A §6 Codex-only additions`; `Stack B Findings F9`; `Stack C §4 F7`).

---

## Stack-Specific Findings

| Finding | Severity | Stack | Root Cause Pattern |
|---|---|---|---|
| Logout URL logging leaks `id_token_hint` (`Stack A §5 F4`) | MEDIUM | A | Logout observability logs token-bearing URLs instead of redacted metadata. |
| Possible duplicate `wordpress_*` cookie collision (`Stack A §6 Codex-only additions`) | MEDIUM | A | Adapter cookie namespace can collide with existing downstream cookies. |
| Retry redirect on unsafe methods can drop the original request body (`Stack A §6 Codex-only additions`) | MEDIUM | A | Reauth/retry logic assumes redirect-safe request semantics. |
| WordPress synthetic login failure can degrade into unauthenticated proxying (`Stack A §6 Subagent-only findings`) | MEDIUM | A | Adapter failure path does not enforce authenticated fail-closed behavior. |
| Sensitive backend material is stored in the browser-bound `JwtSession` (`Stack B Findings F6`; `Stack C §4 F5`) | HIGH | B, C | Vault tokens, downstream credentials, or downstream session material are serialized into a client-facing gateway session. |
| Backchannel internal failures are returned as HTTP 400 (`Stack B Findings F10`; `Stack C §4 F8`) | MEDIUM | B, C | Error handling conflates malformed logout requests with infrastructure/runtime failures. |
| Jellyfin logout reads the wrong OIDC namespace (`Stack B Findings F5`) | HIGH | B | RP-initiated logout drifted from the configured OAuth2 client namespace. |
| Jellyfin access token is injected into browser `localStorage` (`Stack B Findings F8`) | MEDIUM | B | Adapter stores bearer tokens in JS-accessible persistent storage. |
| `sid` vs `sub` mismatch may break backchannel enforcement (`Stack B Findings F11`) | MEDIUM | B | Revocation read/write paths may not use the same identity key. |
| phpMyAdmin cookie-reconciliation filter is not wired into the route chain (`Stack C §4 F6`) | HIGH | C | An adapter safeguard exists in code but is absent from the configured filter chain. |

---

## Login Mechanism Pattern Risk Matrix

| Pattern | Representative App | Stack | Unique Risks (from review) | Shared Risks (from universal findings) |
|---|---|---|---|---|
| OIDC Standard | WordPress, WhoAmI | A | `A §5 F4` logs `id_token_hint`; `A §6 Codex-only additions` note possible `wordpress_*` cookie collision and unsafe-method retry body drop; `A §6 Subagent-only findings` note synthetic login failure can degrade into unauthenticated proxying. | Repo-managed secrets, short revocation TTL, fail-open revocation, and `Host`-derived redirect/public URL behavior. |
| Credential Injection | Redmine | B | `B F6` retains browser-bound `JwtSession` storage of sensitive backend material; `B F10` returns HTTP 400 for internal backchannel failures. | Repo-managed secrets, short revocation TTL, fail-open revocation, and `Host`-derived redirect/public URL behavior. |
| Token Injection + `localStorage` | Jellyfin | B | `B F5` reads the wrong OIDC namespace during logout; `B F8` injects an access token into browser `localStorage`; `B F6` keeps sensitive session material in the browser-bound session. | Repo-managed secrets, short revocation TTL, fail-open revocation, and `Host`-derived redirect/public URL behavior. |
| Trusted Header Injection | Grafana | C | No Grafana-only finding is listed; the row inherits Stack C stack-level risks, including `C F8` misclassifying internal logout failures as HTTP 400. | Repo-managed secrets, short revocation TTL, fail-open revocation, and `Host`-derived redirect/public URL behavior. |
| HTTP Basic Auth Injection | phpMyAdmin | C | `C F5` stores Vault token and phpMyAdmin credentials in the browser-bound `JwtSession`; `C F6` leaves the phpMyAdmin cookie-reconciliation control unwired. | Repo-managed secrets, short revocation TTL, fail-open revocation, and `Host`-derived redirect/public URL behavior. |

---

## Recommended Standard Pattern (Gateway-Level)

- Externalize and rotate all gateway crypto material and OIDC client secrets, then invalidate affected sessions. Addresses Universal repo-managed secrets (`A F1`, `B F1`, `C F1`).
- Make revocation authoritative: TTL must cover the full session lifetime, authenticated requests must fail closed when revocation state is indeterminate, and write/read paths must use the same key. Addresses Universal TTL mismatch and fail-open revocation (`A F3`, `B F2`, `C F2`, `A F2`, `B F3`, `C F3`) plus `B F11`.
- Treat TLS as part of the gateway pattern, not lab scaffolding. Addresses plaintext HTTP in `A §6 Codex-only additions`, `B F4`, and `C F4`.
- Pin canonical public origins and OIDC session namespaces in config; never derive redirects or namespace roots from inbound `Host`. Addresses Universal `Host`-derived behavior (`A F5`, `B F7`, `C F9`) plus `B F5`.
- Bound Redis dependency behavior with explicit connect/read timeouts and proper `5xx` handling for infrastructure faults. Addresses timeout findings in `A §6 Codex-only additions`, `B F9`, `C F7`, and error-classification findings `B F10`, `C F8`.
- Keep Vault tokens, downstream credentials, downstream cookies, and bearer tokens out of browser-visible storage; prefer opaque server-side session references and `httpOnly` cookies. Addresses `B F6`, `B F8`, and `C F5`.
- Make adapter-specific cleanup and logout controls mandatory route-contract elements, not optional helper scripts. Addresses `A F4`, `A §6 Codex-only additions`, `A §6 Subagent-only findings`, and `C F6`.
- Preserve unsafe-method request semantics during reauth/retry flows instead of redirecting and dropping bodies. Addresses `A §6 Codex-only additions`.

---

## Review Method Comparison

| Stack | Sources Used | Findings count | Unique findings by source | Coverage gaps noted |
|---|---|---|---|---|
| A | subagent security-reviewer; subagent code-reviewer; Codex security review; Codex code review | 10 listed (`5` confirmed findings in `§5`, `5` validation items in `§6`) | Codex-only: plaintext HTTP, app1 blacklist filter lacks socket timeout, possible duplicate `wordpress_*` cookie collision, unsafe-method retry body drop. Subagent-only: WordPress synthetic login failure can degrade into unauthenticated proxying. | `§6` says those extra items need follow-up validation; `F5` has only `2/4` agreement. |
| B | subagent security-reviewer; subagent code-reviewer; Codex security review; Codex code review | 11 | Subagent security only: `F6`. Codex code only: `F11`. The Source Comparison table also says Codex found `F8`-`F10` more reliably. | Source Comparison concludes `Codex > Subagent`; `F5`-`F10` do not have `4/4` agreement, and `F6`/`F11` are single-source retains. |
| C | Codex gpt-5.4 security + code review | 9 | N/A: single-source review only. | No Source Comparison table exists in the Stack C review, so there is no cross-review corroboration layer inside that file. |

---

## Next Steps

1. Standardize revocation behavior first: align TTL to session lifetime, fail closed on lookup failures, add bounded Redis timeouts, return `5xx` for infrastructure faults, and verify `sid` consistency. Fixes `A F2/F3`, `B F2/F3/F9/F10/F11`, and `C F2/F3/F7/F8`; affects `A, B, C`.
2. Remove committed secrets and rotate anything already exposed before treating any stack as a reference implementation. Fixes `A F1`, `B F1`, and `C F1`; affects `A, B, C`.
3. Make transport and origin integrity non-optional: require HTTPS end-to-end and replace `Host`-derived redirect/session-root logic with pinned config. Fixes `A §6 Codex-only additions` plus `A F5`, `B F4/F7`, and `C F4/F9`; affects `A, B, C`.
4. Move privileged adapter state out of the browser and eliminate JS-visible token storage. Fixes `B F6/F8` and `C F5`; affects `B, C`.
5. Repair RP-initiated logout and logout hygiene in adapters. Fixes `A F4` and `B F5`; affects `A, B`.
6. Wire adapter cleanup and fail-closed behavior into the configured route chains instead of leaving them as optional script-side logic. Fixes `A §6 Codex-only additions`, `A §6 Subagent-only findings`, and `C F6`; affects `A, C`.
7. Preserve request semantics for unsafe-method reauth/retry flows instead of redirecting and losing bodies. Fixes `A §6 Codex-only additions`; affects `A`.

---
