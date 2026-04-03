#!/usr/bin/env bash
set -euo pipefail

export VAULT_ADDR=http://127.0.0.1:8200
KEYS_FILE=/vault/keys/.vault-keys
BOOTSTRAP_FLAG=/vault/data/.bootstrap-done

WP_ALICE_PASSWORD=${WP_ALICE_PASSWORD:?'ERROR: WP_ALICE_PASSWORD is required'}
WP_BOB_PASSWORD=${WP_BOB_PASSWORD:?'ERROR: WP_BOB_PASSWORD is required'}
REDMINE_ALICE_PASSWORD=${REDMINE_ALICE_PASSWORD:?'ERROR: REDMINE_ALICE_PASSWORD is required'}
REDMINE_BOB_PASSWORD=${REDMINE_BOB_PASSWORD:?'ERROR: REDMINE_BOB_PASSWORD is required'}
PHPMYADMIN_ALICE_PASSWORD=${PHPMYADMIN_ALICE_PASSWORD:?'ERROR: PHPMYADMIN_ALICE_PASSWORD is required'}
PHPMYADMIN_BOB_PASSWORD=${PHPMYADMIN_BOB_PASSWORD:?'ERROR: PHPMYADMIN_BOB_PASSWORD is required'}

# Injected passwords must stay alphanumeric-only because OpenIG posts them
# without URL-encoding during form injection.
JELLYFIN_ALICE_PASSWORD=${JELLYFIN_ALICE_PASSWORD:?'ERROR: JELLYFIN_ALICE_PASSWORD is required'}
JELLYFIN_BOB_PASSWORD=${JELLYFIN_BOB_PASSWORD:?'ERROR: JELLYFIN_BOB_PASSWORD is required'}

wait_for_vault() {
  local code

  for i in $(seq 1 30); do
    code=$(vault status >/dev/null 2>&1; echo $?)
    if [ "$code" -eq 0 ] || [ "$code" -eq 2 ]; then
      return 0
    fi
    sleep 1
  done

  echo "Vault did not become ready after 30 attempts"
  exit 1
}

init_if_needed() {
  local initialized
  local output

  initialized=$(vault status 2>/dev/null | grep '^Initialized' | awk '{print $2}' || true)
  if [ "$initialized" = "false" ]; then
    output=$(vault operator init -key-shares=1 -key-threshold=1)
    echo "$output" | grep 'Unseal Key 1' | awk '{print $NF}' > "${KEYS_FILE}.unseal"
    echo "$output" | grep 'Initial Root Token' | awk '{print $NF}' > "${KEYS_FILE}.root"
    chmod 600 "${KEYS_FILE}.unseal" "${KEYS_FILE}.root"
  fi
}

unseal_if_needed() {
  local sealed

  sealed=$(vault status 2>/dev/null | grep '^Sealed' | awk '{print $2}' || true)
  if [ "$sealed" = "true" ]; then
    vault operator unseal "$(cat "${KEYS_FILE}.unseal")" >/dev/null
  fi
}

login_vault() {
  if [ -f "${KEYS_FILE}.admin" ]; then
    export VAULT_TOKEN="$(cat "${KEYS_FILE}.admin")"
  elif [ -f "${KEYS_FILE}.root" ]; then
    export VAULT_TOKEN="$(cat "${KEYS_FILE}.root")"
  else
    echo "ERROR: No admin or root token found at ${KEYS_FILE}"
    exit 1
  fi
}

enable_audit_log() {
  if ! vault audit list 2>/dev/null | grep -q 'file/'; then
    vault audit enable file file_path=/vault/file/audit.log >/dev/null || true
  fi
}

ensure_secret_engine() {
  if ! vault secrets list 2>/dev/null | grep -q '^secret/'; then
    vault secrets enable -path=secret kv-v2 >/dev/null
  fi
}

ensure_approle_auth() {
  if ! vault auth list 2>/dev/null | grep -q '^approle/'; then
    vault auth enable approle >/dev/null
  fi
}

