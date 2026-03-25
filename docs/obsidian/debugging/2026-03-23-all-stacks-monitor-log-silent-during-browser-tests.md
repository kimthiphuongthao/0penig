---
title: All Stacks Monitor Log Silent During Browser Tests
tags:
  - debugging
  - monitoring
  - sso
  - slo
  - openig
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-23
status: observed-no-events
---

# All Stacks Monitor Log Silent During Browser Tests

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Task: monitor `/tmp/sso-lab-monitor.log` continuously while manual browser SSO/SLO testing ran across all three stacks.
- Requested checks:
  - `ERROR` and `WARN`
  - `TokenRef` failures
  - Vault `403` / `502`
  - Redis errors
  - session overflow
  - SLO failures
  - PASS markers for TokenRef store/restore, SLO blacklist, and Vault credential injection
- Observation window:
  - baseline read at start
  - 30-second polling for approximately 15 minutes

## Method

- Baseline read:
  - `tilth /tmp/sso-lab-monitor.log --scope /`
- Live watch:
  - `tail -n 0 -F /tmp/sso-lab-monitor.log`
- Reporting cadence:
  - every 30 seconds, only new lines since the prior checkpoint

## Findings

> [!warning]
> `/tmp/sso-lab-monitor.log` remained empty for the full monitoring window.

- Baseline result:
  - `0` lines
- End-of-window result:
  - `0` lines
- Critical error signatures observed:
  - none
- PASS signatures observed:
  - none
- FAIL signatures observed:
  - none

| Stack | Scope | Result | Evidence |
| --- | --- | --- | --- |
| A | WordPress + WhoAmI login/logout/SLO | inconclusive | no lines emitted to `/tmp/sso-lab-monitor.log` |
| B | Redmine + Jellyfin login/logout/SLO | inconclusive | no lines emitted to `/tmp/sso-lab-monitor.log` |
| C | Grafana + phpMyAdmin login/logout/SLO | inconclusive | no lines emitted to `/tmp/sso-lab-monitor.log` |

## Interpretation

> [!warning]
> This does not prove that the three stacks passed or failed. It only proves that the expected monitor path produced no evidence during the observed window.

- Most likely issue:
  - the monitoring feed was not writing to `/tmp/sso-lab-monitor.log`
  - or the producer was attached to a different path / process than expected
- Less likely issue:
  - the full test run happened without any monitored events matching the configured output
- What can be concluded safely:
  - no Vault `403` / `502`, Redis errors, session overflow, `TokenRef` failures, or SLO failures were emitted to this file
  - no TokenRef Store/Restore OK, SLO blacklist OK, or Vault credential injected OK messages were emitted to this file

> [!tip]
> Before the next live browser run, verify which process writes `/tmp/sso-lab-monitor.log` and force a known test line into that file so the monitoring path is confirmed before application testing starts.

## Files Changed

- `docs/obsidian/debugging/2026-03-23-all-stacks-monitor-log-silent-during-browser-tests.md`
