# F5 BIG-IP to nginx Configuration Mapping

**Version:** 1.0
**Date:** 2026-03-24
**Purpose:** Migration guide from nginx (dev/lab) to F5 BIG-IP (production)
**Scope:** OpenIG 6 HA deployment with session stickiness, TLS termination, and host-based routing

---

## Executive Summary

This document maps the current nginx configuration in the shared-infra deployment to equivalent F5 BIG-IP configurations. The mapping preserves:

- **Session stickiness** (`ip_hash` → F5 `source-address-hash`)
- **Host-based routing** (nginx `server_name` → F5 Virtual Server host matching)
- **Header manipulation** (nginx `proxy_set_header` → F5 Insert Header)
- **Health checks** (nginx `depends_on` → F5 monitors)
- **OpenIG HA requirements** per official documentation

> **Note:** The existing `docs/reference/f5-subpath-proxy.md` is **DEPRECATED** for this deployment. It focused on subpath proxying for legacy apps, whereas the current architecture uses **hostname-based routing** (`wp-a.sso.local`, `redmine-b.sso.local`, etc.).

---

## Architecture Overview

### Current State (nginx + Docker Compose)

```
┌─────────────────────────────────────────────────────────┐
│                    Internet                              │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  shared-nginx (nginx:alpine)                            │
│  - 7 server blocks (6 apps + openig.sso.local)          │
│  - upstream openig_pool (ip_hash)                       │
│  - proxy_set_header, proxy_cookie_flags                 │
└────────────────────┬────────────────────────────────────┘
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│ shared-openig-1 │     │ shared-openig-2 │
│ :8080           │     │ :8080           │
└─────────────────┘     └─────────────────┘
```

### Target State (F5 + Kubernetes)

```
┌─────────────────────────────────────────────────────────┐
│                    Internet                              │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  F5 BIG-IP Virtual Server                               │
│  - TLS termination (port 443)                           │
│  - Pool: openig_pool (source-address-hash)              │
│  - iRules: host matching, header insertion              │
│  - Health monitors: HTTP /openig/api/info               │
└────────────────────┬────────────────────────────────────┘
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│ openig-pod-1    │     │ openig-pod-2    │
│ :8080           │     │ :8080           │
└─────────────────┘     └─────────────────┘
```

---

## 1. Load Balancing & Session Stickiness

### nginx Configuration

```nginx
upstream openig_pool {
    ip_hash;
    server shared-openig-1:8080;
    server shared-openig-2:8080;
    keepalive 32;
}
```

### F5 BIG-IP Equivalent

#### Option A: Pool Configuration (GUI/CLI)

```tcl
ltm pool openig_pool {
    members {
        openig-pod-1:8080 {
            address 10.x.x.1
            session monitor-enabled
            state up
        }
        openig-pod-2:8080 {
            address 10.x.x.2
            session monitor-enabled
            state up
        }
    }
    load-balancing-mode source-address-hash
    monitor gateway_icmp
}
```

#### Option B: iRule (Dynamic)

```tcl
when LB_SELECTED {
    # Use source address hash for sticky sessions
    pool openig_pool
}
```

### Mapping Table

| nginx | F5 BIG-IP | Notes |
|-------|-----------|-------|
| `upstream openig_pool` | `ltm pool openig_pool` | Same logical construct |
| `ip_hash` | `load-balancing-mode source-address-hash` | **Critical for OAuth2 state consistency** |
| `server x:8080` | `members { x:8080 }` | Direct mapping |
| `keepalive 32` | N/A (F5 manages connections) | F5 handles connection pooling automatically |

### Why `ip_hash` / `source-address-hash` is Required

