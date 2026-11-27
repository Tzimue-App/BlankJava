---
name: start-issue
description: Start the work on a GitHub issue of this project, from the check that it is ready to the issue branch. Run by a person only.
argument-hint: <issue-number>
disable-model-invocation: true
---

# Start issue $ARGUMENTS

Rules of the workflow: `.github/ISSUE_GUIDELINES.md` (*Content* for readiness, *Branches* for names). Stop at the first failed step and report; never work around a stop.

1. If `$ARGUMENTS` is not a single issue number, stop and ask for one.
2. Read the issue: `gh issue view $ARGUMENTS --json number,title,body,labels,milestone,state`. Stop if it is closed.
3. Check that it is **ready**:
   - the *Proposed solution* (or *Root cause* for a bug) and the *Acceptance criteria* are filled in, not left as the template placeholder;
   - it has a milestone;
   - every issue named under *Related → Depends on* is merged into the milestone branch: find its pull request with `gh pr list --state merged --search "<n> in:body" --limit 100 --json number,title,body,baseRefName` and keep the one whose body has `Closes #<n>` (a search also matches mere mentions); check that its `baseRefName` is the milestone branch. An open issue is not a sign it is unmerged: issues close only when the milestone reaches `dev`.
   - a dependency written as a name without `#<n>` is resolved with `gh issue list --state all --search "<name>"`; if it cannot be matched to exactly one issue, **stop** and ask which one.
   - if the issue has sub-issues (`gh api repos/{owner}/{repo}/issues/$ARGUMENTS/sub_issues --jq '.[].number'`), each open one also has its acceptance criteria filled in, and its blockers (`gh api repos/{owner}/{repo}/issues/<n>/dependencies/blocked_by --jq '.[].number'`) are sub-issues of the same issue or already merged into the milestone branch;
   - If anything is missing, **stop**: name the missing part or the unmerged dependency. Create no branch.
4. Find the milestone branch: the milestone title ends with its version (`(v0.2.1)`), so the branch is `v0.2.1-a`. Run `git fetch origin` and check that `origin/<milestone-branch>` exists; stop if not.
5. Check `git status --short`: tracked changes that are not yours stop the skill (ask what to do). Untracked files such as `bin/` are ignored.
6. Choose `<type>` from the issue title prefix (`feat`, `fix`, `chore`, `refactor`, `perf`, `security`, `ci`, `docs`, `test`) and `<slug>` as 2 to 5 lowercase words of the outcome joined by `-`. Create the branch from the remote tip, without tracking: `git checkout -b <type>/$ARGUMENTS-<slug> origin/<milestone-branch> --no-track`. Stop if the branch already exists.
7. Restate the acceptance criteria as the **definition of done**, as a checklist, followed by the standing items of the *Definition of Done* section of the guidelines (tests, Selenium scenario for a new user-visible flow, `CHANGELOG.md` with `Bump-` lines, docs). Say which modules the issue touches. If it has sub-issues, list them in dependency order.
8. Ask whether the person works on it here, or wants an **autonomous run** by `/implement-issue` in its own session. For an autonomous run:
   - the issue branch must not stay checked out here (a branch is checked out in one worktree at a time): `git switch -` back to the previous branch;
   - **ask before pushing**, then `git push -u origin <type>/$ARGUMENTS-<slug>` (sub-issue pull requests target the remote issue branch);
   - print the command to run in a new terminal at the repository root, which opens a session in its own worktree (`.worktreeinclude` copies `.env`, `infra/secrets/` and `docs/` into it): `claude -w issue-$ARGUMENTS "/implement-issue $ARGUMENTS"`. Several issues can run at once, one terminal each; keep it to what the usage limits of the plan allow.
9. Stop. Do not start implementing unless the person asks. Merging is never part of this workflow: a human merges.
