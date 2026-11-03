package com.blankjava.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.blankjava.e2e.pages.HomePage;
import com.blankjava.e2e.support.SeleniumTest;
import com.blankjava.e2e.support.Steps;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

/**
 * First journey of the suite (issue #7): the stack is up, served over HTTPS, and the frontend can
 * reach the backend. Everything else in M1 builds on this passing.
 */
@Tag("smoke")
@SeleniumTest
class SmokeTest {

    @Test
    void theHomePageLoadsOverHttpsAndTheFooterShowsTheBackendVersion(WebDriver driver) {
        HomePage home = new HomePage(driver);

        Steps.step("Open the home page", () -> home.open());

        assertThat(driver.getCurrentUrl()).startsWith("https://");
        assertThat(home.heading()).isEqualTo("BlankJava");

        Steps.step(
                "Wait for the footer to show the backend version",
                () -> assertThat(home.footerShowsBackendVersion()).isTrue());
        assertThat(home.footerText("version-backend")).matches("Backend \\d+\\.\\d+\\.\\d+");
        assertThat(home.footerText("version-api")).matches("API \\d+\\.\\d+\\.\\d+");

        Steps.screenshot("Home page loaded");
    }
}
