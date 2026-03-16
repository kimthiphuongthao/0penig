#!/bin/sh
# Entrypoint: substitute secret placeholders in config.json before OpenIG starts.
# Copies /opt/openig to /tmp/openig so config.json can be written without
# touching the shared host-mounted volume.

set -e

# Validate required env vars before proceeding
for var in JWT_SHARED_SECRET KEYSTORE_PASSWORD; do
  eval val=\$$var
  if [ -z "$val" ]; then
    echo "FATAL: required env var $var is not set. Aborting." >&2
    exit 1
  fi
done

SRC=/opt/openig
DST=/tmp/openig

# Copy entire openig home to writable tmpfs location (clean first to avoid stale config on restart)
rm -rf "$DST"
cp -r "$SRC" "$DST"

# Substitute placeholders in config.json
sed -i \
  -e "s|__JWT_SHARED_SECRET__|${JWT_SHARED_SECRET}|g" \
  -e "s|__KEYSTORE_PASSWORD__|${KEYSTORE_PASSWORD}|g" \
  "$DST/config/config.json"

# Point OpenIG at the processed copy
export OPENIG_BASE="$DST"

exec /usr/local/tomcat/bin/catalina.sh run
