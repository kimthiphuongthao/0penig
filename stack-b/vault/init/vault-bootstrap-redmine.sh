#!/usr/bin/env bash
set -euo pipefail
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=vault-root-token

# Update policy to include redmine-creds
vault policy write openig-readonly - <<EOF
path "secret/data/redmine-creds/*" { capabilities = ["read"] }
path "secret/data/jellyfin-creds/*" { capabilities = ["read"] }
EOF

# Add Redmine user credentials (keyed by email)
vault kv put secret/redmine-creds/alice@lab.local login='alice_rm' password='AliceRm2026!'
vault kv put secret/redmine-creds/bob@lab.local login='bob_rm' password='BobRm2026!'
echo 'Vault Redmine bootstrap complete'
vault kv put secret/redmine-creds/duykk1@bank.com login='duykk1' password='duy123'