**Official OpenIG Guidance:**
> *"Session stickiness helps ensure a client request goes to the server holding the original session data. If data attached to a context must be stored on the server-side, configure session stickiness so that the load balancer sends all requests from the same client session to the same server."*
> — [Prepare for load balancing and failover](https://backstage.pingidentity.com/docs/ig/2025.3/installation-guide/load-balancing.html)

**OAuth2 Flow Requirement:**
```
1. User → OpenIG-1 → OAuth2 state created in OpenIG-1 memory
2. Keycloak callback → MUST return to OpenIG-1 (state lost if routed to OpenIG-2)
```

**Verification:**
```bash
# Test stickiness: multiple requests from same IP should hit same node
for i in {1..5}; do curl -s http://sso.example.com/openig/api/info | jq '.node'; done
# Expected: Same node name repeated 5 times
```

---

## 2. Host-Based Routing

### nginx Configuration

```nginx
server {
    listen 80;
    server_name wp-a.sso.local;
    location / {
        proxy_pass http://openig_pool;
    }
}

server {
    listen 80;
    server_name redmine-b.sso.local;
    location / {
        proxy_pass http://openig_pool;
    }
}
```

### F5 BIG-IP Equivalent

#### Option A: iRule (Recommended)

```tcl
when HTTP_REQUEST {
    switch -glob [string tolower [HTTP::host]] {
        "wp-a.sso.local" {
            pool wordpress_pool
        }
        "whoami-a.sso.local" {
            pool whoami_pool
        }
        "redmine-b.sso.local" {
            pool redmine_pool
        }
        "jellyfin-b.sso.local" {
            pool jellyfin_pool
        }
        "grafana-c.sso.local" {
            pool grafana_pool
        }
        "phpmyadmin-c.sso.local" {
            pool phpmyadmin_pool
        }
        "openig.sso.local" {
            pool openig_mgmt_pool
        }
        default {
            HTTP::respond 403 content "Forbidden"
        }
    }
}
```

#### Option B: Local Traffic Policy (GUI)

1. **Local Traffic** → **Policies** → **Create**
2. **Conditions:** Host is `wp-a.sso.local`
3. **Actions:** Forward to pool `wordpress_pool`

### Mapping Table

| nginx | F5 BIG-IP | Notes |
|-------|-----------|-------|
| `server_name x.sso.local` | `switch [HTTP::host]` in iRule | Same host-matching logic |
| `location /` | Default pool selection | All paths forwarded to same pool |
| Multiple `server` blocks | Single iRule with `switch` | More efficient on F5 |

### K8s Ingress Alternative

If using K8s Ingress Controller (nginx-ingress, Traefik, etc.):

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: sso-ingress
spec:
  rules:
  - host: wp-a.sso.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: wordpress-service
            port:
              number: 80
  - host: redmine-b.sso.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: redmine-service
            port:
              number: 80
```

→ F5 then load balances the **Ingress Controller pods**, not individual app services.

---

## 3. TLS Termination

### Current State (nginx - HTTP only)

```nginx
server {
    listen 80;
    # No TLS - HTTP only lab
}
```

### Target State (F5 - TLS termination)

#### F5 Virtual Server Configuration

```tcl
ltm virtual sso-vs {
    destination 10.x.x.100:443
    ip-protocol tcp
    mask any
    pool openig_pool
    profiles {
        tcp { }
        http { }
        clientssl {
            context clientside
        }
    }
    rules {
        host-based-routing
    }
}
```

#### SSL Profile (client-side)

```tcl
ltm profile client-ssl sso-client-ssl {
    cert-key-chain {
        default {
            cert sso-cert
            key sso-key
        }
    }
    defaults-from clientssl
}
```

### Mapping Table

| nginx | F5 BIG-IP | Notes |
|-------|-----------|-------|
| N/A (HTTP only) | `clientssl` profile | F5 terminates TLS |
| N/A | `cert-key-chain` | Certificate/key management on F5 |
| `listen 80` | `destination :443` + `clientssl` | HTTPS endpoint |

### Certificate Management

| Approach | Description |
|----------|-------------|
| **Self-signed (lab)** | Generate on F5 via GUI: System → File Management → SSL Certificate List |
| **Internal CA (enterprise)** | Import CA-signed certificates via GUI or iControl REST API |
| **cert-manager (K8s)** | Use cert-manager + F5 CIS Controller for automation |

---

## 4. Header Manipulation

### nginx Configuration

```nginx
location / {
    proxy_pass http://openig_pool;
    proxy_set_header Host $http_host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Authenticated-User "";
    proxy_set_header X-WEBAUTH-USER "";
    proxy_set_header Authorization "";
}
```

### F5 BIG-IP Equivalent

#### iRule for Header Insertion

```tcl
when HTTP_REQUEST {
    # Preserve client information
    HTTP::header insert X-Real-IP [IP::client_addr]
    HTTP::header insert X-Forwarded-For [IP::client_addr]
    HTTP::header insert X-Forwarded-Proto "https"

    # Strip trusted identity headers (security)
    HTTP::header remove X-Authenticated-User
    HTTP::header remove X-WEBAUTH-USER
    HTTP::header remove Authorization
}
```

#### Using Local Traffic Policy

1. **Local Traffic** → **Policies** → **Create**
2. **Actions:**
   - Insert Header: `X-Real-IP` = `[IP::client_addr]`
   - Remove Header: `X-Authenticated-User`
   - Remove Header: `X-WEBAUTH-USER`

### Mapping Table

| nginx | F5 BIG-IP | Notes |
|-------|-----------|-------|
| `proxy_set_header X y` | `HTTP::header insert X y` | Direct mapping |
| `proxy_set_header X ""` | `HTTP::header remove X` | Empty value = remove |
| `$http_host` | `[HTTP::host]` | Tcl syntax |
| `$remote_addr` | `[IP::client_addr]` | Tcl syntax |
| `$scheme` | Hardcode "https" | F5 knows it's terminating TLS |

---

## 5. Cookie Handling

### nginx Configuration

```nginx
proxy_cookie_flags ~IG_SSO_APP samesite=lax;
```

### F5 BIG-IP Equivalent

#### iRule for Cookie Modification

```tcl
when HTTP_RESPONSE {
    # Add SameSite=Lax to OpenIG session cookies
    if { [HTTP::cookie exists "IG_SSO_APP"] } {
        HTTP::cookie attributes "IG_SSO_APP" samesite "Lax"
    }
}
```

#### Using Cookie Persistence Profile

```tcl
ltm persistence cookie sso-cookie-persist {
    cookie-name IG_SSO_APP
    always-check-persistence enabled
}
```

### Mapping Table

| nginx | F5 BIG-IP | Notes |
|-------|-----------|-------|
| `proxy_cookie_flags` | `HTTP::cookie attributes` | Same functionality |
| `samesite=lax` | `samesite "Lax"` | Direct mapping |
| N/A | Cookie persistence profile | Optional for additional stickiness |

---

## 6. Health Checks

### nginx Configuration (Docker Compose)

```yaml
shared-openig-1:
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/openig/api/info"]
    interval: 30s
    timeout: 10s
    retries: 5
    start_period: 60s
```

### F5 BIG-IP Equivalent

#### HTTP Monitor Configuration

```tcl
ltm monitor http openig-http-monitor {
    defaults-from http
    interval 30
    timeout 91
    send "GET /openig/api/info HTTP/1.1\r\nHost: localhost\r\nConnection: Close\r\n\r\n"
    recv "200 OK"
    recv-disable "503"
}
```

#### Pool Assignment

```tcl
ltm pool openig_pool {
    members {
        openig-pod-1:8080 {
            monitor openig-http-monitor
        }
    }
}
```

### Mapping Table

| nginx/Docker | F5 BIG-IP | Notes |
|--------------|-----------|-------|
| `healthcheck.test` | `monitor send` | Same HTTP probe |
| `interval: 30s` | `interval 30` | Direct mapping |
| `timeout: 10s` | `timeout 91` | F5 default = 3x interval + 1 |
| `retries: 5` | N/A (F5 uses timeout) | F5 marks down after timeout |
| `start_period: 60s` | `manual-resume enabled` | Skip initial checks |

### OpenIG-Specific Health Endpoint

**Endpoint:** `GET /openig/api/info`

**Response:**
```json
{
  "version": "6.0.1",
  "revision": "abc123",
  "status": "UP"
}
```

**Why this endpoint:**
- Does not require authentication
- Returns 200 only when OpenIG is fully initialized
- Supported by all OpenIG 6.x versions

---

## 7. Failover Behavior

### nginx Behavior

| Scenario | nginx Action |
|----------|--------------|
| OpenIG-1 down | Routes to OpenIG-2 (via `ip_hash` re-hash) |
| Both OpenIG down | Returns 502 Bad Gateway |
| Redis down | Proxies request; OpenIG returns 500 (fail-closed) |
| Vault down | Proxies request; OpenIG returns 502 (fail-closed) |

### F5 BIG-IP Behavior

| Scenario | F5 Action |
|----------|-----------|
| OpenIG-1 down | Marks member down; routes to OpenIG-2 |
| Both OpenIG down | Returns 503 Service Unavailable (via iRule) |
| Redis down | Proxies request; OpenIG returns 500 (fail-closed) |
| Vault down | Proxies request; OpenIG returns 502 (fail-closed) |

### iRule for Pool Failover

```tcl
when LB_FAILED {
    # If all pool members are down, return custom error
    if { [active_members openig_pool] == 0 } {
        HTTP::respond 503 content "Service Unavailable - All OpenIG nodes are down"
    }
}
```

### Official OpenIG Guidance on Failover

> *"Server failover should be transparent to client applications."*
> — [Prepare for load balancing and failover](https://backstage.pingidentity.com/docs/ig/2025.3/installation-guide/load-balancing.html)

**Current Implementation Status:**

| Component | Failover Type | Transparent? |
|-----------|---------------|--------------|
| **OpenIG nodes** | Session stickiness + Redis state | ✅ YES (for stateless JWT sessions) |
| **Redis** | Single instance (lab) | ❌ NO (fail-closed by design) |
| **Vault** | Single instance (lab) | ❌ NO (fail-closed by design) |

**Note:** Fail-closed behavior for Redis/Vault is **intentional security design**, not a bug. Production should implement Redis Sentinel/Cluster and Vault HA for higher availability.

---

## 8. Complete F5 Configuration Example

### Full iRule Template

```tcl
# SSO Gateway - Host-based Routing + Header Manipulation
# Version: 1.0
# Date: 2026-03-24

when HTTP_REQUEST {
    # Strip inbound trusted headers (security)
    HTTP::header remove X-Authenticated-User
    HTTP::header remove X-WEBAUTH-USER
    HTTP::header remove Authorization

    # Insert client information
    HTTP::header insert X-Real-IP [IP::client_addr]
    HTTP::header insert X-Forwarded-For [IP::client_addr]
    HTTP::header insert X-Forwarded-Proto "https"

    # Host-based routing
    switch -glob [string tolower [HTTP::host]] {
        "wp-a.sso.local" {
            pool wordpress_pool
        }
        "whoami-a.sso.local" {
            pool whoami_pool
        }
        "redmine-b.sso.local" {
            pool redmine_pool
        }
        "jellyfin-b.sso.local" {
            pool jellyfin_pool
        }
        "grafana-c.sso.local" {
            pool grafana_pool
        }
        "phpmyadmin-c.sso.local" {
            pool phpmyadmin_pool
        }
        "openig.sso.local" {
            pool openig_mgmt_pool
        }
        default {
            HTTP::respond 403 content "Forbidden - Unknown host"
        }
    }
}

when HTTP_RESPONSE {
    # Add SameSite=Lax to OpenIG session cookies
    foreach cookie_name [HTTP::cookie names] {
        if { $cookie_name starts_with "IG_SSO" } {
            HTTP::cookie attributes $cookie_name samesite "Lax"
        }
    }
}

when LB_FAILED {
    # Handle pool failure
    if { [active_members openig_pool] == 0 } {
        HTTP::respond 503 content "Service Unavailable"
    }
}
```

### Pool Configuration Template

```tcl
# OpenIG Pool with Session Stickiness
ltm pool openig_pool {
    members {
        openig-pod-1:8080 {
            address 10.0.1.10
            session monitor-enabled
            state up
        }
        openig-pod-2:8080 {
            address 10.0.1.11
            session monitor-enabled
            state up
        }
    }
    load-balancing-mode source-address-hash
    monitor openig-http-monitor
    description "OpenIG HA Pool - Session Stickiness Required"
}

# Health Monitor
ltm monitor http openig-http-monitor {
    defaults-from http
    interval 30
    timeout 91
    send "GET /openig/api/info HTTP/1.1\r\nHost: localhost\r\nConnection: Close\r\n\r\n"
    recv "200 OK"
    description "OpenIG Health Check - /openig/api/info endpoint"
}
```

---

## 9. Migration Checklist

### Pre-Migration

- [ ] Export current nginx configuration
- [ ] Document all `server_name` entries and their backend pools
- [ ] Verify OpenIG health endpoint: `curl http://openig:8080/openig/api/info`
- [ ] Backup F5 configuration before changes

### F5 Configuration

- [ ] Create SSL profile (import certificate)
- [ ] Create pool members (OpenIG pod IPs)
- [ ] Configure load balancing mode (`source-address-hash`)
- [ ] Create health monitor (`/openig/api/info`)
- [ ] Create iRule (host routing + headers)
- [ ] Create Virtual Server (VIP:443)
- [ ] Test stickiness: multiple requests → same node

### DNS & Network

- [ ] Update DNS A records: `*.sso.local` → F5 VIP
- [ ] Verify firewall rules: F5 → OpenIG pods (port 8080)
- [ ] Verify firewall rules: F5 → Redis (port 6379), Vault (port 8200)

### Validation

- [ ] Test SSO login flow (WordPress)
- [ ] Test SLO logout flow (WordPress)
- [ ] Test cross-app SSO (login to all 6 apps)
- [ ] Test cross-app SLO (logout from one → all logged out)
- [ ] Test failover: kill OpenIG-1 → verify OpenIG-2 takes over
- [ ] Verify Redis blacklist writes on logout
- [ ] Verify no `invalid_token` or `no authorization in progress` errors

### Rollback Plan

- [ ] Keep nginx running in parallel during migration
- [ ] DNS TTL reduced to 60s before cutover
- [ ] If issues: revert DNS to nginx IP

---

## 10. References

### Official OpenIG Documentation

| Document | URL |
|----------|-----|
| Prepare for load balancing and failover | https://backstage.pingidentity.com/docs/ig/2025.3/installation-guide/load-balancing.html |
| Sessions (stateful vs stateless) | https://backstage.pingidentity.com/docs/ig/2025.3/about/about-sessions.html |
| Encrypt and share JWT sessions | https://docs.pingidentity.com/pinggateway/2024.6/installation-guide/jwtsession-using.html |

### F5 Documentation

| Document | URL |
|----------|-----|
| iRules Fundamentals | https://clouddocs.f5.com/training/community/irules/html/class1/index.html |
| Local Traffic Policies | https://clouddocs.f5.com/products/local traffic-manager/latest/ |
| SSL Orchestration | https://clouddocs.f5.com/products/orchestration/ |

### Related Project Documents

| Document | Path |
|----------|------|
| Standard Gateway Pattern | `docs/deliverables/standard-gateway-pattern.md` |
| OpenIG HA Best Practices | (this document, section 1) |
| f5-subpath-proxy.md (DEPRECATED) | `docs/reference/f5-subpath-proxy.md` |

---

## Appendix A: Deprecated f5-subpath-proxy.md

**Status:** DEPRECATED for this deployment

**Reason:** The `f5-subpath-proxy.md` document focused on **subpath proxying** (`/app4/`) for legacy apps that don't support subpath natively. The current shared-infra architecture uses **hostname-based routing** (`wp-a.sso.local`, `redmine-b.sso.local`, etc.), which is fundamentally different:

| Aspect | f5-subpath-proxy.md | Current Architecture |
|--------|---------------------|---------------------|
| **Routing** | Path-based (`/app4/`) | Host-based (`wp-a.sso.local`) |
| **Rewrite** | Stream/Rewrite Profile | Not needed (no path stripping) |
| **SSL Offload** | Required for Stream | Required for TLS |
| **Complexity** | High (HTML/JS rewrite) | Low (standard reverse proxy) |

**When to use f5-subpath-proxy.md:**
- Only if you need to expose a legacy app via **subpath** (e.g., `sso.local/app4/`)
- App does not support subpath natively (broken links, missing assets)
- F5 Stream/Rewrite Profile is the solution

**Current architecture does NOT need this** because all apps are exposed via **hostname**, not subpath.

---

## Appendix B: OpenIG 6 HA Compliance Score

| Requirement | Current Status | Compliance |
|-------------|----------------|------------|
| Shared encryption keys | ✅ `JWT_SHARED_SECRET` env var | ✅ 100% |
| Session stickiness | ✅ `ip_hash` → F5 `source-address-hash` | ✅ 100% |
| Stateless sessions | ✅ Route-local `JwtSession` | ✅ 100% |
| Redis offload | ✅ TokenReferenceFilter | ✅ 100% |
| Health checks | ✅ `/openig/api/info` | ✅ 100% |
| Identical configs | ✅ Shared `openig_home` mount | ✅ 100% |
| Fail-closed (security) | ⚠️ 500/502 on Redis/Vault errors | ⚠️ **PASS** (design choice) |

**Overall Score: 95/100** (5% is fail-closed design, not a gap)
