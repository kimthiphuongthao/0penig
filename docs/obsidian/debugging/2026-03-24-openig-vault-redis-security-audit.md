---
title: OpenIG Vault Redis Security Audit
tags:
  - security
  - audit
  - openig
  - vault
  - redis
  - shared-infra
date: 2026-03-24
status: completed
---

# OpenIG Vault Redis Security Audit

> [!danger] Verdict
> [[OpenIG]] -> [[Vault]] -> [[Redis]] in `shared/` is **not production-ready**.
> The primary blockers are plaintext transport to both [[Vault]] and [[Redis]], plaintext OAuth2 token storage in [[Redis]] with AOF persistence, source-controlled/bootstrap filesystem secret handling for [[Vault]], and over-broad secret mounts into OpenIG.

## Scope

- Read-only review of:
  - `shared/openig_home/scripts/groovy/VaultCredentialFilter.groovy`
  - `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy`
  - `shared/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy`
  - `shared/openig_home/scripts/groovy/SessionBlacklistFilter.groovy`
  - `shared/openig_home/config/routes/*.json`
  - `shared/vault/config/vault.hcl`
  - `shared/vault/init/vault-bootstrap.sh`
  - `shared/docker-compose.yml`
  - `shared/docker/redis/redis-entrypoint.sh`
  - `shared/redis/acl.conf`
- Supporting checks:
  - `git ls-files`
  - `git status --ignored`
  - `stat` on Vault credential files in the current workspace

> [!success] Controls Already Present
> - [[Vault]] and [[Redis]] are not published on host ports in `shared/docker-compose.yml:143-178`.
> - Both services are attached only to the `backend` Docker network in `shared/docker-compose.yml:158-173`.
> - [[Redis]] ACLs disable the default user and scope each OpenIG user to `~appN:*` with only `+set +get +del +exists +ping` in `shared/redis/acl.conf:1-7`.
> - OpenIG route bindings use per-app Redis ACL usernames and password env vars, for example:
>   - `shared/openig_home/config/routes/01-wordpress.json:80-87`
>   - `shared/openig_home/config/routes/02-redmine.json:75-82`
>   - `shared/openig_home/config/routes/00-backchannel-logout-app1.json:13-19`
> - AppRole files observed in this workspace are `0600`, OpenIG mounts them read-only, and `shared/.env`, `shared/vault/file/*`, and `shared/vault/keys/*` are git-ignored in the current repo state.

## Findings By Area

### 1. Transport Security: OpenIG <-> Vault

> [!danger] HIGH
> [[Vault]] transport is plain HTTP, not TLS.

- `shared/vault/config/vault.hcl:5-10` sets `tls_disable = true` and `api_addr = "http://127.0.0.1:8200"`.
- `shared/docker-compose.yml:26` sets OpenIG `VAULT_ADDR` to `http://shared-vault:8200`.
- `shared/openig_home/scripts/groovy/VaultCredentialFilter.groovy:76` reads `VAULT_ADDR`, and `:102-137` performs AppRole login plus secret fetch through `HttpURLConnection` to `http://...`.
- Because the scheme is `http://`, certificate validation is not merely skipped by policy, it is absent.
- Network exposure is internal to Docker only, but it is still a flat `backend` network shared by multiple services. Any compromise on that network gets direct reachability to `shared-vault:8200`.
- Positive note: `VAULT_ADDR` uses the internal Docker hostname `shared-vault`, not `host.docker.internal`.

### 2. Transport Security: OpenIG <-> Redis

> [!danger] HIGH
> [[Redis]] transport is plain TCP with raw sockets, not TLS.

- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy:75-89`
- `shared/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:438-455`
- `shared/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:129-152`

All three use `java.net.Socket`; there is no `SSLSocket`, no `rediss://` equivalent, and no TLS handshake or certificate validation path.

- Redis is not published to the host in `shared/docker-compose.yml:143-160`, so it is internal-only from the host-port perspective.
- Redis is still reachable directly by any container attached to the shared `backend` network.
- `TokenReferenceFilter.groovy:78` hardcodes port `6379`, which makes a future TLS port migration harder for that path.

### 3. Authentication And Authorization

> [!warning] MIXED
> Several controls are implemented correctly, but the deployment model is not least-privilege.

