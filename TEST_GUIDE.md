# SSO Lab Test Guide

## Stack Overview
```
Browser/curl
    |
    v
nginx :80  (sticky sessions: hash $cookie_JSESSIONID)
    |
    +---> openig-1:8080 \
    +---> openig-2:8080  > OAuth2ClientFilter + VaultCredentialFilter
              |      |
              |      +---> sso-vault:8200 (KV v2 Credential Store)
              |
              +---> wordpress:80  (legacy app, WP Basic Auth)
              +---> whoami:80     (App2 test service)

       keycloak:8080 (Identity Provider, sso-realm)
```

> **Injected headers visible via App2**: `X-Authenticated-User` and `X-OpenIG-Node` (reports `openig-1` or `openig-2`).

Route priority (OpenIG sorts routes by name, lexicographic):
- `00-wp-logout-intercept`: intercepts `/wp-login.php?action=logout` → redirects to `/openid/logout`
- `02-app2` (`app2-whoami`): handles `/app2/` → whoami container
- `01-wordpress` (`wordpress-sso`): handles all other requests → WordPress

## Services
| Service       | URL                        | Credentials      |
|---------------|----------------------------|------------------|
| App (nginx)   | http://localhost/wp-admin/ | SSO via Keycloak |
| App2 (whoami) | http://localhost/app2/     | SSO via Keycloak |
| Keycloak      | http://localhost:8080      | admin/admin      |

## Test Users (Keycloak sso-realm)
| Username | Password   | WP Role |
|----------|------------|---------|
| alice    | alice123   | Editor  |
| bob      | bob123     | Author  |

---

## Prerequisites (Vault Setup)

Vault must be initialized and populated with credentials before testing.

1. **Start infrastructure**: `docker compose up -d`
2. **Copy bootstrap script**: `docker cp vault/init/vault-bootstrap.sh sso-vault:/tmp/`
3. **Execute bootstrap**: `docker exec sso-vault bash /tmp/vault-bootstrap.sh`

---

## Test 0: Vault Bootstrap Verification

Verify Vault is healthy and AppRole is working.

1. **Vault Health**:
   ```bash
   docker exec sso-vault curl -s http://localhost:8200/v1/sys/health | jq
   ```
   **Pass criteria**: `{"initialized":true,"sealed":false,"standby":false,...}`

2. **Credentials Readable**:
   ```bash
   docker exec sso-vault vault kv get secret/wp-creds/alice
   ```
   **Pass criteria**: JSON with `username` and `password` for alice.

---

## Test 1: E2E SSO Login (Manual Browser)

1. Open browser, clear cookies
2. Navigate to: http://localhost/wp-admin/
3. Expected: Redirect to Keycloak login page
4. Login with alice / alice123
5. Expected: Redirect back → WordPress Dashboard loads (HTTP 200)

**Pass criteria**: WordPress Dashboard title visible, logged in as alice

---

## Test 2: E2E SSO Login (curl)

```bash
# Initialize cookie jar
COOKIES=/tmp/sso-test.txt
rm -f $COOKIES

# Step 1: Hit wp-admin (should redirect to Keycloak)
KC_URL=$(curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null http://localhost/wp-admin/ \
  | grep -i "^location:" | tr -d "\r" | sed "s/location: //i")
echo "KC URL: ${KC_URL:0:80}..."

# Step 2: Get login form
ACTION=$(curl -s -c $COOKIES -b $COOKIES "$KC_URL" \
  | grep -o "action=\"[^\"]*\"" | head -1 | sed "s/action=\"//;s/\"//" | sed "s/&amp;/\&/g")

# Step 3: Submit login
CB_URL=$(curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null \
  -d "username=alice&password=alice123&credentialId=" "$ACTION" \
  | grep -i "^location:" | tr -d "\r" | sed "s/location: //i")

# Step 4: Follow callback
curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null "$CB_URL"

# Step 5: Access WP Dashboard
curl -s -c $COOKIES -b $COOKIES -o /tmp/wp-dashboard.html http://localhost/wp-admin/
grep -o "<title>[^<]*</title>" /tmp/wp-dashboard.html
```

**Pass criteria**: `<title>Dashboard ‹ SSO Lab - Legacy App — WordPress</title>`

---

## Test 3: HA Sticky Session Test (JSESSIONID-based)

> **Note**: Nginx uses **JSESSIONID** (post-auth session) for sticky routing, not the `IG_SSO` cookie.

```bash
# After Test 2, verify all subsequent requests go to same backend
# Nginx is configured to stick based on the JSESSIONID cookie
for i in 1 2 3 4 5; do
  STATUS=$(curl -s -c $COOKIES -b $COOKIES -o /dev/null -w "%{http_code}" http://localhost/wp-admin/)
  echo "Request $i: HTTP $STATUS"
done

# Check which OpenIG node handled requests
docker compose logs openig-1 --since 1m | grep -c "GET /wp-admin" || true
docker compose logs openig-2 --since 1m | grep -c "GET /wp-admin" || true

# Confirm sticky routing via App2 header injection
curl -s -c $COOKIES -b $COOKIES -o /tmp/app2.body http://localhost/app2/
grep -i "X-OpenIG-Node" /tmp/app2.body
```

