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
| `.claude/commands/` | Manual slash commands (`/audit-env`); commands are merged into skills, new procedures go in `.claude/skills/` | Yes |
| `.claude/audit/` | Criteria grid and report template of the environment auditor | Yes |
| `.audits/` (repository root) | Local audit reports written by `claude-env-auditor` | No (`.gitignore`) |
| `.claude/worktrees/`, `checkpoints/`, `scheduled_tasks.*`, `routines/.state/`, `mailbox/`, `agent-registry.json`, `agent-memory-local/`, `first-run`, `assistant-daemon-state.json` | Runtime state written by Claude Code | No (`.gitignore`) |
| `.worktreeinclude` (repository root) | Git-ignored files copied into each new worktree: `.env`, `infra/secrets/`, `docs/` | Yes |

The sub-folders appear with the later M1.1 issues. Committed files contain no secret, token, personal preference or machine-specific path: put those in the two `.local` files.

`docs/` stays git-ignored; `.worktreeinclude` copies it into new worktrees so that the references from `README.md`, `backend/build.gradle.kts` and the workflows resolve there.

### Secrets in worktrees

Each new worktree under `.claude/worktrees/<name>/` gets its own copy of `.env` and of the JWT signing key(s) in `infra/secrets/`, so that it can run the stack. The copies stay git-ignored and `.claude` is excluded from the Docker build context (`.dockerignore`), but they remain on disk as long as the worktree exists. Remove worktrees you no longer need (Claude Code offers it when a `-w` session ends; otherwise `git worktree remove .claude/worktrees/<name>`), and after a key rotation remove the old worktrees rather than leaving the previous key around.

## Permissions

`.claude/settings.json` holds three lists, evaluated deny, then ask, then allow. Personal additions go in `.claude/settings.local.json`.

- `allow`: check, lint, build and read-only commands (Gradle check tasks, frontend scripts, Spectral, pytest, `bump_version.py --check`/`--dry-run`, `git status|diff|log`, `gh issue|pr|run` views), and setting a commit status (`gh api repos/*/statuses/*`, the `agent-review` status of `/implement-issue`).
- `ask`: `git push`, `gh pr create`, `gh issue create`, `bump_version.py --start-milestone`, Docker commands removing volumes.
- `deny`: reading, editing, printing or copying the secret files (see *Secret file rules* below); editing generated code; merging or enabling auto-merge: `gh pr merge` (`--auto` included), the REST merge endpoints (`pulls/<n>/merge`, `merges`) and the GraphQL `mergePullRequest` and `enablePullRequestAutoMerge` mutations (a human merges; sub-issue pull requests are auto-merged by a workflow, never by Claude).

Write a new rule from the exact command shown in a permission prompt, with `*` after the subcommand. Only read-only or check commands belong in `allow`.

Secret file rules:

- What is denied: reading or editing (`Read`, `Edit`, which also covers `Write`) the secret files listed in `infra/README.md` (*Secret files*) and their variants, at any depth and by absolute path (the rules start with `//**/`, the filesystem root, so a file of another checkout is covered too): `.env` and every `.env*` name except `.env.example`, `infra/secrets/**`, `*.pem`, `*.key`; showing or copying them through `Bash` (`cat`, `type`, `head`, `tail`, `less`, `more`, `Get-Content`, `grep`, `sed`, `awk`, `cp`, `base64`) and through the `PowerShell` tool (`Get-Content`, `gc`, `cat`, `type`, `Select-String`, `sls`, `Copy-Item`, `cp`, `copy`, `cpi`, `more`) when the command contains `.env`, `infra/secrets` (or `infra\secrets`), `.pem` or `.key`; `git diff --no-index` is denied outright, whatever files it names (it compares any two files, tracked or not), also after git options (`git --no-pager diff --no-index`, `git -C . diff --no-index`): the rule is `Bash(git *diff*--no-index*)`.
- A deny rule has no exception, and `Read` rules do not support negated classes (`[!e]` and `[^e]` are read literally, so `Read(//**/.env.[!e]*)` denies `.env.example`). The `.env` variants are therefore denied with positive classes that hold every character from U+0001 (`\u0001` in the JSON, so a tab or another control character is covered) except the next letter of `example` in both cases, the letters already matched being written as case pairs: `.env[\u0001--0-\uffff]*` (anything but `.` after `.env`: `.envrc`, `.env~`, `.env - Copy`), `.env.[\u0001-DF-df-\uffff]*`, `.env.[eE][\u0001-WY-wy-\uffff]*`, …, `.env.[eE][xX][aA][mM][pP][lL][eE]?*`, plus the exact names `.env`, `.env.` (Windows drops a trailing dot, so it opens `.env`) and each prefix of `example` (`.env.[eE]` … `.env.[eE][xX][aA][mM][pP][lL]`). Names with characters above U+FFFF were not checked. `tests/test_claude_settings.py` checks these rules against a list of variants, with and without case folding; after a change, run it and check live by reading a missing `backend/.env.E2E` (denied) and `.env.example` (readable).
- Case: on Windows, checked live, `Read`, `Edit` and `Bash` matching ignore case (`.ENV`, `X.PEM`, `cat backend/.ENV` are denied), and `Bash` matches `infra\secrets` like `infra/secrets`. The `PowerShell` tool and other systems were not checked: its rules have no class, so they rely on the same folding, and the `infra\secrets` twins are there in case it does not normalise backslashes. Under a case-sensitive matcher (not checked), the case pairs keep `.env.E2E`, `.env.eXtra` and `.env.EX` denied, but only the lower-case `.env` is covered: `.ENV` or `.env.Example` are other files there and stay readable.
- `Bash` rules match the whole command with `*`, which has no class, so `cat .env.example` is denied too: read it with the `Read` tool. They also deny some harmless commands whose arguments contain `.env`, `.pem` or `.key` (`grep -rn "process.env" frontend`, `cp .env.example .env`): use the `Grep` tool, or run the command yourself.
- Not covered, so the `Bash` rules are a guard against mistakes, not a boundary: shell globs (`cat .en?`, `cat infra/secret?/*`), `cd infra/secrets && cat *`, other readers and movers (`xxd`, `od`, `nl`, `tac`, `strings`, `rg`, `diff`, `mv`, `tar`, `source .env`), wrappers and nested shells (`/usr/bin/cat`, `command cat`, `\cat`, `env cat`, `xargs cat`, `bash -c "cat .env"`, `powershell -c "Get-Content .env"`, `cat<.env` with no space), `PowerShell` forms that do not start with a denied command (`(Get-Content .env)`, `$c = gc .env`, `[IO.File]::ReadAllText('.env')`, `& gc .env`, `. gc .env`) and other `PowerShell` readers and movers (`Format-Hex`, `Import-Csv`, `Move-Item`, `mv`, `move`, `mi`), scripts (`python -c`, `docker compose config`), and Windows short names (`ENV~1`). A `PreToolUse` hook is the next step if these rules prove too narrow; `protect-migrations.sh` and `lib.sh` (`relative_target`) are the model to start from.

## Hooks

Declared in `.claude/settings.json`, scripts in `.claude/hooks/` (Bash, they read the hook input with `jq`: on Windows install Git for Windows and `jq`). Check with `/hooks`.

