---
name: add-endpoint
description: Add or change an API endpoint across the contract, the backend and the frontend client, in the order that avoids a CI round trip (contract, Spectral, backend, public routes, slice test, check, client). Use when a task adds an operation to api/openapi.yaml or implements one in the backend.
paths:
  - "api/**"
  - "backend/**"
---

# Add an endpoint

The order is fixed: the contract changes first, then the backend, then the frontend (`CLAUDE.md`, *Gotchas*). The reasons are in `api/README.md` (*Changing the contract*, *Rules*), `backend/README.md` (*Adding an endpoint*, *Tests*) and `frontend/README.md` (*Scripts*). This skill holds the order and the stop conditions. It never pushes and files nothing.

## Which steps belong to the issue

One issue is one pull request, so do only the steps of the area the issue names (its *Area* checkboxes). Do not start the steps of another area; note them as follow-up for its own issue.

| Issue | Steps |
|---|---|
| API (contract) | 1, 2, 8 (`Bump-api` only) |
| Backend | 3 to 6, 8 (`Bump-backend`). The contract it implements must already be merged into the milestone branch: if it is not, **stop** and say so. |
| Frontend | 7, then the frontend code. `Bump-frontend` |
| One issue naming api and backend | 1 to 6, 8 |

## Steps

1. **Contract.** Edit `api/openapi.yaml` before any Java file: the operation, a unique `operationId`, a declared tag, explicit `security` (`[]` when public, `[{ bearerAuth: [] }]` otherwise), the shared `components/responses` for errors and the `Problem` schema by `$ref`. Never edit `info.version`.
2. **Lint.** Run the Spectral command of `CLAUDE.md`. Fix every warning. **Stop** if it still fails.
3. **Backend.** From `backend/`, build to regenerate the interface, then implement the generated interface in a controller. Never edit `backend/build/generated/`.
4. **Public route only.** Add the route to `SecurityConfig.PUBLIC_ROUTES` **and** to `PUBLIC` in `SecurityIntegrationTest`; the operation's `security` must be `[]`. A protected route needs nothing here.
5. **Slice test.** `@WebMvcTest(MyController.class)` with `@ApiSecurity`, tokens from the `AccessTokenService` bean, and `.andExpect(ContractValidation.matchesContract())` on every call. Cover the failure paths (`400`, `401`, and any error the contract documents), not only the success.
6. **Check.** Run `./gradlew spotlessApply`, then `./gradlew check` (integration tests need Docker). **Stop** on a failure and report it; do not loosen a test or a threshold to pass.
7. **Client.** Only if the frontend uses the operation: `npm run generate` from `frontend/`. Never edit the generated client, and never call `ng` directly.
8. **Changelog.** Add the notes to `CHANGELOG.md` `[Unreleased]` and set the `Bump-api` value from the table in `api/README.md` (new operation: `2`) and `Bump-backend` (new feature: `2`). Do not touch a version field.

If a user-visible flow comes with the endpoint, the Selenium journey is a separate task: see `e2e-journey`.
