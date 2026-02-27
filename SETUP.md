# SSO Lab — Setup Guide

## Architecture

```
Browser (port 80)  → Nginx LB → openig-1 ─┐
                               → openig-2 ─┤→ wordpress (internal)
                                            │
Browser (port 8080) ←──────────────────── Keycloak ← MySQL
```

## Prerequisites

- Docker Desktop (with Docker Compose v2)
- Ports 80 and 8080 free on localhost

## Step 1: Start the stack

```bash
cd sso-lab
docker compose up -d
```

Wait ~60s for Keycloak to initialize (MySQL + realm import).

Check status:
```bash
docker compose ps
docker compose logs keycloak --tail=20
```

## Step 2: Setup WordPress

1. Open http://localhost/wp-admin/install.php in your browser
   *(you will be redirected through Keycloak login — bypass this by going directly)*
2. Actually, first time WordPress setup needs to be done via OpenIG:
   - Access http://localhost → OpenIG redirects to Keycloak → login as `alice` / `alice123`
   - After Keycloak login, OpenIG will try to inject WP credentials for alice
   - But WordPress is not yet installed — complete the WP install wizard first

**Easier approach — direct WP setup:**
```bash
# Temporarily access WordPress directly to set up admin
docker compose exec wordpress wp core install \
  --url=http://localhost \
  --title="SSO Lab Legacy App" \
  --admin_user=alice_wp \
  --admin_password="Wp@Alice2024!" \
  --admin_email=alice@lab.local \
  --allow-root
```

Then add the second user:
```bash
docker compose exec wordpress wp user create bob_wp bob@lab.local \
  --role=editor \
  --user_pass="Wp@Bob2024!" \
  --allow-root
```

## Step 3: Configure Keycloak client callback URL

Keycloak realm is auto-imported from `keycloak/realm-export.json`.

Verify at: http://localhost:8080 → admin / admin → Realm: sso-realm → Clients → openig-client

Ensure redirect URIs include:
- `http://localhost/openid/callback`

## Step 4: Test the SSO Flow

1. Open http://localhost/wp-admin/ in an **incognito window**
2. OpenIG detects no session → redirects to Keycloak login
3. Login with `alice / alice123`
4. Keycloak redirects back to OpenIG callback (`/openid/callback`)
5. OpenIG exchanges auth code for ID token
6. OpenIG looks up alice's WP credentials from credentials.csv
7. OpenIG POSTs to wp-login.php with alice_wp credentials
8. WordPress sets WP session cookie
9. OpenIG stores WP cookie in JwtSession (encrypted, shared across HA nodes)
10. OpenIG forwards request to WordPress with WP cookie injected
11. You land in WordPress admin panel — without ever entering WP credentials

## Credential Mapping

| Keycloak Username | Keycloak Password | WP Username | WP Password |
|---|---|---|---|
| alice | alice123 | alice_wp | Wp@Alice2024! |
| bob | bob123 | bob_wp | Wp@Bob2024! |

## HA Failover Test

```bash
# Stop one OpenIG node
docker compose stop openig-1

# Make requests — Nginx routes to openig-2
# JwtSession cookie is shared so no session loss
curl -I http://localhost/wp-admin/

# Bring it back
docker compose start openig-1
```

## Useful Logs

```bash
docker compose logs nginx -f
docker compose logs openig-1 -f
docker compose logs openig-2 -f
docker compose logs keycloak -f
docker compose logs wordpress -f
```

## Common Issues

| Issue | Cause | Fix |
|---|---|---|
| Redirect loop after KC login | Issuer URL mismatch | Check `issuer` in route = `http://localhost:8080/realms/sso-realm` |
| `username = null` in Groovy | Wrong attribute key | Use `['user_info']['preferred_username']` (underscore) |
| OpenIG can't reach Keycloak token endpoint | DNS issue | tokenEndpoint must use `http://keycloak:8080` (Docker DNS) |
| JwtSession not shared across nodes | sharedSecret mismatch | Both nodes must have identical `JWT_SHARED_SECRET` env var |
| WP login 302 no cookies | WP not installed | Run WP install first (Step 2) |