| Event | Script | Does |
|---|---|---|
| `PreToolUse` `Edit\|Write` | `protect-migrations.sh` | Exit 2 when the target is an existing file in `backend/src/main/resources/db/migration/`; a new file is allowed |
| `PostToolUse` `Edit\|Write` | `format-frontend.sh` | Runs Prettier on the edited file when it is under `frontend/` (not `node_modules`, `dist`, `coverage`, `.angular`); the script filters the path, because an `if: Edit(frontend/**)` filter in `settings.json` never matched on Windows (cause not confirmed; removed in #124) |
| `Stop` | `spotless-stop.sh` | Runs `spotlessApply` then `spotlessCheck` in `backend/` and `e2e/`, only for a module with a changed `.java` or `.gradle.kts` file (`git diff HEAD` plus untracked); a failure is returned to Claude (exit 2) |

One more hook is declared in the frontmatter of `claude-env-auditor` (not in `settings.json`, so `/hooks` does not list it), runs only while that subagent is active, and only after the workspace trust dialog is accepted:

| Event | Script | Does |
|---|---|---|
| `PreToolUse` `Write\|Edit\|Bash\|Agent\|Task` | `audit-write-guard.sh` | Write and Edit only under `.audits/claude-env/`; Bash only from a read-only allowlist (no redirection, no chaining, no unquoted glob), split into words by a quote-aware tokenizer, and no word may name a path matched by a `Read(//...)` deny rule of `settings.json` (the folder of `infra/secrets/**` included; `git show <rev>:<path>` and `--opt=<path>` checked after the `:` or `=`; relative paths resolved from the hook's `cwd`); Agent only `claude-env-analyst`. Exit 2 blocks, malformed input blocks; allowed calls answer `allow` |

Limits: the migration guard covers `Edit` and `Write`, not `Bash` (`sed -i`, `mv`, `rm`); a missing `jq` makes it refuse the edit (fail closed) and the two other hooks skip. The `Stop` hook only sees uncommitted and untracked files, so a Java change committed during the same turn is not formatted: run `./gradlew spotlessApply` before committing. Gradle wrappers are run through `bash`, so the missing executable bit of `e2e/gradlew` does not matter.


## Skills

Seven skills in `.claude/skills/<name>/SKILL.md`. Four automate the issue and pull request workflow of `.github/ISSUE_GUIDELINES.md`; each has `disable-model-invocation: true`: it appears in the `/` menu but Claude never starts it by itself, because each one has side effects (a branch, a push, an issue).

| Skill | Does | Asks before |
|---|---|---|
| `/start-issue <number>` | Checks the issue is ready (solution, acceptance criteria, dependencies merged into the milestone branch), creates `<type>/<number>-<slug>` from the milestone branch, restates the definition of done | Stops on a missing part or unmerged dependency, creates no branch |
| `/open-pr` | Runs the checks of the changed modules, updates `CHANGELOG.md` and the `Bump-` lines, runs `bump_version.py --check`, fills the pull request template | `git push` and `gh pr create` |
| `/draft-issue <summary>` | Picks the template, writes the draft in `.github/issues/` with Given / when / then criteria | `gh issue create` |
| `/implement-issue <number>` | Orchestrates an issue: sub-issues in dependency order, `implementer` writes, review loop of at most 3 rounds, pull request per sub-issue into the issue branch, `agent-review` status, then the issue pull request into the milestone branch; resumes from the GitHub and git state | `git push`, `gh pr create`, `gh api` (permission prompts); the issue pull request into the milestone branch |

No skill merges: a human merges every pull request into a milestone branch, `dev`, `test` or `main`; sub-issue pull requests are merged by GitHub auto-merge (see *Repository settings*).

`/issue-status <number>` is read-only: it shows the state `/implement-issue` works from (sub-issues, pull requests, review rounds, `agent-review`, questions waiting) and the next action.

### Issue orchestration

Roles: the orchestrator (`/implement-issue`, in the main session) decides and is the only one to write to GitHub; `implementer` writes and commits the code; `reviewer`, `/code-review` and, on the issue pull request, `/simplify` and `/security-review` check it. Sub-issues of one issue run **in sequence**: parallel work inside an issue costs several times the tokens for little gain. Parallelism is across issues, one session per issue:

1. `/start-issue <n>` in the main checkout: readiness, issue branch, push (asks), and the command to start the run.
2. In a new terminal at the repository root: `claude -w issue-<n> "/implement-issue <n>"` (its own worktree).
3. Per sub-issue: branch `<type>/<issue>-<sub-issue>-<slug>`, `implementer`, draft pull request into the issue branch, review rounds (comment `### Review round k` with each finding's decision, models and tokens), `agent-review` status, then `gh pr ready`: `sub-issue-automerge.yml` enables auto-merge (squash) and GitHub merges once `agent-review` is `success`; then the sub-issue is closed.

Triage: the orchestrator fixes, rejects or sends each finding to the person. The severity is never a reason to reject: a rejection must say the finding is wrong, out of scope (naming the criterion) or already rejected, a finding raised by both `reviewer` and `/code-review` is never rejected as out of scope, and the issue pull request lists every rejected finding so that the person can overrule it.
4. Issue pull request: `/simplify`, checks, `CHANGELOG.md` and `Bump-` lines, then review rounds with `/code-review high`, `/security-review` and `reviewer` against the parent issue. A human merges it.

Interrupted run (usage limit, closed session): run the same `/implement-issue <n>` again. `implementer` commits after each green step, and the orchestrator works out the state from merged and open pull requests, round comments and branches. Questions: critical ones put `need-manual` on the pull request and stop that issue only; answer in a pull request comment and rerun. Minor ones go to the milestone issue labelled `question`.

Measures: each round comment records the model and the token count of each subagent run (the counts the Agent tool returns; `n/a` for skills such as `/code-review`, which give none). Over several issues this compares models on real work; use `/usage` and the `session-report` plugin for the whole session.

Two more skills only describe how to work, so Claude may load them on its own, restricted by `paths` to the modules they concern. Neither pushes nor files anything.

| Skill | Loads for | Does |
|---|---|---|
| `add-endpoint` | `api/**`, `backend/**` | Contract first, Spectral, backend implementation, public routes, slice test with `matchesContract()`, `./gradlew check`, frontend client, `Bump-` values; says which steps belong to an API, a backend and a frontend issue |
| `e2e-journey` | `e2e/**` | Starts the stack with the commands of `e2e/README.md`, Page Object, `@SeleniumTest` test without `Thread.sleep`, accessibility check, runs the suite and reads the results, adds the *Journeys* row |

### Repository settings

Sub-issue auto-merge (`.github/workflows/sub-issue-automerge.yml`) needs these GitHub settings, applied once by a repository admin. Without the ruleset the workflow fails rather than enabling an auto-merge that would merge at once.

1. *Settings → General → Pull Requests*: check **Allow auto-merge** (it only allows auto-merge to be enabled on a pull request). Keep **Allow squash merging** and **Automatically delete head branches** checked.
2. *Settings → Actions → General → Workflow permissions*: leave the default (*Read repository contents*) and leave *Allow GitHub Actions to create and approve pull requests* unchecked; each job asks for its own scopes.
3. *Settings → Rules → Rulesets → New branch ruleset* `issue-branches`:
   - **Enforcement status**: *Active*.
   - **Target branches → Include by pattern**: `feat/*`, `fix/*`, `chore/*`, `refactor/*`, `perf/*`, `security/*`, `ci/*`, `docs/*`, `test/*`.
   - **Rules**: **Require status checks to pass** with `agent-review` (any source), **Block force pushes**. Not `pr-gate` (the pull request suite does not run on pull requests into an issue branch), not *Require a pull request before merging*.
   - **Bypass list**: *Repository admin*, *Always allow*. The patterns also match sub-issue branches (`feat/14-57-backend`), and the status rule also applies to direct pushes, so without it pushes to an issue or sub-issue branch (implementer and fix-round commits, the `/simplify` commit, `CHANGELOG.md`, a merge of the milestone branch) would be refused: only a repository admin can run `/implement-issue`. The auto-merge runs as `github-actions`, outside the list, so it waits for `agent-review`.

A pull request into a milestone branch is not targeted by this ruleset, and the workflow never enables auto-merge on it: it stays merged by a human. `.github/workflows/close-merged-issues.yml` needs no setting: when the person merges an issue pull request into the milestone branch, it closes the issues its `Closes #<n>` lines name.

## Subagents

Subagents in `.claude/agents/`.

| Agent | Tools | Does |
|---|---|---|
| `reviewer` | `Read, Grep, Glob, LSP` (read-only, enforced by Claude Code) | Reviews a change against the acceptance criteria of its issue: criteria implemented (failure paths included), a test that asserts behaviour for each, nothing unrequested, no secret or personal data. Reports an id, file, line, a concrete situation and whether it blocks the merge, then `Verdict: APPROVE\|CHANGES`; from round 2 of `/implement-issue`, reviews only the fixes and the open findings and never repeats a rejected one; no style remarks |
| `implementer` | `Read, Edit, Write, Grep, Glob, Bash, LSP` | Called by `/implement-issue`: implements one sub-issue or a list of findings on the current branch, runs the checks of the changed modules, commits locally; never pushes, calls `gh`, edits `CHANGELOG.md`, version fields or generated code. Reports commits, checks, assumptions and questions (critical or minor) |
| `test-triager` | `Read, Grep, Glob` (read-only), model `sonnet` | Classifies each failure of a test run as product bug, test bug, flaky or environment, from the downloaded JUnit XML and logs (`compose-logs.txt`, screenshots), with the evidence; says "could not determine" instead of guessing; answer under about 40 lines, no code changes |
| `claude-env-auditor` | `Read, Grep, Glob, Write, Bash, WebFetch, WebSearch, Agent`, `background: true`, model `opus`, guarded by `audit-write-guard.sh` | Audits the whole Claude environment (project and, read-only, user level) against `.claude/audit/criteria.md` and the current official docs; writes one English report per run in `.audits/claude-env/`; fixes nothing. Started with `/audit-env [sub-scope]` only |
| `claude-env-analyst` | `Read, Grep, Glob` (read-only), model `opus` (same as the auditor, so the report quality does not depend on the delegation mode) | Worker of the auditor: returns component cards with evidence for a list of paths; not for direct use |

It does not see the conversation and cannot run Git: the caller passes the issue text (`gh issue view <n>`) and the diff (`git diff origin/<milestone-branch>...HEAD`). `/open-pr` calls it after checking the acceptance criteria. To run it by hand, ask Claude to use the `reviewer` agent and give it those two inputs.

`test-triager` cannot run commands either: the caller downloads the artifacts with `gh run download <run-id> --dir <temporary-folder>` and passes the folder path. To run it, ask Claude to use the `test-triager` agent on that folder.

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
