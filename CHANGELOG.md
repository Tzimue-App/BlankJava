# Changelog

## [Unreleased]

_Description: Write the summary here..._

<!--
One line per component: 0 = unchanged, 1 = major, 2 = minor, 3 = patch.
A component whose directory changed must be bumped. Over a milestone, keep the most
significant bump (a PR may raise a value, never lower it).
The project version comes from the milestone branch (vX.Y.Z-a).
-->
Bump-api: 2
Bump-backend: 2
Bump-frontend: 0
Bump-e2e: 0

### Features
- Accounts API contract: `register`, `login`, `refresh`, `logout` (`/auth/*`) and `getCurrentUser` (`GET /users/me`), with the refresh cookie as a `refreshCookie` security scheme, `Set-Cookie` and `Cache-Control: no-store` headers, field-level validation errors (`ValidationProblem`, codes `required`, `invalid_format`, `too_short`, `too_long`, `compromised_password`), `409` `email_taken`, `403` on a missing or foreign `Origin`, and an example for every response (#28)
- Backend security foundation: every route needs a valid access token except an explicit list of public routes (auth, `client-logs`, health and info, Swagger UI in `dev`), a test fails if a route becomes public by accident; RS256 access tokens signed with a key set loaded from PEM files (RSA ≥ 3072 bits, `kid` thumbprints, previous keys accepted during a rotation), checked for `iss`, `aud`, `sub` and expiry; `401`/`403` as Problem Details with `traceId` and `WWW-Authenticate`; public routes ignore an expired `Authorization` header (#37)
- Registration `POST /api/auth/register`: e-mail trimmed and lower-cased, display name 2–50 code points, password 15–64 code points after NFKC and not in an offline list of 5,328 breached passwords (SecLists, MIT), Argon2id hash (`{argon2}`, 19 MiB, 2 iterations); `201` with the session (access token, `refresh_token` cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, 7 days, stored as a SHA-256 hash) and `Cache-Control: no-store`; every `400` is a `ValidationProblem` with a `code` per field; `409` `email_taken`, also when two registrations race; `user.registered` audit event. `login`, `refresh` and `logout` answer `501` until #30 (#29)

### Patches
- Contract: `RegisterRequest.email` loses `format: email`, which made the generated `@Email` reject an address with surrounding spaces before the backend trims it (#29)
- Backend `400` validation errors carry the contract's `code` per field (`required`, `invalid_format`, `too_short`, `too_long`) instead of a free-text message only (#29)
- Contract response validator in the backend tests: no longer adds `additionalProperties: false` to schemas that do not declare it, which made it reject every `ValidationProblem` (`allOf`) (#29)

### Bug Fixes
- Backend `400` and `500` Problem Details now always include `type` (`about:blank`), which the contract requires; Spring left it out (found by the contract response validator of #37) (#37)

### Deployment & Configuration
- JWT signing key: `infra/secrets/jwt-private.pem` mounted read-only into the backend by `compose.yaml` (git-ignored, excluded from Docker build contexts); CI takes it from the `JWT_PRIVATE_KEY` repository secret, or generates a throw-away key on fork and Dependabot PRs; the backend refuses to start without a valid key and names the file. Key creation, CI secret and rotation procedure in `infra/README.md` (#37)
- `BLANKJAVA_ALLOWED_ORIGINS` (default `https://localhost,https://frontend`): origins accepted on the session cookie endpoints (#37)
- Pull request suite: the `docker-build` job fails if a backend or frontend image contains a private key (#37)
- Database migration `V2__accounts.sql`: tables `app_user` and `refresh_token` with their indexes (#29)
- Password hashing cost `blankjava.security.password.argon2.*` (defaults 19 MiB, 2 iterations, parallelism 1; how to tune it in `backend/README.md`) and refresh-token lifetime `blankjava.security.refresh-token.lifetime` (7 days); new dependency Bouncy Castle `bcprov-jdk18on` 1.86 (#29)
- Backend tests: responses checked against `api/openapi.yaml` with `ContractValidation.matchesContract()` (swagger-request-validator 2.44.9) (#37)

### ChangeLog

---

## [v0.1.0-a] - 2026-11-03

# blankjava - Alpha v0.1.0-a

> [!WARNING]
> **Status: Alpha.** Development build for internal integration testing.

**Components:** api `0.1.0` (updated) · backend `0.1.0` (updated) · frontend `0.1.0` (updated) · e2e `0.1.0` (updated)

Project foundation: technical analysis, delivery process, GitHub workflows and issue templates.

### Features
- Test reports on every pull request: backend Allure single-file report (unit, slice and integration tests combined), frontend Vitest HTML report, release-script pytest HTML report; a check per suite (`dorny/test-reporter`) annotates failures inline, and a sticky comment links all three reports with pass/fail/skipped totals (#19)
- Selenium suite (`e2e/`): JUnit 6 + Selenium 4.51 against the running stack, Page Objects, a screenshot after every test (plus console log and page source on failure), a single-file Allure report with the project and component versions as its environment header; one smoke test (home page loads over HTTPS, footer shows the backend version) (#7)
- Frontend skeleton: Angular 22 workspace with lazy-loaded features, Material 3 shell, not-found page, footer with release, frontend, backend and API versions, runtime `/config.json` (#4)
- Backend skeleton: Spring Boot 4.1 on Java 25, Flyway baseline, RFC 9457 errors with `traceId`, `/actuator/health` and `/actuator/info` (backend, release and contract versions) (#3)
- `POST /api/client-logs`: browser log events (max 20 entries, 8 KB) written to the backend logs (#6)
- API contract skeleton `api/openapi.yaml` (OpenAPI 3.1): shared `Problem` schema (RFC 9457 with `traceId`), standard error responses, `bearerAuth` scheme (#2)

### Bug Fixes
- Release script: linear-time parsing of the OpenAPI `info.version` (code scanning alert #1, #9)
- Backend build: contract version read correctly from a Windows (CRLF) checkout (#16)
- Nginx re-resolves `backend` on every request instead of caching its address once at startup; recreating the backend container no longer leaves Nginx proxying to a dead IP until it is itself restarted (found while developing #7) (#7)
- Selenium suite: `e2e/build` is created by the runner before Compose first bind-mounts it, so it is runner-owned instead of root-owned (the `e2e` container runs as root); collecting the Compose logs and generating the Allure report no longer fail with *Permission denied* after the tests themselves pass (found while verifying #8) (#8)

### Deployment & Configuration
- Push suite, pull request suite with diff coverage, Selenium suite with Allure report for reviewers
- Push and pull request suites: `dorny/test-reporter` inline check per suite (backend, backend integration, frontend, release script); pull request suite also publishes the full Allure/Vitest/pytest-html reports and a sticky comment with totals (#19)
- Milestone branch flow (vX.Y.Z-a), releases vX.Y.Z-a (dev) → vX.Y.Z-b (test) → vX.Y.Z (main) with back-merges
- Issue templates, pull request template, issue guidelines, GitHub setup checklist, roadmap
- Release script tests run by a `tooling` job in the push and pull request suites, required through `pr-gate` (#9)
- 10-minute timeout on the release and start-milestone workflows (#9)
- Issue status tracked on the Project board only; `status:` labels removed from the issue templates and guidelines (#10)
- Structured logging: ECS JSON logs, one W3C trace id from the browser through Nginx to the backend (also in every error response), JSON access logs, masked e-mails, `SECURITY_AUDIT` logger, browser errors sent to `POST /api/client-logs`; `observability` profile with Grafana Alloy, Loki (14 days) and Grafana (#6)
- Local stack `compose.yaml`: PostgreSQL 18, backend, Nginx with TLS (self-signed certificate generated once), HTTP to HTTPS redirect, `/api` and `/actuator/info` reverse proxy, security headers, real client IP behind the proxy, rotated container logs; guide in `infra/README.md` (#5)
- `compose.yaml` profiles `e2e` (selenium) and `e2e-runner` (e2e): `docker compose --profile e2e up --build --wait && docker compose --profile e2e run --build --rm e2e` runs the suite against the full stack; guide in `e2e/README.md` (#7)
- Frontend quality gates: angular-eslint with enforced folder boundaries, Prettier, Vitest coverage 80 % statements / 70 % branches over all source files; non-root Nginx image built from the repository root; frontend diff coverage now matched against repository paths (#4)
- Backend quality gates: Spotless (palantir-java-format), Error Prone, JaCoCo 80 % lines / 70 % branches, Testcontainers integration tests in `check`; layered non-root Docker image built from the repository root (#3)
- Contract checks in CI with pinned tools: Spectral 6.17.0 with the project ruleset (push and PR suites), openapi-generator 7.26.0 and ng-openapi-gen 1.1.0 must accept the contract (PR suite) (#2)

### ChangeLog
- Technical analysis v3.2

---
