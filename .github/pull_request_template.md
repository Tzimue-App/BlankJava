<!--
Title: Conventional Commits, same as the issue (e.g. "feat(frontend): register page").
Issue PR → milestone branch vX.Y.Z-a (squash). Milestone → dev, dev → test, test → main (merge commit).
-->

## What and why

Closes #<!-- issue number; for a milestone or promotion PR, list the milestone instead -->

<!-- One or two sentences: what changes for the user or the codebase. -->

## How to review

<!-- Where to start reading, decisions worth challenging, anything generated that can be skipped. -->

## Evidence

- [ ] Pull request suite green (unit, integration, coverage thresholds, diff coverage ≥ 80 %)
- [ ] `CHANGELOG.md` `[Unreleased]` updated; `Bump-<component>:` set for every changed component
- [ ] OpenAPI contract updated first if the API changed
- [ ] Log events added or changed are listed below, and contain no personal data or secrets

### User journeys (Selenium)

<!-- One row per user-visible flow touched by this PR. -->

| Journey | Scenario (test class › method) | New / updated / unchanged |
|---|---|---|
|  |  |  |

### Selenium report (PRs into `dev`, `test`, `main`, or with the `run-e2e` label)

- [ ] I opened the Selenium report linked in the bot comment, checked the failed tests (if any) and the screenshots of the journeys above

## Reviewer checklist

- [ ] The change does what the issue's acceptance criteria say, including failure paths
- [ ] Tests assert behaviour (not just execute lines)
- [ ] No secret, token or personal data in code, logs, screenshots or fixtures
