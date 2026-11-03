package com.blankjava.e2e.pages;

import com.blankjava.e2e.support.Config;
import java.time.Duration;
import org.openqa.selenium.By;
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
        } catch (org.openqa.selenium.TimeoutException notFound) {
            return false;
        }
    }

    private static By testIdSelector(String testId) {
        return By.cssSelector("[data-testid=\"" + testId + "\"]");
    }
}
