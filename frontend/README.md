# Frontend

Angular 22 multi-project workspace (`docs/technical_analysis.md` §3.4). The main application is `projects/web`; further applications or libraries are added with `ng generate application|library`.

## Structure of `projects/web/src/app`

| Folder             | Content                                                                                                         | May import                          |
| ------------------ | --------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| `core/`            | Singletons: runtime config, versions, platform tokens, (later) auth session, interceptors, generated API client | `core/`, `shared/`                  |
| `shared/`          | Presentational components, pipes, validators                                                                    | `shared/`                           |
| `layout/`          | Shell (header, footer with versions), not-found page                                                            | `layout/`, `core/`, `shared/`       |
| `features/<name>/` | One lazy-loaded feature each                                                                                    | its own feature, `core/`, `shared/` |

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

## Image

Built from the repository root (it needs `api/openapi.yaml`):

```bash
docker build -f frontend/Dockerfile --build-arg APP_RELEASE=0.1.0-local -t blankjava-frontend .
```

Nginx (non-root, port 8080) serves the build with an `index.html` fallback for client-side routes. TLS, the API reverse proxy and security headers come with the Docker Compose stack (#5).
