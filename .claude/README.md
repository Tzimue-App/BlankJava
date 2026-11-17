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

## Hooks

Declared in `.claude/settings.json`, scripts in `.claude/hooks/` (Bash, they read the hook input with `jq`: on Windows install Git for Windows and `jq`). Check with `/hooks`.

| Event | Script | Does |
|---|---|---|
| `PreToolUse` `Edit\|Write` | `protect-migrations.sh` | Exit 2 when the target is an existing file in `backend/src/main/resources/db/migration/`; a new file is allowed |
| `PostToolUse` `Edit\|Write` on `frontend/**` | `format-frontend.sh` | Runs Prettier on the edited file |
| `Stop` | `spotless-stop.sh` | Runs `spotlessApply` then `spotlessCheck` in `backend/` and `e2e/`, only for a module with a changed `.java` or `.gradle.kts` file (`git diff HEAD` plus untracked); a failure is returned to Claude (exit 2) |

Limits: the migration guard covers `Edit` and `Write`, not `Bash` (`sed -i`, `mv`, `rm`); a missing `jq` makes it refuse the edit (fail closed) and the two other hooks skip. The `Stop` hook only sees uncommitted and untracked files, so a Java change committed during the same turn is not formatted: run `./gradlew spotlessApply` before committing. Gradle wrappers are run through `bash`, so the missing executable bit of `e2e/gradlew` does not matter.


## Skills

Five skills in `.claude/skills/<name>/SKILL.md`. Three automate the issue and pull request workflow of `.github/ISSUE_GUIDELINES.md`; each has `disable-model-invocation: true`: it appears in the `/` menu but Claude never starts it by itself, because each one has side effects (a branch, a push, an issue).

| Skill | Does | Asks before |
|---|---|---|
| `/start-issue <number>` | Checks the issue is ready (solution, acceptance criteria, dependencies merged into the milestone branch), creates `<type>/<number>-<slug>` from the milestone branch, restates the definition of done | Stops on a missing part or unmerged dependency, creates no branch |
| `/open-pr` | Runs the checks of the changed modules, updates `CHANGELOG.md` and the `Bump-` lines, runs `bump_version.py --check`, fills the pull request template | `git push` and `gh pr create` |
| `/draft-issue <summary>` | Picks the template, writes the draft in `.github/issues/` with Given / when / then criteria | `gh issue create` |

No skill merges: a human merges.

Two more skills only describe how to work, so Claude may load them on its own, restricted by `paths` to the modules they concern. Neither pushes nor files anything.

| Skill | Loads for | Does |
|---|---|---|
| `add-endpoint` | `api/**`, `backend/**` | Contract first, Spectral, backend implementation, public routes, slice test with `matchesContract()`, `./gradlew check`, frontend client, `Bump-` values; says which steps belong to an API, a backend and a frontend issue |
| `e2e-journey` | `e2e/**` | Starts the stack with the commands of `e2e/README.md`, Page Object, `@SeleniumTest` test without `Thread.sleep`, accessibility check, runs the suite and reads the results, adds the *Journeys* row |

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
