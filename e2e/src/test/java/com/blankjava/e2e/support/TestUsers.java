package com.blankjava.e2e.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Each test creates its own user, so journeys are independent and can run in parallel
 * (docs/technical_analysis.md §3.6). A journey that is not about registration creates its user
 * through the API ({@link #registered()}), which is faster than the form and keeps the journey on
 * what it tests.
 */
public final class TestUsers {

    private TestUsers() {}

    /** An account of the stack under test. */
    public record TestUser(String email, String displayName, String password) {}

    /** A unique e-mail that is never a real address: {@code e2e+<uuid>@example.test}. */
    public static String uniqueEmail() {
        return "e2e+" + UUID.randomUUID() + "@example.test";
    }

    /**
     * A unique password that satisfies the policy (15–64 characters) and cannot be in the
     * compromised-password list.
     */
    public static String uniquePassword() {
        return "e2e-" + UUID.randomUUID();
    }

    /** A user that does not exist yet: fill the register form with it. */
    public static TestUser fresh(String displayName) {
        return new TestUser(uniqueEmail(), displayName, uniquePassword());
    }

    /** Registers a new user through {@code POST /api/auth/register}. */
    public static TestUser registered() {
        TestUser user = fresh("E2E User");
        String body = "{\"email\":\"" + json(user.email()) + "\",\"displayName\":\"" + json(user.displayName())
                + "\",\"password\":\"" + json(user.password()) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(Config.baseUrl() + "/api/auth/register"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = StackHttp.client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                throw new IllegalStateException(
                        "Registering a test user failed: HTTP " + response.statusCode() + " " + response.body());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Registering a test user failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while registering a test user", e);
        }
        return user;
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
