#!/usr/bin/env bash
set -euo pipefail

export VAULT_ADDR=http://127.0.0.1:8200
KEYS_FILE=/vault/keys/.vault-keys
BOOTSTRAP_FLAG=/vault/data/.bootstrap-done

mkdir -p /vault/data /vault/init

sql_escape() {
  printf "%s" "${1//\'/\'\'}"
}

ensure_stack_c_mariadb_users() {
  local alice_password="$1"
  local bob_password="$2"
  local mysql_client
  local mysql_root_password
  local alice_password_sql
  local bob_password_sql
  local i
  local mysql_ready=0

  if command -v mariadb >/dev/null 2>&1; then
    mysql_client=mariadb
  elif command -v mysql >/dev/null 2>&1; then
    mysql_client=mysql
  else
    echo "WARNING: mariadb/mysql client not found; skipping MariaDB user bootstrap."
    return 0
  fi

  mysql_root_password="${MYSQL_ROOT_PASSWORD:-${MARIADB_ROOT_PASSWORD:-}}"
  if [ -z "$mysql_root_password" ]; then
    echo "WARNING: MYSQL_ROOT_PASSWORD is not set; skipping MariaDB user bootstrap."
    return 0
  fi

  alice_password_sql=$(sql_escape "$alice_password")
  bob_password_sql=$(sql_escape "$bob_password")

  for i in $(seq 1 30); do
    if MYSQL_PWD="$mysql_root_password" "$mysql_client" -hmariadb -uroot -e "SELECT 1" >/dev/null 2>&1; then
      mysql_ready=1
      break
    fi

    sleep 1
  done

  if [ "$mysql_ready" -ne 1 ]; then
    echo "WARNING: MariaDB did not become ready; skipping user bootstrap."
    return 0
  fi

  MYSQL_PWD="$mysql_root_password" "$mysql_client" -hmariadb -uroot <<SQL
CREATE USER IF NOT EXISTS 'alice'@'%' IDENTIFIED BY '${alice_password_sql}';
ALTER USER 'alice'@'%' IDENTIFIED BY '${alice_password_sql}';
GRANT ALL PRIVILEGES ON appdb.* TO 'alice'@'%';
CREATE USER IF NOT EXISTS 'bob'@'%' IDENTIFIED BY '${bob_password_sql}';
ALTER USER 'bob'@'%' IDENTIFIED BY '${bob_password_sql}';
GRANT ALL PRIVILEGES ON appdb.* TO 'bob'@'%';
FLUSH PRIVILEGES;
SQL
}

# Migrate keys from old location (one-time)
if [ -f "/vault/data/.vault-keys.unseal" ] && [ ! -f "/vault/keys/.vault-keys.unseal" ]; then
  mkdir -p /vault/keys
  cp /vault/data/.vault-keys.unseal /vault/keys/.vault-keys.unseal 2>/dev/null || true
  cp /vault/data/.vault-keys.root /vault/keys/.vault-keys.root 2>/dev/null || true
  cp /vault/data/.vault-keys.admin /vault/keys/.vault-keys.admin 2>/dev/null || true
  chmod 600 /vault/keys/.vault-keys.* 2>/dev/null || true
  echo "Migrated vault keys to /vault/keys/"
fi

for i in $(seq 1 30); do
  code=$(vault status >/dev/null 2>&1; echo $?)
  if [ "$code" -eq 0 ] || [ "$code" -eq 2 ]; then
    break
  fi

  if [ "$i" -eq 30 ]; then
    echo "Vault did not become ready after 30 attempts"
    exit 1
  fi

  sleep 1
done

INITIALIZED=$(vault status 2>/dev/null | grep '^Initialized' | awk '{print $2}' || true)
if [ "$INITIALIZED" = "false" ]; then
  output=$(vault operator init -key-shares=1 -key-threshold=1)
  echo "$output" | grep 'Unseal Key 1' | awk '{print $NF}' > "${KEYS_FILE}.unseal"
  echo "$output" | grep 'Initial Root Token' | awk '{print $NF}' > "${KEYS_FILE}.root"
  chmod 600 "${KEYS_FILE}.unseal" "${KEYS_FILE}.root"
fi

SEALED=$(vault status 2>/dev/null | grep '^Sealed' | awk '{print $2}' || true)
if [ "$SEALED" = "true" ]; then
  vault operator unseal "$(cat "${KEYS_FILE}.unseal")"
