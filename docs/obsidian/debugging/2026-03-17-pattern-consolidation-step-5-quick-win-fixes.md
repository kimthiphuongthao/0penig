---
title: Pattern consolidation step 5 quick-win fixes
tags:
  - debugging
  - openig
  - nginx
  - docker-compose
  - stack-a
  - stack-b
  - stack-c
  - pattern-consolidation
date: 2026-03-17
status: done
---

# Pattern consolidation step 5 quick-win fixes

Context: implemented the remaining quick-win follow-ups after Steps 1-4 for [[Stack A]], [[Stack B]], and [[Stack C]] without restarting containers.

## Root cause

- [[Stack B]] still exposed Redmine directly on host port `3000`, bypassing the intended [[Nginx]] entrypoint and weakening the lab's gateway-only access pattern.
- [[Stack C]] `nginx.conf` did not define explicit proxy buffer settings, which left large upstream response headers dependent on defaults.
- [[Stack A]] and [[Stack B]] were missing the `CANONICAL_ORIGIN_*` environment variables already present in [[Stack C]], leaving the shared redirect/origin pattern inconsistent across stacks.
- [stack-a/openig_home/scripts/groovy/App1ResponseRewriter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/App1ResponseRewriter.groovy) was an empty leftover file with no route references.

## Changes made

- Removed the Redmine `ports` mapping from [stack-b/docker-compose.yml](/Volumes/OS/claude/openig/sso-lab/stack-b/docker-compose.yml) so Redmine is reachable only through the Stack B reverse proxy path.
- Added `proxy_buffer_size 128k;`, `proxy_buffers 4 256k;`, and `proxy_busy_buffers_size 256k;` to [stack-c/nginx/nginx.conf](/Volumes/OS/claude/openig/sso-lab/stack-c/nginx/nginx.conf).
- Added `CANONICAL_ORIGIN_APP1` and `CANONICAL_ORIGIN_APP2` to both OpenIG nodes in [stack-a/docker-compose.yml](/Volumes/OS/claude/openig/sso-lab/stack-a/docker-compose.yml).
- Added `CANONICAL_ORIGIN_APP3` and `CANONICAL_ORIGIN_APP4` to both OpenIG nodes in [stack-b/docker-compose.yml](/Volumes/OS/claude/openig/sso-lab/stack-b/docker-compose.yml).
- Verified [[Stack C]] already had the corresponding `CANONICAL_ORIGIN_APP5` and `CANONICAL_ORIGIN_APP6` pattern and left [stack-c/docker-compose.yml](/Volumes/OS/claude/openig/sso-lab/stack-c/docker-compose.yml) unchanged.
- Deleted the dead Groovy file [stack-a/openig_home/scripts/groovy/App1ResponseRewriter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/App1ResponseRewriter.groovy) after confirming there were no route references under `stack-a/openig_home/config/routes/`.

> [!success] Verification
> `git diff --unified=0` shows only the requested quick-win changes: one Redmine port removal, three nginx proxy buffer directives, four new canonical origin variable pairs, and deletion of the empty unused Groovy file.

> [!warning] Runtime gap
> No containers were restarted in this step, so runtime validation of the updated compose and nginx configuration still needs to happen in a Docker-enabled environment.

> [!tip] Pattern alignment
> Keeping the `CANONICAL_ORIGIN_*` variables aligned across stacks makes the consolidated [[OpenIG]] route and logout patterns easier to reason about and reduces per-stack drift.

## Current state

- [[Stack B]] Redmine no longer has direct host exposure on `3000`.
- [[Stack C]] has explicit proxy buffer sizing in [[Nginx]].
- [[Stack A]] and [[Stack B]] now match the `CANONICAL_ORIGIN_*` environment pattern already used by [[Stack C]].
- The unused Stack A response rewriter stub has been removed.

## Next steps

- Reload or restart the affected services from a Docker-enabled shell when ready.
- Validate Redmine access only through `http://redmine-b.sso.local:9080`.
- Validate the Stack C proxy still serves Grafana and phpMyAdmin normally after the nginx reload.

## Files changed

- [stack-a/docker-compose.yml](/Volumes/OS/claude/openig/sso-lab/stack-a/docker-compose.yml)
- [stack-b/docker-compose.yml](/Volumes/OS/claude/openig/sso-lab/stack-b/docker-compose.yml)
- [stack-c/nginx/nginx.conf](/Volumes/OS/claude/openig/sso-lab/stack-c/nginx/nginx.conf)
- [stack-a/openig_home/scripts/groovy/App1ResponseRewriter.groovy](/Volumes/OS/claude/openig/sso-lab/stack-a/openig_home/scripts/groovy/App1ResponseRewriter.groovy)
