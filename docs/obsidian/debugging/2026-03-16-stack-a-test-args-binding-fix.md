---
title: Stack A test args binding fix
tags:
  - debugging
  - openig
  - stack-a
  - groovy
date: 2026-03-16
status: done
---

# Stack A test args binding fix

Context: updated [[OpenIG]] Stack A test handler at [stack-a/openig_home/scripts/groovy/TestArgsHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/TestArgsHandler.groovy) to validate how route `args` are exposed in [[Stack A]].

## Root cause

- The handler assumed OpenIG exposed route `args` as a single `args` map.
- In OpenIG 6 `AbstractScriptableHeapObject` binds each entry as a separate top-level Groovy variable, so `testParam` should be read directly.

## Changes made

- Replaced the old `cfg` lookup with two checks:
  - top-level binding via `binding.hasVariable('testParam')`
  - optional map binding via `binding.hasVariable('args')`
- Changed the plain-text response to print both values as `top-level=... map=...`.

> [!success] Scope
> Only the Stack A Groovy handler was changed. No target application code, Keycloak config, or other stacks were modified.

> [!warning] Verification blocker
> Docker daemon access is denied in this Codex sandbox, so `docker restart sso-openig-1` and `docker exec sso-nginx curl -s http://sso-openig-1:8080/test-args` could not be executed here.

> [!tip] Expected body
> Based on [stack-a/openig_home/config/routes/99-test-args.json](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/config/routes/99-test-args.json), the updated handler should return `top-level=hello-from-args map=MAP_NOT_AVAILABLE` if OpenIG 6 binds `testParam` only as a top-level variable.

## Current state

- Source change is present in [[OpenIG]] Stack A.
- Runtime verification is still pending in an environment with Docker socket access.

## Files changed

- [stack-a/openig_home/scripts/groovy/TestArgsHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/TestArgsHandler.groovy)
