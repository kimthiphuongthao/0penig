# AI Agent Workflow

## Phân công — TUYỆT ĐỐI TUÂN THỦ

| Agent | Được làm | KHÔNG được làm |
|-------|----------|----------------|
| **Claude** | Điều phối, verify, quyết định bước tiếp theo, viết .md | Tự sửa code/config kỹ thuật |
| **Codex** | MỌI file kỹ thuật: .groovy, .json, .sh, .yml, .conf, .xml, .hcl | Research, web search |
| **Gemini** | Research, web search, đọc source thư viện, phân tích log, viết .md docs | Viết code/config |

## Codex — model selection

```bash
# Task rõ ràng, viết file theo chỉ dẫn
codex exec --skip-git-repo-check --full-auto \
  -m gpt-5.3-codex -c model_reasoning_effort="medium" -C <workdir> "prompt" 2>/dev/null

# Mọi thứ phức tạp hơn: debug, refactor, nhiều file, root cause khó
codex exec --skip-git-repo-check --full-auto \
  -m gpt-5.4 -c model_reasoning_effort="high" -C <workdir> "prompt" 2>/dev/null

# Read-only analysis
codex exec --skip-git-repo-check --full-auto \
  -m gpt-5.3-codex -c model_reasoning_effort="medium" \
  --sandbox read-only -C <workdir> "prompt" 2>/dev/null
```

KHÔNG dùng `--full-auto` + `--sandbox` cùng lúc → conflict.

## Gemini — approval mode

```bash
gemini -p "prompt" --approval-mode default -o text 2>/dev/null    # research
gemini -p "prompt" --approval-mode auto_edit -o text 2>/dev/null  # viết .md
gemini -p "prompt" --approval-mode yolo -o text 2>/dev/null       # chạy shell
```

## Parallel Codex + Gemini (Codex stuck 2+ lần)

```
Claude detect stuck
  ├── Codex gpt-5.4 high: retry broader context
  └── Gemini default: research root cause
Claude tổng hợp → Codex resume với context mới
```

## Context7 MCP — dùng trước khi giao Codex

Tra cứu đúng API/config của library version đang dùng TRƯỚC khi viết prompt cho Codex:
- OpenIG 6 filter/handler params
- Vault API endpoints, policy syntax
- Keycloak OIDC endpoints, backchannel logout spec

## Context management

| Câu nhắn | Claude làm gì |
|----------|---------------|
| "tôi đã trở lại, chúng ta tiếp tục công việc" | Đọc CLAUDE.md + MEMORY.md → khởi động lab → báo cáo |
| "tôi cần tắt máy" | Commit code + update MEMORY.md + push |
| "context còn 10%" | Update CLAUDE.md + MEMORY.md + .gemini/GEMINI.md → báo "có thể /compact" |
| "tóm tắt trạng thái project" | Đọc roadmap + MEMORY.md → báo cáo |
| "nhớ lại điều này: ..." | Ghi vào MEMORY.md (+ CLAUDE.md nếu quan trọng) |

### Quy tắc Claude tự làm (không cần nhắc)
1. Sau milestone lớn: update Roadmap trong CLAUDE.md
2. Phát hiện gotcha/bug mới: thêm vào `rules/gotchas.md`
3. Có decision quan trọng: ghi lý do vào `rules/gotchas.md`
4. Tắt máy: commit + update MEMORY.md + push
