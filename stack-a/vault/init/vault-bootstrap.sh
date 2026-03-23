#!/usr/bin/env bash
set -euo pipefail

export VAULT_ADDR=http://127.0.0.1:8200
KEYS_FILE=/vault/keys/.vault-keys
BOOTSTRAP_FLAG=/vault/data/.bootstrap-done

# LAB SEED DATA
# All passwords below are deterministic lab values.
# Rotate before production deployment.

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
  # exit 0 = unsealed, exit 2 = sealed but reachable — both mean vault is up
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
  vault secrets enable -path=secret kv-v2
  vault auth enable approle
  vault policy write openig-readonly - <<'POLICY'
path "secret/data/wp-creds/*" { capabilities = ["read"] }
POLICY
  vault write auth/approle/role/openig token_ttl=1h token_max_ttl=4h policies=openig-readonly
  mkdir -p /vault/file
  vault read -field=role_id auth/approle/role/openig/role-id > /vault/file/openig-role-id
  vault write -f -field=secret_id auth/approle/role/openig/secret-id > /vault/file/openig-secret-id
  chmod 600 /vault/file/openig-role-id /vault/file/openig-secret-id
  vault kv put secret/wp-creds/alice username=alice_wp password="SDCNDniqeJaYQq3gDcexAa1@"
  vault kv put secret/wp-creds/bob username=bob_wp password="aLOgjDTxlOTWwjEy5QFTBb2#"
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
vault write auth/approle/role/openig \
  token_ttl=1h token_max_ttl=4h \
  secret_id_ttl=72h \
  policies=openig-readonly 2>/dev/null || true
echo "Vault hardening applied."
