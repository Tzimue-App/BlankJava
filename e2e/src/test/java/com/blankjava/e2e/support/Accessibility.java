package com.blankjava.e2e.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.selenium.AxeBuilder;
import io.qameta.allure.Allure;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Accessibility scan of the current page with axe-core (docs/technical_analysis.md §2.1): WCAG 2.1
 * A and AA rules. The full list of violations is attached to the report; the test fails on any
 * {@code serious} or {@code critical} one.
 */
public final class Accessibility {

    private static final List<String> TAGS = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa");
    private static final Set<String> BLOCKING = Set.of("serious", "critical");

    private Accessibility() {}

    public static void assertNoSeriousViolations(WebDriver driver) {
        Steps.step("Scan the page with axe-core", () -> {
            waitForAnimations(driver);
            Results results = new AxeBuilder().withTags(TAGS).analyze(driver);
            List<Rule> violations = results.getViolations();
            if (!violations.isEmpty()) {
                Allure.addAttachment("axe-core violations", "text/plain", describe(violations));
            }
            List<Rule> blocking = violations.stream()
                    .filter(rule -> BLOCKING.contains(rule.getImpact()))
                    .toList();
            assertThat(blocking)
                    .as("serious or critical accessibility violations:%n%s", describe(blocking))
                    .isEmpty();
        });
    }

    /**
     * Waits until no CSS animation or transition runs. Material fades error messages in: scanned
     * mid-fade, their colour is half-way to the background and fails the contrast rule.
     */
    private static void waitForAnimations(WebDriver driver) {
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d)
                        .executeScript("return document.getAnimations().every(a => a.playState !== 'running');")));
    }

    private static String describe(List<Rule> rules) {
        return rules.stream()
                .map(rule -> "- [" + rule.getImpact() + "] " + rule.getId() + ": " + rule.getHelp() + " ("
                        + rule.getNodes().size() + " element(s): "
                        + rule.getNodes().stream()
                                .map(node -> String.valueOf(node.getTarget()))
                                .limit(5)
                                .collect(Collectors.joining(", "))
                        + ")")
                .collect(Collectors.joining("\n"));
    }
}
