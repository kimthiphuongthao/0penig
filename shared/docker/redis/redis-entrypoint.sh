#!/bin/sh
set -eu

TEMPLATE=/etc/redis/acl.conf.template
OUTPUT=/tmp/acl.conf

escape_sed_replacement() {
  printf '%s' "$1" | sed 's/[&|\\]/\\&/g'
}

cp "$TEMPLATE" "$OUTPUT"

for app in 1 2 3 4 5 6; do
  var="REDIS_PASSWORD_APP${app}"
  value=$(printenv "$var" || true)
  if [ -z "$value" ]; then
    echo "FATAL: required env var $var is not set. Aborting." >&2
    exit 1
  fi

  escaped_value=$(escape_sed_replacement "$value")
  sed -i "s|REDIS_PASSWORD_APP${app}_PLACEHOLDER|${escaped_value}|g" "$OUTPUT"
done

exec redis-server --aclfile "$OUTPUT" --appendonly yes
