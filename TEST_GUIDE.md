# SSO Lab Test Guide

## Stack Overview
```
Browser/curl
    |
    v
nginx :80  (sticky sessions: hash $cookie_JSESSIONID)
    |
    +---> openig-1:8080 \
    +---> openig-2:8080  > OAuth2ClientFilter + CredentialInjector
              |
              v
       wordpress:80  (legacy app, WP Basic Auth)

       keycloak:8080 (Identity Provider, sso-realm)
```

## Services
| Service    | URL                              | Credentials      |
|------------|----------------------------------|------------------|
| App (nginx)| http://localhost/wp-admin/       | SSO via Keycloak |
| Keycloak   | http://localhost:8080            | admin/admin      |
| WordPress  | http://localhost:9090/wp-admin/  | alice/wp-alice-pass |

## Test Users (Keycloak sso-realm)
| Username | Password   | WP Role |
|----------|------------|---------|
| alice    | alice123   | Editor  |
| bob      | bob123     | Author  |

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

## Test 3: HA Sticky Session Test

```bash
# After Test 2, verify all subsequent requests go to same backend
for i in 1 2 3 4 5; do
  STATUS=$(curl -s -c $COOKIES -b $COOKIES -o /dev/null -w "%{http_code}" http://localhost/wp-admin/)
  echo "Request $i: HTTP $STATUS"
done

# Check which OpenIG node handled requests
docker compose logs openig-1 --since 1m | grep -c "GET /wp-admin" || true
docker compose logs openig-2 --since 1m | grep -c "GET /wp-admin" || true
```

**Pass criteria**: All requests HTTP 200, only one node shows log entries

---

## Test 4: HA Failover Test

```bash
# Simulate failover: stop the node serving alice
docker compose stop openig-1

# Request should failover to openig-2 (new login required as session is on openig-1)
STATUS=$(curl -s -c $COOKIES -b $COOKIES -o /dev/null -w "%{http_code}" http://localhost/wp-admin/)
echo "After failover: HTTP $STATUS (expect 302 -> re-auth if sticky session broken)"

# Restore
docker compose start openig-1
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

## Troubleshooting

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

### Check all services
```bash
docker compose ps
```

---

## Session Architecture Note

**JwtSession (IG_SSO cookie)**: Used for pre-auth state only. Full OIDC tokens (access_token + id_token) exceed the 4KB JWT cookie limit when encrypted with RSA-OAEP+AES (JWE overhead ~2.4x). This is a known OpenIG 6.0.2 limitation.

**JSESSIONID**: Used for post-auth OAuth2 token storage (server-side, container session).

**HA Strategy**: nginx `hash $cookie_JSESSIONID consistent` ensures session affinity. Each user always routes to the same OpenIG node for session lifetime.
