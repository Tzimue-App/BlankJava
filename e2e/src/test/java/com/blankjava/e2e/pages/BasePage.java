package com.blankjava.e2e.pages;

import com.blankjava.e2e.support.Config;
import java.net.URI;
import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Common ground for every Page Object (docs/technical_analysis.md §3.6): opening a path relative to
 * {@link Config#baseUrl()}, and locating elements by their stable {@code data-testid} attribute with
 * an explicit wait (no implicit wait, no {@code sleep}).
 */
public abstract class BasePage {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    protected final WebDriver driver;
    private final WebDriverWait wait;

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
    }

    protected final void open(String path) {
        driver.get(Config.baseUrl() + path);
    }

    /** Locates {@code [data-testid="<testId>"]}, waiting for it to become visible. */
    protected final WebElement testId(String testId) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(testIdSelector(testId)));
    }

    /** Whether {@code [data-testid="<testId>"]} is present within the default timeout. */
    protected final boolean hasTestId(String testId) {
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(testIdSelector(testId)));
            return true;
        } catch (TimeoutException notFound) {
            return false;
        }
    }

    /** Whether {@code [data-testid="<testId>"]} is absent or hidden, waiting for it to go if needed. */
    protected final boolean lacksTestId(String testId) {
        try {
            return wait.until(ExpectedConditions.invisibilityOfElementLocated(testIdSelector(testId)));
        } catch (TimeoutException stillThere) {
            return false;
        }
    }

    /** Waits until {@code [data-testid="<testId>"]} can be clicked (visible and enabled). */
    protected final void waitUntilEnabled(String testId) {
        wait.until(ExpectedConditions.elementToBeClickable(testIdSelector(testId)));
    }

    /** Waits until the browser shows {@code pathAndQuery} of this application, and returns it. */
    public final String waitForPath(String pathAndQuery) {
        try {
            wait.until(driver -> pathAndQuery.equals(currentPath()));
        } catch (TimeoutException e) {
            throw new AssertionError("Expected " + pathAndQuery + " but the browser shows " + currentPath(), e);
        }
        return currentPath();
    }

    /** Path, query and fragment of the current URL, e.g. {@code /login?returnUrl=%2Fme}. */
    public final String currentPath() {
        URI uri = URI.create(driver.getCurrentUrl());
        return uri.getRawPath()
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
    }

    /** {@code data-testid} of the element that has the keyboard focus, or an empty string. */
    public final String focusedTestId() {
        Object value = ((JavascriptExecutor) driver)
                .executeScript("return document.activeElement?.getAttribute('data-testid') ?? '';");
        return String.valueOf(value);
    }

    protected static By testIdSelector(String testId) {
        return By.cssSelector("[data-testid=\"" + testId + "\"]");
    }
}
