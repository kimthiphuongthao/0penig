# Task 2: Architecture Deep Review — 3 Stacks

**Agent:** architect (Opus, READ-ONLY)
**Date:** 2026-03-16

---

## Summary

Architecture is fundamentally sound for a reference solution. Stack C diverges significantly from A/B. The most impactful issues are cross-stack inconsistencies that undermine reproducibility as a template.

---

## 1. Cross-Stack Consistency

### docker-compose.yml Divergences

| Feature | Stack A | Stack B | Stack C |
|---------|---------|---------|---------|
| `platform: linux/amd64` | Yes | Yes | **Missing** |
| `user: root` | Yes | Yes | **Missing** |
| `container_name` | Explicit | Explicit | **Missing** |
| `restart: unless-stopped` | All services | All except redmine | **Only redis-c** |
| `server.xml` mount | Yes | Yes | **Missing** |
| Healthchecks on OpenIG | Missing | Yes | **Missing** |
| `KEYCLOAK_INTERNAL_URL` env | Yes | Yes | **Missing** |
| `CANONICAL_ORIGIN_*` env | **Missing** | **Missing** | Yes |
| `OPENIG_NODE_NAME` env | Yes | Yes | **Missing** |

### nginx.conf Divergences

| Feature | Stack A | Stack B | Stack C |
|---------|---------|---------|---------|
| `proxy_buffer_size` 128k | Yes | Yes | **Missing** |
| `proxy_buffers` 4 256k | Yes | Yes | **Missing** |
| `proxy_connect_timeout` 3s | Yes | Yes | **Missing** (default 60s) |
| `proxy_next_upstream` | Yes | Yes | **Only backchannel** |
| `fail_timeout` upstream | 10s | 10s | **30s** |
| `keepalive` upstream | Missing | Missing | **Yes, 16** |

### Consistent Components
- `config.json` — identical structure, correct cookie names (IG_SSO/IG_SSO_B/IG_SSO_C)
- `docker-entrypoint.sh` — byte-identical across all stacks
- Vault bootstrap scripts — structurally identical, correct parameter differences

---

## 2. HA Pattern Assessment

**ip_hash + JwtSession = architecturally sound.**

JwtSession makes design NOT dependent on ip_hash for correctness — any node can decrypt any cookie. ip_hash provides: (1) performance optimization, (2) OAuth2 state affinity during auth code exchange.

**Failure modes:**
| Scenario | Impact | Mitigation |
|----------|--------|------------|
| Node failure mid-OAuth2-flow | State mismatch, user retries | None (no shared state store) |
| Node failure post-auth | Session survives in cookie | nginx marks node down (3 failures/10s) |
| Both nodes down | Total outage | No mitigation (2-node limit) |
| NAT-heavy users | Unbalanced load | Acceptable for lab |

**Verdict:** Valid reference showing what works and what does not. Not production HA.

---

## 3. SLO Mechanism Assessment

**Keycloak backchannel → BackchannelLogoutHandler → Redis → SessionBlacklistFilter**

**Verdict: Correct and simplest approach** given OpenIG 6.0.2 constraints (no built-in backchannel logout).

Redis per-stack isolation is correct — cross-stack SLO handled by Keycloak sending separate backchannel requests to each stack.

---

## 4. Vault Integration

**Production-grade features:** AppRole auth, file-backed storage, least-privilege policies, root token revocation, admin token scoping, audit logging, secret_id_ttl=72h.

**Remaining gaps:**
- Manual unseal after Docker restart (~15 steps per stack)
- Single unseal key (no Shamir splitting)
- No TLS (plaintext token transmission on Docker network)

**Secret rotation:** clientSecret requires container restart. sharedSecret invalidates all sessions. App credentials in Vault = no restart needed.

---

## 5. Over-Engineering Assessment

2-node HA setup is **justified** for reference solution — demonstrates JwtSession sharing, ip_hash routing, failover behavior, OAuth2 state loss.

**Not over-engineered.** Current complexity is justified by teaching objectives.

---

## 6. Design Smells

| Smell | Severity | Notes |
|-------|----------|-------|
| Keycloak as SPOF | LOW | Single instance, no replication. Acknowledged lab constraint. |
| `host.docker.internal` dependency | MEDIUM | Not available on Linux Docker Engine by default |
| Hardcoded Keycloak URLs in BackchannelLogoutHandler | MEDIUM | Should use System.getenv() |
| Issuer config style divergence (hardcoded in A+C, env var in B) | MEDIUM | Stack B pattern is correct |
| No circular dependencies detected | — | Clean dependency graph |

---

## Recommendations

| # | Action | Effort | Impact |
|---|--------|--------|--------|
| 1 | Normalize Stack C docker-compose to match A/B | LOW | HIGH |
| 2 | Add proxy_buffer_size to Stack C nginx | LOW | HIGH |
| 3 | Externalize Keycloak URLs in BackchannelLogoutHandler | LOW | MEDIUM |
| 4 | Add SloHandler try-catch (Stack A + C) | LOW | MEDIUM |
| 5 | Add CANONICAL_ORIGIN env vars to A/B docker-compose | LOW | MEDIUM |
| 6 | Externalize Keycloak URLs in Stack A+C routes | MEDIUM | MEDIUM |
| 7 | Document Keycloak SPOF + host.docker.internal portability | LOW | LOW |
