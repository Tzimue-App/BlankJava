package com.blankjava.e2e.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/** What every page shares: the header (with *Log out* while logged in) and the version footer. */
public class AppFrame extends BasePage {

    public static final String LOGOUT = "header-logout";

    public AppFrame(WebDriver driver) {
        super(driver);
    }

    /** Opens {@code path} of the application (a full page load). */
    public AppFrame load(String path) {
        open(path);
        return this;
    }

    public boolean showsLogout() {
        return hasTestId(LOGOUT) && testId(LOGOUT).isDisplayed();
    }

    public boolean hidesLogout() {
        return lacksTestId(LOGOUT);
    }

    public void logOut() {
        testId(LOGOUT).click();
    }

    /** Waits for the not-found page (an unknown path). */
    public AppFrame waitForNotFound() {
        testId("not-found");
        return this;
    }

    /** Clicks the not-found page's link back to {@code /}: an in-app navigation, no page load. */
    public void followNotFoundHomeLink() {
        testId("not-found").findElement(By.tagName("a")).click();
    }

    public String footerText(String testId) {
        return testId(testId).getText();
    }

    public boolean footerShowsBackendVersion() {
        return hasTestId("version-backend");
    }
}
