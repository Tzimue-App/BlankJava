# Changelog

## [Unreleased]

Project foundation: technical analysis, delivery process, GitHub workflows and issue templates.

<!--
One line per component: 0 = unchanged, 1 = major, 2 = minor, 3 = patch.
A component whose directory changed must be bumped. Over a milestone, keep the most
significant bump (a PR may raise a value, never lower it).
The project version comes from the milestone branch (vX.Y.Z-a).
-->
Bump-api: 2
Bump-backend: 2
Bump-frontend: 2
Bump-e2e: 0

### Features
- Frontend skeleton: Angular 22 workspace with lazy-loaded features, Material 3 shell, not-found page, footer with release, frontend, backend and API versions, runtime `/config.json` (#4)
- Backend skeleton: Spring Boot 4.1 on Java 25, Flyway baseline, RFC 9457 errors with `traceId`, `/actuator/health` and `/actuator/info` (backend, release and contract versions) (#3)
- API contract skeleton `api/openapi.yaml` (OpenAPI 3.1): shared `Problem` schema (RFC 9457 with `traceId`), standard error responses, `bearerAuth` scheme (#2)

### Patches

### Bug Fixes
- Release script: linear-time parsing of the OpenAPI `info.version` (code scanning alert #1, #9)
- Backend build: contract version read correctly from a Windows (CRLF) checkout (#16)

### Deployment & Configuration
- Push suite, pull request suite with diff coverage, Selenium suite with Allure report for reviewers
- Milestone branch flow (vX.Y.Z-a), releases vX.Y.Z-a (dev) → vX.Y.Z-b (test) → vX.Y.Z (main) with back-merges
- Issue templates, pull request template, issue guidelines, GitHub setup checklist, roadmap
- Release script tests run by a `tooling` job in the push and pull request suites, required through `pr-gate` (#9)
- 10-minute timeout on the release and start-milestone workflows (#9)
- Issue status tracked on the Project board only; `status:` labels removed from the issue templates and guidelines (#10)
- Frontend quality gates: angular-eslint with enforced folder boundaries, Prettier, Vitest coverage 80 % statements / 70 % branches over all source files; non-root Nginx image built from the repository root; frontend diff coverage now matched against repository paths (#4)
- Backend quality gates: Spotless (palantir-java-format), Error Prone, JaCoCo 80 % lines / 70 % branches, Testcontainers integration tests in `check`; layered non-root Docker image built from the repository root (#3)
- Contract checks in CI with pinned tools: Spectral 6.17.0 with the project ruleset (push and PR suites), openapi-generator 7.26.0 and ng-openapi-gen 1.1.0 must accept the contract (PR suite) (#2)

### ChangeLog
- Technical analysis v3.2

---
