---
title: Standard gateway pattern VI v1.4 full rewrite
tags:
  - openig
  - documentation
  - deliverables
  - translation
  - vi
  - sso
  - slo
date: 2026-04-02
status: completed
---

# Standard gateway pattern VI v1.4 full rewrite

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Redis]] [[2026-04-02-standard-gateway-pattern-en-v1-4-update]] [[2026-04-02-standard-gateway-pattern-en-vi-gap-analysis]]

## Context

Task: rewrite `docs/deliverables/standard-gateway-pattern-vi.md` from scratch using `docs/deliverables/standard-gateway-pattern.md` version `1.4` dated `2026-04-02` as the sole source of truth.

Required constraints:

- translate the entire document to Vietnamese
- keep technical terms in English
- preserve the heading hierarchy, table structure, and overall document layout
- keep code blocks, file paths, env var names, container names, hostnames, and table data unchanged
- overwrite the existing VI file completely instead of patching the older v1.2 content forward

## What Done

- Read the full EN v1.4 source deliverable and verified the target VI file was still the older v1.2 document with different content and structure.
- Replaced `docs/deliverables/standard-gateway-pattern-vi.md` with a full Vietnamese rewrite aligned to the EN v1.4 document.
- Set the VI header to version `1.4` and date `2026-04-02`.
- Preserved all runtime identifiers and technical values, including `OpenIG`, `Keycloak`, `Vault`, `Redis`, `JwtSession`, `TokenReferenceFilter`, `BackchannelLogoutHandler`, `SloHandler`, `clientEndpoint`, hostnames, AppRole names, Redis users, and env vars.
- Kept the EN source layout intact: `24` headings and `27` table separators in both EN and VI files.
- Staged and committed only `docs/deliverables/standard-gateway-pattern-vi.md` with commit `ad1c169`.

> [!success]
> The VI deliverable is now a complete v1.4 rewrite instead of an incremental carry-forward from the older v1.2 document. Verification confirmed `279` lines in the new VI file and no missing sections.

> [!tip]
> For this document family, treat the EN deliverable as the structural source of truth and do a full rewrite when section order, tables, or routing matrices change. Incremental translation patches are high-risk once the EN document adds new contract tables.

> [!warning]
> The Obsidian note was written after the docs commit so the requested Git commit stayed scoped to the deliverable file only.

## Decisions

- Translate prose and operator guidance into Vietnamese, but preserve technical tokens in English to maintain one-to-one traceability with gateway routes, Groovy scripts, runtime env vars, and validation notes.
- Keep the document structure mirrored to EN v1.4 rather than retaining any older VI-only framing from the previous v1.2 file.
- Validate structural parity with the EN source using heading and table counts before committing.

## Current State

- `docs/deliverables/standard-gateway-pattern-vi.md` is aligned with the EN v1.4 deliverable dated `2026-04-02`.
- The VI file line count after rewrite is `279`.
- No sections were skipped.
- Git commit for the deliverable translation: `ad1c169`.

## Next Steps

- If `docs/deliverables/standard-gateway-pattern.md` changes again, repeat a full-source rewrite instead of editing the VI document incrementally.
- If reviewers need a delta summary, derive it from the EN v1.4 update note and the EN/VI gap-analysis note rather than diffing against the old VI v1.2 structure.
- Decide separately whether to stage and commit this Obsidian note with other documentation-tracking changes.

## Files Changed

- `docs/deliverables/standard-gateway-pattern-vi.md`
- `docs/obsidian/how-to/2026-04-02-standard-gateway-pattern-vi-v1-4-full-rewrite.md`
