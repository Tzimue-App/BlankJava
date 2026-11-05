# API contract

`openapi.yaml` is the **source of truth** of the BlankJava REST API (contract-first, `docs/technical_analysis.md` §4.1):

- the backend implements Spring interfaces **generated** from it (openapi-generator);
- the frontend calls the API through a client **generated** from it (ng-openapi-gen);
- no DTO is written by hand on either side.

## OpenAPI version: 3.1

The contract declares `openapi: 3.1.1`. OpenAPI 3.2 is the most recent specification, but neither generator can read it yet. Both reject a 3.2 document; this was checked with the pinned versions below. Moving to 3.2 is tracked in #11; the Spectral rule `blankjava-openapi-3-1` blocks it until then.

Do not use 3.2-only constructs: the `QUERY` method, `additionalOperations`, `in: querystring`, `itemSchema`, tag `parent`/`kind`, `$self`.

## Pinned tools

| Tool | Version | Role | Where it runs |
|---|---|---|---|
| [Spectral CLI](https://github.com/stoplightio/spectral) | `@stoplight/spectral-cli@6.17.0` | Lint with `.spectral.yaml` | Push and PR suites (`api` job) |
| [openapi-generator](https://github.com/OpenAPITools/openapi-generator) | `7.26.0` (wrapper `@openapitools/openapi-generator-cli@2.42.0`, version set in `openapitools.json`) | Spring interfaces for the backend | PR suite; build of the backend (#3) |
| [ng-openapi-gen](https://github.com/cyclosproject/ng-openapi-gen) | `1.1.0` | Angular client for the frontend | PR suite; build of the frontend (#4) |

Upgrading one of them is an improvement issue: change the version here, in `openapitools.json` if relevant, and in the `api` job of `.github/workflows/push.yml` and `pull-request.yml`.

## Changing the contract

1. Change `openapi.yaml` **first**, in the PR of the issue that needs it (e.g. M1-01 for the accounts endpoints).
2. Run the checks below locally. CI runs them on every push and PR that touches `api/`.
3. Set `Bump-api` in the `[Unreleased]` section of `CHANGELOG.md`:

| Change | `Bump-api` |
|---|---|
| Breaking: removed or renamed path, operation, field or enum value; new required request field; changed type | `1` (major) |
| Addition: new path, operation, optional request field, response field | `2` (minor) |
| Documentation, examples, descriptions only | `3` (patch) |

The release script writes the resulting version into `info.version`. Never edit it by hand.

## Rules

Enforced by `.spectral.yaml`, on top of the `spectral:oas` recommended rules:

- `openapi` is `3.1.x`;
- every operation has a unique `operationId` (it becomes the generated method name) and a tag declared under `tags`;
- every operation documents a `default` response, and every error response uses `application/problem+json` with the shared `Problem` schema (RFC 9457, with `traceId`);
- schema property names are `camelCase`;
- no schema reachable from a `2xx` response has a property named `password`, `passwordHash`, `hash` or `refreshToken` (`blankjava-no-secret-in-responses`);
- every component is referenced (`oas3-unused-component`): remove a shared building block once nothing uses it;
- paths are lower-case, nouns, plural for collections (`/users/me`, `/auth/login`). Spectral doesn't check this; reviewers do.

Shared building blocks in `components`:

| Component | Use |
|---|---|
| `schemas/Problem` | Body of every error response; optional `code` for errors the client handles specifically (`email_taken`) |
| `schemas/ValidationProblem`, `FieldError` | `400` listing each invalid field with a stable `code` |
| `responses/BadRequest`, `ValidationFailed`, `Unauthorized`, `Forbidden`, `PayloadTooLarge`, `InternalError` | Reuse with `$ref` instead of redefining errors |
| `headers/SetRefreshCookie`, `ClearRefreshCookie`, `CacheControlNoStore`, `WwwAuthenticateBearer` | Response headers of the session operations |
| `securitySchemes/bearerAuth` | `security: [{ bearerAuth: [] }]` on operations that need a logged-in user |
| `securitySchemes/refreshCookie` | `security: [{ refreshCookie: [] }]` on operations that read the refresh cookie (`refresh`, `logout`) |

Every new operation declares `security` explicitly: `[]` when it is public.

## Accounts API

The `auth` and `users` operations (`register`, `login`, `refresh`, `logout`, `getCurrentUser`). Their security model, as the contract describes it:

| Operation | `security` | Session effect |
|---|---|---|
| `register`, `login` | `[]` | Return an access token and set the refresh cookie |
| `refresh` | `refreshCookie` | Rotate the refresh cookie and return a new access token; `401` clears the cookie |
| `logout` | `refreshCookie` | Revoke the refresh token and clear the cookie; `204` even without a cookie |
| `getCurrentUser` | `bearerAuth` | — (the user comes from the token's `sub`; no user id in the request) |

- **Access token:** JWT (RS256), 10 minutes, in the response body (`SessionResponse`). The client keeps it in memory only.
- **Refresh cookie:** `refresh_token`, `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, 7 days. It never appears in a response body.
- **`Origin` check:** `refresh` and `logout` answer `403` when the `Origin` header is missing or not an allowed origin. This, with `SameSite=Strict`, replaces CSRF tokens.
- **Responses carrying a token** have `Cache-Control: no-store`.
- **Login errors are generic:** any credential mismatch gives the same `401` body; `LoginRequest` carries no policy constraint, so a short password never gets a `400` revealing the rules.

**Why the request schemas carry loose lengths.** The backend generates Bean Validation from the contract (`useBeanValidation`), and `@Size` counts UTF-16 units on the raw value. The rules count Unicode code points after trimming (display name) or after NFKC normalisation (password): a valid 64-emoji password is 128 UTF-16 units. So the schemas only set hard upper bounds (`password` ≤ 256, `displayName` ≤ 200, `email` ≤ 254), and the backend checks the real rules, reported through `FieldError.code`:

| Field | Rule (backend) |
|---|---|
| `email` | Valid address, ≤ 254 characters, trimmed and lower-cased, not registered yet (`409` `email_taken`) |
| `displayName` | 2–50 code points after trimming |
| `password` | 15–64 code points after NFKC, not in the compromised-password blocklist |

Rate limiting (`429`) is added in M2.

## Checks

Run from the repository root (requires Node.js, and Java for openapi-generator):

```bash
npx --yes @stoplight/spectral-cli@6.17.0 lint api/openapi.yaml --ruleset api/.spectral.yaml --fail-severity=warn
```

```bash
npx --yes @openapitools/openapi-generator-cli@2.42.0 --openapitools api/openapitools.json validate -i api/openapi.yaml
```

```bash
npx --yes ng-openapi-gen@1.1.0 --input api/openapi.yaml --output build/api-client-check
```

```bash
python bump_version.py --dry-run
```

The last one prints the component versions read by the release script, including `api` (from `info.version`).
