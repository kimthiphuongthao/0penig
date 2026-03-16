# Research Report: Shared OpenIG Gateway Deployment on Kubernetes Multi-Tenancy

**Date:** 2026-03-16
**Purpose:** Reference documentation cho việc chuyển đổi từ Docker Compose independent stacks sang shared OpenIG gateway trên Kubernetes

---

## Executive Summary

### Key Findings

| Component | Recommendation | Confidence |
|-----------|---------------|------------|
| **OpenIG Deployment** | Shared instance với per-route JwtSession | High |
| **Kubernetes Pattern** | IG behind Ingress Controller | High |
| **Vault on K8s** | Helm chart + Raft integrated storage | High |
| **Redis on K8s** | Bitnami Helm chart với Sentinel | Medium-High |
| **Migration Strategy** | Kompose + incremental | High |

### Critical Gotchas Identified

1. **OpenIG 6.0.2** không có Helm chart chính thức - phải self-manage Deployment + Service
2. **ForgeRock commercial** có ForgeOps (Skaffold/Kustomize), nhưng **OpenIdentityPlatform** chỉ có Dockerfile cơ bản
3. **Vault Helm chart** default là standalone mode - KHÔNG dùng cho production
4. **Redis key namespacing** phải tự implement - không có built-in tenant isolation ở Redis level
5. **ConfigMap hot-reload** trên K8s có delay 1-2 phút (kubelet sync)

---

## 1. OpenIG/OpenAM Shared Gateway Deployment

### 1.1 Official Sources

| Source | URL | Relevance | Notes |
|--------|-----|-----------|-------|
| OpenIdentityPlatform GitHub | https://github.com/OpenIdentityPlatform/OpenIG | HIGH | Official fork, có docker folder |
| OpenIG Docker | https://github.com/OpenIdentityPlatform/OpenIG/tree/master/openig-docker | HIGH | Dockerfile + build instructions |
| Ping Identity Archive | https://docs.pingidentity.com/archive/#pinggateway | MEDIUM | ForgeRock commercial docs (OpenIG archived) |

### 1.2 Deployment Patterns

#### Pattern A: Self-Managed Deployment (OpenIdentityPlatform)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: openig
spec:
  replicas: 2
  selector:
    matchLabels:
      app: openig
  template:
    spec:
      containers:
      - name: openig
        image: openidentityplatform/openig:latest
        ports:
        - containerPort: 8080
        volumeMounts:
        - name: config
          mountPath: /opt/openig/config
        - name: routes
          mountPath: /opt/openig/config/routes
        env:
        - name: JAVA_OPTS
          value: "-Xms512m -Xmx2g"
        livenessProbe:
          httpGet:
            path: /
            port: 8080
          initialDelaySeconds: 30
        readinessProbe:
          httpGet:
            path: /
            port: 8080
          initialDelaySeconds: 45
      volumes:
      - name: config
        configMap:
          name: openig-config
      - name: routes
        configMap:
          name: openig-routes
