---
title: 2026-03-19 End-of-Session Audit JwtSession Phase 1 Status
tags:
  - debugging
  - session-audit
  - openig
  - keycloak
  - jwt-session
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-19
status: in-progress
---

# 2026-03-19 End-of-Session Audit JwtSession Phase 1 Status

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- End-of-session audit before context compact.
- Active branch for the current restore work: `fix/jwtsession-production-pattern`
- Branch point: created from `9a7b855`, intentionally before rename experiment commit `e37536d`
- Backup branch retained: `feat/subdomain-test` at `cdb5425` = working `HttpSession` fallback snapshot

## What completed this session

- [[Stack A]] `CredentialInjector.groovy` was refactored to browser cookie pass-through, so `wp_session_cookies` no longer live inside [[OpenIG]] session state.
- [[Stack B]] `RedmineCredentialInjector.groovy` was refactored to the same pattern, so `redmine_session_cookies` no longer live inside [[OpenIG]] session state.
- [[Keycloak]] access tokens for all four OpenIG clients were trimmed to remove `realm_access` and `resource_access`.
- [[Stack C]] [[Vault]] was unsealed, AppRole access was refreshed, and `secret/phpmyadmin/alice` was realigned to the live MariaDB password `AlicePass123`.
- [[Stack C]] Grafana and phpMyAdmin login/logout were confirmed from user testing plus persisted route-log evidence.

> [!success]
> The gateway-side prep for Phase 1 is largely done: app-cookie pass-through is in place for [[Stack A]] and [[Stack B]], and the first token-size reduction in [[Keycloak]] is already persistent.

## Current state

- All three stacks are still running Tomcat `HttpSession` fallback right now because `config.json` still names the heap object `"JwtSession"` instead of `"Session"`.
- [[Stack C]] is currently the only stack with confirmed end-to-end healthy login/logout behavior on the fix branch.
- [[Stack A]] and [[Stack B]] have the new injector code on the fix branch but still need real browser validation after `JwtSession` is restored.
- [[Stack C]] still has an infrastructure gap for phpMyAdmin user `bob`: Vault can hold the secret, but live MariaDB does not provision the `bob` user.

> [!warning]
> The current `HttpSession` state is a working fallback only. It is not the intended production pattern for this lab.

## Next steps

1. Rename the heap object from `"JwtSession"` to `"Session"` in all three `config.json` files.
2. Switch the four OpenIG [[Keycloak]] clients to `ES256`.
3. Disable `refresh_token` for the same four clients.
4. Retest whether the encrypted cookie session now stays under the 4 KB browser limit.
5. If Phase 1 still fails, move to Phase 2: Token Reference Pattern via Redis.
6. After the session model is settled, validate [[Stack A]] and [[Stack B]] login/logout in a real browser.
7. Handle the separate [[Stack C]] MariaDB `bob` provisioning gap.

> [!tip]
> The next session should treat `BUG-JWTSESSION-4KB` as the active blocker and keep `feat/subdomain-test` untouched until the fix branch proves the real cookie-backed session fits.

## Files changed

- `.memory/MEMORY.md`
- `CLAUDE.md`
- `docs/fix-tracking/master-backlog.md`
- `.claude/rules/gotchas.md`
- `.claude/rules/architecture.md`
- `docs/obsidian/debugging/2026-03-19-end-of-session-audit-jwtsession-phase-1-status.md`
