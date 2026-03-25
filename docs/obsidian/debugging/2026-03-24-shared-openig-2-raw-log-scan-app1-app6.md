---
title: Shared OpenIG 2 raw log scan for app1-app6
tags:
  - openig
  - debugging
  - logs
  - oauth2
  - token-reference
date: 2026-03-24
status: completed
---

# Context

Ran the exact post-`2026-03-24T02:30:00` Docker log commands against `shared-openig-2` only and derived a per-app status summary from the same window.

Related components: [[OpenIG]], [[Keycloak]], [[Stack A]], [[Stack B]], [[Stack C]]

Commands used:

```bash
docker logs --since=2026-03-24T02:30:00 shared-openig-2 2>&1 | grep -E 'ERROR|WARN.*invalid_token|no authorization in progress|Mixed state|Missing Redis|No oauth2 session value' | tail -80
docker logs --since=2026-03-24T02:30:00 shared-openig-2 2>&1 | grep -E 'ERROR' | tail -30
```

# Findings

> [!success]
> No `ERROR`, `invalid_token`, `no authorization in progress`, `Mixed state`, or `Missing Redis payload` lines were found for `shared-openig-2` in this window.

> [!warning]
> `No oauth2 session value found during response phase` still appeared for every active app except `app2`.

## Raw command outcome

- Command 1 returned 25 warning lines, all of them `No oauth2 session value found during response phase` for `app1`, `app3`, `app4`, `app5`, and `app6`.
- Command 2 returned no output.

## Per-app state

- `app1`
  - `ERROR`: `0`
  - `invalid_token`: `0`
  - `no authorization in progress`: `0`
  - `Mixed state`: `0`
  - `Missing Redis payload`: `0`
  - `No oauth2 session value`: `5`
  - TokenRef pattern on `shared-openig-2`: active but incomplete in-window (`33` unique stores, `23` unique restores, `10` stored IDs not restored in this window)
  - Verdict: `WARNING`
- `app2`
  - `ERROR`: `0`
  - `invalid_token`: `0`
  - `no authorization in progress`: `0`
  - `Mixed state`: `0`
  - `Missing Redis payload`: `0`
  - `No oauth2 session value`: `0`
  - TokenRef pattern on `shared-openig-2`: no activity in this window (`0` endpoint mentions)
  - Verdict: `HEALTHY` for this container window, but idle
- `app3`
  - `ERROR`: `0`
  - `invalid_token`: `0`
  - `no authorization in progress`: `0`
  - `Mixed state`: `0`
  - `Missing Redis payload`: `0`
  - `No oauth2 session value`: `5`
  - TokenRef pattern on `shared-openig-2`: active but incomplete in-window (`30` unique stores, `17` unique restores, `13` stored IDs not restored in this window)
  - Verdict: `WARNING`
- `app4`
  - `ERROR`: `0`
  - `invalid_token`: `0`
  - `no authorization in progress`: `0`
  - `Mixed state`: `0`
  - `Missing Redis payload`: `0`
  - `No oauth2 session value`: `5`
  - TokenRef pattern on `shared-openig-2`: heavy traffic and clearly incomplete in-window (`149` unique stores, `44` unique restores, `105` stored IDs not restored in this window)
  - Verdict: `WARNING`
- `app5`
  - `ERROR`: `0`
  - `invalid_token`: `0`
  - `no authorization in progress`: `0`
  - `Mixed state`: `0`
  - `Missing Redis payload`: `0`
  - `No oauth2 session value`: `5`
  - TokenRef pattern on `shared-openig-2`: heavy traffic and clearly incomplete in-window (`192` unique stores, `77` unique restores, `115` stored IDs not restored in this window)
  - Verdict: `WARNING`
- `app6`
  - `ERROR`: `0`
  - `invalid_token`: `0`
  - `no authorization in progress`: `0`
  - `Mixed state`: `0`
  - `Missing Redis payload`: `0`
  - `No oauth2 session value`: `5`
  - TokenRef pattern on `shared-openig-2`: heavy traffic and clearly incomplete in-window (`116` unique stores, `28` unique restores, `88` stored IDs not restored in this window)
  - Verdict: `WARNING`

# Decisions

- Treat the container as free of hard auth failures in this specific window.
- Treat repeated `No oauth2 session value` warnings and the unmatched store-to-restore gap as a warning-level signal, not proof of a hard break, because this note covers only one container and one bounded time window.

# Current state

- `shared-openig-2` is not failing outright after `02:30:00`.
- The `hasPendingState` fix still appears effective because no `Mixed state` log was emitted.
- `app2` had no traffic on this container in the scanned period.

# Next steps

1. If these warnings are user-visible, correlate the unmatched TokenRef IDs across both shared OpenIG containers before concluding the pattern is broken.
2. Capture request-correlated context around the five `No oauth2 session value` warnings per active app to determine whether they are expected response-phase cleanup noise.
3. Compare this isolated `shared-openig-2` view with [[2026-03-24-openig-post-0230-log-scan-app1-app6]] before changing TokenReference logic.

# Files changed

- Added this note: `docs/obsidian/debugging/2026-03-24-shared-openig-2-raw-log-scan-app1-app6.md`
