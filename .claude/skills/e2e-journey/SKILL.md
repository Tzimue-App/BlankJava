---
name: e2e-journey
description: Write a Selenium journey for a user-visible flow and run it against the local Docker stack (Page Object, tagged test, named steps, accessibility check, results, Journeys table). Use when a task adds or changes a test in e2e/.
paths:
  - "e2e/**"
---

# Write a Selenium journey

Conventions and reasons: `e2e/README.md` (*Run it*, *Writing a journey*, *Journeys*) and `.claude/rules/e2e.md`. This skill holds the order and the stop conditions. It never pushes and files nothing.

## Steps

1. **Stack.** Needs Docker, `.env` and `infra/secrets/jwt-private.pem` (`infra/README.md` §1). If one is missing, **stop** and say which. Start it from the repository root, with `--profile e2e` and `--build`:
   `docker compose --profile e2e up --build --wait`
2. **Page Object.** Reuse one from `e2e/src/test/java/com/blankjava/e2e/pages/` or add one extending `BasePage`, which locates by `[data-testid="…"]` with an explicit wait. If the page lacks a `data-testid`, add it to the frontend component, not a CSS or XPath locator.
3. **Test.** One class per journey, `@SeleniumTest` and `@Tag("<area>")`, the `WebDriver` as a method parameter. Wrap meaningful actions in `Steps.step("…", () -> …)`. Each test creates its own data: `TestUsers.fresh(name)` (through the form) or `TestUsers.registered()` (through the API). **Never** `Thread.sleep`: wait on an element or a condition through `BasePage`.
4. **Accessibility.** `Accessibility.assertNoSeriousViolations(driver)` on every page the journey visits.
5. **Run.** From the repository root, with both `--profile e2e` and `--build` (a stale `blankjava-e2e:local` image runs old code):
   `docker compose --profile e2e run --build --rm e2e`
   Read the results in `e2e/build/test-results/` and `e2e/build/allure-results/`; for a failure, the screenshot, console log and page source in `e2e/build/`. **Stop** on a failure and report it; do not skip, retry in a loop or weaken an assertion to pass. Fix the test or the product.
6. **Docs.** Add the row to the *Journeys* table of `e2e/README.md` (class, tag, what it covers, the issue number), and list a new Page Object under *Files*.
7. **Changelog.** `CHANGELOG.md` `[Unreleased]` notes and `Bump-e2e` (a new journey: `2`).
8. **Stop the stack** when done: `docker compose --profile e2e down`.
