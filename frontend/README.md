# Frontend

Angular 22 multi-project workspace (`docs/technical_analysis.md` §3.4). The main application is `projects/web`; further applications or libraries are added with `ng generate application|library`.

## Structure of `projects/web/src/app`

| Folder             | Content                                                                                                 | May import                          |
| ------------------ | ------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| `core/`            | Singletons: runtime config, versions, platform tokens, auth session, interceptors, generated API client | `core/`, `shared/`                  |
| `shared/`          | Presentational components, pipes, validators                                                            | `shared/`                           |
| `layout/`          | Shell (header, footer with versions), not-found page                                                    | `layout/`, `core/`, `shared/`       |
| `features/<name>/` | One lazy-loaded feature each                                                                            | its own feature, `core/`, `shared/` |

The import rules are enforced by `npm run lint` (`eslint-plugin-boundaries`). Browser globals (`window`, `localStorage`) are only reached through `core/platform/tokens.ts`, which keeps the code compatible with server-side rendering.

## Scripts

| Script                            | Does                                                                                                                                                                                                                                           |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `npm start`                       | `ng serve` on http://localhost:4200, proxying `/api` and `/actuator` to the backend on `:8080` (`proxy.conf.json`)                                                                                                                             |
| `npm run build`                   | Production build into `dist/web/browser`                                                                                                                                                                                                       |
| `npm test`                        | Unit tests (Vitest) in watch mode                                                                                                                                                                                                              |
| `npm run test:ci`                 | Unit tests once, with coverage. Fails under 80 % statements or 70 % branches over **all** source files; lcov in `coverage/web/lcov.info`                                                                                                       |
| `npm run lint`                    | angular-eslint and folder boundaries                                                                                                                                                                                                           |
| `npm run format` / `format:check` | Prettier                                                                                                                                                                                                                                       |
| `npm run generate`                | Writes `core/version/frontend-version.generated.ts` from `package.json`, and the API client from `../api/openapi.yaml` into `core/api/generated/` (both git-ignored). Runs automatically before `start`, `build`, `test`, `test:ci` and `lint` |

`version` in `package.json` is managed by the release script (`bump_version.py`); never edit it by hand.

## Runtime configuration

`/config.json` is read before the first render (`provideAppInitializer`). The image writes it from the `APP_RELEASE` build argument, so the same build runs in every environment. Locally it is `projects/web/public/config.json`.

## Session

Code: `core/auth/` (`docs/technical_analysis.md` §5.2). A session is an access token (10 minutes) plus the backend's `refresh_token` cookie (7 days, `HttpOnly`, so the app never sees it).

- **Where the token lives:** in a private field of `AuthSession`, in memory only. Nothing writes it to `localStorage`, `sessionStorage` or a cookie (a unit test checks it), so an injected script cannot read it back. A reload loses it, and the startup restore gets a new one.
- **State:** `AuthSession.currentUser()` and `isAuthenticated()` (signals); `start(sessionResponse)` after register or login, `end()` to forget the session in this tab.
- **Startup restore:** an app initializer calls `refresh` once. A valid cookie restores the user; no cookie or a refused one means anonymous, not an error.
- **Interceptor** (`authInterceptor`): adds `Authorization: Bearer` to `/api/**` except `/api/auth/**`. On a `401` it refreshes once (concurrent requests wait for the same refresh) and retries once. If the refresh or the retry fails, the session is ended and the user goes to `/login?returnUrl=<current URL>`.
- **Guards:** `authGuard` sends an anonymous visitor to `/login?returnUrl=…`; `anonymousGuard` sends a logged-in user to `/me`.

**Rule: every refresh goes through `AuthSession.refresh()`.** The backend accepts a refresh cookie once (strict rotation, backend `README.md`), and the tabs of a browser share that cookie. `refresh()` runs inside the Web Lock `blankjava-auth-refresh` (`navigator.locks`), so two tabs reloading together refresh one after the other: the second one sends the cookie the first one left, and both end up with their own access token. Calling the refresh endpoint any other way would send a cookie twice and log a tab out. Browsers without Web Locks only get the single refresh within a tab: two tabs refreshing at the same instant there can still log one out.

## Image

Built from the repository root (it needs `api/openapi.yaml`):

```bash
docker build -f frontend/Dockerfile --build-arg APP_RELEASE=0.1.0-local -t blankjava-frontend .
```

Nginx (non-root, port 8080) serves the build with an `index.html` fallback for client-side routes. TLS, the API reverse proxy and security headers come with the Docker Compose stack (#5).
