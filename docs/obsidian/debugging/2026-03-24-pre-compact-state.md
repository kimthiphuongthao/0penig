---
title: 2026-03-24 Pre-Compact State
tags:
  - debugging
  - shared-infra
  - openig
  - redis
  - vault
  - stack-c
date: 2026-03-24
status: in-progress
---

# 2026-03-24 Pre-Compact State

Related: [[OpenIG]] [[Keycloak]] [[Redis]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Runtime

- `shared-openig-1`: up, routes loaded at `2026-03-24T03:04:59Z`
- `shared-openig-2`: up for roughly `40` minutes, routes loaded at `2026-03-24T02:29:41Z`
- Shared HA runtime is healthy with both nodes active
- `stack-c-openig-c1-1` and `stack-c-openig-c2-1`: stopped orphaned containers, not serving traffic
- Active runtime fix: commit `5fb549d` in `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`

> [!success] Shared runtime checkpoint
> The live gateway pair is `shared-openig-1` + `shared-openig-2`, both mounted from the shared `openig_home`. The `hasPendingState` fix from `5fb549d` is the code currently running there.

## What Was Verified Today

1. `hasPendingState` fix confirmed on `shared-openig-2`: after user SSO/SLO testing for `1` app, logs stayed at `0 ERROR`.
2. [[Redis]] ACL state confirmed by CLI:
   - `6` per-app users active
   - `default` user disabled
   - cross-app `SET` returns `NOPERM`
   - same-app `SET` succeeds
3. Redis keys are consistently prefixed:
   - `appN:token_ref:*`
   - `appN:blacklist:*`
   - no unprefixed `token_ref:*` or `blacklist:*`
4. Redis ACL gap found:
   - `openig-app1` can still run `KEYS *` and observe other apps' key names
   - visible data is limited to UUID-style identifiers, not secret payloads
   - user decision is still pending: tighten ACL commands or accept this as a known limitation

> [!warning] Redis ACL limitation
> Payload isolation is confirmed, but enumeration isolation is not. The current ACL shape still allows `KEYS` and `SCAN`, so per-app users can discover other apps' key names even though cross-app writes are blocked.

## Shared Infra Plan Status

- Step 1: MOSTLY DONE
  - Open: no hardcoded secrets in committed files
- Step 2: DONE
- Step 3: PARTIAL
  - Confirmed done: Redis key prefix CLI
  - Confirmed done: Redis ACL cross-app write isolation CLI
  - Open: per-app SSO/SLO matrix
  - Open: Redis key-name enumeration decision
- Step 4: PARTIAL
  - Open: full `6`-app SSO/SLO matrix
  - Open: Redis ACL comprehensive closeout
  - Open: [[Vault]] per-app isolation CLI
  - Open: Jellyfin `deviceId`
  - Open: phpMyAdmin bob
- Step 5: `0%` done

## Next Steps

1. User decides whether to fix Redis ACL key enumeration by replacing `+@all` with a narrower command set, or to accept it as a known limitation.
2. Run [[Vault]] per-app AppRole CLI isolation checks.
3. Complete the full SSO/SLO matrix with user testing for:
   - app1 WordPress
   - app2 WhoAmI
   - app3 Redmine
   - app4 Jellyfin
   - app5 Grafana
   - app6 phpMyAdmin
4. Start Step 5 cleanup: archive old stacks, update docs, and prepare the restart checklist.

> [!tip] Resume order
> If the Redis ACL command set is going to change, do that before more validation so the later CLI checks and app-level verification reflect the final isolation model.

## Recent Commits

- `5fb549d`: `hasPendingState` fix in `TokenReferenceFilter`
- `5f4c9ad`: audit tracking docs post-fix
- `74905c4`: mark SSO/SLO acceptance criteria (later corrected)
- `de46237`: correct over-claimed test coverage
- Next pending tracking update: `.omc/plans/shared-infra.md` with Redis ACL CLI results

## Files To Resume From

- `.omc/plans/shared-infra.md`
- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
- `shared/redis/acl.conf`
- `/Users/duykim/.claude/projects/-Volumes-OS-claude-openig-sso-lab/memory/MEMORY.md`
