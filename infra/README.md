# Local development

Two ways to run BlankJava on your machine. Both need Docker (Docker Desktop on Windows and macOS).

## 1. The whole stack in Docker

```bash
cp .env.example .env
```

Create the JWT signing key once (see [JWT signing key](#jwt-signing-key) below):

```bash
mkdir -p infra/secrets && openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out infra/secrets/jwt-private.pem
```

```bash
docker compose up --build --wait
```

Then open **https://localhost**. `--wait` returns once every service is healthy:

| Service | Role | Reachable from your machine |
|---|---|---|
| `frontend` | Nginx: TLS, Angular app, reverse proxy for `/api` and `/actuator/info` | `https://localhost` (443), `http://localhost` (80, redirects to HTTPS) |
| `backend` | Spring Boot API, JSON logs (one access line per request) | Only through Nginx |
| `postgres` | PostgreSQL 18, volume `postgres-data` | `127.0.0.1:5432` (this machine only) |
| `cert-init` | Creates a self-signed certificate once, in the volume `certs`, then exits | — |

Useful commands:

```bash
docker compose logs -f backend
```

```bash
docker compose down
```

```bash
docker compose down --volumes
```

The last one also deletes the database and the certificate.

Logs of every container are rotated (`json-file`, 10 MB × 5). Log format, trace ids and searching them in Grafana (`--profile observability`): [observability/README.md](observability/README.md). Running the Selenium suite against this stack (`--profile e2e`): [../e2e/README.md](../e2e/README.md).

### Trust the development certificate (once)

The certificate is self-signed, so browsers warn until you trust it. Copy it out of the stack:

```bash
docker compose cp frontend:/etc/nginx/certs/tls.crt ./blankjava-dev.crt
```

Then trust it:

- **Windows** (current user):

  ```bash
  certutil -user -addstore Root blankjava-dev.crt
  ```

- **macOS**:

  ```bash
  sudo security add-trusted-cert -d -r trustRoot -k /Library/Keychains/System.keychain blankjava-dev.crt
  ```

- **Linux** (Debian/Ubuntu): copy it to `/usr/local/share/ca-certificates/` and run `sudo update-ca-certificates`.
- **Firefox** keeps its own store: *Settings → Privacy & Security → Certificates → View Certificates → Authorities → Import*.

Delete `blankjava-dev.crt` afterwards. It is regenerated only if the `certs` volume is removed, in which case it must be trusted again. To use a real certificate, put `tls.crt` and `tls.key` in the `certs` volume: `cert-init` keeps existing files.

## 2. Backend and frontend outside Docker (fast feedback)

Run the database only:

```bash
docker compose up -d --wait postgres
```

Run the backend on `http://localhost:8080`, in the `dev` profile (readable logs, Swagger UI on the contract at `/swagger-ui.html`, a JWT key generated at startup so no key file is needed, allowed origin `http://localhost:4200`):

```bash
cd backend && DB_PASSWORD=change-me-local-only ./gradlew bootRun --args='--spring.profiles.active=dev'
```

Run the frontend on `http://localhost:4200`; `/api` and `/actuator` are proxied to the backend (`frontend/proxy.conf.json`):

```bash
cd frontend && npm start
```

On Windows PowerShell, set the password first with `$env:DB_PASSWORD = "change-me-local-only"`, then run `./gradlew bootRun …` without the prefix.

## How the stack is wired

- **Single entry point.** Only Nginx is published. It overwrites `X-Forwarded-For` with the client address it sees, and adds `X-Forwarded-Proto` and `X-Request-Id`. The backend trusts these headers **only from Nginx's fixed address** (`172.30.0.10`, see `compose.yaml` and `server.tomcat.remoteip.internal-proxies` in the backend configuration). A forged `X-Forwarded-For` is therefore ignored, and the backend access log (`client.ip`) shows the real client IP.
- **Nginx re-resolves `backend` on every request** (`resolver 127.0.0.11 valid=10s` in `nginx/default.conf`, Docker's embedded DNS) instead of caching the address once at startup. Without this, recreating just the `backend` container — a new IP on the `stack` network — leaves Nginx proxying to the old, now-dead address with `502` and "Connection refused" in its logs, until Nginx itself is restarted.
- **HTTPS everywhere.** Nginx serves TLS in every environment, so the session cookie (`Secure`, milestone M1) behaves the same locally, in the Selenium runs and in production.
- **Non-root containers.** Nginx runs as `nginx` and binds 80/443 thanks to `net.ipv4.ip_unprivileged_port_start=0` in its own network namespace; the backend runs as `app`.
- **Security headers** (`nginx/security-headers.conf`): `X-Content-Type-Options`, `Referrer-Policy`, `Content-Security-Policy: frame-ancestors 'none'`. A strict CSP and HSTS come with M2.
- **Internal endpoints.** Only `/actuator/info` is exposed; other actuator endpoints answer 404 from outside.
- **Request size.** Bodies over 64 kB are rejected with 413.

Files: `compose.yaml` and `.env.example` at the repository root; `infra/nginx/` for the Nginx configuration (`default.conf` is mounted as a template, rendered over the image's default one at startup, see [Rate limiting](#rate-limiting)); `infra/secrets/` (git-ignored) for the JWT signing key.

## JWT signing key

The backend signs access tokens (RS256) with an RSA private key of **at least 3072 bits**, in **PKCS#8 PEM** (`-----BEGIN PRIVATE KEY-----`, what `openssl genpkey` writes). The public key is derived from it. The key is never in the repository nor in an image: it is mounted at runtime, and the backend refuses to start without it, naming the missing file.

| Environment | Where the key comes from |
|---|---|
| Compose (local) | `infra/secrets/jwt-private.pem`, created once with the command of section 1. `compose.yaml` mounts `infra/secrets/` read-only into the backend. Both folders are git-ignored and excluded from Docker build contexts. |
| `bootRun` (`dev` profile) | None: a key is generated at startup (`blankjava.security.jwt.ephemeral-key`); sessions end when the backend restarts. |
| Backend tests | None: same generated key (`backend/src/test/resources/config/application.yaml`). |
| CI (Selenium suite) | The repository secret `JWT_PRIVATE_KEY`, written to `infra/secrets/` by `e2e.yml` before the stack starts. On a PR from a fork or from Dependabot (no repository secrets), a throw-away key is generated for the run. |
| Production | Your own key, mounted as a file; point `JWT_PRIVATE_KEY_PATH` to it. Never reuse the CI or a local key. |

On Linux, the backend container runs as user `app`: make the key file readable (`chmod 644 infra/secrets/jwt-private.pem`) on a development machine. In production, mount it with an owner or mode the `app` user can read, and nothing broader.

### Setting the CI secret

Generate a key outside the repository, store it, then delete the local copy:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out jwt-ci-private.pem
```

```bash
gh secret set JWT_PRIVATE_KEY --repo Tzimue-App/BlankJava < jwt-ci-private.pem
```

```bash
rm jwt-ci-private.pem
```

It must be an **Actions** repository secret (not an environment secret). No Dependabot secret is needed.

### Rotating the key

Each key is identified by its `kid` (RFC 7638 thumbprint, logged at startup as `JWT keys loaded: active kid=…`).

1. Extract the public key of the current key:

   ```bash
   openssl pkey -in infra/secrets/jwt-private.pem -pubout -out infra/secrets/jwt-previous-public.pem
   ```

2. Replace `jwt-private.pem` with a new key (command of section 1), and accept the previous one for verification by setting in `.env`:

   ```bash
   JWT_PREVIOUS_PUBLIC_KEY_PATHS=/run/secrets/jwt/jwt-previous-public.pem
   ```

3. Restart the backend. New tokens are signed with the new key; tokens of the previous key stay valid.
4. After at least one access-token lifetime (10 minutes), empty `JWT_PREVIOUS_PUBLIC_KEY_PATHS`, delete `jwt-previous-public.pem` and restart.

For the CI secret, no overlap is needed: each Selenium run starts a new stack, so `gh secret set` again is enough. Scheduled rotation comes with M2.

## Allowed origins

`refresh` and `logout` read the session cookie, so they only accept requests whose `Origin` header is one of `BLANKJAVA_ALLOWED_ORIGINS` (comma-separated `scheme://host[:port]`). The default in `compose.yaml` covers this stack: `https://localhost` for your browser and `https://frontend` for the Selenium browser. If you change `HTTPS_PORT`, add the port (`https://localhost:8443`). The `dev` profile accepts `http://localhost:4200` (`ng serve`).

## Rate limiting

Login, registration and refresh each cost the backend a password hash or a database lookup, so `/api/auth/*` is limited in two layers (docs/technical_analysis.md §5.3):

1. **Nginx, per client IP** (`nginx/default.conf`, `limit_req` zone `auth`): a flood is answered by Nginx and never reaches the backend. Only `/api/auth/*` is limited; `/api/users/me`, `/api/client-logs`, `/actuator/info` and the Angular files are not.
2. **Backend, per IP and per e-mail** (Bucket4j, #64): finer limits, e.g. many IPs trying one account.

### Tuning the Nginx layer

`default.conf` is a template: at startup the image's entrypoint replaces the `${NGINX_AUTH_*}` variables (only those, `NGINX_ENVSUBST_FILTER` in `compose.yaml`) and writes the result to `/etc/nginx/conf.d/default.conf`. Set them in `.env`, then `docker compose up -d frontend`:

| Variable | Default | Meaning |
|---|---|---|
| `NGINX_AUTH_RATE` | `10r/m` | Sustained rate per client IP (`r/s` or `r/m`): one request every 6 s |
| `NGINX_AUTH_BURST` | `10` | Requests accepted at once above the rate (`nodelay`: served immediately, not queued) |
| `NGINX_AUTH_RETRY_AFTER` | `6` | `Retry-After` of a `429`, in seconds: when the next request is accepted, i.e. `60 / requests per minute` |

With the defaults, one IP can send 11 requests at once, then one every 6 seconds; the budget refills at that pace. A page load of the app sends one `refresh`, so a user reloading a dozen times in a few seconds can meet it too. Keep `RETRY_AFTER` in line with `RATE` when you change it. Behind a shared address (an office NAT), all users share one budget: raise the rate rather than the burst.

The Selenium suite registers and logs in many users from a single browser container, so `.github/workflows/e2e.yml` starts the stack with its own, much higher values. Do the same in `.env` before running the suite locally (`NGINX_AUTH_RATE=600r/m`, `NGINX_AUTH_BURST=100`, `NGINX_AUTH_RETRY_AFTER=1`), rather than on one command line: `docker compose run` would recreate the frontend with the `.env` values.

### What a 429 looks like

The response has the shape of the contract's `TooManyRequests` (`api/openapi.yaml`), whichever layer answers:

```http
HTTP/2 429
content-type: application/problem+json
retry-after: 6
cache-control: no-store

{"type":"about:blank","title":"Too Many Requests","status":429,"detail":"Too many attempts. Try again later.","traceId":"4bf92f3577b34da6a3ce929d0e0e4736"}
```

In the logs, a request rejected by Nginx has an Nginx access line with `"http.response.status_code":429` and an empty `nginx.upstream_time_s`, a `limiting requests, excess: … by zone "auth", client: …` warning on Nginx's error output, and **no** `backend.access` line for its `trace.id`:

```bash
docker compose logs frontend | grep '"http.response.status_code":429'
```
