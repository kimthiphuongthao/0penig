#!/usr/bin/env bash
set -euo pipefail

export VAULT_ADDR="${VAULT_ADDR:-http://127.0.0.1:8200}"
VAULT_TOKEN_FILE="${VAULT_TOKEN_FILE:-vault/keys/.vault-keys.admin}"
JELLYFIN_BASE_URL="${JELLYFIN_BASE_URL:-http://shared-jellyfin:8096}"
JELLYFIN_AUTH_HEADER='MediaBrowser Client="audit", Device="audit", DeviceId="audit", Version="1"'
MARIADB_DATABASE="${MARIADB_DATABASE:-appdb}"

declare -a SUMMARY_ROWS=()

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

read_file_trimmed() {
  python3 - "$1" <<'PY'
from pathlib import Path
import sys

print(Path(sys.argv[1]).read_text(encoding="utf-8").strip())
PY
}

generate_password() {
  python3 - <<'PY'
import random
import string

chars = string.ascii_letters + string.digits
print("".join(random.SystemRandom().choice(chars) for _ in range(24)))
PY
}

is_compliant_password() {
  [[ "$1" =~ ^[A-Za-z0-9]{24}$ ]]
}

json_get_field() {
  python3 - "$1" <<'PY'
import json
import sys

field = sys.argv[1]
data = json.load(sys.stdin)
print(data["data"]["data"].get(field, ""))
PY
}

json_build_auth_payload() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

print(json.dumps({"Username": sys.argv[1], "Password": sys.argv[2]}))
PY
}

json_build_reset_payload() {
  python3 - "$1" <<'PY'
import json
import sys

print(json.dumps({"CurrentPw": "", "NewPw": sys.argv[1], "ResetPassword": True}))
PY
}

add_summary() {
  SUMMARY_ROWS+=("$1"$'\t'"$2"$'\t'"$3"$'\t'"$4"$'\t'"$5")
}

print_summary() {
  printf '%-12s | %-5s | %-18s | %-16s | %s\n' "App" "User" "Vault username" "Password match" "Action taken"
  printf '%-12s-+-%-5s-+-%-18s-+-%-16s-+-%s\n' "------------" "-----" "------------------" "----------------" "------------"

  local row app user vault_username match action
  for row in "${SUMMARY_ROWS[@]}"; do
    IFS=$'\t' read -r app user vault_username match action <<<"$row"
    printf '%-12s | %-5s | %-18s | %-16s | %s\n' "$app" "$user" "$vault_username" "$match" "$action"
  done
}

vault_exec() {
  docker exec \
    -e "VAULT_ADDR=$VAULT_ADDR" \
    -e "VAULT_TOKEN=$VAULT_TOKEN" \
    shared-vault \
    vault "$@"
}

vault_get_json() {
  vault_exec kv get -format=json "$1"
}

vault_has_path() {
  vault_exec kv get "$1" >/dev/null 2>&1
}

vault_get_field() {
  local path="$1"
  local field="$2"

  vault_get_json "$path" | json_get_field "$field"
}

vault_put() {
  local path="$1"
  shift
  vault_exec kv put "$path" "$@" >/dev/null
}

wordpress_user_exists() {
  docker exec \
    -e "WP_USER=$1" \
    shared-wordpress \
    sh -lc 'wp user get "$WP_USER" --field=ID --allow-root >/dev/null 2>&1'
}

wordpress_check_password() {
  docker exec \
    -e "WP_USER=$1" \
    -e "WP_PASS=$2" \
    shared-wordpress \
    sh -lc 'wp user check-password "$WP_USER" "$WP_PASS" --allow-root >/dev/null 2>&1'
}

wordpress_update_password() {
  docker exec \
    -e "WP_USER=$1" \
    -e "NEW_PASS=$2" \
    shared-wordpress \
    sh -lc 'wp user update "$WP_USER" --user_pass="$NEW_PASS" --allow-root >/dev/null 2>&1'
}

