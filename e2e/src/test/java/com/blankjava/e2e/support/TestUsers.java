package com.blankjava.e2e.support;

import java.util.UUID;

/**
 * Each test registers its own user, so journeys are independent and can run in parallel
 * (docs/technical_analysis.md §3.6). Not used by the smoke test; ready for the M1 account journeys.
 */
public final class TestUsers {

    private TestUsers() {}

    /** A unique e-mail that is never a real address: {@code e2e+<uuid>@example.test}. */
    public static String uniqueEmail() {
        return "e2e+" + UUID.randomUUID() + "@example.test";
    }
}
