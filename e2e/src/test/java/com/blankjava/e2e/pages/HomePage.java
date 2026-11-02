package com.blankjava.e2e.pages;

import org.openqa.selenium.WebDriver;

/** The `/` route: the home feature placeholder inside the shell, with the version footer. */
public class HomePage extends BasePage {

    public HomePage(WebDriver driver) {
        super(driver);
    }

    public HomePage open() {
        open("/");
        return this;
    }

    public String heading() {
        return testId("home").findElement(org.openqa.selenium.By.tagName("h1")).getText();
    }

    public String footerText(String testId) {
        return testId(testId).getText();
    }

    public boolean footerShowsBackendVersion() {
        return hasTestId("version-backend");
    }
}
