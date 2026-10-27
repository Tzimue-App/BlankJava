# syntax=docker/dockerfile:1
# Backend image. Build from the REPOSITORY ROOT: the code is generated from api/openapi.yaml.
#   docker build -f backend/Dockerfile --build-arg APP_RELEASE=0.1.0-b -t blankjava-backend .

# ---- Build: compile and extract the jar layers ------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# Contract and build scripts first, so dependency downloads are cached between code changes
COPY api/openapi.yaml api/openapi.yaml
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.properties backend/
COPY backend/gradle backend/gradle
WORKDIR /src/backend
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet dependencies > /dev/null

COPY backend/src src
# Tests run in CI before any image is published; here only the jar is built
RUN ./gradlew --no-daemon --quiet bootJar \
    && java -Djarmode=tools -jar build/libs/backend-*.jar extract --layers --launcher --destination /extracted

# ---- Runtime: JRE only, non-root user ----------------------------------------------------------
FROM eclipse-temurin:25-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --no-create-home --home-dir /app app
WORKDIR /app

# Project release this image belongs to, shown by /actuator/info (set by build-docker.yml)
ARG APP_RELEASE=unknown
ENV APP_RELEASE=${APP_RELEASE}

# Least to most frequently changed layers
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
    CMD curl --fail --silent --output /dev/null http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
