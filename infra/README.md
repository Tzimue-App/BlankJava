# Local development

Two ways to run BlankJava on your machine. Both need Docker (Docker Desktop on Windows and macOS).

## 1. The whole stack in Docker

```bash
cp .env.example .env
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

Logs of every container are rotated (`json-file`, 10 MB × 5). Log format, trace ids and searching them in Grafana (`--profile observability`): [observability/README.md](observability/README.md).

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

Run the backend on `http://localhost:8080`, in the `dev` profile (readable logs, Swagger UI on the contract at `/swagger-ui.html`):

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
- **HTTPS everywhere.** Nginx serves TLS in every environment, so the session cookie (`Secure`, milestone M1) behaves the same locally, in the Selenium runs and in production.
- **Non-root containers.** Nginx runs as `nginx` and binds 80/443 thanks to `net.ipv4.ip_unprivileged_port_start=0` in its own network namespace; the backend runs as `app`.
- **Security headers** (`nginx/security-headers.conf`): `X-Content-Type-Options`, `Referrer-Policy`, `Content-Security-Policy: frame-ancestors 'none'`. A strict CSP and HSTS come with M2.
- **Internal endpoints.** Only `/actuator/info` is exposed; other actuator endpoints answer 404 from outside.
- **Request size.** Bodies over 64 kB are rejected with 413.

Files: `compose.yaml` and `.env.example` at the repository root; `infra/nginx/` for the Nginx configuration mounted over the image's default one.
