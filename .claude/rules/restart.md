# Shared-infra restart checklist

## Active deployment

```bash
# 1. Keycloak
cd /Volumes/OS/claude/openig/sso-lab/keycloak && docker compose up -d

# 2. Shared infra
cd ../shared && docker compose up -d

# 3. Vault bootstrap (first time or after vault data loss)
docker cp vault/init/vault-bootstrap.sh shared-vault:/tmp/vault-bootstrap.sh
docker exec shared-vault sh /tmp/vault-bootstrap.sh

# 4. Regenerate AppRole secret_ids (after restart if secret_id expired)
ADMIN_TOKEN=$(cat vault/keys/.vault-keys.admin)
for APP in app1 app2 app3 app4 app5 app6; do
  docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN="$ADMIN_TOKEN" shared-vault \
    vault write -f -field=secret_id auth/approle/role/openig-${APP}/secret-id \
    > vault/file/openig-${APP}-secret-id
done

# 5. Restart OpenIG nodes to reload credentials
docker restart shared-openig-1 shared-openig-2

# 6. Verify
docker logs shared-openig-1 2>&1 | grep "Loaded the route"
docker logs shared-openig-2 2>&1 | grep "Loaded the route"
```

Notes:

- Step 3 is only required on first bootstrap or after Vault data loss
- Step 4 is only required when AppRole `secret_id_ttl` has expired or Vault state was reset
- AppRole files live under `shared/vault/file/`

## Legacy stacks (rollback only)

```bash
# Stack A rollback
# cd /Volumes/OS/claude/openig/sso-lab/stack-a && docker compose up -d

# Stack B rollback
# cd /Volumes/OS/claude/openig/sso-lab/stack-b && docker compose up -d

# Stack C rollback
# cd /Volumes/OS/claude/openig/sso-lab/stack-c && docker compose up -d
```
