---
name: issue-status
description: Show where an issue run by /implement-issue stands (sub-issues, pull requests, agent-review status, questions waiting) and what comes next. Read-only.
argument-hint: <issue-number>
allowed-tools: Bash(gh issue view*), Bash(gh pr list*), Bash(gh pr view*), Bash(git fetch*), Bash(git branch*), Bash(git log*)
---

# Status of issue $ARGUMENTS

Read-only: never edit a file, switch a branch, push, comment, label or run another skill. If `$ARGUMENTS` is not a single issue number, ask for one.

1. Read the issue, its sub-issues and their blockers, and find the milestone and issue branches, exactly as steps 1.2 to 1.4 of `.claude/skills/implement-issue/SKILL.md` (no issue branch: say "not started, run `/start-issue $ARGUMENTS`" and stop).
2. Work out the state of each sub-issue with the table of section 2 of that skill. For an open pull request, also read its `agent-review` status (`gh api repos/{owner}/{repo}/commits/<head sha>/status --jq '.statuses[] | select(.context=="agent-review") | .state'`), its round comments (`### Review round`), and whether it is a draft.
3. Show one table:

| Sub-issue | Title | State | Pull request | Rounds | agent-review | Waits for |
|---|---|---|---|---|---|---|

   then: the issue pull request into the milestone branch, if any (draft or ready, rounds); the `need-manual` questions with their links; the minor questions posted on the milestone issue labelled `question` for this issue.
4. End with the next action, in one line: who does what (for example "merge #142", "answer the question on #143", "run `/implement-issue $ARGUMENTS`").
