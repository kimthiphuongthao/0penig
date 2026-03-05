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

Each stack owns its own OpenIG, Vault, and Redis. Only Keycloak is shared.

## 1. Prerequisites

- Docker Desktop for Mac (or Docker Engine on Linux/Windows)
- Add to /etc/hosts (macOS/Linux) or C:\Windows\System32\drivers\etc\hosts (Windows):

```
127.0.0.1 auth.sso.local
127.0.0.1 openiga.sso.local
127.0.0.1 openigb.sso.local
```

## 2. Startup Order (Fresh Start)

Always run docker compose from the stack subdirectory — volume paths are relative.

### Step 1: Start Keycloak

```bash
cd keycloak
docker compose up -d
```

Wait until healthy:
```bash
docker ps --format 'table {{.Names}}\t{{.Status}}' | grep keycloak
# Expected: sso-keycloak   Up X minutes (healthy)
```

Keycloak is ready at http://auth.sso.local:8080 (admin / admin).
Realm `sso-realm` is auto-imported on first start. Users: alice, bob.

### Step 2: Start Stack-A (WordPress + whoami)

```bash
cd stack-a
docker compose up -d
```

Wait for OpenIG healthy:
```bash
docker ps --format 'table {{.Names}}\t{{.Status}}' | grep openig
```

**Bootstrap Vault A** (run once after first start):
```bash
docker exec sso-vault sh /vault/init/vault-bootstrap.sh
```

**WordPress first-run setup** (run once after first start):

1. Go to http://openiga.sso.local/app1/wp-admin/install.php and complete native install (any admin credentials).
2. Create app users via MySQL:

```bash
docker exec sso-mysql mysql -u wordpress -pwordpress wordpress -e "
INSERT INTO wp_users (user_login, user_pass, user_email, user_registered, user_status, display_name)
VALUES
  ('alice_wp', MD5('alice123'), 'alice@lab.local', NOW(), 0, 'Alice'),
  ('bob_wp',   MD5('bob123'),   'bob@lab.local',   NOW(), 0, 'Bob');
INSERT INTO wp_usermeta (user_id, meta_key, meta_value)
SELECT ID, 'wp_capabilities', 'a:1:{s:10:\"subscriber\";b:1;}' FROM wp_users WHERE user_login IN ('alice_wp','bob_wp');
"
```

Note: OpenIG maps Keycloak user alice → alice_wp, bob → bob_wp via Vault credentials.

Access Stack-A at http://openiga.sso.local

### Step 3: Start Stack-B (.NET + Redmine)

```bash
cd stack-b
docker compose up -d
```

Wait for all services healthy:
```bash
docker ps --format 'table {{.Names}}\t{{.Status}}' | grep -E 'b-openig|b-dotnet|b-redmine'
```

**Bootstrap Vault B** (run once after first start):
```bash
docker exec sso-b-vault sh /vault/init/vault-bootstrap.sh
```

**Redmine first-run setup** (run once after first start):

Redmine performs DB migration on first boot — wait ~2 minutes before proceeding.

Create app users via Rails console:
```bash
docker exec -it sso-b-redmine bash -c "cd /usr/src/redmine && bundle exec rails runner '
User.create!(login: \"alice\", firstname: \"Alice\", lastname: \"Lab\", mail: \"alice@lab.local\", password: \"alice123\", password_confirmation: \"alice123\", admin: false, status: 1)
User.create!(login: \"bob\", firstname: \"Bob\", lastname: \"Lab\", mail: \"bob@lab.local\", password: \"bob12345\", password_confirmation: \"bob12345\", admin: false, status: 1)
'"
```

Note: Redmine enforces minimum 8-character passwords (bob12345, not bob123).

Access Stack-B at http://openigb.sso.local:9080

## 3. Maintenance

### Check all containers
```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

### Stop a stack
```bash
cd stack-a   # or stack-b, keycloak
docker compose down
```

### Full reset (destroys all data)
```bash
cd keycloak && docker compose down -v
cd stack-a  && docker compose down -v
cd stack-b  && docker compose down -v
```

After full reset, repeat all first-run setup steps.

## 4. Testing SSO and SLO

### SSO test
1. Open http://openiga.sso.local/app1/ — login with alice (Keycloak)
2. Open http://openigb.sso.local:9080/app4/ — should auto-login (SSO, no credentials prompt)

### Cross-stack SLO test
1. Login to app1 and app4 as alice (both active)
2. Logout from app1 (WordPress logout button)
3. Refresh app4 — should redirect to Keycloak login (session kicked via Redis blacklist)

## 5. Notes

- **Volume paths**: All docker compose volume mounts use relative paths. Always run docker compose from the correct subdirectory.
- **OpenIG JWT sessions**: OpenIG uses an ephemeral JWT key pair per process. Restarting OpenIG invalidates all existing sessions — users must re-login.
- **Vault bootstrap**: Must be re-run if Vault container is recreated (docker compose down -v).
- **Groovy scripts**: OpenIG compiles Groovy scripts at startup. After editing any .groovy file, restart OpenIG containers.
