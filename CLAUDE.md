# CLAUDE.md — SSO Lab Project Instructions

Đây là file instruction cho Claude Code và các AI agent (Codex, Gemini) làm việc trong project này.
Claude phải đọc và tuân thủ toàn bộ file này trước khi thực hiện bất kỳ task nào.

---

## Hướng dẫn duy trì context xuyên suốt project

### Khi bắt đầu conversation mới (hoặc chạy song song nhiều session)

Chỉ cần mở terminal tại đúng thư mục:
```bash
cd /Volumes/OS/claude/openig/sso-lab
claude
```
Claude Code tự đọc `CLAUDE.md` → đủ context để làm việc ngay.

Nếu cần context sâu hơn (trạng thái containers, pending tasks), nhắn:
> "đọc CLAUDE.md và MEMORY.md rồi báo cáo trạng thái hiện tại"

### Các câu lệnh chuẩn để giao tiếp

| Tình huống | Câu nhắn |
|-----------|----------|
| Bắt đầu lại sau tắt máy/ngủ | "tôi đã trở lại, chúng ta tiếp tục công việc" |
| Bắt đầu session mới song song | "đọc CLAUDE.md và cho biết pending tasks hiện tại" |
| Trước khi tắt máy | "tôi cần tắt máy" → Claude commit, update MEMORY.md, xác nhận |
| Muốn biết đang ở đâu trong roadmap | "tóm tắt trạng thái project hiện tại" |
| Muốn Claude nhớ điều gì đó | "nhớ lại điều này: ..." → Claude ghi vào MEMORY.md + CLAUDE.md nếu quan trọng |

### Quy tắc Claude phải tự làm (không cần nhắc)

1. **Sau mỗi milestone lớn**: cập nhật section Roadmap trong CLAUDE.md (tick [x] những gì đã xong)
2. **Khi phát hiện gotcha/bug mới**: thêm vào bảng Gotchas trong CLAUDE.md
3. **Khi tắt máy**: commit code + update MEMORY.md trạng thái + push
4. **Khi có decision quan trọng**: ghi lý do vào Gotchas hoặc tạo decision record trong docs/

### File nào chứa gì

| File | Đọc khi | Nội dung |
|------|---------|----------|
| `CLAUDE.md` (repo) | Mọi session, mọi agent | Architecture, conventions, roadmap, gotchas — **stable** |
| `~/.claude/.../MEMORY.md` | Claude tự đọc | Trạng thái runtime, passwords, session state — **thay đổi thường xuyên** |
| `.gemini/GEMINI.md` (repo) | Gemini tự đọc | Research context, decisions, codebase map |
| `docs/test-cases.md` | Khi test | 28 test cases đầy đủ với URLs + credentials |
| `docs/test-report.md` | Khi debug | Kết quả test gần nhất, lỗi đã phân tích |

---

## Mục tiêu dự án

Lab SSO triển khai Single Sign-On và Single Logout cho các legacy app không hỗ trợ OIDC natively, thông qua:
- **OpenIG 6** — SSO gateway (intercept, authenticate, inject credentials)
- **Keycloak 24** — Identity Provider (OIDC/OAuth2)
- **HashiCorp Vault** — Secrets management (AppRole + KV v2)
- **Redis** — SLO session blacklist

3 stacks độc lập, mỗi stack có 2 OpenIG nodes (HA):

| Stack | Port | Apps | Auth mechanism |
|-------|------|------|----------------|
| stack-a | 8080 | WordPress, WhoAmI | Form login injection |
| stack-b | 9080 | Redmine, Jellyfin | Form login injection, Token injection |
| stack-c | 18080 | Grafana, phpMyAdmin | Header injection, HTTP Basic Auth |

Keycloak chạy shared tại `http://auth.sso.local:8080`, realm `sso-lab`.

---

## Phân công AI — TUYỆT ĐỐI TUÂN THỦ

| Agent | Được làm | KHÔNG được làm |
|-------|----------|----------------|
| **Claude** | Điều phối, verify kết quả, quyết định bước tiếp theo, viết CLAUDE.md/MEMORY.md | Tự sửa code/config kỹ thuật |
| **Codex** | MỌI file kỹ thuật: .groovy, .json, .sh, .yml, .conf, .xml, .hcl | Research, web search |
| **Gemini** | Research, web search, đọc source thư viện, phân tích log, viết/cập nhật .md docs | Viết code/config dù bất kỳ lý do gì |

### Codex — model selection

