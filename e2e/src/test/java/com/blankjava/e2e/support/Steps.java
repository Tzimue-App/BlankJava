package com.blankjava.e2e.support;

import io.qameta.allure.Allure;

/**
 * Small wrapper over the Allure step API, for readable Page Object methods and tests
 * (docs/technical_analysis.md §3.6). Named steps show up in the report in the order they ran.
 */
public final class Steps {

    private Steps() {}

    /** Runs {@code action} as a named Allure step. */
    public static void step(String name, Runnable action) {
        Allure.step(name, action::run);
    }

    /** Attaches a screenshot of the current test's browser, labelled {@code label}, on demand. */
    public static void screenshot(String label) {
        Screenshots.capture(DriverFactory.currentDriver(), label);
    }
}
