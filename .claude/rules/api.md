---
paths:
  - "api/**"
---

# OpenAPI contract

- The contract changes first, in the PR of the issue that needs it; backend and frontend follow (`api/README.md`, *Changing the contract*).
- OpenAPI 3.1 only: no 3.2 constructs (`QUERY`, `additionalOperations`, `in: querystring`, `itemSchema`, tag `parent`/`kind`, `$self`).
- Every operation declares `security` explicitly: `[]` when public, `[{ bearerAuth: [] }]` for a logged-in user. A public route also goes into the backend's public routes (`backend/README.md`, *Adding an endpoint*).
- Errors reuse the shared `components/responses` (`BadRequest`, `ValidationFailed`, `Unauthorized`…) and the `Problem` schema with `$ref`; never redefine an error body.
- Set `Bump-api` in `CHANGELOG.md` from the table in `api/README.md`; never edit `info.version`.
- Check with the Spectral command of `CLAUDE.md` before committing.