```

**Notes:**
- OpenIG 6.0.2 yêu cầu Java 11+
- Không có Helm chart official - phải tự create Deployment + Service
- Config hot-reload: `scanInterval` trong Router handler (default: disabled)

#### Pattern B: ForgeRock ForgeOps (Commercial)

- Sử dụng **Skaffold** + **Kustomize** thay vì Helm
- Helm charts available tại: https://github.com/forgerock-k8s/helm
- **Lưu ý:** Helm được đánh giá "Technology Preview" - không khuyến nghị production

### 1.3 Multi-Tenant/Shared Gateway Considerations

| Aspect | OpenIG 6.0.2 Capability | Notes |
|--------|------------------------|-------|
| **Per-Route Session** | ✅ Supported | `JwtSessionManager` per route |
| **Cookie Namespace** | ✅ Supported | `cookieName` unique per route |
| **Route Isolation** | ✅ Supported | Separate route files |
| **Shared Vault** | ✅ Supported | AppRole per tenant |
| **Shared Redis** | ✅ Supported | Key prefix per tenant |
| **20+ Routes** | ⚠️ No documented limit | Performance depends on RAM/CPU |

### 1.4 Community/Case Studies

**Finding:** Không tìm thấy public case studies về OpenIG shared gateway cho 20+ apps.

- ForgeRock commercial customers có tài liệu riêng (behind paywall)
- OpenIdentityPlatform community nhỏ - chưa có discussion forums active
- GitHub issues: phần lớn về bug fixes, không có architecture discussions

### 1.5 Gotchas

| Issue | Impact | Mitigation |
|-------|--------|------------|
| **No official Helm chart** | Medium | Tự create hoặc dùng generic Java app chart |
| **ConfigMap sync delay** | Low | 1-2 phút kubelet sync - không realtime |
| **JwtSession 4KB hard limit** | Medium | Per-route session đã giải quyết |
| **Route reload requires scanInterval** | Low | Set `scanInterval: "10 seconds"` trong Router |
| **config.json requires restart** | Medium | Rolling update khi thay đổi global config |

---

## 2. Kubernetes Deployment cho Identity Gateway

### 2.1 Recommended Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    LoadBalancer                          │
│                 (Cloud Provider / MetalLB)              │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                   Ingress Controller                     │
│              (nginx-ingress / Traefik)                  │
│         - TLS Termination                                │
│         - Path-based Routing                             │
│         - Rate Limiting                                  │
└─────────────────────────────────────────────────────────┘
                          │
          ┌───────────────┴───────────────┐
          ▼                               ▼
┌──────────────────┐            ┌──────────────────┐
│  OpenIG Service  │            │  Other Services  │
│  (Deployment x2) │            │                  │
└──────────────────┘            └──────────────────┘
```

### 2.2 Health Checks Configuration

```yaml
livenessProbe:
  httpGet:
    path: /
    port: 8080
  initialDelaySeconds: 15
  periodSeconds: 20
  timeoutSeconds: 5
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /openid/app1  # Route cụ thể để test end-to-end
    port: 8080
    httpHeaders:
    - name: Host
      value: app1.sso.local
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 3
  failureThreshold: 3

startupProbe:
  tcpSocket:
    port: 8080
  failureThreshold: 30
  periodSeconds: 10
```

**Best Practice:**
- Readiness probe nên test route cụ thể + check kết nối Vault/Keycloak
- Có thể tạo custom endpoint `/health` trong OpenIG route để check dependencies

### 2.3 Horizontal Pod Autoscaler (HPA)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: openig-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: openig
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 60
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 70
```

**Notes:**
- OpenIG CPU-intensive khi: JWT sign/verify, TLS handshake, Groovy script execution
- Memory spike khi: nhiều concurrent sessions, large JWT payloads
- Yêu cầu: Metrics Server installed trong cluster

### 2.4 Config Hot-Reload Pattern

**Router configuration trong `config.json`:**
```json
{
  "handler": {
    "type": "Router",
    "config": {
      "directory": "${ig.config.dir}/routes",
      "scanInterval": "10 seconds"
    }
  }
}
```

**Kubernetes ConfigMap update flow:**
```
1. kubectl apply -f routes-configmap.yaml
2. kubelet syncs ConfigMap to Pod (1-2 phút delay)
3. OpenIG detects file change via scanInterval
4. Route reloaded without restart
```

**Limitation:**
- `config.json` changes vẫn yêu cầu rolling update
- ConfigMap volume mount không immediate sync

### 2.5 Gotchas

| Issue | Impact | Mitigation |
|-------|--------|------------|
| **Sticky sessions required** | High | Dùng `JwtSession` stateless hoặc Ingress session affinity |
| **ConfigMap delay** | Medium | Test với `kubectl rollout restart` cho config.json changes |
| **Log volume** | Medium | Set log level INFO, push to Loki/ELK |
| **Memory leaks trong Groovy** | Medium | Tránh static collections, use `globals.compute()` atomic |

---

## 3. Vault on Kubernetes (Shared Model)

### 3.1 Official Sources

| Source | URL | Relevance |
|--------|-----|-----------|
| Vault Helm Chart | https://developer.hashicorp.com/vault/docs/platform/k8s/helm | HIGH |
| Vault on K8s Guide | https://developer.hashicorp.com/vault/tutorials/kubernetes/vault-on-kubernetes | HIGH |
| Vault Agent Injector | https://developer.hashicorp.com/vault/tutorials/kubernetes/vault-agent-sidecar-injection | HIGH |
| Vault HA with Raft | https://developer.hashicorp.com/vault/tutorials/ops/hello-vault-raft | HIGH |

### 3.2 Helm Chart Installation

```bash
# Add HashiCorp Helm repo
helm repo add hashicorp https://helm.releases.hashicorp.com

