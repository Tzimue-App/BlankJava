# core/

Singletons provided once for the whole application: runtime configuration, version information, platform tokens (`WINDOW`, `STORAGE`), and later the auth session, HTTP interceptors, logging and the generated API client (`api/generated/`, git-ignored, written by `npm run generate:api`).

- May import: `core/`, `shared/`.
- Never imports `layout/` or `features/` (enforced by `eslint-plugin-boundaries`).
- The only place allowed to touch browser globals (`window`, `localStorage`…), through `platform/tokens.ts`.
