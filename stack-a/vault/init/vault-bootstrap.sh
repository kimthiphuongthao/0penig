#!/usr/bin/env bash
set -euo pipefail

export VAULT_ADDR=http://127.0.0.1:8200
KEYS_FILE=/vault/data/.vault-keys
BOOTSTRAP_FLAG=/vault/data/.bootstrap-done

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

export VAULT_TOKEN="$(cat "${KEYS_FILE}.root")"

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
