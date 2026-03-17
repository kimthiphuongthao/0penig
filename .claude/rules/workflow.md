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
→ Writer (omc) chỉ chạy khi user chủ động yêu cầu — Claude KHÔNG tự gọi Writer
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

## Codex — Trigger Skills

Codex trigger skill chủ yếu theo mức độ khớp ngữ nghĩa giữa prompt và `name` + `description` trong frontmatter của `SKILL.md`, không chỉ theo exact keyword.

| Cách viết prompt | Tác dụng |
|------------------|----------|
| Nêu đúng intent bằng cụm rõ ràng như `"security review"` hoặc `"code review"` | Tăng độ tin cậy khi match skill |
| Chỉ rõ scope | Giúp Codex load đúng skill và giới hạn phạm vi làm việc |
| Ghi checklist/criteria | Cho biết cần review theo tiêu chí nào |
| Chỉ định output format | Giúp kết quả trả về đúng format Claude cần |

**Prompt cho Claude gửi Codex nên có đủ:**
- Intent rõ: ví dụ `"security review"` hoặc `"code review"` nếu muốn tăng reliability
- Scope rõ: file, thư mục, route, module nào cần xử lý
- Checklist/criteria: ví dụ OWASP, secrets, validation, regression risk, test gaps
- Expected output: ví dụ findings theo severity, file/path, proposed fix

**Lưu ý:**
- Exact phrase giúp trigger ổn định hơn, nhưng semantic match mới là cơ chế chính
- Các mục body như `When to Use` hữu ích sau khi skill đã được load, không phải tín hiệu trigger chính
- Không cần flag `--skill`; chất lượng prompt quyết định việc skill có được chọn đúng hay không
- Skills nằm tại `~/.codex/skills/<skill-name>/SKILL.md`

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

## Planning Workflow — khi cần plan bất kỳ task nào

### Input cho Planner

Planner cần nhận ĐỦ 2 loại input trong prompt:
1. **Review findings** — output từ code-review / security-review (file paths, severity, evidence)
2. **Architecture reference** — chỉ Planner đọc: `architecture.md`, `standard-gateway-pattern.md`, `gotchas.md`

Thiếu architecture reference → plan sẽ thiếu enterprise framing (trust rules per mechanism, component roles).
Đã verified qua 2 rounds A/B test (2026-03-15): Planner + architecture docs > Codex cho planning task.

### Standard workflow (mọi task)

```
Claude cung cấp input (review findings + architecture docs paths)
    ↓
PLANNER (Opus)
  → Đọc review findings + architecture docs
  → Interview user cho design decisions (1 question at a time)
  → Spawn explore agent cho codebase verification
  → Generate plan → .omc/plans/*.md
    ↓
CRITIC (Opus, read-only, disallowedTools: Write/Edit)
  → Verify file refs, pre-mortem, gap analysis, multi-perspective
  → Output: REJECT / REVISE / ACCEPT
    ↓
  REJECT → Claude extract structured findings (không forward raw text)
         → Pass về PLANNER với specific issues cần sửa
         → PLANNER revise → CRITIC lại (loop cho đến ACCEPT)
    ↓
  ACCEPT → Plan ready → Claude set Current Task trong MEMORY.md
```

### High-stakes only (plan >10 fixes cross-stack, hoặc thay đổi không reversible)

```
PLANNER → CODEX cross-validate (technical feasibility) → CRITIC → CODEX implement
```

### Critic output handling

Critic có `disallowedTools: Write, Edit` → output chỉ tồn tại trong Agent response text.
Claude PHẢI extract findings thành structured list trước khi pass:
- Findings theo severity (CRITICAL / MAJOR / MINOR)
- Cụ thể: step nào cần sửa, vì sao, evidence
- KHÔNG forward raw Critic text (Planner nhận noise → revise sai)

### Codex prompt cho execution task (1 task = 1 conversation)

Template cho pre-confirmed fix (đã có trong plan, đã có acceptance criteria):
```
[intent label] task — [Stack X] only.

Context: [FIX-XX] already confirmed in .omc/plans/[plan-file].md.
No investigation needed. Implement directly.

File(s) to change: [exact paths]

Changes:
1. [specific change 1]
2. [specific change 2]

After implementing:
- Verify change looks correct
- Run: docker restart [containers]
- Check: docker logs [container] 2>&1 | grep 'Loaded the route'
- Report: what was changed (lines), restart result, any errors
```

Quy tắc label:
- `bug fix` — sửa lỗi logic, namespace, missing check
- `security hardening` — fail-closed, timeout, secret externalization
- `config change` — TTL, requireHttps, env vars
- KHÔNG dùng `security review + code fix` (mislabel → triggers wrong Codex skill)
- KHÔNG dùng 'report format + chờ confirm' (đã pre-confirmed trong plan)

### Post-batch documentation update

Sau mỗi batch fix xong + user test confirm:
1. Codex update `architecture.md` nếu architecture thay đổi (container, URLs, patterns)
2. Codex update `standard-gateway-pattern.md` nếu controls mới implement
3. Codex update `gotchas.md` nếu phát hiện gotcha mới
4. Với `standard-gateway-pattern.md`: dùng 3-pass QA (Codex update → Critic verify → Codex revise nếu cần)

