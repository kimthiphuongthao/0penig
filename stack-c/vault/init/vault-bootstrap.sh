#!/usr/bin/env bash
set -euo pipefail

: "${VAULT_ADDR:=http://127.0.0.1:8200}"
: "${VAULT_TOKEN:=root}"
export VAULT_ADDR VAULT_TOKEN

if vault secrets list | awk '{print $1}' | grep -q '^secret/$'; then
  echo 'KV secrets engine already enabled at secret/'
else
  vault secrets enable -path=secret kv-v2
  echo 'Enabled KV v2 secrets engine at secret/'
fi

if vault auth list | awk '{print $1}' | grep -q '^approle/$'; then
  echo 'AppRole auth method already enabled'
else
  vault auth enable approle
  echo 'Enabled AppRole auth method'
fi

vault policy write openig-policy-c - <<'POLICY'
path "secret/data/phpmyadmin/*" {
  capabilities = ["read"]
}
POLICY

echo 'Applied policy openig-policy-c'

vault write auth/approle/role/openig-role-c \
  token_policies=openig-policy-c \
  token_ttl=1h \
  token_max_ttl=4h

echo 'Configured AppRole openig-role-c'

mkdir -p /vault/init
vault read -field=role_id auth/approle/role/openig-role-c/role-id > /vault/init/role_id
vault write -f -field=secret_id auth/approle/role/openig-role-c/secret-id > /vault/init/secret_id
chmod 600 /vault/init/role_id /vault/init/secret_id

echo 'Wrote role_id and secret_id to /vault/init'

vault kv put secret/phpmyadmin/alice username=alice password='AlicePass123'
vault kv put secret/phpmyadmin/bob username=bob password='BobPass456'

echo 'Pre-seeded test secrets under secret/data/phpmyadmin/*'
echo 'Vault bootstrap complete'
