# Checklist restart sau Docker restart

```bash
# 1. Keycloak
cd /Volumes/OS/claude/openig/sso-lab/keycloak && docker compose up -d

# 2. Stack A
cd ../stack-a && docker compose up -d
docker cp vault/init/vault-bootstrap.sh sso-vault:/tmp/vault-bootstrap.sh
docker exec sso-vault sh /tmp/vault-bootstrap.sh
ROOT_TOKEN=$(cat vault/data/.vault-keys.root)
docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN="$ROOT_TOKEN" sso-vault \
  vault read -field=role_id auth/approle/role/openig/role-id > vault/file/openig-role-id
docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN="$ROOT_TOKEN" sso-vault \
  vault write -f -field=secret_id auth/approle/role/openig/secret-id > vault/file/openig-secret-id
docker restart sso-openig-1 sso-openig-2

# 3. Stack B
cd ../stack-b && docker compose up -d
docker cp vault/init/vault-bootstrap.sh sso-b-vault:/tmp/vault-bootstrap.sh
docker exec sso-b-vault sh /tmp/vault-bootstrap.sh
ROOT_TOKEN_B=$(cat vault/data/.vault-keys.root)
docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN="$ROOT_TOKEN_B" sso-b-vault \
  vault read -field=role_id auth/approle/role/openig-role-b/role-id > vault/file/openig-role-id
docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN="$ROOT_TOKEN_B" sso-b-vault \
  vault write -f -field=secret_id auth/approle/role/openig-role-b/secret-id > vault/file/openig-secret-id
docker restart sso-b-openig-1 sso-b-openig-2

# 4. Stack C
cd ../stack-c && docker compose up -d
docker cp vault/init/vault-bootstrap.sh stack-c-vault-c-1:/tmp/vault-bootstrap.sh
docker exec stack-c-vault-c-1 sh /tmp/vault-bootstrap.sh
ROOT_TOKEN_C=$(cat vault/data/.vault-keys.root)
docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN="$ROOT_TOKEN_C" stack-c-vault-c-1 \
  vault read -field=role_id auth/approle/role/openig-role-c/role-id > openig_home/vault/role_id
docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN="$ROOT_TOKEN_C" stack-c-vault-c-1 \
  vault write -f -field=secret_id auth/approle/role/openig-role-c/secret-id > openig_home/vault/secret_id
docker restart stack-c-openig-c1-1 stack-c-openig-c2-1

# 5. Verify
docker logs sso-openig-1 2>&1 | grep "Loaded the route"
docker logs sso-b-openig-1 2>&1 | grep "Loaded the route"
docker logs stack-c-openig-c1-1 2>&1 | grep "Loaded the route"
```
