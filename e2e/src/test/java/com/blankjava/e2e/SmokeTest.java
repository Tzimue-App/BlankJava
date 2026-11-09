package com.blankjava.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.blankjava.e2e.pages.AppFrame;
import com.blankjava.e2e.pages.LoginPage;
import com.blankjava.e2e.support.SeleniumTest;
import com.blankjava.e2e.support.Steps;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

/**
 * First journey of the suite (issue #7): the stack is up, served over HTTPS, and the frontend can
 * reach the backend. Since M1, {@code /} leads an anonymous visitor to the login page (#35).
 */
@Tag("smoke")
@SeleniumTest
class SmokeTest {

    @Test
    void theAppLoadsOverHttpsAndTheFooterShowsTheBackendVersion(WebDriver driver) {
        AppFrame frame = new AppFrame(driver);
        LoginPage login = new LoginPage(driver);

        Steps.step("Open the application", () -> frame.load("/"));

        assertThat(driver.getCurrentUrl()).startsWith("https://");
        Steps.step("An anonymous visitor lands on the login page", () -> {
            login.waitForPath("/login");
            login.waitUntilShown();
        });

        Steps.step(
                "Wait for the footer to show the backend version",
                () -> assertThat(frame.footerShowsBackendVersion()).isTrue());
        assertThat(frame.footerText("version-backend")).matches("Backend \\d+\\.\\d+\\.\\d+");
        assertThat(frame.footerText("version-api")).matches("API \\d+\\.\\d+\\.\\d+");

        Steps.screenshot("Application loaded");
    }
}