enable_transit() {
  vault secrets enable transit 2>/dev/null || true

  for app in 1 2 3 4 5 6; do
    vault write transit/keys/app${app}-key type=aes256-gcm96 2>/dev/null || true
  done
}

write_policies() {
  vault policy write openig-app1-policy - <<'POLICY'
path "secret/data/wp-creds/*" {
  capabilities = ["read"]
}
path "transit/encrypt/app1-key" {
  capabilities = ["update"]
}
path "transit/decrypt/app1-key" {
  capabilities = ["update"]
}
POLICY

  vault policy write openig-app2-policy - <<'POLICY'
path "secret/data/dummy/*" {
  capabilities = ["read"]
}
path "transit/encrypt/app2-key" {
  capabilities = ["update"]
}
path "transit/decrypt/app2-key" {
  capabilities = ["update"]
}
POLICY

  vault policy write openig-app3-policy - <<'POLICY'
path "secret/data/redmine-creds/*" {
  capabilities = ["read"]
}
path "transit/encrypt/app3-key" {
  capabilities = ["update"]
}
path "transit/decrypt/app3-key" {
  capabilities = ["update"]
}
POLICY

  vault policy write openig-app4-policy - <<'POLICY'
path "secret/data/jellyfin-creds/*" {
  capabilities = ["read"]
}
path "transit/encrypt/app4-key" {
  capabilities = ["update"]
}
path "transit/decrypt/app4-key" {
  capabilities = ["update"]
}
POLICY

  vault policy write openig-app5-policy - <<'POLICY'
path "secret/data/grafana-creds/*" {
  capabilities = ["read"]
}
path "transit/encrypt/app5-key" {
  capabilities = ["update"]
}
path "transit/decrypt/app5-key" {
  capabilities = ["update"]
}
POLICY

  vault policy write openig-app6-policy - <<'POLICY'
path "secret/data/phpmyadmin/*" {
  capabilities = ["read"]
}
path "transit/encrypt/app6-key" {
  capabilities = ["update"]
}
path "transit/decrypt/app6-key" {
  capabilities = ["update"]
}
POLICY
}

write_role() {
  local app="$1"
  local role="openig-app${app}"
  local policy="openig-app${app}-policy"

  vault write "auth/approle/role/${role}" \
    token_ttl=1h \
    token_max_ttl=4h \
    secret_id_ttl=72h \
    policies="${policy}" >/dev/null

  mkdir -p /vault/file
  vault read -field=role_id "auth/approle/role/${role}/role-id" > "/vault/file/${role}-role-id"
  vault write -f -field=secret_id "auth/approle/role/${role}/secret-id" > "/vault/file/${role}-secret-id"
  chmod 600 "/vault/file/${role}-role-id" "/vault/file/${role}-secret-id"
}

seed_secrets() {
  vault kv put secret/wp-creds/alice \
    username=alice_wp \
    password="${WP_ALICE_PASSWORD}" >/dev/null
  vault kv put secret/wp-creds/bob \
    username=bob_wp \
    password="${WP_BOB_PASSWORD}" >/dev/null

  vault kv put secret/redmine-creds/alice \
    login=alice \
    email=alice@lab.local \
    password="${REDMINE_ALICE_PASSWORD}" >/dev/null
  vault kv put secret/redmine-creds/alice@lab.local \
    login=alice \
    email=alice@lab.local \
    password="${REDMINE_ALICE_PASSWORD}" >/dev/null
  vault kv put secret/redmine-creds/bob \
    login=bob \
    email=bob@lab.local \
    password="${REDMINE_BOB_PASSWORD}" >/dev/null
  vault kv put secret/redmine-creds/bob@lab.local \
    login=bob \
    email=bob@lab.local \
    password="${REDMINE_BOB_PASSWORD}" >/dev/null

  vault kv put secret/jellyfin-creds/alice \
    username=alice \
    email=alice@lab.local \
    password="${JELLYFIN_ALICE_PASSWORD}" >/dev/null
  vault kv put secret/jellyfin-creds/alice@lab.local \
    username=alice \
    email=alice@lab.local \
    password="${JELLYFIN_ALICE_PASSWORD}" >/dev/null
  vault kv put secret/jellyfin-creds/bob \
    username=bob \
    email=bob@lab.local \
    password="${JELLYFIN_BOB_PASSWORD}" >/dev/null
  vault kv put secret/jellyfin-creds/bob@lab.local \
    username=bob \
    email=bob@lab.local \
    password="${JELLYFIN_BOB_PASSWORD}" >/dev/null

  vault kv put secret/phpmyadmin/alice \
    username=alice \
    password="${PHPMYADMIN_ALICE_PASSWORD}" >/dev/null
  vault kv put secret/phpmyadmin/bob \
    username=bob \
    password="${PHPMYADMIN_BOB_PASSWORD}" >/dev/null
}

