# SSO Lab Monorepo Guide

Practical guide for running the SSO-Lab monorepo. This repository contains multiple stacks that share a central identity provider (Keycloak).

## Monorepo Structure

```
sso-lab/
├── keycloak/          ← Shared Keycloak (Identity Provider)
├── stack-a/           ← WordPress + whoami protected by OpenIG
├── stack-b/           ← .NET app + Redmine protected by OpenIG
└── docs/              ← Shared documentation
```

## 1. Prerequisites

- **Docker Desktop for Mac** (or Linux/Windows with Docker Compose).
- **Local DNS**: Add the following entries to your `/etc/hosts` file (macOS/Linux) or `C:\Windows\System32\drivers\etc\hosts` (Windows):

```text
127.0.0.1 auth.sso.local
127.0.0.1 openiga.sso.local
127.0.0.1 openigb.sso.local
```

## 2. Startup Order

The central Keycloak instance **must start first** as the other stacks depend on it for OIDC/SAML discovery and authentication.

1. **Keycloak**: Start first.
2. **Stack-A / Stack-B**: Can be started independently once Keycloak is healthy.

## 3. Operational Commands

Always run `docker compose` from the specific stack subdirectory to ensure relative volume paths resolve correctly.

### Start Keycloak
```bash
cd keycloak
docker compose up -d
```
*Wait for Keycloak to be ready at http://auth.sso.local:8080*

### Start Stack-A (WordPress & whoami)
```bash
cd stack-a
docker compose up -d
```
*Access via http://openiga.sso.local*

### Start Stack-B (.NET & Redmine)
```bash
cd stack-b
docker compose up -d
```
*Access via http://openigb.sso.local:9080*

## 4. Maintenance

### Checking running containers
To see all containers across all stacks:
```bash
docker ps --format "table {{.Names}}	{{.Status}}	{{.Ports}}"
```

### Stopping a stack
```bash
# Example for Stack-A
cd stack-a
docker compose down
```

### Cleanup
To remove all data volumes (resets databases):
```bash
docker compose down -v
```

## Important Note on Volume Paths
Docker volume mounts in this project use **relative paths** (e.g., `./openig_home`). You must execute all `docker compose` commands from the directory containing the `docker-compose.yml` file. Failure to do so will result in incorrect mounting or empty configurations.