---

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
| "tôi đã trở lại, chúng ta tiếp tục công việc" | Đọc MEMORY.md `## Current Task` → nếu có `Files liên quan` thì load những file đó → báo cáo: Task, Type, Trạng thái, Last action, Next step — không hỏi thêm |
| "tôi cần tắt máy" / kết thúc conversation | Update `## Current Task` theo template chuẩn → commit/push nếu có thay đổi chưa commit → báo sẵn sàng |
| "task này xong rồi" / "đóng task này" | Move `## Current Task` vào `## Completed` (1 dòng tóm tắt) → set task tiếp theo từ `## Pending tasks` vào `## Current Task` → báo task mới |
| "context còn 10%" | Tóm tắt ngắn + update `## Current Task` → đề xuất /compact |
| "tóm tắt trạng thái project" | Đọc MEMORY.md + CLAUDE.md roadmap → báo cáo |
| "nhớ lại điều này: ..." | Lưu vào MEMORY.md section phù hợp |

**Template chuẩn `## Current Task` — dùng cho MỌI task type:**
```
## Current Task

**Task:** <tên task ngắn gọn>
**Type:** implementation | documentation | investigation | planning | other
**Trạng thái:** <in progress | blocked | waiting for X>
**Last action:** <hành động cuối cùng đã làm — đủ để resume ngay>
**Next step:** <bước tiếp theo cụ thể — không cần đọc thêm gì mới hiểu được>
**Files liên quan:**        ← bỏ section này nếu không có file cần load
- <role>: <path>
```

**Nguyên tắc:**
- `Last action` phải đủ chi tiết để sau crash/restart biết đã làm đến đâu — không ghi chung chung
- `Next step` phải actionable ngay — không assume context nào khác
- `Files liên quan` chỉ ghi file CẦN ĐỌC để hiểu context (plan, checklist, spec) — không ghi file đã đọc xong
- Không có plan file → không cần `Files liên quan` (documentation task, investigation task, v.v.)
- Khi task hoàn thành: move 1 dòng vào `## Completed` → set task mới từ `## Pending tasks`

### Quy tắc Claude tự làm (không cần nhắc)
1. Sau milestone lớn: rà soát roadmap và giao agent phù hợp cập nhật tài liệu khi cần
2. Phát hiện gotcha/bug mới: tạo action để Codex/Gemini cập nhật `rules/gotchas.md`
3. Có decision quan trọng: tạo action ghi rationale vào tài liệu phù hợp qua Codex/Gemini
4. Sau mỗi action quan trọng (fix xong, investigation xong, decision confirmed, file committed): update `Last action` + `Next step` trong `## Current Task` MEMORY.md ngay — không chờ user nói "tắt máy"
5. Tắt máy / kết thúc conversation: final update `## Current Task` → commit/push
6. **Sau mỗi lần fix xong**: yêu cầu Codex chạy restart luôn (không chờ user nhắc), báo "đã restart, bạn test đi"

## Master Backlog + Post-Task Mandatory Checklist

## Master Backlog — nguồn sự thật duy nhất

File: `docs/fix-tracking/master-backlog.md`
- Chứa TẤT CẢ findings từ audit, mọi phase
- Mỗi finding: ID | Finding | Priority | Status | Files to change | Files to update after | Commit hash
- Status: OPEN → IN PROGRESS → DONE
- Commit hash: bằng chứng đã làm, không thể nhầm

**Atomic commit rule (BẮT BUỘC):**
Mỗi finding fix = 1 commit duy nhất gồm: code files + master-backlog.md (status DONE) + doc files cần update
KHÔNG commit code mà không update backlog cùng lúc.

## Post-Task Mandatory Checklist (sau MỖI finding/task)

Thực hiện THEO THỨ TỰ trước khi báo "xong":

1. **Code verified** — restart containers + test pass
2. **.memory/MEMORY.md** — Current Task: finding ID tiếp theo, last action, next step
3. **docs/fix-tracking/master-backlog.md** — status → DONE, điền commit hash
4. **CLAUDE.md** — roadmap nếu milestone/phase hoàn thành
5. **docs/audit/2026-03-17-production-readiness-gap-report.md** — mark finding RESOLVED
6. **docs/audit/2026-03-16-pre-packaging-audit/07-consolidated-action-items.md** — update status
7. Tùy finding type:
   - Security control mới → docs/deliverables/standard-gateway-pattern.md
   - Integration process thay đổi → docs/deliverables/legacy-app-team-checklist.md (FILE TỐI THƯỢNG)
   - Architecture thay đổi → .claude/rules/architecture.md
   - Gotcha mới/resolved → .claude/rules/gotchas.md
   - Stack behavior thay đổi → docs/obsidian/stacks/stack-*.md
   - Auth pattern thay đổi → docs/deliverables/legacy-auth-patterns-definitive.md
8. **docs/obsidian/03-State/Current State.md** — live state snapshot
9. **docs/progress.md** — weekly entry
10. **Atomic commit** — tất cả files trên trong 1 commit

**Start-of-conversation protocol:**
Khi "tiếp tục công việc":
1. Đọc .memory/MEMORY.md → lấy finding ID đang làm
2. Đọc docs/fix-tracking/master-backlog.md → xem status thực tế của finding đó
3. Nếu status = DONE nhưng MEMORY chưa update → update ngay, KHÔNG re-do
4. Báo: Task, Status, Last action, Next step — không hỏi thêm
