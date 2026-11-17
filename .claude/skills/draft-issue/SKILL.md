---
name: draft-issue
description: Draft a GitHub issue from a summary, using the right template, and file it only after confirmation. Run by a person only.
argument-hint: <summary of the issue>
disable-model-invocation: true
---

# Draft an issue: $ARGUMENTS

Rules: `.github/ISSUE_GUIDELINES.md` (templates, titles, content, milestones) and `.github/ISSUE_TEMPLATE/`. Stop and ask when a step cannot be settled from the summary and the code.

1. If `$ARGUMENTS` is empty, stop and ask for a summary. If it describes a security vulnerability, **stop**: such reports are never issues (*Security → Report a vulnerability*).
2. Choose the template by the table in the guidelines: bug report (`fix`), feature request (`feat`), improvement (`chore`, `refactor`, `perf`, `security`, `ci`, `docs`, `test`). Read its `.github/ISSUE_TEMPLATE/<template>.yml` and follow its fields and their order.
3. Look at the code and the existing issues before writing (`gh issue list --state all --search "<keywords>"`): avoid a duplicate and cite real files.
4. Write the draft in `.github/issues/<milestone-id>-<nn>-<slug>.md` (`.github/issues/` is git-ignored and may be empty: take the milestone id and the next number from `docs/roadmap.md`, e.g. `M1-03`):
   - the title as a Conventional Commits line, `<type>(<area>): <outcome>`, with `<area>` one of `api`, `backend`, `frontend`, `e2e`, `db`, `logging`, `infra`, `ci`, `docs`; describe the outcome, not the task;
   - one section per template field, with the template headings;
   - code references as `path/File.java:line`, specs as `docs/technical_analysis.md#section`;
   - acceptance criteria as **Given / when / then**, including the failure paths, plus a non-regression line;
   - *Depends on* in *Related* when another issue must be merged first;
   - no secret, token or real personal data. An issue is sized for a pull request under roughly 400 changed lines: propose a split if it is bigger.
5. Show the draft and the proposed milestone and labels. **Ask before filing.** Nothing is filed without a yes.
6. After confirmation: `gh issue create --title "<title>" --label "<type label>" --milestone "<milestone title>" --body-file <draft>` (the `gh issue create` permission prompt is a second guard). Report the issue number and URL.
7. Do not move the issue on the Project board and do not start it: that is `/start-issue`.
