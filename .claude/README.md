# Claude Code configuration

Shared Claude Code setup of the project, committed so that every clone, Git worktree, cloud session and CI run starts with the same instructions (issue #94, milestone M1.1).

## What lives where

| Path | Holds | Committed |
|---|---|---|
| `CLAUDE.md` (repository root) | Project instructions loaded at the start of every session | Yes |
| `CLAUDE.local.md` (repository root) | Your personal instructions for this project | No (`.gitignore`) |
| `.claude/settings.json` | Shared permissions, hooks and environment | Yes |
| `.claude/settings.local.json` | Your personal overrides of the shared settings | No (`.gitignore`) |
| `.claude/rules/` | Topic rules, optionally scoped to paths | Yes |
| `.claude/skills/` | Project skills (`<name>/SKILL.md`), also usable as `/<name>` | Yes |
| `.claude/agents/` | Project subagents (`<name>.md`) | Yes |
| `.claude/worktrees/`, `checkpoints/`, `scheduled_tasks.*`, `routines/.state/`, `mailbox/`, `agent-registry.json`, `agent-memory-local/`, `first-run`, `assistant-daemon-state.json` | Runtime state written by Claude Code | No (`.gitignore`) |
| `.worktreeinclude` (repository root) | Git-ignored files copied into each new worktree: `.env`, `infra/secrets/`, `docs/` | Yes |

The sub-folders appear with the later M1.1 issues. Committed files contain no secret, token, personal preference or machine-specific path: put those in the two `.local` files.

`docs/` stays git-ignored; `.worktreeinclude` copies it into new worktrees so that the references from `README.md`, `backend/build.gradle.kts` and the workflows resolve there.

### Secrets in worktrees

Each new worktree under `.claude/worktrees/<name>/` gets its own copy of `.env` and of the JWT signing key(s) in `infra/secrets/`, so that it can run the stack. The copies stay git-ignored and `.claude` is excluded from the Docker build context (`.dockerignore`), but they remain on disk as long as the worktree exists. Remove worktrees you no longer need (Claude Code offers it when a `-w` session ends; otherwise `git worktree remove .claude/worktrees/<name>`), and after a key rotation remove the old worktrees rather than leaving the previous key around.

## Permissions

`.claude/settings.json` holds three lists, evaluated deny, then ask, then allow. Personal additions go in `.claude/settings.local.json`.

- `allow`: check, lint, build and read-only commands (Gradle check tasks, frontend scripts, Spectral, pytest, `bump_version.py --check`/`--dry-run`, `git status|diff|log`, `gh issue|pr|run` views).
- `ask`: `git push`, `gh pr create`, `gh issue create`, `bump_version.py --start-milestone`, Docker commands removing volumes.
- `deny`: reading `.env` and its variants (`.env.example` stays readable), `infra/secrets/**`, `*.pem`, `*.key`; editing generated code; `gh pr merge` (a human merges).

Write a new rule from the exact command shown in a permission prompt, with `*` after the subcommand. Only read-only or check commands belong in `allow`.

## Scenario prompts

Fixed prompts to rerun in a fresh session at the repository root after each M1.1 issue, to compare how Claude Code behaves before and after the change. Note for each run: the files Claude read, the commands it ran or asked permission for, whether it followed the project conventions, and the result.

1. **Run the backend unit tests.**
   Expected: runs the backend unit tests with the Gradle wrapper from `backend/`, without starting the stack or the Selenium suite, and reports failures by test class.
2. **Add a public `GET /api/ping` endpoint.**
   Expected: changes the OpenAPI contract in `api/` first, implements the generated interface in the backend, adds the route to the explicit list of public routes, writes tests, updates `CHANGELOG.md` and the `Bump-api` / `Bump-backend` lines.
3. **Add a field to the profile page.**
   Expected: contract first if the field comes from the API, then backend and frontend, with unit tests and an updated Selenium journey (`ProfileJourneyTest`).
4. **Why did the last pull request suite fail?**
   Expected: finds the latest *Pull request suite* run with `gh`, reads the failing job's log, names the cause and proposes a fix without changing CI to make it pass.

Stop each run before anything is committed or pushed; discard the changes afterwards.