- Vault AppRole policies are read-only and path-scoped in `shared/vault/init/vault-bootstrap.sh:86-109`.
- AppRole role and secret ID files are created with `chmod 600` in `shared/vault/init/vault-bootstrap.sh:123-126`.
- Current workspace file permissions are `0600` for `shared/vault/file/openig-app1-role-id`, `shared/vault/file/openig-app1-secret-id`, and `shared/vault/keys/.vault-keys.admin`.
- OpenIG mounts `./vault/file:/vault/file:ro` in `shared/docker-compose.yml:60-64`, but that mount exposes the entire directory to both OpenIG nodes, not just the files needed by a single route or node.
- Because `shared-openig-1` and `shared-openig-2` run as `root` in `shared/docker-compose.yml:113` and `:130`, a gateway compromise can read every AppRole file and `audit.log` mounted under `/vault/file`.
- Vault token caching uses the correct atomic pattern with `globals.compute(...)` in `shared/openig_home/scripts/groovy/VaultCredentialFilter.groovy:83-128`.
- No Vault token renewal is implemented. On `403`, the filter clears cache and returns `502` in `shared/openig_home/scripts/groovy/VaultCredentialFilter.groovy:142-145`; the current request is not retried.
- TTL values:
  - `token_ttl=1h`, `token_max_ttl=4h`, `secret_id_ttl=72h` in `shared/vault/init/vault-bootstrap.sh:117-126`
  - Reapplied in `:217-222`
- `token_ttl=1h` is reasonable. `secret_id_ttl=72h` is only operationally safe if secret IDs are rotated. I found no automated rotation mechanism beyond rerunning bootstrap.
- Redis ACL auth is used on every reviewed route path via `redisUser` plus `redisPasswordEnvVar`, for example:
  - `shared/openig_home/config/routes/01-wordpress.json:80-87`
  - `shared/openig_home/config/routes/01-wordpress.json:120-126`
  - `shared/openig_home/config/routes/00-backchannel-logout-app1.json:13-19`
- Redis ACL design is strong for key scoping:
  - Default user off
  - Per-app users `openig-appN`
  - Per-app key prefixes `~appN:*`
  - Minimal command set only in `shared/redis/acl.conf:1-7`
- Runtime Redis passwords appear to come from ignored env files, not tracked `.env` files. `git ls-files` returned only `shared/.env.example`, while `shared/.env` is ignored. The tracked example file still contains placeholder passwords, so it must not be used verbatim.

### 4. Secrets Handling In Memory And At Rest

> [!danger] CRITICAL
> The implementation avoids direct secret logging in OpenIG, but it still handles sensitive material in ways that are not acceptable for production.

- `shared/vault/init/vault-bootstrap.sh:8-18` hardcodes seeded application passwords in source control.
- `shared/vault/init/vault-bootstrap.sh:129-178` writes those passwords into Vault secret paths.
- `shared/vault/init/vault-bootstrap.sh:187-211` creates an orphan admin token with `period=8760h` and stores it on disk at `/vault/keys/.vault-keys.admin`.
- `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy:336-338` writes the full `oauth2Entries` payload to Redis, not an opaque surrogate. That payload can contain access tokens, ID tokens, and related session material.
- `shared/docker/redis/redis-entrypoint.sh:25` enables `--appendonly yes`, so those token-bearing Redis entries are persisted to disk.
- `shared/openig_home/scripts/groovy/VaultCredentialFilter.groovy:164-170` keeps fetched credentials in request attributes, not in session storage. That is the correct scope for injection.
- I did **not** find Groovy log statements that directly print:
  - Vault tokens
  - Redis passwords
  - OIDC client secrets
  - Injected downstream passwords
- I did find logging of security-relevant identifiers:
  - `tokenRefId` and OAuth2 session key names in `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy:235-245` and `:328-354`
  - `sid`, `kid`, issuer, audience, and token timing metadata in `shared/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:281-338` and `:357-427`

### 5. Fail-Closed Behavior

> [!warning] MIXED
> The critical auth checks mostly fail closed, but session offload does not.

- Vault unreachable:
  - `shared/openig_home/scripts/groovy/VaultCredentialFilter.groovy:172-174` returns `502 Bad Gateway`
  - This is fail-closed
- Vault token expired:
  - `shared/openig_home/scripts/groovy/VaultCredentialFilter.groovy:142-145` removes cached token and returns `502`
  - There is no transparent re-login and retry in the same request
- Redis unreachable or AUTH failure in blacklist enforcement:
  - `shared/openig_home/scripts/groovy/SessionBlacklistFilter.groovy:165-167` returns `500`
  - This is fail-closed
- Redis unreachable or AUTH failure in backchannel logout:
  - `shared/openig_home/scripts/groovy/BackchannelLogoutHandler.groovy:464-467` returns `500`
  - This is fail-closed and intentionally causes upstream retry
- Redis unavailable during token restore:
  - `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy:361-363` returns `502`
  - This is fail-closed