**Pass criteria**: All requests HTTP 200, only one node shows log entries, App2 reports that node in `X-OpenIG-Node`

---

## Test 4: HA Failover Test

```bash
# Capture which node is active via App2 (line shows "X-OpenIG-Node: openig-X")
ACTIVE_NODE=$(curl -s -c $COOKIES -b $COOKIES http://localhost/app2/ | grep -i "X-OpenIG-Node" | awk -F': ' '{print $2}')
echo "Active node: $ACTIVE_NODE"

# Stop that node to force failover (update env if ACTIVE_NODE empty)
docker compose stop "$ACTIVE_NODE"

# Refresh App2 again to confirm the header switched to the other node
curl -s -c $COOKIES -b $COOKIES http://localhost/app2/ | grep -i "X-OpenIG-Node"

# Request should now hit the other node
# EXPECTED BEHAVIOR: HTTP 302 (Redirect to Keycloak)
# Why: JSESSIONID is local per node (no replication). 
# When the active node fails, the new node doesn't recognize the old JSESSIONID, 
# forcing a transparent OIDC re-auth.
STATUS=$(curl -s -c $COOKIES -b $COOKIES -o /dev/null -w "%{http_code}" http://localhost/wp-admin/)
echo "After failover: HTTP $STATUS (EXPECTED 302 for re-auth)"

**Pass criteria**: HTTP 302 is received. This is **EXPECTED BEHAVIOR** per OpenIG specification when using non-replicated server-side sessions.

# Restore
docker compose start "$ACTIVE_NODE"
```

---

## Test 5: Multi-User SSO

```bash
# Test bob in separate cookie jar
BOB_COOKIES=/tmp/sso-bob.txt
rm -f $BOB_COOKIES

# Repeat E2E steps with username=bob&password=bob123
# Expected: Bob gets WordPress Dashboard as Author
```

---

## Test 6: SLO (Single Logout)

```bash
COOKIES=/tmp/slo.txt
rm -f $COOKIES

# Step 1: Fresh login via /app2/ (full OIDC flow with alice/alice123)
KC_URL=$(curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null http://localhost/app2/ \
  | grep -i "^location:" | tr -d "\r" | sed "s/location: //i")
ACTION=$(curl -s -c $COOKIES -b $COOKIES "$KC_URL" \
  | grep -o "action=\"[^\"]*\"" | head -1 | sed "s/action=\"//;s/\"//" | sed "s/&amp;/\&/g")
CB_URL=$(curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null \
  -d "username=alice&password=alice123&credentialId=" "$ACTION" \
  | grep -i "^location:" | tr -d "\r" | sed "s/location: //i")
curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null "$CB_URL"

# Step 2: Verify App2 returns HTTP 200 with X-Authenticated-User: alice
curl -s -c $COOKIES -b $COOKIES -D /tmp/app2.headers -o /tmp/app2.body http://localhost/app2/
grep -i "^HTTP/1.1 200" /tmp/app2.headers
grep "X-Authenticated-User: alice" /tmp/app2.body

# Step 3: Verify /wp-admin/ returns HTTP 200 (SSO - same JSESSIONID session)
curl -s -c $COOKIES -b $COOKIES -D /tmp/wp-pre.headers -o /tmp/wp-pre.html http://localhost/wp-admin/
grep -i "^HTTP/1.1 200" /tmp/wp-pre.headers

# Step 4: Get WP logout nonce from /wp-admin/ HTML
NONCE=$(python3 -c "
import re, sys
html = open('/tmp/wp-pre.html').read()
m = re.search(r'wp-login\.php\?action=logout[^\"\'<>]*_wpnonce=([^\"\'&<>\s]+)', html)
if m: print(m.group(1).replace('&#038;','&').split('&')[0])
")
echo "Nonce: $NONCE"

# Step 5: Hit wp-login.php?action=logout → 00-wp-logout-intercept returns 302 to /openid/logout
curl -s -c $COOKIES -b $COOKIES -D /tmp/wp-logout.headers -o /dev/null \
  "http://localhost/wp-login.php?action=logout&_wpnonce=${NONCE}"
grep -i "^HTTP/1.1 302" /tmp/wp-logout.headers
grep -i "^location: .*/openid/logout" /tmp/wp-logout.headers

# Step 6: Follow /openid/logout → OpenIG clears session → 302 to Keycloak logout
OPENID_LOC=$(grep -i '^location:' /tmp/wp-logout.headers | tr -d '\r' | awk '{print $2}')
curl -s -c $COOKIES -b $COOKIES -D /tmp/openid-logout.headers -o /tmp/openid-logout.html \
  "http://localhost${OPENID_LOC}"
grep -i "^HTTP/1.1 302" /tmp/openid-logout.headers
grep -i "location: .*protocol/openid-connect/logout" /tmp/openid-logout.headers

# Step 7: Complete Keycloak logout
KC_LOGOUT=$(grep -i "^location:" /tmp/openid-logout.headers | tr -d "\r" | sed "s/location: //i")
curl -s -c $COOKIES -b $COOKIES -L -o /tmp/kc-logout.html "$KC_LOGOUT"

# Step 8: Verify /app2/ now returns 302 to Keycloak (SLO success)
curl -s -c $COOKIES -b $COOKIES -D /tmp/app2-post.headers -o /dev/null http://localhost/app2/
grep -i "^HTTP/1.1 302" /tmp/app2-post.headers
grep -i "location: .*realms/sso-realm" /tmp/app2-post.headers

# Step 9: Verify /wp-admin/ now returns 302 to Keycloak (SLO success)
curl -s -c $COOKIES -b $COOKIES -D /tmp/wp-post.headers -o /dev/null http://localhost/wp-admin/
grep -i "^HTTP/1.1 302" /tmp/wp-post.headers
grep -i "location: .*realms/sso-realm" /tmp/wp-post.headers
```

