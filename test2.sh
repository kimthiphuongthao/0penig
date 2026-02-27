#!/bin/bash
# Initialize cookie jar
COOKIES=/tmp/sso-test.txt
rm -f $COOKIES

# Step 1: Hit wp-admin (should redirect to Keycloak)
echo "1. Hit wp-admin..."
KC_URL=$(curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null http://localhost/wp-admin/ \
  | grep -i "^location:" | tr -d "\r" | sed "s/location: //i" | sed "s/Location: //i")
echo "KC URL: ${KC_URL:0:80}..."

# Step 2: Get login form
echo "2. Get login form action URL..."
ACTION=$(curl -s -c $COOKIES -b $COOKIES "$KC_URL" \
  | grep -o "action=\"[^\"]*\"" | head -1 | sed "s/action=\"//;s/\"//" | sed "s/&amp;/\&/g")
echo "ACTION: ${ACTION:0:80}..."

# Step 3: Submit login
echo "3. Submit login for alice..."
CB_URL=$(curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null \
  -d "username=alice&password=alice123&credentialId=" "$ACTION" \
  | grep -i "^location:" | tr -d "\r" | sed "s/location: //i" | sed "s/Location: //i")
echo "CALLBACK URL: ${CB_URL:0:80}..."

# Step 4: Follow callback
echo "4. Follow callback..."
curl -s -c $COOKIES -b $COOKIES -D - -o /dev/null "$CB_URL"

# Step 5: Access WP Dashboard
echo "5. Access WP Dashboard..."
curl -s -c $COOKIES -b $COOKIES -o /tmp/wp-dashboard.html http://localhost/wp-admin/
grep -o "<title>[^<]*</title>" /tmp/wp-dashboard.html
