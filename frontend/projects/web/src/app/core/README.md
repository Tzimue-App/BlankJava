# core/

Singletons provided once for the whole application: runtime configuration, version information, platform tokens (`WINDOW`, `STORAGE`), tracing (`tracing/`, `http/tracing.interceptor.ts`: W3C `traceparent` on every API call), logging (`logging/`: `LoggerService`, global `ErrorHandler`, batched shipping of warnings and errors to `POST /api/client-logs`), the generated API client (`api/generated/`, git-ignored, written by `npm run generate:api`), and later the auth session.

- May import: `core/`, `shared/`.
- Never imports `layout/` or `features/` (enforced by `eslint-plugin-boundaries`).
- The only place allowed to touch browser globals (`window`, `localStorage`…), through `platform/tokens.ts`.
