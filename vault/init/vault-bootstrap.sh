#!/usr/bin/env bash
set -euo pipefail

export VAULT_ADDR=http://127.0.0.1:8200
# NOTE: dev-mode only — root token used for bootstrap, not for runtime
export VAULT_TOKEN=vault-root-token

vault secrets enable -path=secret kv-v2
vault auth enable approle
vault policy write openig-readonly - <<EOF
path "secret/data/wp-creds/*" { capabilities = ["read"] }
EOF
vault write auth/approle/role/openig token_ttl=1h token_max_ttl=4h policies=openig-readonly
mkdir -p /vault/file
vault read -field=role_id auth/approle/role/openig/role-id > /vault/file/openig-role-id
vault write -f -field=secret_id auth/approle/role/openig/secret-id > /vault/file/openig-secret-id
chmod 600 /vault/file/openig-role-id /vault/file/openig-secret-id
# NOTE: lab only — in production, source passwords from a secure secrets manager
vault kv put secret/wp-creds/alice username=alice password='T7#xK9@mP2$nQ8!vL'
vault kv put secret/wp-creds/bob username=bob password='Rw$4Yz!sN6@jH3^cX'
echo 'Vault bootstrap complete'