# Install Vault (dev mode - NOT for production)
helm install vault hashicorp/vault --set server.dev.enabled=true

# Install Vault (standalone - NOT for production)
helm install vault hashicorp/vault \
  --set server.standalone.enabled=true

# Install Vault HA with Raft (PRODUCTION)
helm install vault hashicorp/vault \
  --set server.ha.enabled=true \
  --set server.ha.raft.enabled=true \
  --set server.ha.raft.config="\"{ \"retry_join\": [\"mode=provider api_addr=https://vault-active:8200 cluster_addr=https://vault-active:8201\"] }\"" \
  --set server.ha.replicas=2
```

### 3.3 Storage Backend Comparison

| Backend | HA Support | Production Ready | Complexity |
|---------|------------|------------------|------------|
| **dev** | No | No | Lowest |
| **file** | No | No | Low |
| **Raft (integrated)** | Yes (3-5 nodes) | Yes | Medium |
| **Consul** | Yes | Yes | High |
| **Cloud (AWS/Azure/GCP)** | Yes | Yes | Medium-High |

**Recommendation:** Raft integrated storage - đơn giản nhất cho production HA

### 3.4 Vault Agent Injector Patterns

#### Pattern A: Sidecar Injection (Recommended)

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: openig-app
  annotations:
    vault.hashicorp.com/agent-inject: "true"
    vault.hashicorp.com/agent-inject-secret-approle-credentials.txt: "auth/approle/role/openig"
    vault.hashicorp.com/agent-pre-populate-only: "true"
    vault.hashicorp.com/role: "openig-role"
spec:
  containers:
  - name: openig
    image: openidentityplatform/openig
    volumeMounts:
    - name: vault-secrets
      mountPath: /vault/secrets
      readOnly: true
```

**Injected file content:**
```
role_id: <role_id>
secret_id: <secret_id>
```

#### Pattern B: External Secrets Operator

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: vault-openig-credentials
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-backend
    kind: ClusterSecretStore
  target:
    name: openig-credentials
  data:
  - secretKey: role_id
    remoteRef:
      key: auth/approle/role/openig
      property: role_id
```

### 3.5 Multi-Tenant Vault Policies

```hcl
# Policy for Tenant A (Stack A equivalent)
path "secret/data/tenant-a/*" {
  capabilities = ["read", "list"]
}

path "secret/data/tenant-a/creds/*" {
  capabilities = ["read"]
}

# Policy for Tenant B (Stack B equivalent)
path "secret/data/tenant-b/*" {
  capabilities = ["read", "list"]
}

# Common policy for shared credentials
path "secret/data/shared/jellyfin" {
  capabilities = ["read"]
}
```

### 3.6 Auto-Unseal Configuration

```yaml
server:
  ha:
    enabled: true
    raft:
      enabled: true
  autoUnseal:
    enabled: true
    awsKms:
      region: us-east-1
      kmsKey: "alias/vault-unseal-key"
