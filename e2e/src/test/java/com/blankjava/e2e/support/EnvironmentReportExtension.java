package com.blankjava.e2e.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Writes the Allure report's "Environment" header once per run: the project release and the
 * backend/API versions (read from {@code /actuator/info}) and this module's own version (issue #7).
 * Registered automatically for every test class ({@code src/test/resources/META-INF/services},
 * {@code junit-platform.properties}), so no test class needs to declare it.
 */
public class EnvironmentReportExtension implements BeforeAllCallback {

    private static final AtomicBoolean WRITTEN = new AtomicBoolean(false);
    private static final Path RESULTS_DIRECTORY = Path.of("build", "allure-results");
    private static final Pattern FIELD = Pattern.compile("\"(release|version|api)\"\\s*:\\s*\"([^\"]*)\"");

    @Override
    public void beforeAll(ExtensionContext context) {
        if (WRITTEN.compareAndSet(false, true)) {
            write(fetchBackendInfo());
        }
    }

    private void write(java.util.Map<String, String> backendInfo) {
        Properties environment = new Properties();
        environment.setProperty("Release", backendInfo.getOrDefault("release", "unknown"));
        environment.setProperty("Backend", backendInfo.getOrDefault("version", "unknown"));
        environment.setProperty("API", backendInfo.getOrDefault("api", "unknown"));
        environment.setProperty("E2E", Config.e2eVersion());
        try {
            Files.createDirectories(RESULTS_DIRECTORY);
            try (var out = Files.newOutputStream(RESULTS_DIRECTORY.resolve("environment.properties"))) {
                environment.store(out, "BlankJava Selenium suite environment");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the Allure environment file", e);
        }
    }

    /**
     * A minimal, dependency-free read of {@code /actuator/info}: the fields needed are matched with
     * a regular expression rather than parsed as JSON, since no JSON library is otherwise needed by
     * this module. Never throws: a missing or unreachable backend just leaves the fields "unknown".
     */
    private java.util.Map<String, String> fetchBackendInfo() {
        java.util.Map<String, String> info = new java.util.HashMap<>();
        try {
            HttpClient client = StackHttp.client();
            HttpRequest request = HttpRequest.newBuilder(URI.create(Config.baseUrl() + "/actuator/info"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Matcher matcher = FIELD.matcher(response.body());
            while (matcher.find()) {
                info.putIfAbsent(matcher.group(1), matcher.group(2));
            }
        } catch (Exception e) {
            // Best-effort header only; the suite's actual checks are the tests themselves.
            info.put("release", "unreachable: " + e.getMessage());
        }
        return info;
    }
}
