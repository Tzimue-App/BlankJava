# BlankJava

Commands and rules that cannot be read from the code. Each module's `README.md` has the details.

## Commands

Run from the module directory unless stated. On Windows, use `gradlew.bat`, or `./gradlew` from Git Bash.

Backend (`backend/`):
- `./gradlew test`: unit and slice tests
- `./gradlew test --tests com.blankjava.web.FieldErrorsTest`: one test class
- `./gradlew integrationTest`: integration tests (Testcontainers, needs Docker)
- `./gradlew check`: unit and integration tests, coverage thresholds, Spotless check (Error Prone runs on every compile)
- `./gradlew spotlessApply`: format the Java code

Frontend (`frontend/`):
- `npm ci`: install
- `npm run lint`, `npm run format` (Prettier writes LF: on a Windows CRLF checkout files with no real change show as modified; `git checkout -- <file>` reverts them)
- `npm run test:ci`: unit tests once, with coverage thresholds
- `npm test -- --watch=false --include=projects/web/src/app/core/auth/auth-session.spec.ts`: one spec file (path from `frontend/`)
- `npm run build`

API, from the repository root:
- `npx --yes @stoplight/spectral-cli@6.17.0 lint api/openapi.yaml --ruleset api/.spectral.yaml --fail-severity=warn`

Selenium suite, from the repository root (needs Docker, `.env` and `infra/secrets/jwt-private.pem`, created as in `infra/README.md` §1):
- `docker compose --profile e2e up --build --wait`: start the stack
- `docker compose --profile e2e run --build --rm e2e`: run the suite
- `docker compose --profile e2e down`: stop the stack

Tooling, from the repository root:
- `python -m pytest tests/`
- `python bump_version.py --check origin/<milestone-branch> --head-ref <branch>`: branch flow, `CHANGELOG.md` and `Bump-*` lines, as checked by CI

## Gotchas

- Generated code is never edited: `backend/build/generated/`, `frontend/projects/web/src/app/core/api/generated/`, `*.generated.ts`. Change `api/openapi.yaml` and regenerate (the backend build and the frontend `npm` scripts regenerate it; a direct `ng` or `npx ng` call does not, so go through `npm`).
- Version fields are written by `bump_version.py` only: `version` in `backend/gradle.properties` and `e2e/gradle.properties`, `version` in `frontend/package.json` and the root `package.json`, `info.version` in `api/openapi.yaml`.
- The contract changes first (`api/openapi.yaml`), then the backend and the frontend.
- Flyway migrations (`backend/src/main/resources/db/migration/`) are forward-only: add a new `V<n>__<name>.sql`, never edit an existing one.
- Integration tests and the Selenium suite need Docker running.

## Workflow

- One issue = one branch `<type>/<issue>-<slug>` (e.g. `feat/14-register-page`), created from the milestone branch `vX.Y.Z-a`.
- One pull request into the milestone branch, with `Closes #<issue>`.
- Each PR adds its notes to `CHANGELOG.md` `[Unreleased]` and sets `Bump-<component>:` for every component it changes.
- Large issues are split into sub-issues: branch `<type>/<issue>-<sub-issue>-<slug>` from the issue branch, PR into the issue branch, no `CHANGELOG.md` change; `/implement-issue <n>` runs them.
- A human merges; never merge a pull request yourself.
- Everything else: `.github/ISSUE_GUIDELINES.md`.
