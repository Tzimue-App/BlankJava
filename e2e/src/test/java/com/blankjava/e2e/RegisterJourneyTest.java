package com.blankjava.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.blankjava.e2e.pages.AppFrame;
import com.blankjava.e2e.pages.LoginPage;
import com.blankjava.e2e.pages.ProfilePage;
import com.blankjava.e2e.pages.RegisterPage;
import com.blankjava.e2e.support.Accessibility;
import com.blankjava.e2e.support.SeleniumTest;
import com.blankjava.e2e.support.Steps;
import com.blankjava.e2e.support.TestUsers;
import com.blankjava.e2e.support.TestUsers.TestUser;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.interactions.Actions;

/** {@code /register} (#33), journeys collected in #45. */
@Tag("accounts")
@SeleniumTest
class RegisterJourneyTest {

    /** In the backend's compromised-password list (backend/src/main/resources/passwords/). */
    private static final String BREACHED_PASSWORD = "123456789vuonggialong";

    @Test
    void aVisitorRegistersAndLandsOnTheirProfile(WebDriver driver) {
        RegisterPage register = new RegisterPage(driver);
        TestUser jane = TestUsers.fresh("Jane");

        Steps.step("Open /register and fill the form", () -> register.open().fill(jane));
        Steps.step("Submit", register::submit);

        ProfilePage profile = new ProfilePage(driver).waitUntilLoaded();
        assertThat(profile.displayName()).isEqualTo("Jane");
        assertThat(new AppFrame(driver).showsLogout()).isTrue();
    }

    @Test
    void aSafeReturnUrlIsFollowedAndAnotherSiteIsNot(WebDriver driver) {
        RegisterPage register = new RegisterPage(driver);

        Steps.step("Register from /register?returnUrl=//evil.example", () -> {
            register.openWithReturnUrl("%2F%2Fevil.example").fill(TestUsers.fresh("Jane"));
            register.submit();
        });
        new ProfilePage(driver).waitUntilLoaded();
        assertThat(driver.getCurrentUrl()).doesNotContain("evil.example");

        Steps.step(
                "Log out, register again from /register?returnUrl=%2Fme",
                () -> {
                    new AppFrame(driver).logOut();
                    new LoginPage(driver).waitForPath("/login");
                    register.openWithReturnUrl("%2Fme").fill(TestUsers.fresh("Jim"));
                    register.submit();
                });
        assertThat(new ProfilePage(driver).waitUntilLoaded().displayName()).isEqualTo("Jim");
    }

    @Test
    void anEmailAlreadyRegisteredIsReportedUnderTheEmail(WebDriver driver) {
        RegisterPage register = new RegisterPage(driver);
        TestUser existing = TestUsers.registered();
        String sameEmailOtherCase = "  " + existing.email().toUpperCase(Locale.ROOT) + "  ";

        Steps.step("Register again with the same e-mail in capitals and with spaces", () -> {
            String password = TestUsers.uniquePassword();
            register.open().fill(sameEmailOtherCase, "Jane", password, password);
            register.submit();
        });

        assertThat(register.errorUnder("email")).contains("already registered");
        assertThat(register.currentPath()).isEqualTo("/register");
        assertThat(register.canSubmit()).isFalse();
        Steps.screenshot("E-mail already registered");

        Steps.step("Edit the e-mail", () -> register.type(RegisterPage.EMAIL, TestUsers.uniqueEmail()));
        assertThat(register.hasNoErrorUnder("email")).isTrue();
        assertThat(register.canSubmit()).isTrue();
    }

    @Test
    void invalidInputsAreExplainedAndKeepTheButtonDisabled(WebDriver driver) {
        RegisterPage register = new RegisterPage(driver);

        Steps.step(
                "Fill every field with an invalid value",
                () -> register.open().fill("jane@localhost", "J", "a".repeat(14), "something else"));

        assertThat(register.errorUnder("email")).contains("valid e-mail address");
        assertThat(register.errorUnder("display-name")).contains("at least 2");
        assertThat(register.errorUnder("password")).contains("at least 15");
        assertThat(register.errorUnder("confirmation")).contains("do not match");
        assertThat(register.canSubmit()).isFalse();
    }