fi

# Authenticate — prefer admin token, fallback to root
if [ -f "${KEYS_FILE}.admin" ]; then
  export VAULT_TOKEN="$(cat "${KEYS_FILE}.admin")"
elif [ -f "${KEYS_FILE}.root" ]; then
  export VAULT_TOKEN="$(cat "${KEYS_FILE}.root")"
else
  echo "ERROR: No admin or root token found at ${KEYS_FILE}"
  exit 1
fi

# Enable audit logging (idempotent — skip if already enabled)
if ! vault audit list 2>/dev/null | grep -q "file/"; then
  vault audit enable file file_path=/vault/file/audit.log || true
  echo "Vault audit logging enabled"
else
  echo "Vault audit logging already enabled"
fi

if [ ! -f "$BOOTSTRAP_FLAG" ]; then
  ALICE_DB_PASSWORD="AlicePass123"
  BOB_DB_PASSWORD="OYHupH3pbskR6sY5vcKr6X0Dd4"

  vault secrets enable -path=secret kv-v2
  vault auth enable approle
  vault policy write openig-policy-c - <<'POLICY'
path "secret/data/phpmyadmin/*" {
  capabilities = ["read"]
}
POLICY
  vault write auth/approle/role/openig-role-c token_ttl=1h token_max_ttl=4h policies=openig-policy-c
  vault read -field=role_id auth/approle/role/openig-role-c/role-id > /vault/init/role_id
  vault write -f -field=secret_id auth/approle/role/openig-role-c/secret-id > /vault/init/secret_id
  chmod 600 /vault/init/role_id /vault/init/secret_id
  if BOB_DB_PASSWORD_FROM_VAULT=$(vault kv get -field=password secret/phpmyadmin/bob 2>/dev/null); then
    BOB_DB_PASSWORD="$BOB_DB_PASSWORD_FROM_VAULT"
  fi
  vault kv put secret/phpmyadmin/alice username=alice password="$ALICE_DB_PASSWORD"
  vault kv put secret/phpmyadmin/bob username=bob password="$BOB_DB_PASSWORD"
  ensure_stack_c_mariadb_users "$ALICE_DB_PASSWORD" "$BOB_DB_PASSWORD"
  touch "$BOOTSTRAP_FLAG"
  echo "Bootstrap complete."
else
  echo "Already bootstrapped."
fi

# Admin token management (idempotent — skip if admin token already exists)
if [ ! -f "${KEYS_FILE}.admin" ]; then
  vault policy write vault-admin - <<'ADMIN_POLICY'
path "auth/approle/role/*" { capabilities = ["read", "update"] }
path "auth/approle/role/+/role-id" { capabilities = ["read"] }
path "auth/approle/role/+/secret-id" { capabilities = ["create", "update"] }
path "secret/config" { capabilities = ["read", "update"] }
path "secret/data/phpmyadmin/*" { capabilities = ["create", "read", "update"] }
path "secret/metadata/phpmyadmin/*" { capabilities = ["read", "list"] }
path "sys/audit" { capabilities = ["read", "sudo"] }
path "sys/audit/*" { capabilities = ["create", "read", "update", "delete", "sudo"] }
path "sys/health" { capabilities = ["read"] }
path "sys/mounts" { capabilities = ["read"] }
path "sys/mounts/*" { capabilities = ["read"] }
ADMIN_POLICY

  ADMIN_TOKEN=$(vault token create -orphan -policy=vault-admin -period=8760h -field=token)
  if [ -z "$ADMIN_TOKEN" ]; then
    echo "WARNING: Failed to create admin token. Root token NOT revoked."
  else
    echo "$ADMIN_TOKEN" > "${KEYS_FILE}.admin"
    chmod 600 "${KEYS_FILE}.admin"
    vault token revoke -self
    export VAULT_TOKEN="$ADMIN_TOKEN"
    rm -f "${KEYS_FILE}.root"
    echo "Root token revoked. Admin token created."
  fi
else
  echo "Admin token already exists."
fi

# Post-bootstrap hardening (idempotent, runs every bootstrap call)
vault write secret/config max_versions=5 2>/dev/null || true
vault write auth/approle/role/openig-role-c \
  token_ttl=1h token_max_ttl=4h \
  secret_id_ttl=72h \
  policies=openig-policy-c 2>/dev/null || true
echo "Vault hardening applied."
