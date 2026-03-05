#!/bin/bash
PASS=0; FAIL=0
ok() { echo "✓ $1"; PASS=$((PASS+1)); }
fail() { echo "✗ $1"; FAIL=$((FAIL+1)); }

echo "=== T0: Vault ==="
VAULT_PASS=$(docker exec sso-vault env VAULT_ADDR=http://127.0.0.1:8200 VAULT_TOKEN=vault-root-token \
  vault kv get -field=password secret/wp-creds/alice 2>/dev/null)
if [ -n "$VAULT_PASS" ]; then ok "alice password found: $VAULT_PASS"; else fail "Vault not accessible"; fi

echo ""
echo "=== T1: App1 SSO (openiga.sso.local/app1/) ==="
C1=/tmp/t1-cookies.txt; rm -f $C1
KC_URL=$(curl -s -c $C1 -b $C1 -D - -o /dev/null "http://openiga.sso.local/app1/wp-admin/" 2>/dev/null \
  | grep -i "^location:" | tr -d "\r" | awk '{print $2}')
curl -s -c $C1 -b $C1 "${KC_URL}" > /tmp/t1-form.html
ACTION=$(python3 -c "
import re; html=open('/tmp/t1-form.html').read()
m=re.search(r'<form[^>]+action=\"([^\"]+)\"',html)
if m: print(m.group(1).replace('&amp;','&'))
")
CB=$(curl -s -c $C1 -b $C1 -D - -o /dev/null \
  --data-urlencode "username=alice" --data-urlencode "password=alice123" -d "credentialId=" \
  "${ACTION}" 2>/dev/null | grep -i "^location:" | tr -d "\r" | awk '{print $2}')
curl -s -c $C1 -b $C1 -L -o /dev/null "${CB}" 2>/dev/null
TITLE=$(curl -s -c $C1 -b $C1 -L "http://openiga.sso.local/app1/wp-admin/" | grep -o "<title>[^<]*</title>" | head -1)
if echo "$TITLE" | grep -qi "dashboard"; then ok "WP Dashboard loaded"; else fail "Expected dashboard, got: $TITLE"; fi

echo ""
echo "=== T2: App2 SSO (openiga.sso.local/app2/) ==="
C2=/tmp/t2-cookies.txt; rm -f $C2
KC2=$(curl -s -c $C2 -b $C2 -D - -o /dev/null "http://openiga.sso.local/app2/" 2>/dev/null \
  | grep -i "^location:" | tr -d "\r" | awk '{print $2}')
curl -s -c $C2 -b $C2 "${KC2}" > /tmp/t2-form.html
A2=$(python3 -c "
import re; html=open('/tmp/t2-form.html').read()
m=re.search(r'<form[^>]+action=\"([^\"]+)\"',html)
if m: print(m.group(1).replace('&amp;','&'))
")
CB2=$(curl -s -c $C2 -b $C2 -D - -o /dev/null \
  --data-urlencode "username=alice" --data-urlencode "password=alice123" -d "credentialId=" \
  "${A2}" 2>/dev/null | grep -i "^location:" | tr -d "\r" | awk '{print $2}')
curl -s -c $C2 -b $C2 -L -o /dev/null "${CB2}" 2>/dev/null
USER=$(curl -s -c $C2 -b $C2 "http://openiga.sso.local/app2/" | grep -i "X-Authenticated-User" | head -1)
if echo "$USER" | grep -qi "alice"; then ok "App2 authenticated: $USER"; else fail "No auth. Got: $USER"; fi

echo ""
echo "=== T3: SLO ==="
curl -s -c $C1 -b $C1 -o /tmp/t3-wp.html "http://openiga.sso.local/app1/wp-admin/" 2>/dev/null
NONCE=$(python3 -c "
import re; html=open('/tmp/t3-wp.html').read()
m=re.search(r'action=logout[^\"\'<>]*_wpnonce=([a-f0-9]+)', html)
if m: print(m.group(1))
")
LOC1=$(curl -s -c $C1 -b $C1 -D - -o /dev/null \
  "http://openiga.sso.local/app1/wp-login.php?action=logout&_wpnonce=${NONCE}" 2>/dev/null \
  | grep -i "^location:" | tr -d "\r" | awk '{print $2}')
LOC2=$(curl -s -c $C1 -b $C1 -D - -o /dev/null "http://openiga.sso.local${LOC1}" 2>/dev/null \
  | grep -i "^location:" | tr -d "\r" | awk '{print $2}')
curl -s -c $C1 -b $C1 -L -o /dev/null "${LOC2}" 2>/dev/null
APP1_LOC=$(curl -s -c $C1 -b $C1 -D - -o /dev/null "http://openiga.sso.local/app1/wp-admin/" 2>/dev/null \
  | grep -i "^location:" | tr -d "\r" | awk '{print $2}')
APP2_LOC=$(curl -s -c $C1 -b $C1 -D - -o /dev/null "http://openiga.sso.local/app2/" 2>/dev/null \
  | grep -i "^location:" | tr -d "\r" | awk '{print $2}')
if echo "$APP1_LOC" | grep -q "sso-realm"; then ok "App1 post-SLO → Keycloak"; else fail "App1 SLO: $APP1_LOC"; fi
if echo "$APP2_LOC" | grep -q "sso-realm"; then ok "App2 post-SLO → Keycloak"; else fail "App2 SLO: $APP2_LOC"; fi

echo ""
echo "=== Results: $PASS pass, $FAIL fail ==="
