---
title: Safe Docker Cleanup With Pinned OpenIG
tags:
  - ops
  - docker
  - cleanup
  - openig
  - keycloak
  - shared-infra
date: 2026-04-01
status: guide
---

# Safe Docker Cleanup With Pinned OpenIG

Related: [[OpenIG]] [[Keycloak]] [[Vault]]

## Context

- Requested task: inspect Docker state, identify active and unused images, and recommend safe cleanup commands.
- Critical constraint: `openidentityplatform/openig:6.0.1` must not be lost because re-pull risk is not acceptable for this lab session.
- Runtime limitation: this Codex sandbox could not connect to the Docker daemon socket at `/Users/duykim/.docker/run/docker.sock`.

> [!warning]
> Live Docker state was not observable from this session. `docker ps -a`, `docker images`, and `docker system df` all failed with Docker socket permission errors.

## Declared lab images from compose

Based on `shared/docker-compose.yml` and `keycloak/docker-compose.yml`, the shared lab uses these image references:

- `nginx:alpine`
- `openidentityplatform/openig:6.0.1`
- `redis:7-alpine`
- `hashicorp/vault:1.15`
- `wordpress:latest`
- `traefik/whoami`
- `redmine:5`
- `jellyfin/jellyfin`
- `grafana/grafana`
- `phpmyadmin:latest`
- `mysql:8`
- `mysql:8.0`
- `mariadb:11`
- `quay.io/keycloak/keycloak:24.0`

> [!success]
> `openidentityplatform/openig:6.0.1` is explicitly referenced by both `shared-openig-1` and `shared-openig-2`. Treat it as pinned and exclude it from any manual image removal workflow.

## Conservative cleanup boundary

Only these cleanup commands are safe without live review because they do not remove tagged lab images or any container:

```bash
docker image prune -f
docker builder prune -f
```

- `docker image prune -f` removes dangling images only (`<none>:<none>` layers).
- `docker builder prune -f` removes build cache only.

> [!tip]
> Avoid `docker system prune`, `docker system prune -a`, `docker image prune -a`, `docker container prune`, and `docker volume prune` on this host unless the live daemon state is reviewed first.

## Exact follow-up commands on a host with Docker access

Use these to finish the classification safely on the real host:

```bash
docker ps -a --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
docker images --format 'table {{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.Size}}'
docker system df
docker image ls --filter dangling=true
docker ps --format '{{.Image}}' | sort -u
docker ps -a --format '{{.Image}}' | sort -u
```

## Current recommendation

- Run `docker image prune -f`
- Run `docker builder prune -f`
- Do not remove any tagged lab image manually until the live output confirms it has no container and is not part of the shared lab allowlist above

## Files Referenced

- `shared/docker-compose.yml`
- `keycloak/docker-compose.yml`
- `docs/obsidian/how-to/2026-04-01-safe-docker-cleanup-with-pinned-openig.md`
