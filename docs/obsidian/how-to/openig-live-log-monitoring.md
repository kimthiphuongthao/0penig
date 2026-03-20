---
title: OpenIG Live Log Monitoring
tags:
  - openig
  - monitoring
  - debugging
  - sso
date: 2026-03-21
status: active
---

# OpenIG Live Log Monitoring

Context: browser testing is running across the SSO/SLO stacks and OpenIG findings need to be watched live without losing the monitor process when the command runner detaches.

## Recommended setup

- Aggregate all matching OpenIG log lines into `/tmp/openig-monitor.log`
- Keep the monitor logic in a small script such as `/tmp/codex_openig_live_monitor.py`
- Run the monitor in a persistent PTY session, not as a detached one-shot background job
- Write optional helper state files such as `/tmp/codex-openig-monitor.session` and `/tmp/codex-openig-monitor.state` so the session can be resumed or stopped cleanly

## Parsing rules

- New `ERROR`, `Exception`, or `Traceback` lines are surfaced immediately with container context
- Success lines are emitted every 15 seconds as concise `✅` summaries
- `Stored oauth2 session` and `Restored oauth2 session` are treated as login success signals
- `Redis blacklist updated successfully` is treated as backchannel logout success
- Explicit logout success is only emitted if the log contains an actual logout success/completion string
- Repeated `Session keys at .then()` warnings are suppressed
- Existing warning patterns are seeded from the baseline so old noise is not replayed during the test run

> [!warning]
> Detached background jobs started from the command runner do not survive reliably in this environment. The monitor must stay attached to a persistent PTY session instead of `nohup`.

> [!success]
> This pattern keeps the live monitor stable across a full browser test run and avoids missing errors because a detached process died silently.

## Related components

- [[OpenIG]]
- [[Keycloak]]
- [[Vault]]

## Operational notes

- Start the monitor before browser testing so the baseline can be recorded once and older noise does not get replayed as a fresh failure
- This setup only reads `/tmp/openig-monitor.log`; it does not modify stack config or target applications
- Stop the active PTY session with `Ctrl-C` when the test window ends

> [!tip]
> For noisy runs, the useful success markers are `Stored oauth2 session`, `Restored oauth2 session`, and `Redis blacklist updated successfully`. Everything else is usually supporting detail.