redmine_user_exists() {
  local output
  output=$(docker exec \
    -e "REDMINE_LOGIN=$1" \
    shared-redmine \
    sh -lc 'cd /usr/src/redmine && bundle exec rails runner '\''u=User.find_by_login(ENV["REDMINE_LOGIN"]); puts(u ? "true" : "false")'\'' 2>/dev/null')
  [[ "$output" == "true" ]]
}

redmine_check_password() {
  local output
  output=$(docker exec \
    -e "REDMINE_LOGIN=$1" \
    -e "REDMINE_PASS=$2" \
    shared-redmine \
    sh -lc 'cd /usr/src/redmine && bundle exec rails runner '\''u=User.find_by_login(ENV["REDMINE_LOGIN"]); puts((u && u.check_password?(ENV["REDMINE_PASS"])) ? "true" : "false")'\'' 2>/dev/null')
  [[ "$output" == "true" ]]
}

redmine_update_password() {
  docker exec \
    -e "REDMINE_LOGIN=$1" \
    -e "REDMINE_PASS=$2" \
    shared-redmine \
    sh -lc 'cd /usr/src/redmine && bundle exec rails runner '\''u=User.find_by_login(ENV["REDMINE_LOGIN"]); raise "missing user" unless u; u.password = ENV["REDMINE_PASS"]; u.password_confirmation = ENV["REDMINE_PASS"]; u.save!'\'' 2>/dev/null'
}

jellyfin_authenticate() {
  local username="$1"
  local password="$2"
  local payload

  payload=$(json_build_auth_payload "$username" "$password")
  curl -fsS -X POST "$JELLYFIN_BASE_URL/Users/AuthenticateByName" \
    -H "X-Emby-Authorization: $JELLYFIN_AUTH_HEADER" \
    -H 'Content-Type: application/json' \
    -d "$payload"
}

jellyfin_get_access_token() {
  python3 - <<'PY'
import json
import sys

data = json.load(sys.stdin)
print(data.get("AccessToken", ""))
PY
}

jellyfin_admin_token() {
  jellyfin_authenticate root root | jellyfin_get_access_token
}

jellyfin_user_id() {
  local admin_token="$1"
  local username="$2"

  curl -fsS "$JELLYFIN_BASE_URL/Users" \
    -H "X-Emby-Authorization: MediaBrowser Token=\"$admin_token\"" |
    python3 - "$username" <<'PY'
import json
import sys

target = sys.argv[1]
for user in json.load(sys.stdin):
    if user.get("Name") == target:
        print(user.get("Id", ""))
        break
else:
    print("")
PY
}

jellyfin_check_password() {
  local token

  token=$(jellyfin_authenticate "$1" "$2" | jellyfin_get_access_token || true)
  [[ -n "$token" ]]
}

jellyfin_update_password() {
  local admin_token="$1"
  local user_id="$2"
  local new_password="$3"
  local payload

  payload=$(json_build_reset_payload "$new_password")
  curl -fsS -X POST "$JELLYFIN_BASE_URL/Users/$user_id/Password" \
    -H "X-Emby-Authorization: MediaBrowser Token=\"$admin_token\"" \
    -H 'Content-Type: application/json' \
    -d "$payload" >/dev/null
}

mariadb_exec_root() {
  docker exec \
    -e "MYSQL_ROOT_PASS=$MYSQL_ROOT_PASS" \
    shared-mariadb \
    sh -lc "mysql -u root -p\"\$MYSQL_ROOT_PASS\" -e \"$1\" 2>/dev/null"
}

mariadb_user_exists() {
  local output
  output=$(mariadb_exec_root "SELECT COUNT(*) AS count FROM mysql.user WHERE User = '$1';" | tail -n 1 | tr -d '[:space:]')
  [[ "$output" == "1" ]]
}

mariadb_check_password() {
  docker exec \
    -e "DB_USER=$1" \
    -e "DB_PASS=$2" \
    shared-mariadb \
    sh -lc 'mysql -u "$DB_USER" -p"$DB_PASS" -e "SELECT 1;" >/dev/null 2>&1'
}

mariadb_set_password() {
  docker exec \
    -e "MYSQL_ROOT_PASS=$MYSQL_ROOT_PASS" \
    -e "DB_USER=$1" \
    -e "DB_PASS=$2" \
    -e "APP_DB=$MARIADB_DATABASE" \
    shared-mariadb \
    sh -lc 'mysql -u root -p"$MYSQL_ROOT_PASS" -e "CREATE USER IF NOT EXISTS '\''$DB_USER'\''@'\''%'\'' IDENTIFIED BY '\''$DB_PASS'\''; ALTER USER '\''$DB_USER'\''@'\''%'\'' IDENTIFIED BY '\''$DB_PASS'\''; GRANT ALL PRIVILEGES ON $APP_DB.* TO '\''$DB_USER'\''@'\''%'\''; FLUSH PRIVILEGES;" >/dev/null 2>&1'
}

audit_wordpress_user() {
  local user="$1"
  local path="secret/wp-creds/$user"
  local vault_username vault_password match action new_password

  vault_username=$(vault_get_field "$path" username)
  vault_password=$(vault_get_field "$path" password)
  match="N"
  action="none"

  if ! wordpress_user_exists "$vault_username"; then
    add_summary "WordPress" "$user" "$vault_username" "$match" "user missing in WordPress"
    return
  fi

  if wordpress_check_password "$vault_username" "$vault_password"; then
    match="Y"
  fi

  if ! is_compliant_password "$vault_password" || [[ "$match" == "N" ]]; then
    new_password=$(generate_password)
    wordpress_update_password "$vault_username" "$new_password"
    vault_put "$path" "username=$vault_username" "password=$new_password"
    wordpress_check_password "$vault_username" "$new_password"
    action="rotated Vault + WordPress password"
  fi

  add_summary "WordPress" "$user" "$vault_username" "$match" "$action"
}

audit_redmine_user() {
  local user="$1"
  local alias_path="secret/redmine-creds/$user@lab.local"
  local path="secret/redmine-creds/$user"
  local vault_login vault_password alias_login alias_password
  local match action new_password alias_sync_required

  vault_login=$(vault_get_field "$path" login)
  vault_password=$(vault_get_field "$path" password)
  match="N"
  action="none"
  alias_sync_required="N"

  if vault_has_path "$alias_path"; then
    alias_login=$(vault_get_field "$alias_path" login)
    alias_password=$(vault_get_field "$alias_path" password)
    if [[ "$alias_login" != "$vault_login" || "$alias_password" != "$vault_password" ]]; then
      alias_sync_required="Y"
    fi
  else
    alias_sync_required="Y"
  fi

  if ! redmine_user_exists "$vault_login"; then
    if [[ "$alias_sync_required" == "Y" ]]; then
      vault_put "$alias_path" "login=$vault_login" "email=$user@lab.local" "password=$vault_password"
      action="synced missing Redmine alias path"
    else
      action="user missing in Redmine"
    fi
    add_summary "Redmine" "$user" "$vault_login" "$match" "$action"
    return
  fi

  if redmine_check_password "$vault_login" "$vault_password"; then
    match="Y"
  fi

  if ! is_compliant_password "$vault_password" || [[ "$match" == "N" ]]; then
    new_password=$(generate_password)
    redmine_update_password "$vault_login" "$new_password"
    vault_put "$path" "login=$vault_login" "email=$user@lab.local" "password=$new_password"
    vault_put "$alias_path" "login=$vault_login" "email=$user@lab.local" "password=$new_password"
    redmine_check_password "$vault_login" "$new_password"
    action="rotated Vault + Redmine password"
  elif [[ "$alias_sync_required" == "Y" ]]; then
    vault_put "$alias_path" "login=$vault_login" "email=$user@lab.local" "password=$vault_password"
    action="synced Redmine alias path"
  fi

  add_summary "Redmine" "$user" "$vault_login" "$match" "$action"
}

audit_jellyfin_user() {
  local user="$1"
  local path="secret/jellyfin-creds/$user"
  local vault_username vault_password user_id match action new_password

  vault_username=$(vault_get_field "$path" username)
  vault_password=$(vault_get_field "$path" password)
  match="N"
  action="none"
  user_id=$(jellyfin_user_id "$ADMIN_TOKEN" "$vault_username")

  if [[ -z "$user_id" ]]; then
    add_summary "Jellyfin" "$user" "$vault_username" "$match" "user missing in Jellyfin"
    return
  fi

  if jellyfin_check_password "$vault_username" "$vault_password"; then
    match="Y"
  fi

  if ! is_compliant_password "$vault_password" || [[ "$match" == "N" ]]; then
    new_password=$(generate_password)
    jellyfin_update_password "$ADMIN_TOKEN" "$user_id" "$new_password"
    vault_put "$path" "username=$vault_username" "email=$user@lab.local" "password=$new_password"
    jellyfin_check_password "$vault_username" "$new_password"
    action="rotated Vault + Jellyfin password"
  fi

  add_summary "Jellyfin" "$user" "$vault_username" "$match" "$action"
}

audit_mariadb_user() {
  local user="$1"
  local path="secret/phpmyadmin/$user"
  local vault_username vault_password match action new_password existed_before

  vault_username=$(vault_get_field "$path" username)
  vault_password=$(vault_get_field "$path" password)
  match="N"
  action="none"
  existed_before="N"

  if mariadb_user_exists "$vault_username"; then
    existed_before="Y"
  fi

  if [[ "$existed_before" == "Y" ]] && mariadb_check_password "$vault_username" "$vault_password"; then
    match="Y"
  fi

  if ! is_compliant_password "$vault_password" || [[ "$match" == "N" ]]; then
    new_password=$(generate_password)
    mariadb_set_password "$vault_username" "$new_password"
    vault_put "$path" "username=$vault_username" "password=$new_password"
    mariadb_check_password "$vault_username" "$new_password"
    if [[ "$existed_before" == "Y" ]]; then
      action="rotated Vault + MariaDB password"
    else
      action="created MariaDB user and rotated Vault password"
    fi
  fi

  add_summary "MariaDB" "$user" "$vault_username" "$match" "$action"
}

main() {
  require_command curl
  require_command docker
  require_command python3

  if [[ ! -f "$VAULT_TOKEN_FILE" ]]; then
    echo "Vault admin token file not found: $VAULT_TOKEN_FILE" >&2
    exit 1
  fi

  VAULT_TOKEN="${VAULT_TOKEN:-$(read_file_trimmed "$VAULT_TOKEN_FILE")}"
  export VAULT_TOKEN

  ADMIN_TOKEN=$(jellyfin_admin_token)
  if [[ -z "$ADMIN_TOKEN" ]]; then
    echo "Unable to authenticate to Jellyfin as root/root." >&2
    exit 1
  fi

  MYSQL_ROOT_PASS=$(docker exec shared-mariadb sh -lc 'echo ${MYSQL_ROOT_PASSWORD:-changeme_mysql_root_password_c}')
  export MYSQL_ROOT_PASS ADMIN_TOKEN

  audit_wordpress_user alice
  audit_wordpress_user bob
  audit_redmine_user alice
  audit_redmine_user bob
  audit_jellyfin_user alice
  audit_jellyfin_user bob
  audit_mariadb_user alice
  audit_mariadb_user bob

  print_summary
}

main "$@"
