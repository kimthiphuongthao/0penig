# SSO Lab Test Guide

## Stack Overview
```
Browser/curl
    |
    v
[Port 80] nginx-a (sticky sessions: hash $cookie_JSESSIONID)  --> [Stack A] openig-1/2 --> wordpress (app1) + whoami (app2)
    |                                                      --> redis-a (blacklist)
    |
[Port 9080] nginx-b                                         --> [Stack B] openig-b1/b2 --> dotnet-app (app3)
                                                           --> redis-b (blacklist)

[Common] keycloak:8080 (OIDC IdP, sso-realm)
         sso-vault:8200 (KV v2 Credential Store)
```

> **Architecture Note**: This is a multi-stack SSO environment.
> - **Stack A**: `openiga.sso.local` (port 80).
> - **Stack B**: `openigb.sso.local` (port 9080).
> - **Cross-stack SLO**: Integrated via Redis-based Back-channel Logout.
> - WordPress is configured with `siteurl` = `home` = `http://openiga.sso.local/app1`.
> - The WordPress container uses Apache `Alias /app1 /var/www/html` to serve the app natively at the sub-path.
> - This prevents "URL surgery" anti-patterns and ensures the app generates correct internal links.

> **Injected headers visible via App2**: `X-Authenticated-User` and `X-OpenIG-Node` (reports `openig-1` or `openig-2`).

Route priority (OpenIG sorts routes by name, lexicographic):
- `00-backchannel-logout`: handles `/openid/app1/backchannel_logout` (Back-channel POST)
- `00-wp-logout-intercept`: intercepts `/app1/wp-login.php?action=logout` (Front-channel GET)
- `02-app2` (`app2-whoami`): handles `/app2/*` and `/openid/app2/*` → whoami container
- `01-wordpress` (`wordpress-sso`): handles `/app1/*` and `/openid/app1/*` → WordPress

## Services
| Service       | URL                                     | Credentials      |
|---------------|-----------------------------------------|------------------|
| App1 (WP)     | http://openiga.sso.local/app1/wp-admin/ | SSO via Keycloak |
| App2 (whoami) | http://openiga.sso.local/app2/          | SSO via Keycloak |
| App3 (.NET)   | http://openigb.sso.local:9080/app3/      | SSO via Keycloak |
| Keycloak Admin| http://auth.sso.local:8080              | admin/admin      |

## Test Users (Keycloak sso-realm)
| Username | Password   | WP Role | App3 Access |
|----------|------------|---------|-------------|
| alice    | alice123   | Editor  | Yes         |
| bob      | bob123     | Author  | No          |

---

## Automated Testing

The entire SSO flow can be verified automatically using the provided script:

```bash
bash tests/test-sso.sh
```

---

## Prerequisites (Vault Setup)

Vault must be initialized and populated with credentials before testing.

1. **Start infrastructure**: `docker compose up -d`
2. **Execute bootstrap**: `docker exec sso-vault bash /tmp/vault-bootstrap.sh`

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
   docker exec sso-vault env VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=vault-root-token vault kv get -field=password secret/wp-creds/alice
   ```
   **Pass criteria**: Returns the plaintext password for alice.

---

## Test 1: E2E SSO Login (Manual Browser)

1. Open browser, clear cookies
2. Navigate to: http://openiga.sso.local/app1/wp-admin/
3. Expected: Redirect to Keycloak login page
4. Login with alice / alice123
5. Expected: Redirect back → WordPress Dashboard loads (HTTP 200)

**Pass criteria**: WordPress Dashboard title visible, logged in as alice

---

## Test 2: E2E SSO Login (curl)

Verify authentication for App2 (whoami):

```bash
# Initialize cookie jar
COOKIES=/tmp/sso-test.txt
rm -f $COOKIES

# Step 1: Hit app2 (should redirect to Keycloak)
KC_URL=$(curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null http://openiga.sso.local/app2/ \
  | grep -i "^location:" | tr -d "\r" | sed "s/location: //i")

# Step 2: Get login form
ACTION=$(curl -s -c $COOKIES -b $COOKIES "$KC_URL" \
  | grep -o "action=\"[^\"]*\"" | head -1 | sed "s/action=\"//;s/\"//" | sed "s/&amp;/\&/g")

# Step 3: Submit login
CB_URL=$(curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null \
  -d "username=alice&password=alice123&credentialId=" "$ACTION" \
  | grep -i "^location:" | tr -d "\r" | sed "s/location: //i")

# Step 4: Follow callback
curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null "$CB_URL"

