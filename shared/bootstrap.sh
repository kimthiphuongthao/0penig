#!/usr/bin/env bash
set -euo pipefail

if [ ! -f docker-compose.yml ]; then
  echo "ERROR: Run this script from the shared/ directory (docker-compose.yml not found)." >&2
  exit 1
fi

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
else
  echo "WARNING: .env not found in shared/; continuing with the current environment." >&2
fi

if ! docker ps --format '{{.Names}}' | grep -qx 'shared-vault'; then
  echo "ERROR: shared-vault container is not running." >&2
  exit 1
fi

bootstrap_vault_if_needed() {
  if docker exec shared-vault test -f /vault/data/.bootstrap-done >/dev/null 2>&1; then
    echo "Vault bootstrap already completed; skipping initialization."
    return 0
  fi

  local required_vars=(
    WP_ALICE_PASSWORD
    WP_BOB_PASSWORD
    REDMINE_ALICE_PASSWORD
    REDMINE_BOB_PASSWORD
    PHPMYADMIN_ALICE_PASSWORD
    PHPMYADMIN_BOB_PASSWORD
    JELLYFIN_ALICE_PASSWORD
    JELLYFIN_BOB_PASSWORD
  )
  local var
  local env_args=()

  for var in "${required_vars[@]}"; do
    if [ -z "${!var:-}" ]; then
      echo "ERROR: ${var} is required for initial Vault bootstrap." >&2
      exit 1
    fi
    env_args+=("-e" "${var}=${!var}")
  done

  echo "Running initial Vault bootstrap..."
  docker cp vault/init/vault-bootstrap.sh shared-vault:/tmp/vault-bootstrap.sh
  docker exec "${env_args[@]}" shared-vault sh /tmp/vault-bootstrap.sh
}

regenerate_secret_ids() {
  local admin_token_file="vault/keys/.vault-keys.admin"
  local app

  if [ ! -f "$admin_token_file" ]; then
    echo "ERROR: Vault admin token not found at ${admin_token_file}." >&2
    exit 1
  fi

  ADMIN_TOKEN=$(cat "$admin_token_file")

  for app in app1 app2 app3 app4 app5 app6; do
    docker exec \
      -e VAULT_ADDR=http://127.0.0.1:8200 \
      -e "VAULT_TOKEN=${ADMIN_TOKEN}" \
      shared-vault \
      vault write -f -field=secret_id "auth/approle/role/openig-${app}/secret-id" \
      > "vault/file/openig-${app}-secret-id"
  done
}

show_loaded_routes() {
  local container="$1"

  echo "${container}:"
  docker logs "$container" 2>&1 | grep 'Loaded the route' | tail -5
}

bootstrap_vault_if_needed
regenerate_secret_ids

docker restart shared-openig-1 shared-openig-2

sleep 15

show_loaded_routes shared-openig-1
show_loaded_routes shared-openig-2

echo "Bootstrap complete."
