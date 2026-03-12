# Codex CLI — Best Practices cho Prompt Input

**Mục đích:** Tài liệu này hướng dẫn cách tối ưu prompt khi gọi OpenAI Codex CLI để tránh tình trạng treo/stuck và đạt hiệu quả cao nhất.

**Nguồn:** OpenAI Codex CLI official documentation (Context7: `/openai/codex`)

---

## 1. Độ dài prompt tối ưu

| Thông số | Giá trị | Ghi chú |
|----------|---------|---------|
| Context window | 400K/1M+ tokens (UNVERIFIED) | Chưa có nguồn chính thức đủ thẩm quyền để xác nhận các con số này |
| **Khuyến nghị thực tế** | **50-200 từ** | Prompt ngắn, tập trung hiệu quả hơn |
| Ngưỡng "lost in the middle" | ~272K tokens | Thông tin ở giữa prompt dài dễ bị bỏ qua |

**⚠️ Lưu ý:** Dù context window lớn, prompt **ngắn và tập trung** vẫn hiệu quả hơn prompt dài loãng.

---

## 2. Cấu trúc prompt lý tưởng

### 2.1. Framework 4 phần

```
Goal: [hành động cụ thể]
Context: [thông tin cần thiết, file liên quan]
Constraints: [ràng buộc, framework, standards]
Done when: [tiêu chí hoàn thành rõ ràng]
```

### 2.2. Ví dụ

**Prompt tốt:**
```
Goal: Fix 401 error in backchannel logout route
Context: Route file `stack-c/openig_home/config/routes/00-backchannel-logout-app5.json`
         Keycloak POST to `/openid/app5/backchannel_logout`
Constraints: Do not modify Keycloak config, only OpenIG route changes
Done when: Backchannel logout triggers SloHandlerGrafana correctly
```

**Prompt không tốt:**
```
Sửa lỗi logout
```

---

## 3. Cấu hình model_reasoning_effort

Theo Context7, các option hợp lệ:

```toml
# ~/.codex/config.toml
model_reasoning_effort = "medium"  # none, minimal, low, medium, high, xhigh
model_reasoning_summary = "concise"  # auto, concise, detailed, none
```

### Khuyến nghị theo task

| Task type | Effort | Lý do |
|-----------|--------|-------|
| Code fix đơn giản | `low` hoặc `minimal` | Nhanh, đủ chính xác |
| Debug phức tạp | `medium` | Cân bằng tốc độ/độ sâu |
| Architecture review | `high` | Cần phân tích sâu |
| Research/refactor lớn | `xhigh` | Tối đa độ sâu |

---

## 4. Nguyên nhân Codex bị treo/stuck

### 4.1. Đã verify từ Context7

| Nguyên nhân | Mức độ | Giải pháp |
|-------------|--------|-----------|
| **Rate limits exceeded** | Cao | Monitor qua `account/rateLimits/read` |
| **ContextWindowExceeded** | Trung bình | Dùng `/compact` hoặc chia nhỏ task |
| **UsageLimitExceeded** | Trung bình | Check credits, plan limits |
| **Timeout API** | Thấp | Default 10 giây (`DEFAULT_EXEC_COMMAND_TIMEOUT_MS = 10_000`), có thể cấu hình |

### 4.2. Chưa verify từ Context7

| Nguyên nhân | Trạng thái |
|-------------|------------|
| Orphaned child processes | ⚠️ Chưa tìm thấy trong docs chính thức |
| Authentication loop | ⚠️ Chưa tìm thấy trong docs chính thức |

---

## 5. Best practices để tránh treo

### 5.1. Chia nhỏ task

**Thay vì:**
```bash
codex exec "Refactor toàn bộ hệ thống auth, thêm SSO multi-tenant, fix tất cả bugs"
```

**Dùng:**
```bash
# Task 1: Phân tích
codex exec "Phân tích codebase auth, liệt kê files cần thay đổi"

# Task 2: Implement từng phần
codex exec "Implement SSO cho component A"
codex exec "Implement SSO cho component B"
```

### 5.2. Thiết lập timeout qua Bash tool (không dùng `timeout -k`)

- Trên macOS không có sẵn lệnh `timeout`; ví dụ `timeout -k ...` chỉ phù hợp khi đã cài GNU coreutils.
- Trong Claude Code, ưu tiên đặt timeout trực tiếp ở tham số tool, ví dụ: `timeout: 120000` (120 giây).

### 5.3. Dùng --ephemeral cho task không cần persist

```bash
codex exec --ephemeral "analyze this function"
```

### 5.4. Skip shell init scripts (UNVERIFIED)

⚠️ Chưa có nguồn chính thức xác nhận pattern này là best practice của Codex CLI.

```bash
env -i bash --noprofile --norc codex exec "prompt"
```

### 5.5. `timeoutMs` trong `command/exec` (UNVERIFIED)

⚠️ Chưa có nguồn chính thức xác nhận `timeoutMs` là field được support ổn định cho `command/exec`.

```json
{
  "method": "command/exec",
  "params": {
    "command": ["ls", "-la"],
    "timeoutMs": 10000,
    "disableTimeout": false
  }
}
```

### 5.6. `--search` không phải flag hợp lệ của `codex exec`

- `codex exec --search ...` trả về lỗi (exit code `2`) vì flag không tồn tại.
- Khi cần web search, dùng Gemini (hoặc tool web riêng) thay vì `codex exec`.

---

## 6. Workflow Claude → Codex tối ưu

### 6.1. Pattern hiện tại (có thể cải thiện)

```bash
# Hiện tại: effort="high" cho mọi task
codex exec --skip-git-repo-check --full-auto \
  -m gpt-5.4 -c model_reasoning_effort="high" ...
```

### 6.2. Pattern khuyến nghị

```bash
# Task đơn giản (fix bug nhỏ, đọc file)
codex exec --skip-git-repo-check --full-auto \
  -m gpt-5.3-codex -c model_reasoning_effort="medium" ...

# Task phức tạp (debug sâu, architecture)
codex exec --skip-git-repo-check --full-auto \
  -m gpt-5.4 -c model_reasoning_effort="high" ...
```

### 6.3. Parallel execution cho task độc lập

```
Claude gọi song song:
├── Codex 1: "Phân tích log Stack A"
├── Codex 2: "Phân tích log Stack B"
└── Gemini: "Research Keycloak SLO issues"
```

---

## 7. Checklist trước khi gọi Codex

- [ ] Prompt đã dưới 200 từ chưa?
- [ ] Đã rõ Goal/Context/Constraints/Done criteria?
- [ ] Effort level phù hợp với task complexity?
- [ ] Có thể chia nhỏ thành nhiều task độc lập không?
- [ ] Đã đặt timeout ở tool layer (vd `timeout: 120000`) chưa?
- [ ] Rate limits còn đủ không? (`account/rateLimits/read` - UNVERIFIED)

---

## 8. Tham khảo

- **Context7 Library:** `/openai/codex`
- **GitHub:** https://github.com/openai/codex
- **Config docs:** `~/.codex/config.toml`
- **Rate limits:** `account/rateLimits/read` JSON-RPC (UNVERIFIED)

---

## 9. Lịch sử cập nhật

| Date | Change |
|------|--------|
| 2026-03-12 | Initial version — verify từ Context7 |
| 2026-03-12 | Corrections from Codex+Gemini verification |
