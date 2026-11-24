# Implement-issue trial

`/implement-issue <n>` runs the sub-issues of one issue in sequence, in its own worktree started with `claude -w issue-<n> "/implement-issue <n>"`. For each sub-issue the orchestrator creates the branch, lets the `implementer` subagent write and commit the code, opens a draft pull request into the issue branch and runs review rounds until it is ready to merge. It then prepares the issue pull request with `/simplify`, the checks, `CHANGELOG.md` and the `Bump-` lines, and reviews it with `/code-review`, `/security-review` and `reviewer`, while only the orchestrator writes to GitHub and a human merges.

## Resume a run

1. After an interruption (usage limit, closed session), run the same `/implement-issue <n>` again.
2. The orchestrator works out the state from merged and open pull requests, round comments and branches, and `implementer` resumes from its last commit, made after each green step.
3. If a critical question stopped the issue (`need-manual` on the pull request), answer it in a pull request comment, then rerun.
