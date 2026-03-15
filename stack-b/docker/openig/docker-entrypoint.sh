#!/bin/sh
# Entrypoint: substitute secret placeholders in config.json before OpenIG starts.
# Copies /opt/openig to /tmp/openig so config.json can be written without
# touching the shared host-mounted volume.

set -e

SRC=/opt/openig
DST=/tmp/openig

# Copy entire openig home to writable tmpfs location
cp -r "$SRC" "$DST"

# Substitute placeholders in config.json
sed -i \
  -e "s|__JWT_SHARED_SECRET__|${JWT_SHARED_SECRET}|g" \
  "$DST/config/config.json"

# Point OpenIG at the processed copy
export OPENIG_BASE="$DST"

exec /usr/local/tomcat/bin/catalina.sh run
