# Claude environment audit criteria

> **Initial grid, to be validated by the owner.** Sources: the official Claude Code docs (https://code.claude.com/docs/) as read on 2026-11-20 (Claude Code 2.1.329) and common best practices. The auditor re-verifies the docs at each audit: where this grid and the docs differ, the docs win and the audit says so.

Severity guide: **critical** = security hole, secret exposure, or a component that silently does the opposite of its intent. **major** = a component that does not work as intended, a real conflict, a large context cost. **minor** = hygiene, naming, small redundancy. **info** = observation or convention remark.

## 1. CLAUDE.md and imports

- Locations that load: managed policy, `~/.claude/CLAUDE.md`, `./CLAUDE.md` or `./.claude/CLAUDE.md`, `./CLAUDE.local.md` (personal, must be in `.gitignore`). Files are concatenated, not overridden; subfolder files load on demand.
- Short and specific (the docs suggest staying under about 200 lines): concrete, verifiable instructions ("run `npm test` before committing"), not vague ones ("write good code").
- Only what Claude cannot read from the code (commands, gotchas, workflow). No duplicated README content.
- No contradictions between files (root, subfolders, rules, user level).
- `@path` imports: the target exists; max depth 4; no import of a huge file that costs context at every start.
- Deterministic requirements (must always happen) belong in a hook or a permission, not in prose. Topic-specific guidance for some paths belongs in a rule with `paths`. A multi-step procedure belongs in a skill.
- No obsolete references (renamed files, removed commands, past milestones).

## 2. Rules (`.claude/rules/`)

- Plain `.md` files, found recursively. `paths` frontmatter (list or comma-separated string, glob patterns, brace expansion) is the only field Claude Code reads. Invalid YAML makes it ignored.
- A rule without `paths` loads at launch like CLAUDE.md: it must earn that cost.
- A path-targeted rule's globs match files that exist; no rule duplicated in CLAUDE.md; no contradiction between rules.

## 3. Skills (`.claude/skills/<name>/SKILL.md`) and commands (`.claude/commands/`)

- Custom commands are merged into skills: a command file still works, but a skill of the same name wins. Prefer skills for new work; report a mix as a convention point.
- Frontmatter: `description` (and `when_to_use`) states what it does and when to use it, specifically enough to trigger correctly and not over-trigger. Kebab-case field names (`allowed-tools`, `disable-model-invocation`, `argument-hint`).
- Side-effect skills (commit, push, deploy, create issue) are manual-only (`disable-model-invocation: true`).
- `allowed-tools` is least privilege. Referenced files and scripts exist. `SKILL.md` is concise; long reference material is split into supporting files.
- No two skills with overlapping triggers. Total description size is a context cost (listed at every session).

## 4. Subagents (`.claude/agents/<name>.md`)

- Required: `name`, `description` (says when to delegate). Fields are camelCase and an unknown field is silently ignored (check with `claude --debug`): `tools`, `disallowedTools`, `model`, `permissionMode`, `maxTurns`, `skills`, `mcpServers`, `hooks`, `memory`, `background`, `effort`, `isolation`, `color`, `initialPrompt`, `omitClaudeMd`.
- Least privilege on `tools`; a read-only role has no Write, Edit or Bash. Model chosen on purpose (cheaper for mechanical work, `inherit` otherwise).
- `Agent(name)` allowlists only work for a main-thread agent (`claude --agent`); they are ignored in a subagent definition.
- Plugin subagents ignore `hooks`, `mcpServers` and `permissionMode`.
- Project-agent hooks run only after the workspace trust dialog is accepted.
- The prompt is self-contained: a subagent does not see the conversation.

## 5. Hooks

- Declared in settings (or frontmatter of a skill or agent) with event, matcher, `type`, `command`, `timeout`. Exit code 2 blocks (stderr is the reason); exit 1 is non-blocking, so a policy hook must use 2; exit 0 with JSON `hookSpecificOutput.permissionDecision` is the alternative.
- Paths go through `$CLAUDE_PROJECT_DIR` and are quoted. Windows: bash scripts need Git Bash and `jq`; otherwise declare `shell: powershell`.
- A guard fails closed; a convenience hook fails open and says so.
- Read the script: no unquoted variable in a command, no `eval` of input, bounded runtime, idempotent, no secret in output. Latency of `PostToolUse` and `Stop` hooks counts.
- A hook that duplicates a permission rule (or the reverse) is reported. `if` filters on a hook must use the documented syntax.

## 6. Settings and permissions

- `settings.json` (shared) versus `settings.local.json` (personal, ignored). Precedence: managed, command line, local, project, user.
- Rules evaluated deny, then ask, then allow. Allow rules for read-only and routine commands; destructive or outward-facing commands in `ask` or `deny`; secrets files in `deny` (`Read`).
- No over-broad allow (`Bash(*)`, `Bash(rm*)`, `Bash(curl*)`), no allow that a deny contradicts. Rules that Bash patterns cannot enforce reliably (arguments, chaining) are noted.
- `env` contains no secret. Model and effort settings are intentional.

## 7. MCP and plugins

- `.mcp.json` (shared) holds no secret (use environment variable expansion). Each server is used: tool definitions cost context at every session.
- Enabled plugins are intended and trusted; their skills, agents and hooks are counted in the inventory.

## 8. Versioning hygiene

- Shared (committed): `CLAUDE.md`, `.claude/settings.json`, rules, skills, commands, agents, hooks.
- Not committed: `CLAUDE.local.md`, `settings.local.json`, runtime state (worktrees, checkpoints, locks, agent memory), audit reports (`.audits/`).
- A documented index of the environment exists and is current (here: `.claude/README.md`).

## 9. Cross-cutting

- Single source of truth per rule of conduct; no contradiction across levels (managed, user, project, local).
- Separation of responsibilities: CLAUDE.md = facts and conventions; rules = path-scoped guidance; skills = procedures; agents = isolated delegated work; hooks and permissions = enforcement.
- Naming consistency (kebab-case names, verb-first skills, role-noun agents). No dead component (never referenced, obsolete, broken reference).
- Total context cost at session start (CLAUDE.md, unscoped rules, skill and agent descriptions, MCP tools).
