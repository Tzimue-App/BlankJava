package com.blankjava.e2e.pages;

import com.blankjava.e2e.support.TestUsers.TestUser;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/** {@code /register}: the account creation form (#33). */
public class RegisterPage extends BasePage {

    public static final String EMAIL = "register-email";
    public static final String DISPLAY_NAME = "register-display-name";
    public static final String PASSWORD = "register-password";
    public static final String CONFIRMATION = "register-confirmation";
    public static final String TOGGLE = "register-password-toggle";
    public static final String SUBMIT = "register-submit";
    public static final String LOGIN_LINK = "register-login-link";

    public RegisterPage(WebDriver driver) {
        super(driver);
    }

    public RegisterPage open() {
        open("/register");
        testId(EMAIL);
        return this;
    }

    /** Opens {@code /register?returnUrl=<encodedReturnUrl>} (already URL-encoded). */
    public RegisterPage openWithReturnUrl(String encodedReturnUrl) {
        open("/register?returnUrl=" + encodedReturnUrl);
        testId(EMAIL);
        return this;
    }

    /** Fills the four fields, the confirmation repeating the password. */
    public RegisterPage fill(TestUser user) {
        return fill(user.email(), user.displayName(), user.password(), user.password());
    }

    public RegisterPage fill(String email, String displayName, String password, String confirmation) {
        type(EMAIL, email);
        type(DISPLAY_NAME, displayName);
        type(PASSWORD, password);
        type(CONFIRMATION, confirmation);
        // Leaves the last field, so its error (if any) shows
        testId(CONFIRMATION).sendKeys(Keys.TAB);
        return this;
    }

    public void type(String field, String value) {
        WebElement input = testId(field);
        input.clear();
        input.sendKeys(value);
    }

    public void submit() {
        testId(SUBMIT).click();
    }

    /** Waits until the form is valid and the submit button enabled. */
    public void waitUntilSubmittable() {
        waitUntilEnabled(SUBMIT);
    }

    public boolean canSubmit() {
        return testId(SUBMIT).isEnabled();
    }

    /** The error shown under a field ({@code email}, {@code display-name}, {@code password}…). */
    public String errorUnder(String field) {
        return testId("register-" + field + "-error").getText();
    }

    public boolean hasNoErrorUnder(String field) {
        return lacksTestId("register-" + field + "-error");
    }

    public String passwordCount() {
        return testId("register-password-count").getText().trim();
    }
}
