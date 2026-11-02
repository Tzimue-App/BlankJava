package com.blankjava.e2e.support;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * One {@link WebDriver} per test method, against the Selenium Grid of {@link Config#seleniumUrl()}
 * (docs/technical_analysis.md §3.6). Injects it as a test method parameter, and keeps it reachable
 * through {@link #currentDriver()} for {@link Steps} and {@link ReportingExtension}.
 *
 * <p>Register through {@link SeleniumTest}, not directly: {@link ReportingExtension} must capture
 * the screenshot and the browser state <em>before</em> this extension quits the driver, which JUnit
 * guarantees by the order the two are declared in.
 */
public class DriverFactory implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final ThreadLocal<WebDriver> CURRENT = new ThreadLocal<>();

    private static final int WINDOW_WIDTH = 1920;
    private static final int WINDOW_HEIGHT = 1080;

    @Override
    public void beforeEach(ExtensionContext context) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=" + WINDOW_WIDTH + "," + WINDOW_HEIGHT);
        // The stack's TLS certificate is self-signed (infra/README.md); its SAN still covers the
        // hostnames used here, so only trust is relaxed, not hostname verification.
        options.setAcceptInsecureCerts(true);

        WebDriver driver = new RemoteWebDriver(seleniumUrl(), options);
        driver.manage().timeouts().implicitlyWait(Duration.ZERO); // explicit waits only (BasePage)
        CURRENT.set(driver);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        WebDriver driver = CURRENT.get();
        if (driver != null) {
            try {
                driver.quit();
            } finally {
                CURRENT.remove();
            }
        }
    }

    private static URL seleniumUrl() {
        try {
            return URI.create(Config.seleniumUrl()).toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid SELENIUM_URL: " + Config.seleniumUrl(), e);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return WebDriver.class.isAssignableFrom(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return currentDriver();
    }

    /** The driver of the test currently running on this thread, or {@code null} between tests. */
    public static WebDriver currentDriver() {
        return CURRENT.get();
    }
}