```

**Providers:**
- AWS KMS
- Azure Key Vault
- GCP Cloud KMS
- Transit (another Vault instance)

### 3.7 Gotchas

| Issue | Impact | Mitigation |
|-------|--------|------------|
| **Dev mode loses data on restart** | Critical | Luôn dùng Raft HA cho production |
| **Default standalone = NOT HA** | High | Set `server.ha.enabled=true` |
| **Vault seal on K8s node failure** | High | Auto-unseal + Raft với 3-5 nodes |
| **AppRole secret_id rotation** | Medium | Automate via Vault Agent hoặc External Secrets |
| **Helm 2 not supported** | Low | Yêu cầu Helm 3.6+ |

---

## 4. Redis on Kubernetes (Shared Model)

### 4.1 Official Sources

| Source | URL | Relevance |
|--------|-----|-----------|
| Redis K8s Guide | https://redis.io/docs/latest/operate/oss_and_stack/install/install-redis/kubernetes/ | HIGH |
| Spotahome Redis Operator | https://github.com/spotahome/redis-operator | HIGH |
| Bitnami Redis Helm | https://github.com/bitnami/charts/tree/main/bitnami/redis | HIGH |

### 4.2 HA Patterns Comparison

| Pattern | Replicas | Failover | Sharding | Complexity | Use Case |
|---------|----------|----------|----------|------------|----------|
| **Master-Replica** | 3 | Manual | No | Low | Simple HA |
| **Sentinel** | 3+ | Automatic | No | Medium | HA only |
| **Cluster** | 6+ | Automatic | Yes | High | HA + Scale |
| **Operator** | Configurable | Automatic | Configurable | Medium | Production |

### 4.3 Recommended: Bitnami Redis Helm Chart

```bash
# Install Redis with Sentinel (HA mode)
helm install redis bitnami/redis \
  --set architecture=replication \
  --set sentinel.enabled=true \
  --set sentinel.quorum=2 \
  --set master.persistence.enabled=true \
  --set replica.persistence.enabled=true \
  --set auth.enabled=true \
  --set auth.password="your-redis-password" \
  --set metrics.enabled=true \
  --set metrics.serviceMonitor.enabled=true
```

**Values for production:**
```yaml
architecture: replication
sentinel:
  enabled: true
  quorum: 2
  masterSet: mymaster
  downAfterMilliseconds: 5000
  failoverTimeout: 10000
master:
  persistence:
    enabled: true
    size: 8Gi
    storageClass: standard
  resources:
    requests:
      cpu: 250m
      memory: 512Mi
    limits:
      cpu: 1000m
      memory: 2Gi
replica:
  replicaCount: 2
  persistence:
    enabled: true
    size: 8Gi
auth:
  enabled: true
  password: "<secure-password>"
  sentinel: true
```

### 4.4 Redis Operator Option

**Spotahome Redis Operator:**
```yaml
apiVersion: spotahome.com/v1
kind: RedisFailover
metadata:
  name: sso-redis
spec:
  auth:
    secretPath: redis-secret
  sentinel:
    replicas: 3
    resources:
      requests:
        cpu: 100m
        memory: 100Mi
  redis:
    replicas: 3
    resources:
      requests:
        cpu: 200m
        memory: 500Mi
    storage:
      persistentVolumeClaim:
        spec:
          accessModes: ["ReadWriteOnce"]
          resources:
            requests:
              storage: 1Gi
          storageClassName: standard
```

**Install Operator:**
```bash
helm install redis-operator redis-operator/redis-operator
```

### 4.5 Per-Tenant Key Namespacing

**Pattern cho SSO Blacklist:**
```
# Stack A
blacklist:stack-a:<session_id>

# Stack B
blacklist:stack-b:<session_id>

# Stack B - App specific
blacklist:stack-b-app3:<session_id>
blacklist:stack-b-app4:<session_id>

# Stack C
blacklist:stack-c-app5:<session_id>
blacklist:stack-c-app6:<session_id>
```

**Groovy implementation:**
```groovy
def tenantPrefix = globals["tenant_prefix"] ?: "blacklist:stack-a"
def key = "${tenantPrefix}:${sessionId}"
def redisValue = redis.get(key)
```

### 4.6 Persistence Configuration

**AOF (Append Only File) - Recommended:**
```yaml
redis:
  extraFlags:
  - "--appendonly yes"
  - "--appendfsync everysec"
  - "--appendfilename appendonly.aof"
```

**RDB Snapshots:**
```yaml
redis:
  extraFlags:
  - "--save 900 1"
  - "--save 300 10"
  - "--save 60 10000"
  - "--dbfilename dump.rdb"
```

**Hybrid (Redis 4+):**
```yaml
redis:
  extraFlags:
  - "--aof-use-rdb-preamble yes"
