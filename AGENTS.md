Always use tilth skill for file search, grep, or code read operations.

## Obsidian Auto-Write

After every significant task (feature, bugfix, debug, route/script change, milestone) — write Obsidian notes **automatically, no confirmation required**.

**Vault:** `docs/obsidian/`

**Structure:**
```
docs/obsidian/
├── decisions/   # Architecture and design decisions
├── debugging/   # Bug investigations, root causes, fixes
├── how-to/      # Patterns and step-by-step guides
└── stacks/      # Per-stack status (stack-a.md, stack-b.md, stack-c.md)
```

**Rules:**
- Use `obsidian-markdown` skill
- Required frontmatter: `title`, `tags`, `date`, `status`
- Use wikilinks: `[[OpenIG]]`, `[[Keycloak]]`, `[[Vault]]`, `[[Stack C]]`
- Callouts: `[!tip]` best practices, `[!warning]` gotchas, `[!success]` confirmed working
- Content must be technical and specific: root cause, fix, decision rationale — no generics
- Route: bugfix/debug → `debugging/`, decision → `decisions/`, pattern → `how-to/`, stack state → `stacks/stack-X.md`

## Obsidian Skills

Codex có quyền truy cập vào bộ **obsidian-skills** đã cài đặt tại `~/.codex/skills/obsidian-skills/`:

| Skill | Khi nào dùng |
|-------|-------------|
| **obsidian-markdown** | Tạo/sửa tài liệu `.md` với Obsidian extensions: wikilinks `[[Note]]`, callouts `> [!tip]`, embeds `![[file]]`, frontmatter properties |
| **json-canvas** | Tạo/sửa file `.canvas` — mind maps, flowcharts, architecture diagrams với nodes/edges |
| **obsidian-bases** | Tạo/sửa file `.base` — database-style views với filters và formulas |
| **obsidian-cli** | Thao tác với Obsidian CLI (plugins, themes, vaults) |
| **defuddle** | Extract clean markdown từ URLs — thay thế WebFetch khi đọc docs, blogs, articles |

### Ví dụ sử dụng

```markdown
# Tạo architecture note
Dùng skill obsidian-markdown để tạo note mới về SSO Lab architecture với:
- Frontmatter properties (title, tags, date)
- Wikilinks đến các components [[OpenIG]], [[Keycloak]], [[Vault]]
- Callout cho known issues và best practices

# Vẽ diagram
Dùng skill json-canvas để tạo flow diagram cho SLO flow:
- Nodes: User, OpenIG, Keycloak, Redis
- Edges: kết nối các bước trong flow

# Research docs
Dùng skill defuddle để lấy nội dung markdown từ URL docs của OpenIG/Keycloak
```
