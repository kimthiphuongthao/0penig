# SSO Lab — Codex Instructions

Always use tilth skill for file search, grep, or code read operations.

## Critical Rules

- NEVER modify target application code/config (app servers, databases, Keycloak config, app docker-compose)
- Only modify: OpenIG routes (*.json), Groovy scripts (*.groovy), nginx/nginx.conf, vault/ bootstrap
- Report format when finding issues: Which file/section → Compliant with rules? (Yes/No) → Proposed change → Wait for user confirmation

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

## Available Skills

| Skill | Directive |
|-------|-----------|
| **tilth** | Always use tilth for ALL code reading — replaces cat, grep, find, ls, tree, ripgrep. Use for file search, code navigation, symbol lookup, directory browsing. |
| **obsidian-markdown** | Always use obsidian-markdown when creating or editing `.md` files in the Obsidian vault — wikilinks, callouts, embeds, frontmatter. |
| **json-canvas** | Always use json-canvas when creating or editing `.canvas` files — diagrams, mind maps, flowcharts. |
| **obsidian-bases** | Always use obsidian-bases when creating or editing `.base` files — database-style views with filters and formulas. |
| **obsidian-cli** | Always use obsidian-cli for Obsidian vault operations via CLI (requires Obsidian open). |
| **defuddle** | Always use defuddle instead of WebFetch when reading content from any URL — docs, articles, blogs. Returns clean markdown. |
| **ask-gemini** | Use only as second opinion after completing primary analysis independently. Consult for supplementary external data (API docs, GitHub examples, Stack Overflow). Never use as primary source — Gemini supplements, never replaces, your own work. |
| **code-review** | Always use code-review when performing code review — quality, maintainability, SOLID principles, severity-rated findings (CRITICAL/HIGH/MEDIUM/LOW). |
| **security-review** | Always use security-review when performing security audit — OWASP Top 10, hardcoded secrets, unsafe patterns, auth/authz review. |