```bash
# Task rõ ràng, viết file theo chỉ dẫn
codex exec --skip-git-repo-check --full-auto \
  -m gpt-5.3-codex -c model_reasoning_effort="medium" \
  -C <workdir> "prompt" 2>/dev/null

# Debug phức tạp, refactor lớn, nhiều file
codex exec --skip-git-repo-check --full-auto \
  -m gpt-5.3-codex -c model_reasoning_effort="high" \
  -C <workdir> "prompt" 2>/dev/null

# Bug lặp lại, root cause khó, cần 1M context
codex exec --skip-git-repo-check --full-auto \
  -m gpt-5.4 -c model_reasoning_effort="high" \
  -C <workdir> "prompt" 2>/dev/null

# Read-only analysis
codex exec --skip-git-repo-check --full-auto \
  -m gpt-5.3-codex -c model_reasoning_effort="medium" \
  --sandbox read-only -C <workdir> "prompt" 2>/dev/null
```

**Lưu ý:** KHÔNG dùng `--full-auto` + `--sandbox` cùng lúc → conflict.

### Gemini — approval mode

```bash
gemini -p "prompt" --approval-mode default -o text 2>/dev/null    # research
gemini -p "prompt" --approval-mode auto_edit -o text 2>/dev/null  # viết .md
gemini -p "prompt" --approval-mode yolo -o text 2>/dev/null       # chạy shell
```

### Parallel Codex + Gemini (khi Codex stuck 2+ lần)

```
Claude detect stuck
  ├── Codex gpt-5.4 high: retry với broader context
  └── Gemini default: research root cause từ source/docs/web
Claude tổng hợp → Codex resume với context mới
```

---

## Kiến trúc quan trọng

### HA pattern (tất cả stacks)
- nginx `ip_hash` → sticky routing (cùng IP → cùng OpenIG node)
- `JwtSession` → session mã hóa trong cookie, stateless, mọi node đọc được
- Vault credentials shared mount → cả 2 node dùng chung `role_id`/`secret_id`

### Vault — file storage (production mode)
- Config: `vault/config/vault.hcl` (multi-line HCL, KHÔNG dùng semicolon)
- docker-compose: `command: server` (KHÔNG explicit `-config` path)
- Keys: `vault/data/.vault-keys.unseal`, `vault/data/.vault-keys.root`
- Bootstrap flag: `vault/data/.bootstrap-done`
- **Sau mỗi Docker restart**: Vault sealed → chạy bootstrap script → regenerate `secret_id` → restart OpenIG

### Cookie session
- Stack A: `IG_SSO`, `cookieDomain: ".sso.local"`
- Stack B: `IG_SSO_B` (thiếu cookieDomain — optimization gap, không ảnh hưởng correctness)
- Stack C: `IG_SSO_C`, `cookieDomain: ".sso.local"`

### SLO mechanism
- Keycloak gửi backchannel logout → `BackchannelLogoutHandler.groovy` → ghi Redis blacklist
- `SessionBlacklistFilter.groovy` check Redis trên mỗi request → kick session nếu blacklisted
- Mỗi stack có Redis riêng: `sso-redis-a`, `sso-redis-b`, `stack-c-redis-c-1`

---

## Container names

| Component | Stack A | Stack B | Stack C |
|-----------|---------|---------|---------|
| nginx | `sso-nginx` | `sso-b-nginx` | `stack-c-nginx-c-1` |
| openig-1 | `sso-openig-1` | `sso-b-openig-1` | `stack-c-openig-c1-1` |
| openig-2 | `sso-openig-2` | `sso-b-openig-2` | `stack-c-openig-c2-1` |
| vault | `sso-vault` | `sso-b-vault` | `stack-c-vault-c-1` |
| redis | `sso-redis-a` | `sso-redis-b` | `stack-c-redis-c-1` |
| app1 | `sso-wordpress` | `sso-b-redmine` | `stack-c-grafana-1` |
| app2 | `sso-whoami` | `sso-b-jellyfin` | `stack-c-phpmyadmin-1` |
| db | `sso-mysql` | `sso-b-mysql-redmine` | `stack-c-mariadb-1` |
| keycloak | `sso-keycloak` (shared) | — | — |

---

## URLs và credentials

### App URLs (browser)
| App | URL |
|-----|-----|
| Keycloak | `http://auth.sso.local:8080` |
| WordPress | `http://wp-a.sso.local` (port 80) |
| WhoAmI | `http://whoami-a.sso.local` (port 80) |
| Redmine | `http://redmine-b.sso.local:9080` |
| Jellyfin | `http://jellyfin-b.sso.local:9080` |
| Grafana | `http://grafana-c.sso.local:18080` |
| phpMyAdmin | `http://phpmyadmin-c.sso.local:18080` |

