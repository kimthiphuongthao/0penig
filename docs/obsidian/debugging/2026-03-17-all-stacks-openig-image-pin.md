---
title: All Stacks OpenIG Image Pin
tags:
  - debugging
  - docker
  - openig
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-17
status: complete
---

# All Stacks OpenIG Image Pin

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Root cause confirmed from the lab run: `openidentityplatform/openig:latest` resolved to `6.0.2`, rebuilt on 2026-02-05 with Tomcat `11.0.18`.
- OpenIG 6 still depends on the `javax.servlet` namespace and does not load correctly on Tomcat 10+ / 11.
- Failure signature: `ClassNotFoundException: org.forgerock.openig.web.OpenIGInitializer`.
- Requested fix: pin all three stacks to a known-good `6.x` tag instead of `latest`, then validate all three stacks.

## Decision

- Chosen image tag: `openidentityplatform/openig:6.0.1`
- Reason: `6.0.1` remains on the Tomcat 9 / `javax.servlet` line compatible with OpenIG 6, while `latest=6.0.2` moved to Tomcat 11 / `jakarta.servlet`.

> [!success]
> All six OpenIG service definitions in the three compose files now pin `openidentityplatform/openig:6.0.1`.

> [!tip]
> Keep OpenIG 6 on an explicit `6.x` image tag. Avoid `latest` until the upstream image line is verified against a Tomcat 9 base.

## What Changed

- Updated `stack-a/docker-compose.yml`
  - `openig-1`
  - `openig-2`
- Updated `stack-b/docker-compose.yml`
  - `openig-b1`
  - `openig-b2`
- Updated `stack-c/docker-compose.yml`
  - `openig-c1`
  - `openig-c2`

### Requested diff

```diff
- image: openidentityplatform/openig
+ image: openidentityplatform/openig:6.0.1
```

## Validation

- Stack A validation passed: 4 routes loaded successfully with `openidentityplatform/openig:6.0.1`.
- Stack B validation passed: 6 routes loaded successfully with `openidentityplatform/openig:6.0.1`.
- Stack C validation passed: 6 routes loaded successfully with `openidentityplatform/openig:6.0.1`.

> [!success]
> All three stacks are now on the same pinned OpenIG baseline and no longer depend on the mutable `latest` tag.

## Commands Attempted

```bash
docker compose config
docker logs <openig-container> 2>&1 | grep -E '(Loaded the route|ClassNotFoundException|SEVERE)'
```

## Current State

- Stack A compose pin applied: yes
- Stack B compose pin applied: yes
- Stack C compose pin applied: yes
- Stack A route-load verification: yes (4 routes)
- Stack B route-load verification: yes (6 routes)
- Stack C route-load verification: yes (6 routes)

## Next Steps

1. Keep all OpenIG compose services pinned to `openidentityplatform/openig:6.0.1` until a newer OpenIG 6-compatible tag is verified.
2. Treat `latest` as unsafe for this lab unless the upstream Tomcat base and servlet namespace are explicitly revalidated.

## Files Changed

- `stack-a/docker-compose.yml`
- `stack-b/docker-compose.yml`
- `stack-c/docker-compose.yml`
- `docs/obsidian/debugging/2026-03-17-all-stacks-openig-image-pin.md`
