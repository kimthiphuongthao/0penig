#!/usr/bin/env bash
set -euo pipefail

export VAULT_ADDR="http://127.0.0.1:8200"
export VAULT_TOKEN="vault-root-token"

vault secrets enable -path=secret kv-v2 || true
vault auth enable approle || true

vault policy write openig-readonly - <<'EOF'
path "secret/data/dotnet-creds/*" {
  capabilities = ["read"]
}

path "secret/data/redmine-creds/*" {
  capabilities = ["read"]
}
EOF

vault write auth/approle/role/openig token_policies="openig-readonly"

mkdir -p /vault/file
vault read -field=role_id auth/approle/role/openig/role-id > /vault/file/openig-role-id
vault write -f -field=secret_id auth/approle/role/openig/secret-id > /vault/file/openig-secret-id

vault kv put secret/dotnet-creds/alice username="alice" password="Alice2024"
vault kv put secret/dotnet-creds/bob username="bob" password="Bob2024"

vault kv put "secret/redmine-creds/alice@lab.local" login="alice" password="alice123"
vault kv put "secret/redmine-creds/bob@lab.local" login="bob" password="bob12345"

echo "Vault bootstrap completed for Stack B."
