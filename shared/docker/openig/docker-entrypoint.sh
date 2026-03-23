#!/bin/sh
set -eu

for var in JWT_SHARED_SECRET KEYSTORE_PASSWORD; do
  eval val=\$$var
  if [ -z "$val" ]; then
    echo "FATAL: required env var $var is not set. Aborting." >&2
    exit 1
  fi
done

OPENIG_SOURCE_DIR="${OPENIG_CONFIG_DIR:-/opt/openig-config}"
OPENIG_RUNTIME_DIR="${OPENIG_RUNTIME_DIR:-/tmp/openig}"
CONFIG_FILE="${OPENIG_RUNTIME_DIR}/config/config.json"

if [ ! -d "$OPENIG_SOURCE_DIR" ]; then
  echo "FATAL: expected mounted OpenIG config at $OPENIG_SOURCE_DIR. Aborting." >&2
  exit 1
fi

escape_sed_replacement() {
  printf '%s' "$1" | sed 's/[&|\\]/\\&/g'
}

rm -rf "$OPENIG_RUNTIME_DIR"
cp -R "$OPENIG_SOURCE_DIR" "$OPENIG_RUNTIME_DIR"

if [ ! -f "$CONFIG_FILE" ]; then
  echo "FATAL: expected OpenIG config at $CONFIG_FILE. Aborting." >&2
  exit 1
fi

JWT_SHARED_SECRET_ESCAPED=$(escape_sed_replacement "$JWT_SHARED_SECRET")
KEYSTORE_PASSWORD_ESCAPED=$(escape_sed_replacement "$KEYSTORE_PASSWORD")

sed -i \
  -e "s|__JWT_SHARED_SECRET__|${JWT_SHARED_SECRET_ESCAPED}|g" \
  -e "s|__KEYSTORE_PASSWORD__|${KEYSTORE_PASSWORD_ESCAPED}|g" \
  "$CONFIG_FILE"

export OPENIG_BASE="$OPENIG_RUNTIME_DIR"

exec /usr/local/tomcat/bin/catalina.sh run
