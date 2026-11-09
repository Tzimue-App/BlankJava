package com.blankjava.e2e.pages;

import java.time.Duration;
import java.util.List;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** {@code /login} (#34). */
public class LoginPage extends BasePage {

    public static final String EMAIL = "login-email";
    public static final String PASSWORD = "login-password";
    public static final String TOGGLE = "login-password-toggle";
    public static final String SUBMIT = "login-submit";
    public static final String REGISTER_LINK = "login-register-link";

    public LoginPage(WebDriver driver) {
        super(driver);
    }

    public LoginPage open() {
        open("/login");
        return waitUntilShown();
    }

    /** Opens {@code /login?returnUrl=<encodedReturnUrl>} (already URL-encoded). */
    public LoginPage openWithReturnUrl(String encodedReturnUrl) {
        open("/login?returnUrl=" + encodedReturnUrl);
        return waitUntilShown();
    }

    public LoginPage waitUntilShown() {
        testId(EMAIL);
        return this;
    }

    /**
     * Fills and submits the form. A refusal still shown from a previous attempt is waited out, so
     * {@link #rejection()} then reads the answer to this attempt.
     */
    public void logIn(String email, String password) {
        List<WebElement> previous = driver.findElements(testIdSelector("login-rejected"));
        type(EMAIL, email);
        type(PASSWORD, password);
        testId(SUBMIT).click();
        if (!previous.isEmpty()) {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.stalenessOf(previous.getFirst()));
        }
    }

    /** Waits until the form is valid and the submit button enabled. */
    public void waitUntilSubmittable() {
        waitUntilEnabled(SUBMIT);
    }

    public String rejection() {
        return testId("login-rejected").getText().trim();
    }

    public String value(String field) {
        return testId(field).getDomProperty("value");
    }

    private void type(String field, String value) {
        WebElement input = testId(field);
        input.clear();
        input.sendKeys(value);
    }
}
