package com.blankjava.e2e.support;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Environment the suite runs against (docs/technical_analysis.md §3.6). Defaults match the
 * Compose network (the {@code e2e} service reaches {@code frontend} and {@code selenium} by their
 * service names); override with environment variables to run from the host against published ports,
 * e.g. {@code SELENIUM_URL=http://localhost:4444 E2E_BASE_URL=https://localhost}.
 */
public final class Config {

    private Config() {}

    public static String baseUrl() {
        return env("E2E_BASE_URL", "https://frontend");
    }

    public static String seleniumUrl() {
        return env("SELENIUM_URL", "http://selenium:4444");
    }

    /** Environment variable naming the stack's TLS certificate (PEM), trusted by {@link StackHttp}. */
    public static final String TLS_CERT_VARIABLE = "E2E_TLS_CERT";

    /** The stack's TLS certificate, if {@code E2E_TLS_CERT} is set. */
    public static Optional<Path> tlsCertificate() {
        return Optional.ofNullable(env(TLS_CERT_VARIABLE, null)).map(Path::of);
    }

    /** Version of this module, for the Allure report header (build.gradle.kts). */
    public static String e2eVersion() {
        return System.getProperty("e2e.version", "unknown");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
