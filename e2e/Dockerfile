# syntax=docker/dockerfile:1
# Selenium suite runner image. Build from the REPOSITORY ROOT, like the other images.
#   docker build -f e2e/Dockerfile -t blankjava-e2e .
# Not published: used only by compose.yaml (profile e2e-runner) and the CI Selenium suite.

FROM eclipse-temurin:25-jdk
WORKDIR /src/e2e

COPY e2e/gradlew e2e/settings.gradle.kts e2e/build.gradle.kts e2e/gradle.properties ./
COPY e2e/gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet dependencies > /dev/null

COPY e2e/src src

# SELENIUM_URL and E2E_BASE_URL (Config.java) are set by compose.yaml, to the Compose service
# names; results land in ./e2e/build on the host through the volume mount in compose.yaml.
ENTRYPOINT ["./gradlew", "--no-daemon", "test"]
