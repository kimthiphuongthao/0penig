#!/usr/bin/env bash
set -euo pipefail
export VAULT_ADDR=http://127.0.0.1:8200
export VAULT_TOKEN=vault-root-token
vault secrets enable -path=secret kv-v2
vault auth enable approle
vault policy write openig-readonly - <<EOF
path "secret/data/dotnet-creds/*" { capabilities = ["read"] }
