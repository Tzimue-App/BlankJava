# e2e — Selenium suite

User journeys driven by a real Chrome browser, against the full stack over HTTPS
(`docs/technical_analysis.md` §3.6). Every test method gets its own `WebDriver`; `@SeleniumTest`
wires it in and captures a screenshot after the test (plus the browser console and page source on
failure). Results and the Allure report are what a reviewer opens before merging a pull request
into `dev`, `test` or `main`.

## Run it

```bash
cp .env.example .env
docker compose --profile e2e up --build --wait
docker compose --profile e2e run --build --rm e2e
```

Results land in `e2e/build`: `reports/tests/test/index.html` (JUnit HTML), `allure-results/`
(raw Allure data), `screenshots/` (one PNG per test, labelled). Build the single-file Allure
report from `allure-results/`:

```bash
npx --yes allure-commandline@2 generate --single-file --clean e2e/build/allure-results -o e2e/build/allure-report
```

Stop the stack with `docker compose --profile e2e down`.

Backend, frontend and release-script tests have their own reports (Allure, Vitest HTML and
pytest HTML respectively) published the same way on every pull request — see
`docs/github_setup.md` §11.

### Why two `--profile e2e` flags, and why `--build` on `run`

- `selenium` only exists under the `e2e` profile; `e2e` (the runner) only exists under
  `e2e-runner`. `docker compose run <service>` does **not** need the named service's own profile
  active — it is requested by name — but it does need an active profile for every service that
  service's `depends_on` names, so `selenium`'s profile (`e2e`) must still be passed to `run`.
- `docker compose run` only **builds** an image when none exists yet; a `blankjava-e2e:local`
  left over from an earlier run is reused as-is even if the source changed since. `--build` avoids
  running stale code — found the hard way while developing this module (a fixed test kept
  "passing" against yesterday's jar).

### Running against a stack without Docker Compose

Point the suite at any reachable Selenium Grid and frontend, e.g. a stack whose ports are
published on the host:

```bash
SELENIUM_URL=http://localhost:4444 E2E_BASE_URL=https://localhost ./gradlew test
```

## Writing a journey

- One test class per journey, `@Tag("<area>")` (`smoke`, later `accounts`…), `@SeleniumTest`.
- `WebDriver` as a test method parameter (`@SeleniumTest` injects it); build or reuse a Page
  Object from `pages/` extending `BasePage`, which locates elements by `[data-testid="…"]` with
  an explicit wait — no `Thread.sleep`.
- Wrap meaningful actions in `Steps.step("…", () -> …)`, so the Allure report reads like the
  journey; call `Steps.screenshot("…")` at any point worth a picture besides the automatic one at
  the end of the test.
- Each test creates its own data (`TestUsers.uniqueEmail()`), so tests are independent and safe to
  run in parallel.

## Files

| Path | Role |
|---|---|
| `support/Config.java` | `SELENIUM_URL`, `E2E_BASE_URL`, this module's version |
| `support/DriverFactory.java` | One `WebDriver` per test, injected as a parameter |
| `support/ReportingExtension.java` | Screenshot (+ console log, page source on failure) after every test |
| `support/EnvironmentReportExtension.java` | Writes the Allure report's "Environment" header once, from `/actuator/info` |
| `support/Steps.java`, `support/Screenshots.java` | Named steps, on-demand screenshots |
| `support/TestUsers.java` | Unique test accounts for future journeys |
| `pages/BasePage.java`, `pages/HomePage.java` | Page Objects |

## Pinned versions

Selenium client (`build.gradle.kts`, `seleniumVersion`) is pinned to match the Grid image tag in
`compose.yaml`, so the client and server speak the same WebDriver protocol version. Bump both
together.