**Pass criteria**: Both `/app2/` and `/wp-admin/` redirect (HTTP 302) to Keycloak after the single logout, requiring re-authentication.

> **Note**: SLO works because OpenIG's OAuth2ClientFilter clears the JSESSIONID session tokens,
> then redirects to Keycloak's end_session endpoint (configured via defaultLogoutGoto).
> Keycloak kills the SSO session, so all protected apps require re-authentication.

---

## Troubleshooting

### Vault not returning credentials
Check OpenIG logs for Vault filter errors:
```bash
docker compose logs openig-1 | grep VaultCredential
```
Verify Vault connectivity from OpenIG:
```bash
docker compose exec openig-1 curl -i http://sso-vault:8200/v1/sys/health
```

### E2E fails at callback
Check OpenIG logs:
```bash
docker compose logs openig-1 --tail=50 | grep -i "error\|warn\|session"
```

### Keycloak not accessible
```bash
curl -s http://localhost:8080/realms/sso-realm/.well-known/openid-configuration | python3 -m json.tool | head -5
```

### WordPress not loading
```bash
docker compose logs wordpress --tail=20
```

### JWT session too large error
- **Error**: `JWT session is too large (>4KB)` in OpenIG logs or browser errors.
- **Reason**: Encrypted OIDC tokens (access + id_token) are ~4.8KB, exceeding the 4KB limit for a single cookie.
- **Check**: Verify `config.json` is using `JwtSession` only for OIDC state, and `OAuth2ClientFilter` is configured to use the default `Tomcat` session (`JSESSIONID`).

### Failed to save session (JWT too large)
If you see errors in OpenIG logs about "Header too large" or "Failed to save session":
- **Cause**: Encrypted OIDC tokens (access + id_token) exceed the 4KB limit of a single `IG_SSO` cookie.
- **Solution**: This project uses **JSESSIONID** (server-side storage) for post-auth tokens. Ensure `JwtSession` is only used for OIDC state, and `OAuth2ClientFilter` is configured to use the default server-side session.

### Check all services
```bash
docker compose ps
```

---

## Session Architecture Note (Dual-Layer)

This project implements a **Dual-Layer Session Architecture** to overcome standard cookie size limitations.

**1. JwtSession (IG_SSO cookie)**: 
- Used **strictly for pre-authentication state** (OIDC login flow state like nonce/state). 
- Keeps the OIDC flow stateless and lightweight.

**2. JSESSIONID (Server-side session)**: 
- Used for **post-authentication storage** of full OAuth2 tokens.
- **Reason**: Full tokens (access_token + id_token + refresh_token) in this lab total ~4.8KB, which exceeds the standard **4KB browser cookie limit** (and OpenIG's default `JwtSession` limit). Storing them in the Tomcat container session (`JSESSIONID`) allows for virtually unlimited token size.

**HA Strategy & Failover**: 
- **Sticky Sessions**: Nginx uses `hash $cookie_JSESSIONID consistent` to ensure a user always hits the same OpenIG node where their server-side tokens are stored.
- **Failover Behavior**: Failover results in an **HTTP 302 redirect to Keycloak**. Since `JSESSIONID` is local to each node (non-replicated in this lab), when the active node fails, the peer node will not find the session and will trigger a transparent re-authentication flow. This is the **intended behavior** according to OpenIG documentation for this architectural pattern.

**SLO**: Logout flow is intercepted by `00-wp-logout-intercept` which redirects to `/openid/logout`. This clears the local `JSESSIONID` and then triggers a global logout at Keycloak via the `end_session` endpoint.