    @Test
    void aBreachedPasswordIsRefusedUnderThePassword(WebDriver driver) {
        RegisterPage register = new RegisterPage(driver);

        Steps.step("Register with a password from the breach list", () -> {
            register.open().fill(TestUsers.uniqueEmail(), "Jane", BREACHED_PASSWORD, BREACHED_PASSWORD);
            register.submit();
        });

        assertThat(register.errorUnder("password")).contains("known data breaches");
        assertThat(register.currentPath()).isEqualTo("/register");
    }

    @Test
    void thePasswordIsCountedAfterNfkcLikeTheBackend(WebDriver driver) {
        RegisterPage register = new RegisterPage(driver);
        // 14 characters typed; "ﬃ" becomes "ffi" under NFKC: 16 for the policy
        String password = randomLetters(13) + "ﬃ";

        Steps.step(
                "Type 13 letters and the ligature ﬃ",
                () -> register.open().fill(TestUsers.uniqueEmail(), "Jane", password, password));
        assertThat(register.passwordCount()).isEqualTo("16 / 64");
        assertThat(register.hasNoErrorUnder("password")).isTrue();

        Steps.step("Submit: the backend accepts it too", register::submit);
        new ProfilePage(driver).waitUntilLoaded();
    }

    @Test
    void theFormWorksWithTheKeyboardOnly(WebDriver driver) {
        RegisterPage register = new RegisterPage(driver).open();
        TestUser jane = TestUsers.fresh("Jane");
        Actions keyboard = new Actions(driver);

        Steps.step("Tab into the e-mail field and type every field", () -> {
            driver.findElement(By.tagName("body")).click();
            // Header link first, then the form
            for (int i = 0; i < 5 && !RegisterPage.EMAIL.equals(register.focusedTestId()); i++) {
                keyboard.sendKeys(Keys.TAB).perform();
            }
            assertThat(register.focusedTestId()).isEqualTo(RegisterPage.EMAIL);
            keyboard.sendKeys(jane.email(), Keys.TAB).perform();
            assertThat(register.focusedTestId()).isEqualTo(RegisterPage.DISPLAY_NAME);
            keyboard.sendKeys(jane.displayName(), Keys.TAB).perform();
            assertThat(register.focusedTestId()).isEqualTo(RegisterPage.PASSWORD);
            keyboard.sendKeys(jane.password(), Keys.TAB).perform();
            assertThat(register.focusedTestId()).isEqualTo(RegisterPage.TOGGLE);
            keyboard.sendKeys(Keys.TAB).perform();
            assertThat(register.focusedTestId()).isEqualTo(RegisterPage.CONFIRMATION);
            keyboard.sendKeys(jane.password()).perform();
            // A person is never faster than Angular enabling the button; the test could be
            register.waitUntilSubmittable();
            keyboard.sendKeys(Keys.TAB).perform();
            assertThat(register.focusedTestId()).isEqualTo(RegisterPage.SUBMIT);
        });
        Steps.step(
                "Press Enter on the submit button",
                () -> keyboard.sendKeys(Keys.ENTER).perform());

        assertThat(new ProfilePage(driver).waitUntilLoaded().displayName()).isEqualTo("Jane");
    }

    @Test
    void aLoggedInUserOpeningRegisterGoesToTheProfile(WebDriver driver) {
        TestUser user = TestUsers.registered();
        Steps.step("Log in", () -> {
            new LoginPage(driver).open().logIn(user.email(), user.password());
            new ProfilePage(driver).waitUntilLoaded();
        });

        Steps.step("Open /register", () -> new AppFrame(driver).load("/register"));

        new ProfilePage(driver).waitUntilLoaded();
    }

    @Test
    void theRegisterPageHasNoSeriousAccessibilityViolation(WebDriver driver) {
        RegisterPage register = new RegisterPage(driver).open();
        Accessibility.assertNoSeriousViolations(driver);

        Steps.step("Show every field error", () -> register.fill("x", "J", "short", "other"));
        Accessibility.assertNoSeriousViolations(driver);
    }

    private static String randomLetters(int count) {
        String source = UUID.randomUUID().toString().replaceAll("[^a-f]", "") + "abcdefghijklmnop";
        return source.substring(0, count);
    }
}
