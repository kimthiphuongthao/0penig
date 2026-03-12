# AI Agent Workflow

## Phân công — TUYỆT ĐỐI TUÂN THỦ

| Agent | Được làm | KHÔNG được làm |
|-------|----------|----------------|
| **Claude** | Điều phối, verify nguyên tắc, quyết định bước tiếp theo | Tự debug, tự đọc code tìm bug, tự sửa code/config kỹ thuật, tự viết bất kỳ file nào (kể cả .md) |
| **Codex** | MỌI file kỹ thuật: .groovy, .json, .sh, .yml, .conf, .xml, .hcl; đọc log, tìm root cause, đề xuất fix | Research web, web search |
| **Gemini** | Research, web search, đọc source thư viện, phân tích log ngoài project, viết .md docs | Viết code/config |

**Viết file (.md và mọi loại khác):**
→ Codex viết khi user/Claude chỉ định Codex
→ Gemini viết khi user/Claude chỉ định Gemini
→ Claude KHÔNG tự viết file nào — khi chưa có chỉ định, Claude hỏi trước

**Khi user gửi log/error:**
→ Claude KHÔNG tự debug → giao thẳng Codex phân tích log + tìm root cause + đề xuất fix
→ Codex báo cáo findings theo format (file/section, tuân thủ nguyên tắc, nội dung thay đổi)
→ Claude verify nguyên tắc → trình bày cho user confirm → Codex implement

## Codex — model selection

```bash
# gpt-5.3-codex medium — CHỈ dùng khi: viết file mới theo spec đã rõ ràng 100%, không có bug, không cần đọc code cũ
codex exec --skip-git-repo-check --full-auto \
  -m gpt-5.3-codex -c model_reasoning_effort="medium" -C <workdir> "prompt" 2>/dev/null

# gpt-5.4 high — dùng cho MỌI thứ còn lại: bug fix, debug, refactor, đọc code cũ, sửa config có logic
codex exec --skip-git-repo-check --full-auto \
  -m gpt-5.4 -c model_reasoning_effort="high" -C <workdir> "prompt" 2>/dev/null

# Read-only analysis (--sandbox bị --full-auto override → thực tế vẫn workspace-write)
codex exec --skip-git-repo-check --full-auto \
  -m gpt-5.3-codex -c model_reasoning_effort="medium" \
  -C <workdir> "prompt" 2>/dev/null
```

Notes:
- `--full-auto` + `--sandbox` KHÔNG conflict — chạy được, nhưng `--sandbox` bị override (luôn là workspace-write)
- `--search` KHÔNG phải flag hợp lệ của `codex exec` → exit code 2. Dùng Gemini cho web search thay thế.

## Gemini — approval mode

Mọi prompt gửi Gemini PHẢI bắt đầu bằng đoạn context sau:
```
Trước khi làm task, đọc các file sau để hiểu đủ ngữ cảnh:
- CLAUDE.md
- .gemini/GEMINI.md
- .claude/rules/ (toàn bộ)
Sau đó thực hiện task: [task cụ thể]
```

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

## Tra cứu docs trước khi giao Codex

Verify đúng API/config TRƯỚC khi viết prompt cho Codex:

| Library | Tool | Library ID / URL |
|---------|------|-----------------|
| Keycloak | Context7 | `/keycloak/keycloak` |
| Vault | Context7 | `/websites/developer_hashicorp_vault` |
| OpenIG 6 | WebFetch | `https://github.com/OpenIdentityPlatform/OpenIG` (raw source) |

**OpenIG WebFetch pattern:**
```
WebFetch https://raw.githubusercontent.com/OpenIdentityPlatform/OpenIG/master/<path>
```
Tìm file path theo thứ tự:
1. Gemini web search tìm path
2. Nếu không ra → Codex gpt-5.3-codex medium browse repo verify
3. Cả 2 confirm → Claude WebFetch raw content

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
5. **Sau mỗi lần fix xong**: tự chạy restart luôn (không chờ user nhắc), báo "đã restart, bạn test đi"
