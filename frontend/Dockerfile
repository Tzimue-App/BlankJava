# syntax=docker/dockerfile:1
# Frontend image. Build from the REPOSITORY ROOT: the API client is generated from api/openapi.yaml.
#   docker build -f frontend/Dockerfile --build-arg APP_RELEASE=0.1.0-b -t blankjava-frontend .

# ---- Build: install, generate, compile ---------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /src

# Contract and lock file first, so dependency installation is cached between code changes
COPY api/openapi.yaml api/openapi.yaml
COPY frontend/package.json frontend/package-lock.json frontend/
WORKDIR /src/frontend
RUN npm ci --no-audit --no-fund

COPY frontend/ ./
# Tests run in CI before any image is published; here only the production build
RUN npm run build

# Project release this image belongs to, read by the footer from /config.json (runtime config)
ARG APP_RELEASE=unknown
RUN printf '{\n  "release": "%s"\n}\n' "$APP_RELEASE" > dist/web/browser/config.json

# ---- Runtime: Nginx as a non-root user (port 8080) --------------------------------------------
FROM nginxinc/nginx-unprivileged:1.30-alpine
COPY frontend/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/frontend/dist/web/browser /usr/share/nginx/html

EXPOSE 8080
# 127.0.0.1, not localhost: on Alpine localhost resolves to ::1 and Nginx listens on IPv4
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --quiet --spider http://127.0.0.1:8080/ || exit 1
