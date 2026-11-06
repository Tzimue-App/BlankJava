# Backend

Spring Boot API implementing the interfaces generated from `api/openapi.yaml` (see `api/README.md`). How to run it: `infra/README.md`.

## Security

Code: `src/main/java/com/blankjava/security/`.

### Access rules

Every route needs a valid access token (`Authorization: Bearer <JWT>`), **except** the public routes of `SecurityConfig.PUBLIC_ROUTES`:

| Route | Why public |
|---|---|
| `POST /api/auth/register`, `/login`, `/refresh`, `/logout` | Open or end a session (`refresh` and `logout` use the cookie, not a token) |
| `POST /api/client-logs` | Browser error reports, also from anonymous visitors |
| `GET /actuator/health`, `/actuator/health/**`, `/actuator/info` | Healthchecks, versions in the footer |
| `/error` | Spring Boot error page |
| Swagger UI, `/v3/api-docs/**`, `/openapi.yaml` | Only when springdoc is on (`dev` profile) |

Public routes **ignore** the `Authorization` header: the frontend sends its token on every call, and an expired token must not turn an error report or a login into a `401`.

Without or with an invalid token, the answer is a `401` Problem Details with `traceId` and `WWW-Authenticate: Bearer` (`Bearer error="invalid_token"` when a token was sent). Unknown paths are `401` too without a token: routes cannot be discovered anonymously.

### Adding an endpoint

- **Protected** (the default): nothing to do. Read the user id with `@AuthenticationPrincipal Jwt jwt` → `jwt.getSubject()`.
- **Public**: add it to `SecurityConfig.PUBLIC_ROUTES` **and** to `PUBLIC` in `SecurityIntegrationTest`. That test calls every mapped route without a token and fails if one answers anything else than `401` without being listed, so a route never becomes public by accident.
- Declare `security` on the operation in the contract accordingly (`[]` when public).

### Why CSRF protection is disabled

Bearer tokens are not sent by the browser on its own, so Bearer endpoints are not exposed to CSRF. The two cookie endpoints, `refresh` and `logout`, are protected by `SameSite=Strict` plus a check that the `Origin` header is one of `blankjava.security.allowed-origins` (`BLANKJAVA_ALLOWED_ORIGINS`). Sessions are stateless: no HTTP session, no `JSESSIONID`.

HSTS is not sent by the backend: it belongs to the production Nginx configuration (M2).

### Access tokens

`AccessTokenService.issue(userId)` returns a JWT signed with RS256 by the active key, valid 10 minutes, with `kid` in the header and the claims `iss` (`blankjava-backend`), `aud` (`blankjava-web`), `sub` (user id, never the e-mail), `iat`, `exp`, `jti`.

Every token is checked for: RS256 signature by one of the accepted keys (`alg: none`, HS256 and other algorithms are refused), `iss`, `aud`, a non-blank `sub`, and `exp` with 30 s clock skew. Spring's default validator only checks the timestamps; the extra checks are in `JwtConfig`.

Keys: `JwtKeySet`, one active private key plus the previous public keys still accepted during a rotation. Where the key file comes from in each environment, and the rotation procedure: `infra/README.md`, section *JWT signing key*.

| Property | Variable | Default |
|---|---|---|
| `blankjava.security.jwt.private-key-path` | `JWT_PRIVATE_KEY_PATH` | — (required unless `ephemeral-key`) |
| `blankjava.security.jwt.previous-public-key-paths` | `JWT_PREVIOUS_PUBLIC_KEY_PATHS` | empty |
| `blankjava.security.jwt.ephemeral-key` | — | `false` (`true` in `dev` and tests) |
| `blankjava.security.allowed-origins` | `BLANKJAVA_ALLOWED_ORIGINS` | `https://localhost` |

## Tests

| Task | Content |
|---|---|
| `./gradlew test` | Unit and `@WebMvcTest` slice tests |
| `./gradlew integrationTest` | `@Tag("integration")`, Testcontainers PostgreSQL (needs Docker) |
| `./gradlew check` | Both, Spotless, Error Prone, JaCoCo thresholds (80 % lines, 70 % branches) |

- **Slice tests of protected or public routes** put the real access rules in the slice with `@ApiSecurity`, and get valid tokens from the `AccessTokenService` bean.
- **Contract compliance:** add `.andExpect(ContractValidation.matchesContract())` to a MockMvc call to check the response (status, headers, body) against `api/openapi.yaml`. Requests are not checked, so tests can send invalid ones on purpose.
