package com.blankjava.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.blankjava.e2e.pages.AppFrame;
import com.blankjava.e2e.pages.LoginPage;
import com.blankjava.e2e.pages.ProfilePage;
import com.blankjava.e2e.support.Accessibility;
import com.blankjava.e2e.support.SeleniumTest;
import com.blankjava.e2e.support.Steps;
import com.blankjava.e2e.support.TestUsers;
import com.blankjava.e2e.support.TestUsers.TestUser;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.interactions.Actions;

/** {@code /login} and *Log out* (#34), journeys collected in #45. */
@Tag("accounts")
@SeleniumTest
class LoginJourneyTest {

    private static final String INVALID = "Invalid e-mail or password.";

    @Test
    void aRegisteredUserLogsInWithTheirEmailInAnyCase(WebDriver driver) {
        TestUser user = TestUsers.registered();
        LoginPage login = new LoginPage(driver);

        Steps.step(
                "Log in with the e-mail in capitals and with spaces",
                () -> login.open().logIn("  " + user.email().toUpperCase(Locale.ROOT) + " ", user.password()));

        new ProfilePage(driver).waitUntilLoaded();
        assertThat(new AppFrame(driver).showsLogout()).isTrue();
    }

    @Test
    void aWrongPasswordAnUnknownEmailAndAShortPasswordGetTheSameMessage(WebDriver driver) {
        TestUser user = TestUsers.registered();
        LoginPage login = new LoginPage(driver).open();

        Steps.step("Wrong password", () -> login.logIn(user.email(), user.password() + "x"));
        String wrongPassword = login.rejection();
        assertThat(login.value(LoginPage.PASSWORD)).isEmpty();
        assertThat(login.value(LoginPage.EMAIL)).isEqualTo(user.email());
        Steps.screenshot("Wrong password");

        Steps.step("Unknown e-mail", () -> login.logIn(TestUsers.uniqueEmail(), user.password()));
        String unknownEmail = login.rejection();

        Steps.step("3-character password", () -> login.logIn(user.email(), "abc"));
        String shortPassword = login.rejection();

        assertThat(wrongPassword).isEqualTo(INVALID);
        assertThat(unknownEmail).isEqualTo(INVALID);
        assertThat(shortPassword).isEqualTo(INVALID);
        assertThat(login.currentPath()).isEqualTo("/login");
    }

    @Test
    void aReturnUrlOfTheAppIsFollowed(WebDriver driver) {
        TestUser user = TestUsers.registered();

        Steps.step(
                "Log in from /login?returnUrl=%2Fme",
                () -> new LoginPage(driver).openWithReturnUrl("%2Fme").logIn(user.email(), user.password()));

        new ProfilePage(driver).waitUntilLoaded();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"https%3A%2F%2Fevil.example", "%2F%2Fevil.example", "%2F%5Cevil.example", "%2F%09%2Fevil.example"
            })
    void aReturnUrlToAnotherSiteIsIgnored(String encodedReturnUrl, WebDriver driver) {
        TestUser user = TestUsers.registered();

        Steps.step(
                "Log in from /login?returnUrl=" + encodedReturnUrl,
                () -> new LoginPage(driver).openWithReturnUrl(encodedReturnUrl).logIn(user.email(), user.password()));

        new ProfilePage(driver).waitUntilLoaded();
        assertThat(driver.getCurrentUrl()).doesNotContain("evil.example");
    }

    @Test
    void loggingOutEndsTheSessionForGood(WebDriver driver) {
        TestUser user = TestUsers.registered();
        AppFrame frame = new AppFrame(driver);
        LoginPage login = new LoginPage(driver);

        Steps.step("Log in", () -> login.open().logIn(user.email(), user.password()));
        new ProfilePage(driver).waitUntilLoaded();

        Steps.step("Click Log out", frame::logOut);
        login.waitForPath("/login");
        assertThat(frame.hidesLogout()).isTrue();

        Steps.step("Open /me", () -> frame.load("/me"));
        login.waitForPath("/login?returnUrl=%2Fme");

        Steps.step("Reload: the refresh cookie no longer restores a session", driver.navigate()::refresh);
        login.waitForPath("/login?returnUrl=%2Fme");
        assertThat(frame.hidesLogout()).isTrue();
    }

    @Test
    void aLoggedInUserOpeningLoginGoesToTheProfile(WebDriver driver) {
        TestUser user = TestUsers.registered();
        Steps.step("Log in", () -> new LoginPage(driver).open().logIn(user.email(), user.password()));
        new ProfilePage(driver).waitUntilLoaded();

        Steps.step("Open /login", () -> new AppFrame(driver).load("/login"));

        new ProfilePage(driver).waitUntilLoaded();
    }

    @Test
    void theFormWorksWithTheKeyboardOnly(WebDriver driver) {
        TestUser user = TestUsers.registered();
        LoginPage login = new LoginPage(driver).open();
        Actions keyboard = new Actions(driver);

        Steps.step("Tab through the form and press Enter", () -> {
            driver.findElement(By.tagName("body")).click();
            for (int i = 0; i < 5 && !LoginPage.EMAIL.equals(login.focusedTestId()); i++) {
                keyboard.sendKeys(Keys.TAB).perform();
            }
            assertThat(login.focusedTestId()).isEqualTo(LoginPage.EMAIL);
            keyboard.sendKeys(user.email(), Keys.TAB).perform();
            assertThat(login.focusedTestId()).isEqualTo(LoginPage.PASSWORD);
            keyboard.sendKeys(user.password(), Keys.TAB).perform();
            assertThat(login.focusedTestId()).isEqualTo(LoginPage.TOGGLE);
            login.waitUntilSubmittable();
            keyboard.sendKeys(Keys.TAB).perform();
            assertThat(login.focusedTestId()).isEqualTo(LoginPage.SUBMIT);
            keyboard.sendKeys(Keys.ENTER).perform();
        });

        new ProfilePage(driver).waitUntilLoaded();
    }

    @Test
    void theLoginPageHasNoSeriousAccessibilityViolation(WebDriver driver) {
        LoginPage login = new LoginPage(driver).open();
        Accessibility.assertNoSeriousViolations(driver);

        Steps.step("Show the generic refusal", () -> {
            login.logIn(TestUsers.uniqueEmail(), "whatever-password");
            login.rejection();
        });
        Accessibility.assertNoSeriousViolations(driver);
    }
}
