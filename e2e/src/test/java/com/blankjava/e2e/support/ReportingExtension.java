package com.blankjava.e2e.support;

import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogType;

/**
 * Evidence attached to the Allure report after every test, passed or failed (docs/technical_analysis.md
 * §3.6): a screenshot always, and on failure the browser console log and the page source too. Must
 * run, and therefore be declared, <strong>after</strong> {@link DriverFactory} in {@code @ExtendWith}
 * (see {@link SeleniumTest}): JUnit runs {@code afterEach} callbacks in the reverse of their declared
 * order, so this one captures the page while the driver from {@link DriverFactory} is still open.
 */
public class ReportingExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        WebDriver driver = DriverFactory.currentDriver();
        if (driver == null) {
            return; // the test failed before a driver was created
        }
        boolean failed = context.getExecutionException().isPresent();
        String label = failed ? "Failure" : "Final state";
        Screenshots.capture(driver, label);
        if (failed) {
            attachConsoleLog(driver);
            Allure.addAttachment("Page source", "text/plain", driver.getPageSource());
        }
    }

    private void attachConsoleLog(WebDriver driver) {
        // Chrome's legacy logging API (W3C "goog:loggingPrefs") is not always available depending on
        // the browser/driver combination; a missing console log must never hide the real failure.
        try {
            String console = driver.manage().logs().get(LogType.BROWSER).getAll().stream()
                    .map(Object::toString)
                    .reduce("", (a, b) -> a + b + "\n");
            if (!console.isBlank()) {
                Allure.addAttachment("Browser console", "text/plain", console);
            }
        } catch (RuntimeException e) {
            Allure.addAttachment("Browser console (unavailable)", "text/plain", String.valueOf(e.getMessage()));
        }
    }
}