```

### 4.7 Memory Management

```yaml
redis:
  maxmemory: "4gb"
  maxmemoryPolicy: "noeviction"  # Returns error when full
```

**Policies:**
| Policy | Behavior | Use Case |
|--------|----------|----------|
| **noeviction** | Return error on OOM | Blacklist (không được mất data) |
| **allkeys-lru** | Evict oldest keys | Cache |
| **volatile-lru** | Evict keys with TTL | Cache với fallback |
| **allkeys-lfu** | Evict least frequently used | Hot/cold separation |

### 4.8 Gotchas

| Issue | Impact | Mitigation |
|-------|--------|------------|
| **Ephemeral storage default** | High | Enable PVC cho persistence |
| **Sentinel auth sync** | Medium | Set `auth.sentinel=true` |
| **ServiceMonitor CRD missing** | Low | Install Prometheus Operator first |
| **ConfigMap latency** | Low | Similar to OpenIG - 1-2 phút sync |
| **No native tenant isolation** | Medium | Implement key prefixing ở app layer |

---

## 5. Multi-Tenancy Patterns trên Kubernetes

### 5.1 Namespace Strategies

| Pattern | Description | Pros | Cons |
|---------|-------------|------|------|
| **Namespace-per-Tenant** | Mỗi tenant 1 namespace | Isolation tốt, quota riêng | Resource overhead, complex networking |
| **Shared Namespace** | Tất cả trong 1 namespace | Simple, efficient | Less isolation, naming conflicts |
| **Hybrid** | Shared infra, tenant app namespaces | Balance | More complex |

**Recommended cho SSO Lab:**
```
sso-gateway/          # OpenIG, shared
sso-vault/            # Vault HA cluster
sso-redis/            # Redis Sentinel
sso-keycloak/         # Keycloak
tenant-stack-a/       # Stack A apps
tenant-stack-b/       # Stack B apps
tenant-stack-c/       # Stack C apps
```

### 5.2 Network Policies

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: openig-ingress
  namespace: sso-gateway
spec:
  podSelector:
    matchLabels:
      app: openig
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: redis-access
  namespace: sso-redis
spec:
  podSelector:
    matchLabels:
      app: redis
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          access: redis
    ports:
    - protocol: TCP
      port: 6379
```

### 5.3 Resource Quotas

```yaml
apiVersion: v1
kind: ResourceQuota
metadata:
  name: tenant-a-quota
  namespace: tenant-stack-a
spec:
  hard:
    requests.cpu: "2"
    requests.memory: 4Gi
    limits.cpu: "4"
    limits.memory: 8Gi
    pods: "20"
    services: "10"
    secrets: "20"
    configmaps: "20"
```

### 5.4 Service Mesh (Optional)

| Mesh | Pros | Cons |
|------|------|------|
| **Istio** | Full-featured, mTLS, observability | Heavy, complex |
| **Linkerd** | Lightweight, simple | Less features |
| **Consul Connect** | Integrated with Vault | Additional dependency |

**Recommendation:** KHÔNG cần cho lab - chỉ xét khi production với yêu cầu mTLS strict

### 5.5 Monitoring per-Tenant

**Prometheus labels:**
```yaml
scrape_configs:
- job_name: 'openig'
  kubernetes_sd_configs:
  - role: pod
  relabel_configs:
  - source_labels: [__meta_kubernetes_pod_label_tenant]
    target_label: tenant
```

**Grafana dashboard variables:**
```
tenant: label_values(openig_request_duration_seconds_count, tenant)
```

---

## 6. Migration Patterns (Docker Compose → Kubernetes)

### 6.1 Official Sources

| Source | URL | Relevance |
|--------|-----|-----------|
| Kubernetes Docs | https://kubernetes.io/docs/tasks/configure-pod-container/translate-compose-kubernetes/ | HIGH |
| Kompose | https://kompose.io/user-guide/ | HIGH |

### 6.2 Migration Tools

#### Kompose (Recommended)

**Installation:**
```bash
# macOS
brew install kompose

# Linux
curl -L https://github.com/kubernetes/kompose/releases/download/v1.34.0/kompose-linux-amd64 -o kompose
chmod +x kompose
sudo mv ./kompose /usr/local/bin/kompose
```

