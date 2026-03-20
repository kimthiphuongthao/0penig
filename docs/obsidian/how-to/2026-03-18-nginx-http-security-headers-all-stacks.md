---
title: Nginx HTTP Security Headers Across All Stacks
tags:
  - how-to
  - nginx
  - security-hardening
  - stack-a
  - stack-b
  - stack-c
  - sso
date: 2026-03-18
status: done
---

# Nginx HTTP Security Headers Across All Stacks

Related: [[Stack A]] [[Stack B]] [[Stack C]] [[OpenIG]] [[Keycloak]] [[Vault]]

## Context

Task executed directly from `.omc/plans/phase2-security-hardening.md` after `[M-3/S-7]` had already been confirmed. No investigation was needed.

The hardening target was the public nginx entrypoint in all three stacks:

- `stack-a/nginx/nginx.conf`
- `stack-b/nginx/nginx.conf`
- `stack-c/nginx/nginx.conf`

Baseline gap: the shared `http {}` blocks did not emit consistent response hardening headers at the edge.

## What Changed

- Added the same header block inside each `http {}` block, immediately before the first `server {}` block.
- Enabled:
  - `X-Frame-Options: SAMEORIGIN`
  - `X-Content-Type-Options: nosniff`
  - `Referrer-Policy: strict-origin-when-cross-origin`
- Left `Strict-Transport-Security` commented for future TLS enablement.
- Did not add `Content-Security-Policy`, per task constraint, because CSP requires app-specific tuning.

> [!success]
> All nginx containers restarted successfully after the config change: `sso-nginx`, `sso-b-nginx`, and `stack-c-nginx-c-1`.

## Validation

- `curl -sI http://wp-a.sso.local/ | grep -i 'x-frame-options'`
  - Result: `X-Frame-Options: SAMEORIGIN`
- `curl -sI http://redmine-b.sso.local:9080/ | grep -i 'x-content-type'`
  - Result: `X-Content-Type-Options: nosniff`
- `curl -sI http://grafana-c.sso.local:18080/ | grep -i 'referrer-policy'`
  - Result: `Referrer-Policy: strict-origin-when-cross-origin`

> [!warning]
> Keep HSTS commented until TLS termination is enabled on the lab entrypoints. Advertising HSTS over plain HTTP would be incorrect and would not provide the intended transport guarantee.

> [!tip]
> Defining the headers at the `http {}` level keeps all existing `server {}` blocks aligned without duplicating per-vhost directives.

## Current State

- Stack A, Stack B, and Stack C now emit the requested baseline security headers from nginx.
- The change is edge-only and does not alter OpenIG routes, upstream app behavior, or proxy timeout/buffering settings.
- No other nginx directives were modified.

## Files Changed

- `stack-a/nginx/nginx.conf`
- `stack-b/nginx/nginx.conf`
- `stack-c/nginx/nginx.conf`
- `docs/obsidian/how-to/2026-03-18-nginx-http-security-headers-all-stacks.md`