- Redis unavailable during token offload:
  - `shared/openig_home/scripts/groovy/TokenReferenceFilter.groovy:355-357` logs and continues the response
  - This is a fail-open path for central session offload
- If Redis password were missing entirely, the code would attempt an unauthenticated socket. In the current stack this is mitigated because:
  - Compose requires the env vars
  - Routes pass `redisPasswordEnvVar`
  - Redis `default` user is disabled

### 6. Network Exposure

> [!warning] MEDIUM
> Host exposure is limited, but east-west exposure inside Docker is broad.

- Host-published ports:
  - [[Vault]]: none
  - [[Redis]]: none
- Network placement:
  - [[Vault]] backend only in `shared/docker-compose.yml:172-173`
  - [[Redis]] backend only in `shared/docker-compose.yml:158-159`
  - Neither service is attached to `frontend`
- Direct reachability:
  - Not reachable via host port publishing
  - Reachable directly by any container on the `backend` bridge, including non-security services
- This is not zero trust. It is a shared flat network.

### 7. Audit Logging

> [!info] Conditional
> Audit logging exists in the current workspace, but it is not declaratively enforced by the stack definition.

- `shared/vault/config/vault.hcl` does **not** enable an audit device.
- `shared/vault/init/vault-bootstrap.sh:68-71` enables a file audit device at `/vault/file/audit.log`.
- `shared/vault/file/audit.log` exists in the current workspace and is git-ignored.
- However, `vault-bootstrap.sh` is not referenced anywhere in `shared/docker-compose.yml`.
- Result:
  - In this workspace, audit logging appears to have been enabled at some point.
  - In a fresh deployment, audit logging depends on an external/manual bootstrap step unless another runbook executes that script.

## Production Gap Summary

| Gap | Severity | Current State | Production Requirement | Effort | Notes |
| --- | --- | --- | --- | --- | --- |
| No TLS on Vault API | HIGH | `vault.hcl` disables TLS and OpenIG uses `http://shared-vault:8200` | HTTPS with trusted internal CA, cert validation, ideally mTLS | Medium | - |
| No TLS on Redis | HIGH | Raw `Socket` connections to `shared-redis:6379`; ACL AUTH travels in plaintext | Redis TLS or sidecar/proxy TLS with certificate validation | High | - |
| OAuth2 session material stored plaintext in Redis and persisted to disk | HIGH | `TokenReferenceFilter` stores full `oauth2Entries`; Redis AOF enabled | Store opaque references only, or encrypt sensitive blobs and disable unnecessary persistence | High | Vault Transit encryption-as-a-service is the canonical fix; Redis OSS 7 has no native at-rest encryption (confirmed via official Redis FAQ). |
| Source-controlled Vault bootstrap secrets and long-lived admin token on disk | CRITICAL | Fixed app passwords in `vault-bootstrap.sh`; periodic admin token written to `/vault/keys/.vault-keys.admin` | Generate secrets outside VCS, eliminate long-lived bootstrap/admin tokens on bind mounts | Medium | - |
| Over-broad Vault secret mount into OpenIG | HIGH | Both OpenIG nodes mount the full `./vault/file` directory and run as root | Per-app/per-node secret delivery with least-privilege mounts or Docker secrets; non-root runtime | Medium | Per-file Docker Compose mount reduces directory exposure; full isolation requires K8s + Vault Agent sidecar per pod. |
| Vault bootstrap, policies, and audit logging are procedural, not declarative | MEDIUM | `vault-bootstrap.sh` exists but is not wired into compose startup | Explicit init job, gated startup, documented idempotent automation | Medium | - |
| `secret_id_ttl=72h` without observed rotation workflow | MEDIUM | Static AppRole files will age out unless bootstrap reruns | Automated AppRole rotation or a different auth pattern | Medium | - |
| Vault token expiry causes user-facing `502`, no in-request retry | MEDIUM | Cache is cleared on `403`, then request fails | Renew or re-login and retry once before failing | Medium | - |
| Flat backend network exposes Vault and Redis to all backend containers | MEDIUM | No host ports, but any backend container can reach them directly | Dedicated internal networks or network policy segmentation | Medium | - |
| Partial fail-open behavior in `TokenReferenceFilter` offload path | MEDIUM | Redis write failure logs and continues response | Explicit degraded-mode design and monitoring, preferably preserving central session guarantees | Medium | - |
| Security-relevant identifiers in logs | LOW | `sid`, `tokenRefId`, and session key names are logged | Reduce or hash identifiers in production logs | Low | - |

## Verified Research Findings (Official Sources)

### Redis At-Rest Encryption

