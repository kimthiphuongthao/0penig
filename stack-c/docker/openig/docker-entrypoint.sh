#!/bin/sh
# Entrypoint: render secret placeholders into the mounted OpenIG config before
# Tomcat starts. OpenIG resolves config from /opt/openig inside this stack.

set -e

# Validate required env vars before proceeding.
for var in JWT_SHARED_SECRET KEYSTORE_PASSWORD; do
  eval val=\$$var
  if [ -z "$val" ]; then
    echo "FATAL: required env var $var is not set. Aborting." >&2
    exit 1
  fi
done

OPENIG_BASE_DIR="${OPENIG_BASE:-/opt/openig}"
CONFIG_FILE="${OPENIG_BASE_DIR}/config/config.json"

if [ ! -f "$CONFIG_FILE" ]; then
  echo "FATAL: expected OpenIG config at $CONFIG_FILE. Aborting." >&2
  exit 1
fi

escape_sed_replacement() {
  printf '%s' "$1" | sed 's/[&|\\]/\\&/g'
}

JWT_SHARED_SECRET_ESCAPED=$(escape_sed_replacement "$JWT_SHARED_SECRET")
KEYSTORE_PASSWORD_ESCAPED=$(escape_sed_replacement "$KEYSTORE_PASSWORD")

# Render secrets into the mounted config OpenIG actually reads.
sed -i \
  -e "s|__JWT_SHARED_SECRET__|${JWT_SHARED_SECRET_ESCAPED}|g" \
  -e "s|__KEYSTORE_PASSWORD__|${KEYSTORE_PASSWORD_ESCAPED}|g" \
  "$CONFIG_FILE"

exec /usr/local/tomcat/bin/catalina.sh run
