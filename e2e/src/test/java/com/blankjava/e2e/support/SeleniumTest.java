package com.blankjava.e2e.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Put on every journey test class: provides a {@code WebDriver} parameter and captures a screenshot
 * (plus the console log and page source on failure) after each test.
 *
 * <p>Order matters: JUnit runs {@code afterEach} callbacks in the <em>reverse</em> of their declared
 * order, so {@link DriverFactory} is declared first — its {@code afterEach} (quitting the browser)
 * then runs <em>last</em>, after {@link ReportingExtension} has captured the page.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith({DriverFactory.class, ReportingExtension.class})
public @interface SeleniumTest {}
