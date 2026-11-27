---
paths:
  - "e2e/**"
---

# Selenium suite

- One test class per journey, annotated `@SeleniumTest` and `@Tag("<area>")` (`smoke`, `accounts`…); list it in *Journeys* of `e2e/README.md`.
- Page Objects live in `pages/` and extend `BasePage`, which locates by `[data-testid="…"]` with an explicit wait; no `Thread.sleep`.
- Wrap meaningful actions in `Steps.step("…", () -> …)`.
- Each test creates its own user: `TestUsers.fresh(name)` through the form, `TestUsers.registered()` through the API.
- Check every page visited with `Accessibility.assertNoSeriousViolations(driver)`.
- Details: `e2e/README.md`, *Writing a journey*.
