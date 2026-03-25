---
title: Vault Per-App AppRole Isolation Test
tags:
  - debugging
  - vault
  - openig
  - shared-infra
  - security
date: 2026-03-24
status: verified
---

# Vault Per-App AppRole Isolation Test

Related: [[OpenIG]] [[Vault]]

## Context

- Requested task: verify that each shared-infra [[Vault]] AppRole can read only its own secret namespace and cannot read another app's namespace.
- Scope: read and verify only. No route, Groovy, bootstrap, or compose changes were applied.
- Test date: 2026-03-24.

## Config Discovery

- `shared/docker-compose.yml`
  - OpenIG containers use `VAULT_ADDR=http://shared-vault:8200`.
  - AppRole material is mounted read-only into OpenIG at `/vault/file/openig-appN-role-id` and `/vault/file/openig-appN-secret-id`.
- `shared/vault/config/vault.hcl`
  - Vault listens on `0.0.0.0:8200`.
  - `api_addr` is `http://127.0.0.1:8200`, so `docker exec` checks inside `shared-vault` should use loopback.
- `shared/vault/init/vault-bootstrap.sh`
  - Creates six AppRoles: `openig-app1` through `openig-app6`.
  - Attaches one scoped policy per AppRole.

## AppRole to Secret Mapping

| AppRole | Policy | Allowed Vault path | Seeded namespace | Notes |
| --- | --- | --- | --- | --- |
| `openig-app1` | `openig-app1-policy` | `secret/data/wp-creds/*` | `secret/wp-creds/*` | [[OpenIG]] credential injection for WordPress |
| `openig-app2` | `openig-app2-policy` | `secret/data/dummy/*` | none seeded | WhoAmI app, skipped for runtime isolation test |
| `openig-app3` | `openig-app3-policy` | `secret/data/redmine-creds/*` | `secret/redmine-creds/*` | [[OpenIG]] credential injection for Redmine |
| `openig-app4` | `openig-app4-policy` | `secret/data/jellyfin-creds/*` | `secret/jellyfin-creds/*` | [[OpenIG]] credential injection for Jellyfin |
| `openig-app5` | `openig-app5-policy` | `secret/data/grafana-creds/*` | none seeded in bootstrap | Grafana auto-provisions, skipped for runtime isolation test |
| `openig-app6` | `openig-app6-policy` | `secret/data/phpmyadmin/*` | `secret/phpmyadmin/*` | [[OpenIG]] credential injection for phpMyAdmin |

## Live Vault Verification

- Admin token source: `shared/vault/keys/.vault-keys.admin`
- Vault status from `docker exec shared-vault vault status`
  - `Initialized=true`
  - `Sealed=false`
  - `Version=1.15.6`
- `docker ps --filter name=shared-vault --format '{{.Ports}}'` returned `8200/tcp`, which confirms the port is internal to the container network and not published to the host.

> [!warning]
> The admin token can read known AppRoles individually, but it cannot enumerate them. `vault list auth/approle/role` returned `403 permission denied`.

## Live AppRole Reads

Each configured role was readable directly with the admin token:

| AppRole | Attached policy | token_ttl | token_max_ttl | secret_id_ttl |
| --- | --- | ---: | ---: | ---: |
| `openig-app1` | `openig-app1-policy` | `3600` | `14400` | `259200` |
| `openig-app2` | `openig-app2-policy` | `3600` | `14400` | `259200` |
| `openig-app3` | `openig-app3-policy` | `3600` | `14400` | `259200` |
| `openig-app4` | `openig-app4-policy` | `3600` | `14400` | `259200` |
| `openig-app5` | `openig-app5-policy` | `3600` | `14400` | `259200` |
| `openig-app6` | `openig-app6-policy` | `3600` | `14400` | `259200` |

## Isolation Test Results

Test method for each app:

1. Read `role_id` and `secret_id` from the configured `/vault/file/openig-appN-*` files.
2. Log in with `vault write auth/approle/login`.
3. Read one secret under the app's own namespace.
4. Read one secret under a different app's namespace.

| App | Own path tested | Cross-app path tested | Own secret read | Cross-app read blocked | Verdict |
| --- | --- | --- | --- | --- | --- |
| app1 (WordPress) | `secret/data/wp-creds/alice` | `secret/data/redmine-creds/alice` | OK | BLOCKED | PASS |
| app3 (Redmine) | `secret/data/redmine-creds/alice` | `secret/data/wp-creds/alice` | OK | BLOCKED | PASS |
| app4 (Jellyfin) | `secret/data/jellyfin-creds/alice` | `secret/data/phpmyadmin/alice` | OK | BLOCKED | PASS |
| app6 (phpMyAdmin) | `secret/data/phpmyadmin/alice` | `secret/data/jellyfin-creds/alice` | OK | BLOCKED | PASS |

> [!success]
> The per-app isolation objective is working for the tested runtime paths. Every tested AppRole could read its own namespace and received `403 permission denied` on a cross-app read.

## Gaps and Observations

- `app2` and `app5` were intentionally skipped based on task scope.
  - `app2` has no credential injection target.
  - `app5` is auto-provisioned and bootstrap does not seed a Grafana credential payload.
- The live secret payloads currently stored in Vault do not match every hardcoded seed literal in `shared/vault/init/vault-bootstrap.sh`.
  - This suggests later secret rotation or reseeding.
  - Isolation still passed because policy boundaries are path-scoped, not value-scoped.

> [!tip]
> If admin-role enumeration is intended, extend the `vault-admin` policy in `shared/vault/init/vault-bootstrap.sh` with `list` on `auth/approle/role`. That is not required for AppRole isolation itself, but it is required for a successful `vault list auth/approle/role`.

## Exact Enumeration Error

```text
Error listing auth/approle/role: Error making API request.

URL: GET http://127.0.0.1:8200/v1/auth/approle/role?list=true
Code: 403. Errors:

* 1 error occurred:
        * permission denied
```

## Files Changed

- `docs/obsidian/debugging/2026-03-24-vault-per-app-isolation-test.md`