**Usage:**
```bash
# Convert
kompose convert -f docker-compose.yml

# Convert with specific options
kompose convert \
  -f docker-compose.yml \
  --out k8s/ \
  --controller deployment \
  --with-konnectivity
```

**Generated files:**
```
k8s/
├── openig-deployment.yaml
├── openig-service.yaml
├── vault-statefulset.yaml
├── vault-service.yaml
├── redis-statefulset.yaml
└── redis-service.yaml
```

### 6.3 Migration Strategies

#### Strategy A: Big Bang (NOT Recommended)

```
Day 1: Stop all Docker Compose stacks
Day 2: Deploy entire K8s manifest
Day 3: Test and fix
```

**Risks:**
- Single point of failure
- Hard to rollback
- Difficult debugging

#### Strategy B: Incremental (Recommended)

```
Phase 1: Deploy shared infra (Vault, Redis) on K8s
Phase 2: Point existing Docker Compose to K8s infra
Phase 3: Migrate OpenIG to K8s (1 stack at a time)
Phase 4: Migrate apps to K8s (optional)
Phase 5: Decommission Docker Compose
```

**Benefits:**
- Lower risk
- Easy rollback per phase
- Learn as you go

#### Strategy C: Hybrid/Parallel

```
Phase 1: Deploy K8s cluster alongside Docker
Phase 2: Run both in parallel with shared external DB
Phase 3: Cutover traffic via DNS/LoadBalancer
Phase 4: Decommission Docker
```

### 6.4 Canary Deployment cho Gateway

```yaml
# Canary Service (10% traffic)
apiVersion: v1
kind: Service
metadata:
  name: openig-canary
spec:
  selector:
    app: openig
    version: v2
  ports:
  - port: 8080
---
# Stable Service (90% traffic)
apiVersion: v1
kind: Service
metadata:
  name: openig-stable
spec:
  selector:
    app: openig
    version: v1
  ports:
  - port: 8080
---
# Ingress with weighted routing
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: openig-ingress
  annotations:
    nginx.ingress.kubernetes.io/canary: "true"
    nginx.ingress.kubernetes.io/canary-weight: "10"
spec:
  rules:
  - host: openig.sso.local
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: openig-canary
            port:
              number: 8080
```

### 6.5 Rollback Strategies

#### Rollback via Deployment History

```bash
# Check rollout history
kubectl rollout history deployment/openig

# Rollback to previous version
kubectl rollout undo deployment/openig

# Rollback to specific revision
kubectl rollout undo deployment/openig --to-revision=2
```

#### Rollback via Helm

```bash
# List releases
helm list -n sso-gateway

# Rollback to previous revision
helm rollback vault -n sso-vault

# Rollback to specific revision
helm rollback vault 3 -n sso-vault
```

### 6.6 Migration Checklist

```markdown
## Pre-Migration
- [ ] K8s cluster ready (1.31+)
- [ ] Helm 3.6+ installed
- [ ] kubectl configured
- [ ] Backup all Docker Compose configs
- [ ] Export Vault data (if migrating)
- [ ] Export Redis data (if migrating)

## Phase 1: Shared Infra
- [ ] Deploy Vault HA on K8s
- [ ] Initialize and unseal Vault
- [ ] Migrate Vault policies
- [ ] Deploy Redis Sentinel on K8s
- [ ] Verify Redis persistence

## Phase 2: Gateway
- [ ] Create OpenIG Deployment
- [ ] Create ConfigMaps for routes
- [ ] Externalize secrets to K8s Secrets
- [ ] Deploy OpenIG Service
- [ ] Configure Ingress
- [ ] Test SSO/SLO

## Phase 3: Apps (Optional)
- [ ] Migrate apps one by one
- [ ] Update OpenIG backend URLs
- [ ] Test each app after migration

## Post-Migration
- [ ] Verify all SSO/SLO flows
- [ ] Check monitoring/alerting
- [ ] Update documentation
- [ ] Decommission Docker Compose
```

---

## 7. Keycloak on Kubernetes (Reference)

### 7.1 Official Sources