ensure_admin_token() {
  local admin_token

  if [ -f "${KEYS_FILE}.admin" ]; then
    echo "Admin token already exists."
    return 0
  fi

  vault policy write vault-admin - <<'ADMIN_POLICY'
path "auth/approle/role/*" { capabilities = ["read", "update"] }
path "auth/approle/role/+/role-id" { capabilities = ["read"] }
path "auth/approle/role/+/secret-id" { capabilities = ["create", "update"] }
path "secret/config" { capabilities = ["read", "update"] }
path "secret/data/*" { capabilities = ["create", "read", "update"] }
path "secret/metadata/*" { capabilities = ["read", "list"] }
path "sys/audit" { capabilities = ["read", "sudo"] }
path "sys/audit/*" { capabilities = ["create", "read", "update", "delete", "sudo"] }
path "sys/health" { capabilities = ["read"] }
path "sys/mounts" { capabilities = ["read"] }
path "sys/mounts/*" { capabilities = ["read"] }
path "sys/mounts/transit" { capabilities = ["create", "update", "read", "delete"] }
path "sys/mounts/transit/tune" { capabilities = ["update"] }
path "transit/*" { capabilities = ["create", "read", "update", "list"] }
ADMIN_POLICY

  # Admin token: period=720h (30d). Renew with: vault token renew <token> before expiry.
  # If expired, re-run bootstrap to generate a new token.
  admin_token=$(vault token create -orphan -policy=vault-admin -period=720h -field=token)
  if [ -z "$admin_token" ]; then
    echo "WARNING: Failed to create admin token. Root token NOT revoked."
    return 0
  fi

  echo "$admin_token" > "${KEYS_FILE}.admin"
  chmod 600 "${KEYS_FILE}.admin"
  vault token revoke -self >/dev/null
  export VAULT_TOKEN="$admin_token"
  rm -f "${KEYS_FILE}.root"
}

apply_hardening() {
  vault write secret/config max_versions=5 >/dev/null 2>&1 || true

  for app in 1 2 3 4 5 6; do
    vault write "auth/approle/role/openig-app${app}" \
      token_ttl=1h \
      token_max_ttl=4h \
      secret_id_ttl=72h \
      policies="openig-app${app}-policy" >/dev/null 2>&1 || true
  done
}

bootstrapped_now=false

wait_for_vault
init_if_needed
unseal_if_needed
login_vault
enable_audit_log
ensure_secret_engine
ensure_approle_auth
enable_transit
write_policies

for app in 1 2 3 4 5 6; do
  write_role "$app"
done

if [ ! -f "$BOOTSTRAP_FLAG" ]; then
  seed_secrets
  bootstrapped_now=true
  echo "Bootstrap complete."
else
  echo "Already bootstrapped."
fi

ensure_admin_token
apply_hardening
echo "Vault hardening applied."

if [ "$bootstrapped_now" = true ]; then
  touch "$BOOTSTRAP_FLAG"
fi
