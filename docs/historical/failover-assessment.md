# Failover Assessment Report

**Project:** SSO Lab
**Date:** 2026-03-10
**Scope:** HA/Failover implementation compliance with OpenIG 6.5 best practices

---

## Executive Summary

| Aspect | Status |
|--------|--------|
| OpenIG Session Stateless | ✅ **Compliant** |
| Seamless Failover | ✅ **Working** (JwtSession enables this) |
| Nginx Stickiness | ⚠️ **Stack B Misconfigured** |
| Shared Volume | ❌ **High Risk** |
| True End-to-End HA | ❌ **Not Achieved** — Multiple SPOFs |

**Overall Assessment:** The system achieves "Failover-ready at OpenIG layer" but is not "True HA" due to single points of failure and shared volume risks.

---

## 1. Current HA Architecture

### OpenIG Nodes Configuration

| Stack | Nodes | Session Type | Shared Secret |
|-------|-------|--------------|---------------|
| A | 2 (`sso-openig-1`, `sso-openig-2`) | JwtSession | `U1NPLUxhYi1Kd3RTZWNyZXQtMzJieXRlcy1LRVlcIVwh` |
| B | 2 (`sso-b-openig-1`, `sso-b-openig-2`) | JwtSession | Configured |
| C | 2 (`stack-c-openig-c1-1`, `stack-c-openig-c2-1`) | JwtSession | Configured |

### Key Strength: JwtSession with Shared Secret

All stacks use `JwtSession` with identical `sharedSecret` across nodes. This is the **strongest design point**:

- Session state is encoded in the cookie (stateless)
- Any node can decrypt and validate cookies from any other node
- Enables seamless failover without user logout

**Configuration location:** `stack-*/openig_home/config/config.json`

```json
{
  "session": {
    "type": "JwtSession",
    "config": {
      "sharedSecret": "U1NPLUxhYi1Kd3RTZWNyZXQtMzJieXRlcy1LRVlcIVwh",
      "cookieName": "IG_SSO",
      "cookieDomain": ".sso.local"
    }
  }
}
```

---

## 2. Configuration Issues Found

### 2.1 Stack B — Nginx Misconfiguration

**File:** `stack-b/nginx/nginx.conf`

```nginx
upstream openig_backend {
    hash $cookie_JSESSIONID consistent;  # ❌ WRONG
    server sso-b-openig-1:8080;
    server sso-b-openig-2:8080;
}
```

**Problem:** OpenIG 6.5 uses `JwtSession` with cookie name `IG_SSO_B`, not `JSESSIONID`. The `JSESSIONID` cookie is for Tomcat container sessions, which OpenIG does not rely on when using JwtSession.

**Impact:**
- Stickiness does not work as intended
- Nginx falls back to round-robin
- Potential session inconsistency during OIDC flows

**Recommended Fix:**
```nginx
upstream openig_backend {
    ip_hash;  # ✅ Use ip_hash like Stack A and C
    server sso-b-openig-1:8080;
    server sso-b-openig-2:8080;
}
```

### 2.2 Shared Volume — High Risk

**Configuration:** Both OpenIG nodes mount the same host directory:

```yaml
# docker-compose.yml (all stacks)
volumes:
  - ./openig_home:/opt/openig
```

**Problems:**

| Issue | Evidence | Impact |
|-------|----------|--------|
| Access log collision | `server.xml` writes to `localhost_access_log.YYYY-MM-DD.txt` | Race condition, corrupted/interleaved logs |
| Temporary file conflicts | Both nodes use same temp directories | File locking issues, logic errors |
| Configuration conflicts | Both nodes share exact same config | Cannot have node-specific settings |

**Log Configuration in `server.xml`:**
```xml
<Valve className="org.apache.catalina.valves.AccessLogValve"
       directory="logs"
       prefix="localhost_access_log"
       suffix=".txt"
       pattern="%h %l %u %t &quot;%r&quot; %s %b %D %{OPENIG_NODE_NAME}i" />
```

Both nodes write to the same `logs/localhost_access_log.YYYY-MM-DD.txt` file.

---

## 3. Single Points of Failure (SPOF)

All critical infrastructure components have only 1 instance:

| Component | Stack A | Stack B | Stack C | Impact if Fails |
|-----------|---------|---------|---------|-----------------|
| **Nginx** | `sso-nginx` | `sso-b-nginx` | `stack-c-nginx-c-1` | Entire stack unreachable |
| **Vault** | `sso-vault` | `sso-b-vault` | `stack-c-vault-c-1` | OpenIG cannot retrieve credentials |
| **Redis** | `sso-redis-a` | `sso-b-redis-b` | `stack-c-redis-c-1` | SLO (logout) fails — sessions not blacklisted |
| **Database** | `sso-mysql` | `sso-b-mysql-redmine` | `stack-c-mariadb-1` | Application data inaccessible |

### Vault Specific Issue

**Configuration:** `vault/config/vault.hcl`
```hcl
storage "file" {
  path = "/vault/data"
}
```

- Uses file storage (not Raft/HA)
- No replication
- Single instance per stack

---

## 4. Comparison with OpenIG 6.5 Best Practices

| Best Practice | Lab Implementation | Compliance |
|---------------|-------------------|------------|
| Use JwtSession for HA | ✅ Implemented | Compliant |
| Shared secret across nodes | ✅ Implemented | Compliant |
| Session stickiness | ⚠️ Partial (Stack B broken) | Non-compliant |
| Separate logs per node | ❌ Not implemented | Non-compliant |
| Health checks | ⚠️ Passive only | Partial |
| Vault HA | ❌ Single instance | Non-compliant |

---

## 5. Recommendations

### High Priority

1. **Fix Stack B Nginx Configuration**
   - Change from `hash $cookie_JSESSIONID` to `ip_hash`
   - File: `stack-b/nginx/nginx.conf`

2. **Separate Logs by Node**
   - Mount logs to node-specific directories or use stdout
   - Leverage `OPENIG_NODE_NAME` environment variable in log paths

### Medium Priority

3. **Implement Vault HA**
   - Migrate from file storage to Raft (`integrated_storage`)
   - Deploy 3-node Vault cluster per stack

4. **Add Active Health Checks**
   - Configure nginx `health_check` for upstream OpenIG nodes

### Low Priority

5. **Node-Specific Vault Credentials**
   - Each OpenIG node should have unique `role-id`/`secret-id`
   - Improves auditability and security

---

## 6. Conclusion

The SSO Lab project implements a **partially compliant** HA architecture:

- **Strengths:** JwtSession design is correct and enables seamless failover at the OpenIG layer
- **Weaknesses:**
  - Stack B nginx misconfiguration breaks stickiness
  - Shared volumes create operational risks
  - Multiple SPOFs prevent true end-to-end HA

For a production deployment, addressing the SPOFs and shared volume issues would be required to achieve true high availability.

---

## Appendix: File References

| File | Purpose |
|------|---------|
| `stack-a/openig_home/config/config.json` | JwtSession configuration |
| `stack-b/nginx/nginx.conf` | ❌ Misconfigured upstream |
| `stack-a/nginx/nginx.conf` | ✅ Correct ip_hash configuration |
| `stack-*/docker/openig/server.xml` | Tomcat access log configuration |
| `stack-*/vault/config/vault.hcl` | Vault storage configuration |
| `stack-*/docker-compose.yml` | Volume mounts and service definitions |