# Step 5: Access App2
curl -s -c $COOKIES -b $COOKIES http://openiga.sso.local/app2/ | grep "X-Authenticated-User"
```

**Pass criteria**: `X-Authenticated-User: alice` header present in response.

---

## Test 3: HA Sticky Session Test (JSESSIONID-based)

> **Note**: Nginx uses **JSESSIONID** (post-auth session) for sticky routing, not the `IG_SSO` cookie.

```bash
# After Test 2, verify all subsequent requests go to same backend
for i in 1 2 3 4 5; do
  STATUS=$(curl -s -c $COOKIES -b $COOKIES -o /dev/null -w "%{http_code}" http://openiga.sso.local/app2/)
  echo "Request $i: HTTP $STATUS"
done

# Confirm sticky routing via App2 header injection
curl -s -c $COOKIES -b $COOKIES http://openiga.sso.local/app2/ | grep -i "X-OpenIG-Node"
```

**Pass criteria**: All requests HTTP 200, App2 reports the same node in `X-OpenIG-Node`.

---

## Test 4: HA Failover Test

```bash
# Capture which node is active via App2
ACTIVE_NODE=$(curl -s -c $COOKIES -b $COOKIES http://openiga.sso.local/app2/ | grep -i "X-OpenIG-Node" | awk -F': ' '{print $2}' | tr -d '\r')
echo "Active node: $ACTIVE_NODE"

# Stop that node to force failover
docker compose stop "$ACTIVE_NODE"

# Refresh App2 again
# EXPECTED BEHAVIOR: HTTP 302 (Redirect to Keycloak)
# Why: JSESSIONID is local per node. Peer node triggers transparent re-auth.
STATUS=$(curl -s -c $COOKIES -b $COOKIES -o /dev/null -w "%{http_code}" http://openiga.sso.local/app2/)
echo "After failover: HTTP $STATUS (EXPECTED 302 for re-auth)"

# Restore
docker compose start "$ACTIVE_NODE"
```

---

## Test 5: SLO (Single Logout)

```bash
# Navigate to WP logout URL
# This triggers 00-wp-logout-intercept -> Keycloak logout
curl -s -c $COOKIES -b $COOKIES -D /tmp/logout.headers "http://openiga.sso.local/app1/wp-login.php?action=logout"

# Verify redirect to Keycloak end_session
grep -i "location: .*protocol/openid-connect/logout" /tmp/logout.headers
```

**Pass criteria**: Requesting logout in App1 redirects to Keycloak, and subsequent requests to App2 require re-authentication.

---

## Test 6: Cross-stack SLO (App1 logout kicks App3)

1. Log in to App1 (Stack A) and App3 (Stack B) in the same browser.
2. Click "Log Out" in App1 (WordPress).
3. Switch to App3 tab and refresh (F5).
4. **Pass criteria**: App3 redirects to Keycloak login page.
   → **Reason**: Keycloak sends a back-channel POST to Stack B. `BackchannelLogoutHandler` blacklists the `sid` in Redis-B.

---

## Test 7: Reverse Cross-stack SLO (App3 logout kicks App1)

1. Log in to App1 and App3.
2. Click "Log Out" in App3 (.NET).
3. Refresh App1.
4. **Pass criteria**: App1 redirects to Keycloak login page.

---

## Troubleshooting

### Keycloak Redirect URI Issues
Keycloak distinguishes between URLs with and without the `:80` port. 
**Ensure both are registered in Keycloak Admin:**
- `http://openiga.sso.local/openid/app1/*`
- `http://openiga.sso.local/openid/app1:80/*`

### Multiple Post-logout URIs
When using multiple `post_logout_redirect_uris`, Keycloak requires the `##` (double-hash) separator in the admin console.

### Redis Blacklist Check
If cross-stack SLO fails, verify the `sid` is stored in Redis:
```bash
docker exec redis-a redis-cli KEYS "blacklist:*"
```

### Nginx Host Header
Nginx must pass the original Host header correctly. Using `$http_host` (which includes the port) is safer than `$host` (which lowercases and might strip the port) for OIDC callbacks and `SessionBlacklistFilter` redirects.

---

## Session Architecture Note (Dual-Layer)

This project implements a **Dual-Layer Session Architecture** to overcome standard cookie size limitations.

**1. JwtSession (IG_SSO cookie)**: 
- Used **strictly for pre-authentication state** (OIDC login flow state like nonce/state). 
- Keeps the OIDC flow stateless and lightweight.

**2. JSESSIONID (Server-side session)**: 
- Used for **post-authentication storage** of full OAuth2 tokens.
- **Reason**: Full tokens (access_token + id_token + refresh_token) in this lab total ~4.8KB, which exceeds the standard **4KB browser cookie limit**. Storing them in the Tomcat container session (`JSESSIONID`) allows for virtually unlimited token size.

**HA Strategy & Failover**: 
- **Sticky Sessions**: Nginx uses `hash $cookie_JSESSIONID consistent` on `openiga.sso.local` to ensure a user always hits the same OpenIG node.
- **Failover Behavior**: Failover results in an **HTTP 302 redirect to Keycloak**. Since `JSESSIONID` is local to each node, when the active node fails, the peer node triggers a transparent re-authentication flow.
