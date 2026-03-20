---
title: All Stacks host.docker.internal Extra Hosts
tags:
  - debugging
  - docker
  - openig
  - host-docker-internal
  - stack-a
  - stack-b
  - stack-c
date: 2026-03-18
status: complete
---

# All Stacks host.docker.internal Extra Hosts

Related: [[OpenIG]] [[Keycloak]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Phase 2 security hardening item `[A-4]` was already confirmed in `.omc/plans/phase2-security-hardening.md`.
- The requested implementation was to add an explicit Docker host-gateway mapping for every OpenIG service that references `host.docker.internal`.
- Scope was limited to the six OpenIG services across the three stack compose files.

## Change

- Added the same block directly after `restart: unless-stopped` for:
  - `stack-a/docker-compose.yml`: `openig-1`, `openig-2`
  - `stack-b/docker-compose.yml`: `openig-b1`, `openig-b2`
  - `stack-c/docker-compose.yml`: `openig-c1`, `openig-c2`

```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```

> [!success]
> All six OpenIG services now declare the host-gateway mapping explicitly in Compose.

> [!warning]
> No non-OpenIG services were changed. Redis, Vault, nginx, and app service definitions were left untouched.

## Validation

- Ran:

```bash
docker compose -f stack-a/docker-compose.yml up -d
docker compose -f stack-b/docker-compose.yml up -d
docker compose -f stack-c/docker-compose.yml up -d
docker exec sso-openig-1 cat /etc/hosts | grep host.docker.internal
```

- Runtime verification inside `sso-openig-1` returned:

```text
192.168.65.254 host.docker.internal
```

> [!success]
> `host.docker.internal` is present in `/etc/hosts` inside the recreated OpenIG container.

## Current State

- Stack A:
  - `sso-openig-1` running and healthy
  - `sso-openig-2` running and healthy
- Stack B:
  - `sso-b-openig-1` running and healthy
  - `sso-b-openig-2` running and healthy
- Stack C:
  - `stack-c-openig-c1-1` running and healthy
  - `stack-c-openig-c2-1` running and healthy

## Next Steps

1. Keep the `extra_hosts` block on any future OpenIG service that depends on `host.docker.internal`.
2. Recheck container recreation after future compose edits so the mapping is validated from inside the container, not only from the YAML.

## Files Changed

- `stack-a/docker-compose.yml`
- `stack-b/docker-compose.yml`
- `stack-c/docker-compose.yml`
- `docs/obsidian/debugging/2026-03-18-all-stacks-host-docker-internal-extra-hosts.md`
