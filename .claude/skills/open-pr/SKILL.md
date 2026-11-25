---
name: open-pr
description: Finish an issue branch and open its pull request into the milestone branch (checks, CHANGELOG, bump lines, template). Run by a person only.
argument-hint: (none, uses the current branch)
disable-model-invocation: true
---

# Open the pull request of the current issue branch

Rules: `.github/ISSUE_GUIDELINES.md` (*Changelog and versions*, *Definition of Done*) and `.github/pull_request_template.md`. Stop at the first failure and report it; never lower a check to make it pass.

1. Read the branch: `git branch --show-current` must match `<type>/<issue>-<slug>`. Otherwise stop. Read the issue (`gh issue view <issue>`) and find the milestone branch as `/start-issue` does (`vX.Y.Z-a`). Stop if there is none.
2. `git status --short`: nothing tracked may be left uncommitted except what this PR adds. Commit the work in Conventional Commits style when the person asked for it, otherwise ask.
3. List the changed modules: `git diff --name-only origin/<milestone-branch>...HEAD`, grouped by `api/`, `backend/`, `frontend/`, `e2e/`, `.github/`, tooling.
4. Run the checks of the changed modules, as listed in `CLAUDE.md` (Commands):
   - `api/`: the Spectral command;
   - `backend/`: `./gradlew check` (it includes `integrationTest`, which needs Docker: if Docker is not running, say so and stop rather than skip it);
   - `frontend/`: `npm run lint`, `npm run test:ci` and `npm run build`;
   - `e2e/`: `./gradlew check`; the Selenium suite itself runs in CI (add the `run-e2e` label only as the project rules allow, see `.github/ISSUE_GUIDELINES.md` *Labels*);
   - `tests/`, `bump_version.py` or `.claude/settings.json`: `python -m pytest tests/` (the `tooling` filter of `.github/module-filters.yml` runs it in CI for the same paths).
5. Check each acceptance criterion of the issue against the diff and the test results. List any that is not met and **stop**. Then call the `reviewer` subagent (`.claude/agents/reviewer.md`, read-only, it does not see this conversation and cannot run Git): pass it the issue text (`gh issue view <issue>`) and the diff (`git diff origin/<milestone-branch>...HEAD`). That diff omits untracked files: list them with `git status --short` and give the reviewer their paths (or commit them first, step 2). Show its findings; **stop** on any that blocks the merge, and show the others in the pull request description under *How to review*.
6. Update `CHANGELOG.md` `[Unreleased]`:
   - add one line per change under the fitting heading (usually `### ChangeLog` for tooling), ending with `(#<issue>)`, in the style of the existing lines; replace the `_Description: ..._` placeholder only if it is still there;
   - set each `Bump-<component>: <0-3>` for every component whose directory changed (`api`, `backend`, `frontend`, `e2e`). Never lower an existing value: 1 is major, 2 minor, 3 patch, so the most significant is the smallest non-zero number. Tooling-only changes leave all four at 0 unless a component directory changed.
7. Commit the `CHANGELOG.md` change (Conventional Commits, ending with `(#<issue>)`): the check and CI read the committed head, not the working tree. This is a local commit, nothing is pushed yet. Then run `python bump_version.py --check origin/<milestone-branch> --head-ref <branch>`. Fix and rerun until it passes; this is what `flow-and-versions` runs.
8. Fill the pull request description from `.github/pull_request_template.md`: title = the issue title; `Closes #<issue>`; *What and why*; *How to review*; the *Evidence* boxes that are true; the journeys table (one row per touched user-visible flow, or `none` when nothing user-visible changes). Do not copy the template comments. End the description with the attribution line given in the session system reminder, if any.
9. Show the person the title, the description and the diff summary. **Ask before pushing and before creating the pull request**; do nothing until they confirm (the `git push` and `gh pr create` permission prompts are a second guard, not a replacement).
10. After confirmation: `git push -u origin <branch>`, then `gh pr create --base <milestone-branch> --head <branch> --title "<title>" --body-file <file>`. Show the URL and, once available, `gh pr checks`.
11. Stop. Never merge, approve or enable auto-merge: a human merges.
