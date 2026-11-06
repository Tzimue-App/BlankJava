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

## Accounts and sessions

Code: `src/main/java/com/blankjava/account/` (accounts, password rules) and `src/main/java/com/blankjava/auth/` (`/api/auth/*`, refresh tokens). Tables: `V2__accounts.sql` (`app_user`, `refresh_token`).

`POST /api/auth/register` normalises the input, checks every rule (all broken rules are reported at once), stores the account and opens a session in one transaction: `201` with the access token and the user, the refresh token in the `refresh_token` cookie, `Cache-Control: no-store`, and a `user.registered` event on the `SECURITY_AUDIT` logger (user id and client IP, never the e-mail). Login, refresh and logout: [Sessions](#sessions).

| Field | Normalisation | Rule (`code` when broken) |
|---|---|---|
| `email` | Trimmed (Unicode white space), lower-cased with `Locale.ROOT` | Required; ≤ 254 code points; `local@domain`, dot-atom local part ≤ 64, at least two domain labels (`invalid_format`); not registered yet (`409` `email_taken`) |
| `displayName` | Trimmed | 2–50 code points (`too_short` / `too_long`); no control character (`invalid_format`) |
| `password` | NFKC | 15–64 code points; not in the compromised-password list (`compromised_password`) |

E-mail uniqueness is enforced by the unique index `app_user_email_uk`: when two registrations of the same address race past the existence check, the second insert fails on the index and still answers `409`.

**Validation errors.** Every `400` caused by a field is the contract's `ValidationProblem`: `errors[] { field, code, message }`. Rules checked by the backend throw `InvalidFieldsException`; constraints generated from the contract (`@NotNull`, `@Size`…) are mapped by `FieldErrors`: missing or empty → `required`, length → `too_short` / `too_long`, anything else → `invalid_format`. Messages never repeat the rejected value.

### Sessions

A session is an access token (response body, kept in memory by the frontend, 10 minutes) plus a refresh token (cookie, 7 days). Code: `auth/SessionService.java`, `RefreshTokenService.java`, `AuthController.java`.

| Operation | What it does | Answers |
|---|---|---|
| `POST /api/auth/login` | E-mail normalised as at registration, password with NFKC, Argon2id match. Opens a new session: a new token family. | `200` like registration; any mismatch `401` *Invalid e-mail or password.* |
| `POST /api/auth/refresh` | Rotates the cookie's token: revoked, `replaced_by` set, a new token of the same family and a new access token issued. | `200`; no, unknown, expired or revoked token `401` with the cookie cleared |
| `POST /api/auth/logout` | Revokes the cookie's token and clears the cookie. Idempotent. | `204`, also without cookie or with a dead token |

- **Cookie:** `refresh_token`, `HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=604800`; cleared with the same attributes and `Max-Age=0`. The value is 256 random bits (base64url); the database keeps only its SHA-256 (hex), the user, the `family_id` of the login and the expiry (`blankjava.security.refresh-token.lifetime`).
- **Generic login error:** an unknown e-mail, a wrong password and a password that could never be valid (3 characters…) give the same `401` body; `LoginRequest` carries no policy rule, so no `400` reveals it. An unknown e-mail is checked against a dummy hash of the same cost, so it takes as long as a wrong password. Only a structurally invalid body (missing field) is a `400`.
- **Hash upgrade:** a hash made with a lower Argon2 cost than the configuration is re-encoded at the next successful login.
- **Strict rotation:** a token works once. Presenting it again, even a second later, is a `401` and clears the cookie. The token row is locked (`SELECT … FOR UPDATE`) during a rotation, so two concurrent requests with the same cookie give one `200` and one `401`. Tabs of the same browser serialise their refreshes with the Web Locks API (#32), so a browser never sends a cookie twice. Revoking the whole family when a used token comes back is M2.
- **`Origin` rule:** `refresh` and `logout` first check that the `Origin` header is one of `blankjava.security.allowed-origins` (`BLANKJAVA_ALLOWED_ORIGINS`, see `infra/README.md`), compared without case and trailing slash. A missing `Origin`, or `null`, is refused like a foreign one: `403` *Origin not allowed.*, nothing revoked. With `SameSite=Strict`, this is what replaces CSRF tokens.
- **What logout ends:** the refresh token, so the session cannot be renewed. An access token already issued stays valid until it expires (at most 10 minutes); the frontend drops it from memory. Ending them at once ("log out everywhere", `tokens_valid_after`) is M2.
- **Purge:** `RefreshTokenPurge`, every night at 03:30 server time (`blankjava.security.refresh-token.purge-cron`), deletes the tokens expired or revoked for more than 7 days (`purge-after`), and logs `refresh_token.purged` with the count. ShedLock (`shedlock` table, `V3__shedlock.sql`, database clock) runs it on one instance when several backends run. `blankjava.scheduling.enabled=false` turns scheduled jobs off.

**Audit events** (`SECURITY_AUDIT` logger, `event.category=authentication`):

| `event.action` | Level | Fields |
|---|---|---|
| `user.registered` | INFO | `user.id`, `client.ip` |
| `auth.login.succeeded` | INFO | `user.id`, `client.ip` |
| `auth.login.failed` | WARN | `user.email` masked (`j***@example.com`, or `***` when the value is not an address), `client.ip` |
| `auth.logout` | INFO | `user.id`, `client.ip`; only when an active token was revoked |


### Passwords

- **Hashing:** Argon2id (Bouncy Castle) behind a `DelegatingPasswordEncoder`, stored as `{argon2}$argon2id$v=19$m=19456,t=2,p=1$<salt>$<hash>`. The prefix and the parameters travel with each hash, so the cost or the algorithm can be raised later without invalidating existing passwords. BCrypt is not used: its 72-byte input limit is below the 64 code points (up to 256 bytes) the policy accepts.
- **Normalisation:** NFKC before measuring, checking and hashing, so the same password typed on another keyboard matches. Login does the same (`PasswordPolicy.normalize`).
- **Compromised passwords:** `BlocklistPasswordChecker`, a Spring Security `CompromisedPasswordChecker` over `src/main/resources/passwords/compromised-passwords.txt` (5,328 entries from SecLists, MIT; source, version and how the file was made in the README next to it). Offline, case-insensitive.

| Property | Default |
|---|---|
| `blankjava.security.password.argon2.memory-kib` | `19456` (19 MiB, OWASP minimum) |
| `blankjava.security.password.argon2.iterations` | `2` |
| `blankjava.security.password.argon2.parallelism` | `1` |

The tests lower the cost (`src/test/resources/config/application.yaml`); `PasswordConfigTest` checks that `application.yaml` keeps the values above.

**Tuning the cost on a new host.** One hash should take 100–300 ms on the production host: enough to slow down an offline attack, little enough for a login. Raise the memory first (it is what makes GPU attacks expensive), then the iterations; keep parallelism at 1 unless the host has cores to spare per login. Measure on the host itself, then set the properties (environment variables `BLANKJAVA_SECURITY_PASSWORD_ARGON2_MEMORYKIB`, `BLANKJAVA_SECURITY_PASSWORD_ARGON2_ITERATIONS`: Spring drops the dashes). Existing hashes keep working: each one is checked with the parameters it was made with. Memory is reserved per concurrent hash: 19 MiB × the number of simultaneous logins must fit in the container's memory.

## Tests

| Task | Content |
|---|---|
| `./gradlew test` | Unit and `@WebMvcTest` slice tests |
| `./gradlew integrationTest` | `@Tag("integration")`, Testcontainers PostgreSQL (needs Docker) |
| `./gradlew check` | Both, Spotless, Error Prone, JaCoCo thresholds (80 % lines, 70 % branches) |

- **Slice tests of protected or public routes** put the real access rules in the slice with `@ApiSecurity`, and get valid tokens from the `AccessTokenService` bean.
- **Slice tests** list their controller (`@WebMvcTest(MyController.class)`): without one, the slice loads every controller of the application and needs all their dependencies.
- **Contract compliance:** add `.andExpect(ContractValidation.matchesContract())` to a MockMvc call to check the response (status, headers, body) against `api/openapi.yaml`. Requests are not checked, so tests can send invalid ones on purpose.