### Keycloak test users
- `alice` / `alice123` / `alice@lab.local`
- `bob` / `bob123` / `bob@lab.local`

### App credentials (injected by OpenIG via Vault)
- WordPress: `alice_wp`, `bob_wp`
- Redmine: `alice` (login `alice@lab.local`), `bob` (login `bob@lab.local`)
- Jellyfin: `alice` / `AliceJelly2026`, `bob` / `BobJelly2026` (**set thủ công, không trong bootstrap**)
- Grafana: auto-provisioned từ `preferred_username`
- phpMyAdmin: `alice`, `bob` (MariaDB users, từ Vault `secret/phpmyadmin/*`)

---

## Checklist restart sau Docker restart

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

---

## Conventions bắt buộc

### Nginx — F5 BIG-IP alignment
Mọi nginx config phải viết theo pattern F5 BIG-IP để dễ migrate:
- Upstream pool naming: `<app>_pool` (ví dụ: `grafana_pool`, `wordpress_pool`)
- Luôn có `proxy_set_header Host $host` và `X-Real-IP $remote_addr`
- Strip trusted headers từ client trước khi inject: `proxy_set_header X-WEBAUTH-USER ""`
- Dùng `ip_hash` cho sticky routing, `keepalive` cho OneConnect pattern

### Vault bootstrap
- HCL multi-line (KHÔNG semicolon)
- `command: server` trong docker-compose (KHÔNG explicit `-config` path)
- Fix exit code: `code=$(vault status >/dev/null 2>&1; echo $?)` + `|| true` cho pipelines

### Docs
- Mọi docs nằm trong `docs/` với tên lowercase
- `docs/test-cases.md` — test cases chuẩn
- `docs/test-report.md` — kết quả test gần nhất
- `docs/vault-hardening-gaps.md` — gap analysis Vault production hardening

---

## Roadmap

### Đã hoàn thành
- [x] Stack A: WordPress SSO + SLO
- [x] Stack B: Redmine SSO + SLO, Jellyfin token injection
- [x] Stack C: Grafana header injection, phpMyAdmin Basic Auth
- [x] Vault migration: dev mode → file storage (tất cả stacks)
- [x] Test cases + test report (docs/)

### Đang pending
- [ ] SLO test thủ công: stack-b (Redmine + Jellyfin), stack-c (Grafana + phpMyAdmin)
- [ ] Cross-stack SLO test
- [ ] Fix Jellyfin WebSocket: `http://` → `ws://` scheme trong route `01-jellyfin.json`
- [ ] Stack B: thêm `cookieDomain: ".sso.local"` vào `config.json` (LOW priority)

### Phase tiếp theo
- [ ] Phase 3: Vault Production Hardening (9 gaps — xem `docs/vault-hardening-gaps.md`)
- [ ] Post-Stack C docs: OpenIG built-in filter selection guide (Gemini research OpenIG source)

---

## /etc/hosts cần có

```
127.0.0.1  auth.sso.local
127.0.0.1  wp-a.sso.local
127.0.0.1  whoami-a.sso.local
127.0.0.1  openiga.sso.local
127.0.0.1  redmine-b.sso.local
127.0.0.1  jellyfin-b.sso.local
127.0.0.1  openigb.sso.local
127.0.0.1  grafana-c.sso.local
127.0.0.1  phpmyadmin-c.sso.local
127.0.0.1  openig-c.sso.local
```

---

## Gotchas đã biết

| Vấn đề | Nguyên nhân | Fix |
|--------|-------------|-----|
| "SSO authentication failed" | openig-2 có file `role_id`/`secret_id` rỗng | Regenerate AppRole credentials → ghi vào shared mount |
| Vault "bind: address already in use" | `command: server -config=...` + entrypoint tự thêm `-config` → 2 listeners | Dùng `command: server` only |
| Bootstrap script exit code 2 | `vault status` exit 2 khi sealed, `set -euo pipefail` abort | Dùng `code=$(vault status; echo $?)` + `|| true` |
| phpMyAdmin không nhận Basic Auth từ env | `PMA_AUTH_TYPE=http` không hoạt động với Docker image | Mount `config.user.inc.php` với `$cfg['Servers'][1]['auth_type'] = 'http'` |
| Jellyfin WebSocket lỗi | Route proxy dùng `http://` thay vì `ws://` | **Chưa fix** — pending |
