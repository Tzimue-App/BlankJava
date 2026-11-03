# Logs: format, correlation, search

How BlankJava logs, and how to find everything about one request (`docs/technical_analysis.md` §6).

## What each service writes

Every container writes to stdout, one event per line. Docker rotates the files (10 MB × 5).

| Service | Format | Notable fields |
|---|---|---|
| `frontend` (Nginx) | JSON, flat ECS keys | `event.dataset=nginx.access`, `trace.id`, `request.id`, `client.ip`, `url.path` (no query string), `http.response.status_code`, `nginx.request_time_s` |
| `backend` | ECS JSON (Spring Boot structured logging); readable text in the `dev` profile | `log.level`, `message`, `trace.id`, `span.id`, `request.id`, `event.dataset` |
| `postgres` | Text | Statements slower than 500 ms |

Backend datasets:

| `event.dataset` | Content |
|---|---|
| `backend.access` | One line per request: method, path, status, duration, client IP (health checks excluded) |
| `frontend` | Browser errors and warnings sent to `POST /api/client-logs`; the page's last trace id is in `frontend.trace.id` |
| *(none)* | Application events, errors; `SECURITY_AUDIT` logger for security events (`event.action`, `event.outcome`, `user.id`, `client.ip`) |

**Never in the logs:** passwords, tokens, `Authorization` or `Cookie` headers, full e-mail addresses (masked as `j***@example.com`). Users are identified by their id.

## One trace id per request

1. The Angular app sends a W3C `traceparent` header with every API call (`core/http/tracing.interceptor.ts`) and remembers the last trace id.
2. Nginx logs that trace id. When a request has no valid `traceparent` (curl, scripts), Nginx creates one from its own `$request_id`. Either way it forwards `traceparent` and `X-Request-Id` to the backend.
3. The backend (Micrometer Tracing, OpenTelemetry bridge) continues that trace: every log line of the request carries the same `trace.id`, and every error response contains it as `traceId`.
4. Browser errors reported to `/api/client-logs` carry the trace id of the page's last request (`frontend.trace.id`).

So a `traceId` quoted from an error response finds the Nginx line, the backend lines, and the browser events around it.

## Search the logs

Without extra services:

```bash
docker compose logs backend | grep 4bf92f3577b34da6a3ce929d0e0e4736
```

With the `observability` profile (Grafana Alloy → Loki → Grafana, 14-day retention):

```bash
docker compose --profile observability up -d --wait
```

Open **http://localhost:3000** → *Explore* (data source *Loki*, no login: local tool, bound to 127.0.0.1). Useful queries:

| Question | LogQL |
|---|---|
| Everything about one request | `{service=~"frontend\|backend"} \| trace_id="4bf92f3577b34da6a3ce929d0e0e4736"` |
| Backend errors | `{service="backend", level="ERROR"}` |
| Browser errors | `{service="backend", dataset="frontend"}` |
| Server errors seen by Nginx | `{service="frontend", dataset="nginx.access"} \| json \| http_response_status_code >= 500` |
| Security events | `{service="backend"} \|= "SECURITY_AUDIT"` |

Labels: `service` (Compose service), `level`, `dataset`. `trace_id` and `request_id` are *structured metadata*: filter on them with `| trace_id="…"`, not in the `{…}` selector, because one value per request would overload Loki's index.

The Loki API is also reachable on `http://127.0.0.1:3100`, for example:

```bash
curl -G http://127.0.0.1:3100/loki/api/v1/query_range --data-urlencode 'query={service="backend", level="ERROR"}'
```

## Files

| File | Role |
|---|---|
| `config.alloy` | Discovers this project's containers through the Docker socket (read-only), parses the JSON lines (both backend and Nginx shapes), sets labels and structured metadata, pushes to Loki |
| `loki.yaml` | Single-process Loki, filesystem storage in the `loki-data` volume, 14-day retention |
| `grafana/datasources/loki.yaml` | Loki data source provisioned at start-up |

Stop the profile with `docker compose --profile observability down`; add `--volumes` to also delete the stored logs.
