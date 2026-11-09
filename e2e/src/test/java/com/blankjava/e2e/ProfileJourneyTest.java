package com.blankjava.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blankjava.e2e.pages.AppFrame;
import com.blankjava.e2e.pages.LoginPage;
import com.blankjava.e2e.pages.ProfilePage;
import com.blankjava.e2e.pages.RegisterPage;
import com.blankjava.e2e.support.Accessibility;
import com.blankjava.e2e.support.SeleniumTest;
import com.blankjava.e2e.support.Steps;
import com.blankjava.e2e.support.TestUsers;
import com.blankjava.e2e.support.TestUsers.TestUser;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.WebDriver;

/** {@code /me} and the root redirect (#35), journeys collected in #45. */
@Tag("accounts")
@SeleniumTest
class ProfileJourneyTest {

    @Test
    void aNewUserSeesTheirProfile(WebDriver driver) {
        TestUser jane = TestUsers.fresh("Jane");
        String typedEmail = " " + jane.email().toUpperCase(Locale.ROOT) + " ";

        Steps.step("Register as Jane", () -> {
            RegisterPage register = new RegisterPage(driver).open();
            register.fill(typedEmail, "Jane", jane.password(), jane.password());
            register.submit();
        });

        ProfilePage profile = new ProfilePage(driver).waitUntilLoaded();
        assertThat(profile.displayName()).isEqualTo("Jane");
        assertThat(profile.email()).isEqualTo(jane.email());
        assertThat(profile.memberSinceText()).isNotBlank();
        // The browser and the test runner may not share a time zone near midnight: today ± 1 day
        LocalDate registered =
                profile.memberSince().withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        assertThat(registered).isBetween(today.minusDays(1), today.plusDays(1));
    }

    @Test
    void anAnonymousVisitorLogsInAndComesBackToTheProfile(WebDriver driver) {
        TestUser user = TestUsers.registered();
        LoginPage login = new LoginPage(driver);

        Steps.step("Open /me without a session", () -> new AppFrame(driver).load("/me"));
        login.waitForPath("/login?returnUrl=%2Fme");

        Steps.step("Log in", () -> login.waitUntilShown().logIn(user.email(), user.password()));

        assertThat(new ProfilePage(driver).waitUntilLoaded().email()).isEqualTo(user.email());
    }

    @Test
    void theRootLeadsToLoginOrToTheProfile(WebDriver driver) {
        TestUser user = TestUsers.registered();
        AppFrame frame = new AppFrame(driver);
        LoginPage login = new LoginPage(driver);

        Steps.step("Open / without a session", () -> frame.load("/"));
        login.waitForPath("/login");

        Steps.step("Log in, then open /", () -> {
            login.waitUntilShown().logIn(user.email(), user.password());
            new ProfilePage(driver).waitUntilLoaded();
            frame.load("/");
        });
        new ProfilePage(driver).waitUntilLoaded();
    }

    @Test
    void aDisplayNameThatLooksLikeHtmlIsShownAsText(WebDriver driver) {
        String hostile = "<img src=x onerror=alert(1)>";
        TestUser user = TestUsers.fresh(hostile);

        Steps.step("Register with an HTML display name", () -> {
            RegisterPage register = new RegisterPage(driver).open().fill(user);
            register.submit();
        });

        ProfilePage profile = new ProfilePage(driver).waitUntilLoaded();
        assertThat(profile.displayName()).isEqualTo(hostile);
        assertThat(profile.displayNameHasMarkup()).isFalse();
        // An alert would have blocked every command above; none is open
        assertThatThrownBy(() -> driver.switchTo().alert()).isInstanceOf(NoAlertPresentException.class);
    }

    @Test
    void theProfilePageHasNoSeriousAccessibilityViolation(WebDriver driver) {
        TestUser user = TestUsers.registered();
        Steps.step("Log in", () -> new LoginPage(driver).open().logIn(user.email(), user.password()));
        new ProfilePage(driver).waitUntilLoaded();

        Accessibility.assertNoSeriousViolations(driver);
    }
}
