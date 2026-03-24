---
title: 2026-03-24 Pre-Compact State V2
tags:
  - debugging
  - shared-infra
  - openig
  - redis
  - vault
  - nginx
date: 2026-03-24
status: in-progress
---

# 2026-03-24 Pre-Compact State V2

Related: [[OpenIG]] [[Keycloak]] [[Redis]] [[Vault]] [[Nginx]] [[Stack A]] [[Stack B]] [[Stack C]]

## Session Outcome

- Branch: `feat/shared-infra`
- Overall matrix: `5/6` apps PASS for SSO/SLO on shared infra
- PASS via logs and user validation: app1 WordPress, app3 Redmine, app4 Jellyfin, app5 Grafana, app6 phpMyAdmin
- Pending: app2 WhoAmI still needs `1` user login + logout test
- Session commits:
  - `afef643`: Redis ACL hardened to minimal commands
  - `28a7d95`: nginx `proxy_cookie_flags` fix + docs sync
  - `31f2e4c`: pre-compact state note

> [!success] Current checkpoint
> Shared infra is functionally validated for `5` applications. The remaining closeout item before a `6/6` matrix is a single user test for [[WhoAmI]].

## SSO/SLO Matrix

| App | Login | SLO | Backchannel | Verdict |
| --- | --- | --- | --- | --- |
| app1 WordPress | PASS | PASS | `app1:blacklist` written `x2` | PASS |
| app2 WhoAmI | NOT TESTED YET | - | - | PENDING |
| app3 Redmine | PASS | PASS | `app3:blacklist` written `x2` | PASS |
| app4 Jellyfin | PASS | PASS | `app4:blacklist` written `x1` | PASS |
| app5 Grafana | PASS | PASS | `app5:blacklist` written `x1` | PASS |
| app6 phpMyAdmin | PASS | PASS | `app6:blacklist` written `x2` | PASS |

## Security Hardening Applied

- [[Nginx]] cookie hardening fixed `proxy_cookie_flags` from `IG_SSO` to `~IG_SSO_APP` in `28a7d95`
- [[Redis]] ACL hardened in `afef643` from `+@all` to the minimal command set: `+set`, `+get`, `+del`, `+exists`, `+ping`
- [[Vault]] per-app isolation CLI tests PASS for app1, app3, app4, and app6 on `2026-03-24`
- Plan and architecture docs now reflect the per-route `JwtSession` design in `28a7d95`
- No new `JWT session is too large` errors observed during the shared-infra validation pass

> [!success] Security closeout
> The earlier Redis ACL enumeration concern is closed for the shared runtime because the ACL now permits only the commands needed by the gateway routes.

## Pending Items

- [[WhoAmI]] login + logout still needs one user test before the matrix can be marked `6/6`
- Full audit run remains open: Codex + Architect comprehensive review of the shared-infra implementation
- Step 5 is still open: archive old stack directories, update the restart checklist, and finish final documentation migration

> [!warning] Remaining validation gap
> Full cross-app closeout is still blocked on app2 because the current session ended before the final [[WhoAmI]] browser test.

## Next Steps After Compact

1. User tests [[WhoAmI]] login + logout and the matrix is updated to `PASS` if the flow holds.
2. Run the full audit with Codex + Architect agents against the shared-infra branch.
3. Execute Step 5 cleanup: archive old stacks, refresh restart docs, and finalize the shared-infra documentation set.

> [!tip] Resume point
> Start from `.omc/plans/shared-infra.md` and `/Users/duykim/.claude/projects/-Volumes-OS-claude-openig-sso-lab/memory/MEMORY.md`, then close app2 before beginning the audit pass.
