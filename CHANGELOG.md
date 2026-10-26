# Changelog

## [Unreleased]

Project foundation: technical analysis, delivery process, GitHub workflows and issue templates.

<!--
One line per component: 0 = unchanged, 1 = major, 2 = minor, 3 = patch.
A component whose directory changed must be bumped. Over a milestone, keep the most
significant bump (a PR may raise a value, never lower it).
The project version comes from the milestone branch (vX.Y.Z-a).
-->
Bump-api: 0
Bump-backend: 0
Bump-frontend: 0
Bump-e2e: 0

### Features

### Patches

### Bug Fixes
- Release script: linear-time parsing of the OpenAPI `info.version` (code scanning alert #1, #9)

### Deployment & Configuration
- Push suite, pull request suite with diff coverage, Selenium suite with Allure report for reviewers
- Milestone branch flow (vX.Y.Z-a), releases vX.Y.Z-a (dev) → vX.Y.Z-b (test) → vX.Y.Z (main) with back-merges
- Issue templates, pull request template, issue guidelines, GitHub setup checklist, roadmap
- Release script tests run by a `tooling` job in the push and pull request suites, required through `pr-gate` (#9)
- 10-minute timeout on the release and start-milestone workflows (#9)

### ChangeLog
- Technical analysis v3.2

---
