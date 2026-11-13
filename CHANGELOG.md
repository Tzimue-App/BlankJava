# Changelog

## [Unreleased]

_Description: Write the summary here..._

<!--
One line per component: 0 = unchanged, 1 = major, 2 = minor, 3 = patch.
A component whose directory changed must be bumped. Over a milestone, keep the most
significant bump (a PR may raise a value, never lower it).
The project version comes from the milestone branch (vX.Y.Z-a, project X.Y.Z-alpha).
-->
Bump-api: 0
Bump-backend: 0
Bump-frontend: 3
Bump-e2e: 0

### Features

### Patches
- Frontend dev tooling clears Dependabot alerts on `vite` and `minimatch`: Angular aligned on 22.2.6 (`@angular/build`, `@angular/cli`, `@angular/cdk`, `@angular/material` 22.2.7), whose build pins the patched `vite@8.3.5`, and an npm `overrides` entry forces `minimatch@^11.0.1` under the ESLint toolchain, which still asks for 10.x; drop it once `eslint` requires 11 (#25)
- Frontend image upgrades its Alpine packages at build time (`apk upgrade`) instead of keeping those baked into `nginx-unprivileged:1.30-alpine` at its last publish, and removes the unused `curl`/`libcurl` (clears CVE-2026-86413, CVSS 9.1) (#26)

### Bug Fixes

### Deployment & Configuration
- `.gitignore` ignores `bin/`, where VS Code's Java extension (Eclipse JDT) compiles `backend/` and `e2e/` for its own tooling, apart from the Gradle `build/` (#27)

### ChangeLog

---

## [v0.2.0-alpha] - 2026-11-10

# blankjava - Alpha v0.2.0-alpha

> [!WARNING]
> **Status: Alpha.** Development build for internal integration testing.

**Components:** api `0.2.0` (updated) · backend `0.2.0` (updated) · frontend `0.2.0` (updated) · e2e `0.2.0` (updated)

### Features
- Accounts API contract: `register`, `login`, `refresh`, `logout` (`/auth/*`) and `getCurrentUser` (`GET /users/me`), with the refresh cookie as a `refreshCookie` security scheme, `Set-Cookie` and `Cache-Control: no-store` headers, field-level validation errors (`ValidationProblem`, codes `required`, `invalid_format`, `too_short`, `too_long`, `compromised_password`), `409` `email_taken`, `403` on a missing or foreign `Origin`, and an example for every response (#28)
- Backend security foundation: every route needs a valid access token except an explicit list of public routes (auth, `client-logs`, health and info, Swagger UI in `dev`), a test fails if a route becomes public by accident; RS256 access tokens signed with a key set loaded from PEM files (RSA ≥ 3072 bits, `kid` thumbprints, previous keys accepted during a rotation), checked for `iss`, `aud`, `sub` and expiry; `401`/`403` as Problem Details with `traceId` and `WWW-Authenticate`; public routes ignore an expired `Authorization` header (#37)
- Registration `POST /api/auth/register`: e-mail trimmed and lower-cased, display name 2–50 code points, password 15–64 code points after NFKC and not in an offline list of 5,328 breached passwords (SecLists, MIT), Argon2id hash (`{argon2}`, 19 MiB, 2 iterations); `201` with the session (access token, `refresh_token` cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, 7 days, stored as a SHA-256 hash) and `Cache-Control: no-store`; every `400` is a `ValidationProblem` with a `code` per field; `409` `email_taken`, also when two registrations race; `user.registered` audit event. (#29)
- Sessions: `POST /api/auth/login` (same `401` body for an unknown e-mail, a wrong password or a 3-character one, dummy hash for unknown e-mails, hash re-encoded when its cost is below the configuration), `refresh` (strict rotation in the same token family, row-locked so a cookie works once even under concurrent requests; `401` clears the cookie) and `logout` (`204`, idempotent, cookie cleared); `refresh` and `logout` require an allowed `Origin`, a missing one is `403`; `Cache-Control: no-store` on token responses; audit events `auth.login.succeeded`, `auth.login.failed` (masked e-mail), `auth.logout` (#30)
- Current user `GET /api/users/me`: the access token owner's `id`, `email`, `displayName` and `createdAt`, read from the database; the user comes from the token's `sub` only; a valid token whose account was deleted is a `401` like an invalid token; `user.id` in the logging context of every authenticated request and on its access log line (#31)
- Frontend session handling in `core/auth/`: `AuthSession` keeps the access token in memory only (never Web Storage or cookies) with `currentUser` and `isAuthenticated` signals; the session is restored from the refresh cookie at startup (anonymous without one, no error); `authInterceptor` adds the bearer token to `/api/**` except `/api/auth/**`, refreshes once for concurrent `401`s and retries once, and sends the user to `/login?returnUrl=…` when that fails; refreshes run one at a time across tabs inside the Web Lock `blankjava-auth-refresh`; `authGuard` and `anonymousGuard` (#32)
- Register page `/register` (lazy `accounts` feature, `anonymousGuard`): e-mail, display name, password and confirmation checked with the backend's rules (`shared/validators`, lengths in code points, password counted after NFKC with a live counter), show/hide password, `autocomplete` hints, pasting allowed; server field errors under their field (`compromised_password`, `email_taken`…), other failures as a banner with the trace id; on success the session opens and the user goes to a same-site `returnUrl` or `/me` (#33)
- Login page `/login` (`anonymousGuard`): e-mail and password only required, one generic message on `401` whatever was wrong, password cleared after a refusal, other failures as a banner with the trace id; *Log out* in the header while logged in: revokes the refresh cookie inside the refresh Web Lock, forgets the session even when the backend is unreachable, goes to `/login`; `returnUrl` must also resolve to the application once tabs and new lines are removed (#34)
- Profile page `/me` (`authGuard`): display name, e-mail and registration date in the browser's language, read from `GET /api/users/me` on every visit; loading state, error banner with the trace id and *Try again*; `/` now redirects to `/me` or `/login`, replacing the M0 home placeholder (#35)
- Selenium journeys of the accounts (`@Tag("accounts")`): register, login and logout, profile and root redirect, and the session (reload, two tabs reloading together, logout in one tab ending the other, refused access token renewed and retried); axe-core scan of the register, login and profile pages (WCAG 2.1 A/AA, fails on serious or critical violations; `com.deque.html.axe-core:selenium` 4.14.0); test users registered through the API (#45)

### Patches
- Contract: `RegisterRequest.email` loses `format: email`, which made the generated `@Email` reject an address with surrounding spaces before the backend trims it (#29)
- Backend `400` validation errors carry the contract's `code` per field (`required`, `invalid_format`, `too_short`, `too_long`) instead of a free-text message only (#29)
- Contract response validator in the backend tests: no longer adds `additionalProperties: false` to schemas that do not declare it, which made it reject every `ValidationProblem` (`allOf`) (#29)
- Selenium `SmokeTest`: `/` now leads to the login page (#35), so the smoke journey checks that instead of the removed M0 home heading; `HomePage` replaced by `AppFrame` (#45)
- Selenium suite: its HTTP client (test users, report header) trusts only the stack's certificate, given by `E2E_TLS_CERT` (the `certs` volume, mounted read-only into the `e2e` service), instead of accepting any certificate; hostname verification stays on (code scanning alert #3) (#51)
- Backend `401`/`403` Problem Details from the security filter chain are built as a `ProblemDetail` and serialized by the application's Jackson mapper instead of a string template; same fields and values; `instance` is the request path as received, left out when it is not a valid URI reference (code scanning alert #4) (#52)
- Backend log masking: the e-mail pattern is bounded to the RFC 5321 lengths (local part 64, domain 253), so masking a text is linear in its length instead of quadratic on a text without `@`; addresses within those limits are masked as before, a longer local part from its last 64 characters; the timing guard now uses a 100,000-character text (#59)

### Bug Fixes
- Backend `400` and `500` Problem Details now always include `type` (`about:blank`), which the contract requires; Spring left it out (found by the contract response validator of #37) (#37)
- Release tooling: back-merges into `dev` (test → dev, main → dev) keep `dev`'s project version, so an open milestone branch no longer conflicts with `dev` or loses its `X.Y.Z-a` version when `dev` is merged into it; a conflict on the version alone is resolved automatically; `--start-milestone` fast-forwards `dev` from `origin` first; `v0.2.0-a` back to `0.2.0-a` (#54)

### Deployment & Configuration
- JWT signing key: `infra/secrets/jwt-private.pem` mounted read-only into the backend by `compose.yaml` (git-ignored, excluded from Docker build contexts); CI takes it from the `JWT_PRIVATE_KEY` repository secret, or generates a throw-away key on fork and Dependabot PRs; the backend refuses to start without a valid key and names the file. Key creation, CI secret and rotation procedure in `infra/README.md` (#37)
- `BLANKJAVA_ALLOWED_ORIGINS` (default `https://localhost,https://frontend`): origins accepted on the session cookie endpoints (#37)
- Pull request suite: the `docker-build` job fails if a backend or frontend image contains a private key (#37)
- Database migration `V2__accounts.sql`: tables `app_user` and `refresh_token` with their indexes (#29)
- Password hashing cost `blankjava.security.password.argon2.*` (defaults 19 MiB, 2 iterations, parallelism 1; how to tune it in `backend/README.md`) and refresh-token lifetime `blankjava.security.refresh-token.lifetime` (7 days); new dependency Bouncy Castle `bcprov-jdk18on` 1.86 (#29)
- Nightly refresh-token purge (03:30, `blankjava.security.refresh-token.purge-cron`): deletes tokens expired or revoked for more than 7 days (`purge-after`); ShedLock 7.10.1 with the `shedlock` table of `V3__shedlock.sql` runs it on one instance; `blankjava.scheduling.enabled=false` turns scheduled jobs off (#30)
- Backend tests: responses checked against `api/openapi.yaml` with `ContractValidation.matchesContract()` (swagger-request-validator 2.44.9) (#37)
- Project version and tags spell out their stage: `X.Y.Z-alpha` (`dev`), `X.Y.Z-beta` (`test`), `X.Y.Z` (`main`) in the root `package.json` (never with a `v`), tags and GitHub releases `vX.Y.Z-alpha`, `vX.Y.Z-beta`, `vX.Y.Z`, Docker images tagged e.g. `0.2.0-beta` and built on `v*-beta` tags; component versions unchanged; milestone branches keep the name `vX.Y.Z-a`; the `v0.1.0-a`/`v0.1.0-b` tags and changelog blocks stay as they are and are still read (#54)
- Push and pull request suites: `dorny/test-reporter` writes the backend, backend integration, frontend and release-script reports to the job summary instead of check runs, which sometimes stayed pending forever after a `404` from GitHub; failed tests still fail the job; `checks: write` permission removed (#59)

---

## [v0.1.0-b] - 2026-11-05

# blankjava - Beta v0.1.0-b

> [!IMPORTANT]
> **Status: Beta.** Stabilised test build.

**Components:** api `0.1.0` (updated) · backend `0.1.0` (updated) · frontend `0.1.0` (updated) · e2e `0.1.0` (updated)

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