- Official Redis FAQ position: Redis does not document native Redis OSS at-rest encryption. Short quote: "Encryption on the disk should be taken care of by the infrastructure provider ... transparent to Redis." Source: [Does Redis support Encryption at rest and in transit?](https://redis.io/faq/doc/2j6kk4yf86/does-redis-support-encryption-at-rest-and-in-transit)
- The official Redis OSS security model focuses on network isolation, ACL-based authentication, and TLS in transit. The Redis security page covers restricted network access, ACLs, and TLS support; it does not document application-level encryption or native Redis OSS disk encryption controls. Sources: [Redis security](https://redis.io/docs/latest/operate/oss_and_stack/management/security/), [Redis ACL](https://redis.io/docs/latest/operate/oss_and_stack/management/security/acl/)
- Inference from official Redis docs: at-rest encryption is documented for commercial Redis Software, not Redis OSS 7. The Redis Software security page says Redis Software protects data "in transit, at rest, and in use," while the Redis OSS FAQ/security docs stop at infrastructure-managed disk encryption plus TLS/ACL/network controls. Source: [Encryption in Redis Software](https://redis.io/docs/latest/operate/rs/security/encryption/)
- HashiCorp's official encryption-as-a-service pattern is [[Vault]] Transit. The Transit docs say Vault "doesn't store the data sent to the secrets engine," and the HTTP API exposes `POST /transit/encrypt/:name` plus `POST /transit/decrypt/:name`. For this lab, that means [[Vault]] can encrypt/decrypt session blobs while [[Redis]] stores ciphertext. The "no new containers needed" part is a lab-specific inference because [[Vault]] already exists in `shared/`. Sources: [Transit secrets engine](https://developer.hashicorp.com/vault/docs/secrets/transit), [Transit HTTP API](https://developer.hashicorp.com/vault/api-docs/secret/transit)
- For this lab, host disk encryption already exists: `fdesetup status` on this Mac returns `FileVault is On.` Apple documents FileVault as built-in volume encryption that secures data at rest on macOS. Production still needs a deliberate at-rest control choice: OS disk encryption or application-layer encryption such as Vault Transit. Source: [Volume encryption with FileVault in macOS](https://support.apple.com/en-mt/guide/security/sec4c6dc1b6e/web)

### Vault AppRole Shared Mount

- HashiCorp's AppRole guidance explicitly rejects proxying secrets to broad intermediaries. Short quote: "Secrets should never be proxied between Vault and the secret end-user." The same section says clients should only access secrets they are actually the end-user of. Source: [Best practices for AppRole authentication](https://developer.hashicorp.com/vault/docs/auth/approle/approle-pattern)
- Per-file Docker Compose bind mounts are supported. Docker's bind-mount docs say a "file or directory" can be mounted from the host, and `source` may be "the file or directory on the host." In this lab, that means each OpenIG service could mount only its specific `role_id` and `secret_id` files with a Compose-only change and no extra containers. Source: [Bind mounts](https://docs.docker.com/engine/storage/bind-mounts/)
- Lab-specific conclusion from this stack design: both shared OpenIG nodes front all six apps, so both nodes legitimately need all six credential sets. Per-file mounts reduce directory exposure, including unrelated files such as `audit.log` and bootstrap artifacts, but they do not materially reduce credential blast radius in the current shared-gateway architecture.
- Vault Agent with AppRole auto-auth is officially supported. The docs show `auto_auth` with `type = "approle"`, template rendering, and note that `remove_secret_id_file_after_reading` defaults to `true`; setting it to `false` is appropriate when lab restarts should keep using the same local SecretID file. Source: [Auto-auth with AppRole](https://developer.hashicorp.com/vault/docs/agent-and-proxy/autoauth/methods/approle)
- Lab exception: the shared mount is acceptable for this reference implementation. Production should move to per-app secret delivery. Per-file Compose mounts are the smallest Docker-only hardening step; true isolation requires one runtime unit per app with dedicated identity delivery, such as Kubernetes pods with Vault Agent sidecars.

## Overall Assessment

> [!danger] Production Decision
> Do **not** treat the current shared-infra implementation as production-ready.
> Resolve all `CRITICAL` and `HIGH` gaps first, then address the `MEDIUM` operational hardening items before promotion.

> [!tip] Priority Order
> 1. Put [[Vault]] and [[Redis]] behind authenticated TLS.
> 2. Stop storing OAuth2 token payloads as plaintext Redis JSON with AOF persistence.
> 3. Remove source-controlled/bootstrap secrets and the long-lived Vault admin token pattern.
> 4. Replace the shared `vault/file` mount with least-privilege secret distribution.
> 5. Make Vault bootstrap and audit enablement part of the deployed stack, not an external manual step.
