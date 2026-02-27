#!/bin/bash
COOKIES=/tmp/slo.txt
rm -f $COOKIES

echo "=== Test 6: SLO (Single Logout) ==="

echo "Step 1: Fresh login via /app2/ (full OIDC flow with alice/alice123)"
KC_URL=$(curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null http://localhost/app2/ \
  | grep -i "^location:" | tr -d "\r" | sed "s/location: //i" | sed "s/Location: //i")
ACTION=$(curl -s -c $COOKIES -b $COOKIES "$KC_URL" \
  | grep -o "action=\"[^\"]*\"" | head -1 | sed "s/action=\"//;s/\"//" | sed "s/&amp;/\&/g")
CB_URL=$(curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null \
  -d "username=alice&password=alice123&credentialId=" "$ACTION" \
  | grep -i "^location:" | tr -d "\r" | sed "s/location: //i" | sed "s/Location: //i")
curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null "$CB_URL"

echo "Step 2: Verify App2 returns HTTP 200 with X-Authenticated-User: alice"
curl -s -c $COOKIES -b $COOKIES -D /tmp/app2.headers -o /tmp/app2.body http://localhost/app2/
grep -i "^HTTP/1.1 200" /tmp/app2.headers
grep -i "X-Authenticated-User: alice" /tmp/app2.body

echo "Step 3: Verify /wp-admin/ returns HTTP 200 (SSO - same JSESSIONID session)"
curl -s -c $COOKIES -b $COOKIES -D /tmp/wp-pre.headers -o /tmp/wp-pre.html http://localhost/wp-admin/
grep -i "^HTTP/1.1 200" /tmp/wp-pre.headers

echo "Step 4: Get WP logout nonce from /wp-admin/ HTML"
NONCE=$(python3 -c "
import re, sys
html = open('/tmp/wp-pre.html').read()
m = re.search(r'wp-login\.php\?action=logout[^\"\'<>]*_wpnonce=([^\"\'&<>\s]+)', html)
if m: print(m.group(1).replace('&#038;','&').split('&')[0])
")
echo "Nonce: $NONCE"

echo "Step 5: Hit wp-login.php?action=logout → 00-wp-logout-intercept returns 302 to /openid/logout"
curl -s -c $COOKIES -b $COOKIES -D /tmp/wp-logout.headers -o /dev/null \
  "http://localhost/wp-login.php?action=logout&_wpnonce=${NONCE}"
grep -i "^HTTP/1.1 302" /tmp/wp-logout.headers
grep -i "^location: .*/openid/logout" /tmp/wp-logout.headers

echo "Step 6: Follow /openid/logout → OpenIG clears session → 302 to Keycloak logout"
OPENID_LOC=$(grep -i '^location:' /tmp/wp-logout.headers | tr -d '\r' | awk '{print $2}')
curl -s -c $COOKIES -b $COOKIES -D /tmp/openid-logout.headers -o /tmp/openid-logout.html \
  "http://localhost${OPENID_LOC}"
grep -i "^HTTP/1.1 302" /tmp/openid-logout.headers
grep -i "location: .*protocol/openid-connect/logout" /tmp/openid-logout.headers

echo "Step 7: Complete Keycloak logout"
KC_LOGOUT=$(grep -i "^location:" /tmp/openid-logout.headers | tr -d "\r" | sed "s/location: //i" | sed "s/Location: //i")
curl -s -c $COOKIES -b $COOKIES -L -o /tmp/kc-logout.html "$KC_LOGOUT"

echo "Step 8: Verify /app2/ now returns 302 to Keycloak (SLO success)"
curl -s -c $COOKIES -b $COOKIES -D /tmp/app2-post.headers -o /dev/null http://localhost/app2/
grep -i "^HTTP/1.1 302" /tmp/app2-post.headers
grep -i "location: .*realms/sso-realm" /tmp/app2-post.headers

echo "Step 9: Verify /wp-admin/ now returns 302 to Keycloak (SLO success)"
curl -s -c $COOKIES -b $COOKIES -D /tmp/wp-post.headers -o /dev/null http://localhost/wp-admin/
grep -i "^HTTP/1.1 302" /tmp/wp-post.headers
grep -i "location: .*realms/sso-realm" /tmp/wp-post.headers

echo "=== Test 6 Finished ==="
