package com.blankjava.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.blankjava.e2e.pages.AppFrame;
import com.blankjava.e2e.pages.LoginPage;
import com.blankjava.e2e.pages.ProfilePage;
import com.blankjava.e2e.support.SeleniumTest;
import com.blankjava.e2e.support.Steps;
import com.blankjava.e2e.support.TestUsers;
import com.blankjava.e2e.support.TestUsers.TestUser;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WindowType;

/**
 * The client session (#32) in a real browser: restored on reload, shared by tabs whose refreshes
 * are serialised by the Web Lock, ended everywhere by a logout, renewed when the access token is
 * refused. Journeys collected in #45.
 */
@Tag("accounts")
@SeleniumTest
class SessionJourneyTest {

    /**
     * Counts the session calls of the page, and makes the next {@code GET /api/users/me} carry a
     * token the backend refuses, as an expired one would be.
     */
    private static final String SPOIL_NEXT_PROFILE_TOKEN = """
            const original = window.fetch.bind(window);
            const counts = { refresh: 0, me: 0 };
            let spoil = true;
            window.__e2eCounts = counts;
            window.fetch = (input, init) => {
              const url = typeof input === 'string' ? input : input.url;
              if (url.includes('/api/auth/refresh')) counts.refresh++;
              if (url.includes('/api/users/me')) {
                counts.me++;
                if (spoil) {
                  spoil = false;
                  const headers = new Headers(init?.headers ?? {});
                  headers.set('Authorization', 'Bearer expired.token.value');
                  init = { ...init, headers };
                }
              }
              return original(input, init);
            };
            """;

    @Test
    void aReloadKeepsTheSession(WebDriver driver) {
        TestUser user = logIn(driver);

        Steps.step("Reload /me", driver.navigate()::refresh);

        ProfilePage profile = new ProfilePage(driver).waitUntilLoaded();
        assertThat(profile.email()).isEqualTo(user.email());
    }

    @Test
    void twoTabsReloadingTogetherBothStayLoggedIn(WebDriver driver) {
        TestUser user = logIn(driver);
        String first = driver.getWindowHandle();
        String second = Steps.supply("Open /me in a second tab", () -> {
            driver.switchTo().newWindow(WindowType.TAB);
            new ProfilePage(driver).open().waitUntilLoaded();
            return driver.getWindowHandle();
        });

        Steps.step("Reload both tabs at the same instant", () -> {
            // Both tabs schedule their reload for the same wall-clock time, 1.5 s from now: their
            // startup refreshes then race for the same refresh cookie
            long at = System.currentTimeMillis() + 1_500;
            for (String tab : new String[] {first, second}) {
                driver.switchTo().window(tab);
                js(driver).executeScript("setTimeout(() => location.reload(), arguments[0] - Date.now());", at);
            }
        });

        for (String tab : new String[] {first, second}) {
            driver.switchTo().window(tab);
            Steps.step("Tab " + (tab.equals(first) ? "1" : "2") + " is still logged in", () -> {
                ProfilePage profile = new ProfilePage(driver).waitUntilLoaded();
                assertThat(profile.email()).isEqualTo(user.email());
                Steps.screenshot("Tab " + (tab.equals(first) ? "1" : "2") + " after the reload");
            });
        }
    }

    @Test
    void loggingOutInOneTabEndsTheSessionOfTheOther(WebDriver driver) {
        logIn(driver);
        String first = driver.getWindowHandle();
        Steps.step("Open /me in a second tab", () -> {
            driver.switchTo().newWindow(WindowType.TAB);
            new ProfilePage(driver).open().waitUntilLoaded();
        });
        String second = driver.getWindowHandle();

        Steps.step("Log out in the first tab", () -> {
            driver.switchTo().window(first);
            new AppFrame(driver).logOut();
            new LoginPage(driver).waitForPath("/login");
        });

        Steps.step("Reload the second tab", () -> {
            driver.switchTo().window(second);
            driver.navigate().refresh();
        });
        new LoginPage(driver).waitForPath("/login?returnUrl=%2Fme");
        assertThat(new AppFrame(driver).hidesLogout()).isTrue();
    }

    @Test
    void aRefusedAccessTokenIsRenewedOnceAndTheRequestRetried(WebDriver driver) {
        TestUser user = logIn(driver);
        AppFrame frame = new AppFrame(driver);

        Steps.step("Open an unknown page (full load), then spoil the next profile request", () -> {
            frame.load("/nowhere");
            frame.waitForNotFound();
            js(driver).executeScript(SPOIL_NEXT_PROFILE_TOKEN);
        });
        Steps.step("Go back to the profile through the app", frame::followNotFoundHomeLink);

        ProfilePage profile = new ProfilePage(driver).waitUntilLoaded();
        assertThat(profile.email()).isEqualTo(user.email());
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) js(driver).executeScript("return window.__e2eCounts;");
        assertThat(counts)
                .as("one refused profile request, one refresh, one retry")
                .containsEntry("me", 2L)
                .containsEntry("refresh", 1L);
    }

    private static TestUser logIn(WebDriver driver) {
        TestUser user = TestUsers.registered();
        Steps.step("Log in", () -> new LoginPage(driver).open().logIn(user.email(), user.password()));
        new ProfilePage(driver).waitUntilLoaded();
        return user;
    }

    private static JavascriptExecutor js(WebDriver driver) {
        return (JavascriptExecutor) driver;
    }
}
