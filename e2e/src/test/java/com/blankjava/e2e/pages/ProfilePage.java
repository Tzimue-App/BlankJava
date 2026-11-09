package com.blankjava.e2e.pages;

import java.time.OffsetDateTime;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/** {@code /me}: the logged-in user's profile (#35). */
public class ProfilePage extends BasePage {

    public ProfilePage(WebDriver driver) {
        super(driver);
    }

    public ProfilePage open() {
        open("/me");
        return this;
    }

    /** Waits for {@code /me} with the profile loaded. */
    public ProfilePage waitUntilLoaded() {
        waitForPath("/me");
        testId("profile-display-name");
        return this;
    }

    public String displayName() {
        return testId("profile-display-name").getText().trim();
    }

    /** Whether the heading holds any element (an injected {@code <img>} for instance). */
    public boolean displayNameHasMarkup() {
        return !testId("profile-display-name").findElements(By.cssSelector("*")).isEmpty();
    }

    public String email() {
        return testId("profile-email").getText().trim();
    }

    public String memberSinceText() {
        return testId("profile-member-since").getText().trim();
    }

    /** The registration instant, from the {@code datetime} attribute of {@code <time>}. */
    public OffsetDateTime memberSince() {
        return OffsetDateTime.parse(testId("profile-member-since").getDomAttribute("datetime"));
    }
}
