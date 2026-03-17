---
title: Memory symlink write check
tags:
  - debugging
  - codex
  - memory
date: 2026-03-17
status: done
---

# Memory symlink write check

## Context
- Repo root needed `.memory` ignored by git because it points to an external memory directory.
- `/.memory/MEMORY.md` needed one new workflow pointer for `feedback_claude_self_edit.md`.
- Required verification: confirm Codex can write through the `.memory` path, then restore the file.

## What changed
- Added `.memory` to root `.gitignore`.
- Added this workflow entry under `## Workflow rules` in `/.memory/MEMORY.md`:
  - `- [feedback_claude_self_edit.md] Khi Codex bị block: KHÔNG tự xử lý — báo user lý do + hỏi confirm trước`

## Verification
> [!success]
> `apply_patch` successfully wrote through `/.memory/MEMORY.md`, proving Codex can edit the symlink target from this workspace.

- Added temporary EOF marker `# symlink-test-ok` to `/.memory/MEMORY.md`.
- Read the file back and confirmed the marker appeared.
- Removed the same marker immediately after verification.

> [!warning]
> Direct shell redirection to `/.memory/MEMORY.md` was sandbox-blocked, but normal Codex file edits via patching worked on the symlink path.

## Current state
- Git diff only contains the intentional `.gitignore` update.
- `/.memory/MEMORY.md` keeps the new workflow pointer and does not retain the temporary test marker.
- Relevant context for workflow/memory handling remains aligned with [[Vault]] and project continuity notes.
