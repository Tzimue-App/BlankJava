# Issue guidelines

Every issue is created from one of the templates in `.github/ISSUE_TEMPLATE/`. Blank issues are disabled. Drafts are prepared in `.github/issues/` and filed with `gh issue create --body-file` (see `docs/github_setup.md`).

| Template | Use it when | Title prefix |
|---|---|---|
| Bug report | Existing behaviour is wrong | `fix(<area>):` |
| Feature request | A new user-visible capability, or a part of one (API contract, backend, page) | `feat(<area>):` |
| Improvement | Nothing is broken and nothing new is visible: setup, refactoring, performance, security hardening, upgrades, CI, docs, tests | `chore` / `refactor` / `perf` / `security` / `ci` / `docs` / `test` |

Security vulnerabilities are **never** filed as issues: use *Security → Report a vulnerability*.

## Milestones and issues

- A **milestone** is a planned release with a target version (`docs/roadmap.md`): a GitHub milestone, a milestone branch `vX.Y.Z-a`, and a set of issues.
- Every issue belongs to exactly one milestone (GitHub milestone field and the template's dropdown).
- **One issue = one issue branch = one pull request.** An issue is sized so that its PR can be reviewed in one sitting (roughly under 400 changed lines, excluding generated code). A capability too large for that is split into several issues (e.g. API contract, backend, frontend page), linked by `Depends on #<issue>`.
- Dependencies between issues are listed in the issue (*Related*) and in `docs/roadmap.md`.

## Titles

[Conventional Commits](https://www.conventionalcommits.org/) style, describing the outcome rather than the task. The PR title reuses it, and becomes the squash commit message.

- Good: `fix(backend): expired JWT returns 500 instead of 401`
- Bad: `JWT bug`

`<area>` is one of `api`, `backend`, `frontend`, `e2e`, `db`, `logging`, `infra`, `ci`, `docs`.

## Content

- Never paste secrets, tokens, passwords, real e-mail addresses or other personal data. Use test accounts.
- Link code as `path/File.java:line` and the spec as `docs/technical_analysis.md#section`.
- Acceptance criteria are observable and pass/fail (**Given / when / then**), including the failure paths.
- An issue is **Ready** when its root cause (bugs) or proposed solution (features, improvements) and its acceptance criteria are filled in, and its dependencies are merged or in progress.

## Labels

| Label | Meaning |
|---|---|
| `type: bug` / `type: feature` / `type: improvement` | Set by the template |
| `status: needs-triage` | Set by the template; removed at triage |
| `status: ready` | Can be picked up |
| `status: blocked` | Waiting on another issue or decision |
| `priority: high` / `priority: medium` / `priority: low` | Set at triage |
| `run-e2e` | On a PR into a milestone branch: also run the Selenium suite (it always runs on PRs into `dev`, `test`, `main`) |

## Branches

| Branch | Created from | Role | PR into |
|---|---|---|---|
| `vX.Y.Z-a` | `dev`, with `python bump_version.py --start-milestone X.Y.Z` or the *Start milestone* workflow | Milestone integration | `dev` |
| `<type>/<issue>-<slug>`, e.g. `feat/14-register-page` | the milestone branch | One issue | the milestone branch |
| `dev` | — | Alpha releases `vX.Y.Z-a` | `test` |
| `test` | — | Beta releases `vX.Y.Z-b`, Docker images | `main` |
| `main` | — | Stable releases `vX.Y.Z` | — |

- Issue branches cannot be named `vX.Y.Z-a/…` (Git cannot hold a branch and sub-branches of the same name).
- The milestone branch `vX.Y.Z-a` is deleted when merged into `dev`, where the release creates the tag of the same name. Delete your local copy too (`git branch -d vX.Y.Z-a`).
- A version is released once per stage: a defect found in `vX.Y.Z-b` is fixed in the next patch milestone.
- Issue PRs are **squash-merged**; milestone and promotion PRs use **merge commits**.
- **Every PR is reviewed and merged by a human.** PRs into `dev`, `test` and `main` must have a green Selenium suite; the reviewer opens its report (linked in the PR comment) before approving.

## Changelog and versions

Each issue PR adds its notes to the `[Unreleased]` section of `CHANGELOG.md` and sets the bump of every component it changes:

```text
Bump-api: 2        # api/openapi.yaml        — 0 = unchanged, 1 = major, 2 = minor, 3 = patch
Bump-backend: 2    # backend/gradle.properties
Bump-frontend: 0   # frontend/package.json
Bump-e2e: 0        # e2e/gradle.properties
```

Over a milestone, each line keeps the most significant bump: a PR may raise a value (`3` → `2`), never lower it. The project version comes from the milestone branch. CI (`flow-and-versions`) checks all of this. See `docs/technical_analysis.md` §8.

## Definition of Done

- All acceptance criteria are checked.
- Tests cover the change (coverage thresholds and 80 % diff coverage met); for a bug, a regression test fails without the fix.
- Every new user-visible flow has a Selenium scenario.
- `CHANGELOG.md` updated with the right `Bump-<component>:` lines; docs updated.
- PR into the milestone branch reviewed and merged by a human, with `Closes #<issue>` in its description. The issue closes automatically when the milestone is merged into `dev` (the default branch).
