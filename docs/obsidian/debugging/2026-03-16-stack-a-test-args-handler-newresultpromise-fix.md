---
title: Stack A TestArgsHandler newResultPromise fix
tags:
  - debugging
  - openig
  - stack-a
  - groovy
date: 2026-03-16
status: done
---

# Stack A TestArgsHandler newResultPromise fix

Context: fixed the runtime failure in [[OpenIG]] Stack A test route handler at [stack-a/openig_home/scripts/groovy/TestArgsHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/TestArgsHandler.groovy).

## Root cause

- `newResultPromise(response)` was called without the required static import from `org.forgerock.util.promise.Promises`.
- Groovy resolved the call against the script class at runtime and failed with `No signature of method: TestArgsHandler.newResultPromise()`.

## Changes made

- Added `import static org.forgerock.util.promise.Promises.newResultPromise` at the top of the script.

> [!success] Scope
> Only the Stack A Groovy handler was changed. No target application code or Stack B/Stack C files were modified.

> [!warning] Verification blocker
> Docker daemon access is denied in this Codex sandbox, so `docker restart sso-openig-1`, `docker exec sso-nginx curl -s http://sso-openig-1:8080/test-args`, and `docker logs sso-openig-1 2>&1 | grep -E 'ERROR|test-args'` could not be executed here.

## Current state

- Source-level fix is present in [[OpenIG]] Stack A `TestArgsHandler.groovy`.
- Runtime verification remains pending in an environment with Docker socket access.

## Files changed

- [[OpenIG]]
  File: [stack-a/openig_home/scripts/groovy/TestArgsHandler.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/TestArgsHandler.groovy)
