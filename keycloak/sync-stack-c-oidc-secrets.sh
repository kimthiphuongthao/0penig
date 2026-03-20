#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
ENV_FILE="${ENV_FILE:-$ROOT_DIR/stack-c/.env}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://127.0.0.1:8080}"
REALM="${REALM:-sso-realm}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

require_var() {
  local name=$1
  if [[ -z "${!name:-}" ]]; then
    echo "Required variable is missing: $name" >&2
    exit 1
  fi
}

require_var OIDC_CLIENT_SECRET_APP5
require_var OIDC_CLIENT_SECRET_APP6

get_token() {
  curl -fsS -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
    -d "client_id=admin-cli" \
    -d "username=$ADMIN_USER" \
    -d "password=$ADMIN_PASSWORD" \
    -d "grant_type=password" \
  | python3 -c 'import sys, json; print(json.load(sys.stdin)["access_token"])'
}

get_client_uuid() {
  local token=$1
  local client_id=$2

  curl -fsS -H "Authorization: Bearer $token" \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$client_id" \
  | python3 -c 'import json, sys; client_id = sys.argv[1]; items = json.load(sys.stdin); print(items[0]["id"]) if items else (_ for _ in ()).throw(SystemExit(f"Client not found: {client_id}"))' "$client_id"
}

update_client_secret() {
  local token=$1
  local client_uuid=$2
  local new_secret=$3

  local current_json
  current_json=$(curl -fsS -H "Authorization: Bearer $token" \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients/$client_uuid")

  local updated_json
  updated_json=$(printf '%s' "$current_json" \
    | python3 -c 'import json, sys; payload = json.load(sys.stdin); payload["secret"] = sys.argv[1]; print(json.dumps(payload))' "$new_secret")

  curl -fsS -X PUT \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients/$client_uuid" \
    -d "$updated_json" >/dev/null
}

read_client_secret() {
  local token=$1
  local client_uuid=$2

  curl -fsS -H "Authorization: Bearer $token" \
    "$KEYCLOAK_URL/admin/realms/$REALM/clients/$client_uuid/client-secret" \
  | python3 -c 'import sys, json; print(json.load(sys.stdin)["value"])'
}

sync_one() {
  local token=$1
  local client_id=$2
  local expected_secret=$3

  local client_uuid
  client_uuid=$(get_client_uuid "$token" "$client_id")

  update_client_secret "$token" "$client_uuid" "$expected_secret"

  local actual_secret
  actual_secret=$(read_client_secret "$token" "$client_uuid")

  if [[ "$actual_secret" != "$expected_secret" ]]; then
    echo "Secret verification failed for $client_id" >&2
    echo "Expected: $expected_secret" >&2
    echo "Actual:   $actual_secret" >&2
    exit 1
  fi

  printf '%s %s %s\n' "$client_id" "$client_uuid" "$actual_secret"
}

main() {
  local token
  token=$(get_token)

  echo "Syncing Stack C OIDC client secrets from $ENV_FILE"

  sync_one "$token" "openig-client-c-app5" "$OIDC_CLIENT_SECRET_APP5"
  sync_one "$token" "openig-client-c-app6" "$OIDC_CLIENT_SECRET_APP6"

  cat <<'EOF'
Keycloak secrets now match stack-c/.env.
If Stack C containers were created before the env rotation, recreate them:
  DOCKER_HOST=unix:///Users/duykim/.docker/run/docker.sock docker compose -f stack-c/docker-compose.yml up -d --force-recreate openig-c1 openig-c2 nginx-c
EOF
}

main "$@"
