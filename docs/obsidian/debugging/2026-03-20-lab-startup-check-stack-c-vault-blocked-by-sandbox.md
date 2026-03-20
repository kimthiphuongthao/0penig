---
title: Lab Startup Check and Stack C Vault Recovery Blocked by Sandbox
tags:
  - debugging
  - stack-a
  - stack-b
  - stack-c
  - vault
  - openig
  - docker
date: 2026-03-20
status: investigated
---

# Lab Startup Check and Stack C Vault Recovery Blocked by Sandbox

Related: [[OpenIG]] [[Vault]] [[Stack A]] [[Stack B]] [[Stack C]]

## Context

- Requested task: check full lab startup status, inspect Vault state across all stacks, and fix the Stack C phpMyAdmin Vault failure.
- Runtime limitation: this Codex session cannot access the Docker daemon socket, so live `docker ps`, `docker exec`, `docker logs`, and `docker restart` commands were blocked before container inspection could start.
- Repo-side scope still allowed: inspect compose files, bootstrap scripts, persisted OpenIG route logs, and mounted Vault credential files in the workspace.

> [!warning]
> Live container state was not recoverable from this sandbox. Any container status or restart result still requires running the Docker commands from a shell with Docker socket access.

## Findings

- Stack C OpenIG is configured to authenticate to [[Vault]] with AppRole files mounted from `stack-c/openig_home/vault/role_id` and `stack-c/openig_home/vault/secret_id`.
- Stack C persisted route logs show repeated `Vault AppRole login failed with HTTP 503` in `route-11-phpmyadmin.log` and `route-11-phpmyadmin-2026-03-18.0.log`.
- That `503` is thrown by `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy` when `POST ${VAULT_ADDR}/v1/auth/approle/login` returns non-2xx.
- Stack C `docker-compose.yml` does not publish Vault to the host, so there is no localhost fallback path from this sandbox.
- Stack A and Stack B use the same recovery pattern: rerun `vault-bootstrap.sh`, regenerate AppRole material, and restart OpenIG nodes.
- Repo-side Vault key files already exist for all three stacks: `.vault-keys.admin` and `.vault-keys.unseal`.
- Repo-side Vault data directories for all three stacks already contain `.bootstrap-done`, so this is not a fresh-init or missing-bootstrap case.

> [!success]
> The Stack C user-visible phpMyAdmin message is consistent with Vault runtime unavailability. The current persisted evidence points to Vault being sealed or otherwise unavailable, not to an empty AppRole credential file.

## Evidence

- `stack-c/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
  - reads `VAULT_ROLE_ID_FILE` and `VAULT_SECRET_ID_FILE`
  - logs in with `POST /v1/auth/approle/login`
  - throws `Vault AppRole login failed with HTTP ${loginStatus}` on non-2xx
- `stack-c/openig_home/logs/route-11-phpmyadmin.log`
  - contains `java.lang.IllegalStateException: Vault AppRole login failed with HTTP 503`
- `stack-c/openig_home/logs/route-11-phpmyadmin-2026-03-18.0.log`
  - contains the same `HTTP 503` failure signature multiple times
- `stack-c/docker-compose.yml`
  - `VAULT_ADDR=http://vault-c:8200`
  - no host port published for service `vault-c`
- `.claude/rules/restart.md`
  - matches the intended recovery flow for Stacks A, B, and C
- `stack-a/vault/data`, `stack-b/vault/data`, `stack-c/vault/data`
  - each contains `.bootstrap-done`

## Recovery Path

1. Run `docker ps --format '{{.Names}}\t{{.Status}}' | sort`.
2. Run `docker exec stack-c-vault-c-1 vault status 2>&1`.
3. If Stack C Vault is sealed or AppRole refresh is needed:
   - `docker cp stack-c/vault/init/vault-bootstrap.sh stack-c-vault-c-1:/tmp/vault-bootstrap.sh`
   - `docker exec stack-c-vault-c-1 sh /tmp/vault-bootstrap.sh`
   - `ADMIN_TOKEN_C=$(cat stack-c/vault/keys/.vault-keys.admin)`
   - `docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN="$ADMIN_TOKEN_C" stack-c-vault-c-1 vault read -field=role_id auth/approle/role/openig-role-c/role-id > stack-c/openig_home/vault/role_id`
   - `docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN="$ADMIN_TOKEN_C" stack-c-vault-c-1 vault write -f -field=secret_id auth/approle/role/openig-role-c/secret-id > stack-c/openig_home/vault/secret_id`
   - `docker restart stack-c-openig-c1-1 stack-c-openig-c2-1`
4. Repeat the same status check for Stack A and Stack B Vault containers and rerun their documented bootstrap flow only if sealed or unavailable.
5. Recheck OpenIG logs after restart for new Vault errors.

> [!tip]
> If Vault returns `503` on AppRole login, prioritize `vault status` first. A stale `secret_id` more commonly surfaces as a Vault auth error other than `503`.

## Current State

- Live container table: blocked by Docker socket sandbox restriction.
- Stack A Vault sealed state: unverified live.
- Stack B Vault sealed state: unverified live.
- Stack C Vault sealed state: unverified live, but persisted OpenIG logs strongly suggest Vault unavailability or sealed state.
- OpenIG restart result: unverified live because container restart was blocked.

## Files Changed

- `docs/obsidian/debugging/2026-03-20-lab-startup-check-stack-c-vault-blocked-by-sandbox.md`