| Source | URL |
|--------|-----|
| Keycloak Operator | https://www.keycloak.org/getting-started/getting-started-k8s |
| Codecentric Helm Chart | https://github.com/codecentric/helm-charts/tree/master/charts/keycloak |

### 7.2 Helm Installation

```bash
# Add repo
helm repo add codecentric https://codecentric.github.io/helm-charts

# Install Keycloak
helm install keycloak codecentric/keycloak \
  --set replicaCount=2 \
  --set ingress.enabled=true \
  --set ingress.hosts[0]=auth.sso.local \
  --set postgresql.enabled=true
```

---

## 8. Summary và Recommendations

### 8.1 Recommended Architecture cho SSO Lab

```yaml
# Namespace layout
namespaces:
  - sso-infra    # Vault, Redis
  - sso-gateway  # OpenIG
  - sso-idp      # Keycloak
  - tenant-a     # Stack A apps
  - tenant-b     # Stack B apps
  - tenant-c     # Stack C apps
```

### 8.2 Component Specifications

| Component | Deployment | Storage | HA |
|-----------|------------|---------|-----|
| **OpenIG** | 2+ replicas | ConfigMap | Via HPA |
| **Vault** | 3 replicas (Raft) | PVC (10Gi each) | Raft consensus |
| **Redis** | 3 replicas (Sentinel) | PVC (8Gi each) | Sentinel failover |
| **Keycloak** | 2 replicas | PostgreSQL | Via DB replication |

### 8.3 Critical Implementation Notes

1. **OpenIG:**
   - Self-manage Deployment + Service (no Helm chart)
   - Use `scanInterval` for route hot-reload
   - Per-route `JwtSessionManager` cho multi-tenant

2. **Vault:**
   - ALWAYS use Raft HA (NOT standalone)
   - Configure auto-unseal (AWS/Azure/GCP KMS)
   - Use Vault Agent Injector cho OpenIG

3. **Redis:**
   - Use Bitnami chart với Sentinel enabled
   - Enable AOF persistence
   - Implement key prefixing cho tenant isolation
   - Set `maxmemoryPolicy: "noeviction"` cho blacklist

4. **Migration:**
   - Use Kompose cho initial conversion
   - Incremental migration (NOT big bang)
   - Canary deployment cho gateway changes

### 8.4 Unresolved Questions (Need Further Research)

| Question | Priority | Notes |
|----------|----------|-------|
| OpenIG route performance at 50+ routes | Medium | No benchmark data available |
| Vault auto-unseal cost on K8s | Low | AWS KMS pricing varies |
| Redis memory growth rate with blacklist | Medium | Depends on session volume |
| Keycloak operator vs Helm chart | Low | Both viable |

---

## Appendix A: Quick Reference Commands

```bash
# === OpenIG ===
kubectl rollout restart deployment/openig -n sso-gateway
kubectl logs -f deployment/openig -n sso-gateway

# === Vault ===
kubectl exec -it vault-0 -n sso-infra -- vault status
kubectl exec -it vault-0 -n sso-infra -- vault operator unseal

# === Redis ===
kubectl exec -it redis-master-0 -n sso-infra -- redis-cli ping
kubectl exec -it redis-master-0 -n sso-infra -- redis-cli info

# === Migration ===
kompose convert -f docker-compose.yml --out k8s/
kubectl apply -f k8s/

# === Rollback ===
kubectl rollout undo deployment/openig -n sso-gateway
helm rollback vault -n sso-infra
```

---

## Appendix B: Helm Values Summary

### Vault (hashicorp/vault)
```yaml
server:
  ha:
    enabled: true
    raft:
      enabled: true
  autoUnseal:
    enabled: true
    awsKms:
      region: us-east-1
      kmsKey: "alias/vault-unseal"
```

### Redis (bitnami/redis)
```yaml
architecture: replication
sentinel:
  enabled: true
  quorum: 2
master:
  persistence:
    enabled: true
auth:
  enabled: true
  sentinel: true
```

### OpenIG (self-managed)
```yaml
# No official chart - use generic Java app chart or create custom
```

---

*Document generated from research conducted on 2026-03-16. Sources verified via WebFetch and WebSearch tools.*
