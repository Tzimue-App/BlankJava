package com.blankjava.e2e.support;

import io.qameta.allure.Allure;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 * Captures a PNG of the browser, attached to the Allure report and saved under
 * {@code build/screenshots/} (docs/technical_analysis.md §3.6, issue #7).
 */
public final class Screenshots {

    private static final Path DIRECTORY = Path.of("build", "screenshots");

    private Screenshots() {}

    /** Takes and attaches a screenshot labelled {@code label}; does nothing if the driver is gone. */
    public static void capture(WebDriver driver, String label) {
        if (!(driver instanceof TakesScreenshot screenshotTaker)) {
            return;
        }
        byte[] png = screenshotTaker.getScreenshotAs(OutputType.BYTES);
        Allure.addAttachment(label, "image/png", new ByteArrayInputStream(png), "png");
        writeToDisk(png, label);
    }

    private static void writeToDisk(byte[] png, String label) {
        try {
            Files.createDirectories(DIRECTORY);
            String safeLabel = label.replaceAll("[^A-Za-z0-9._-]", "_");
            Path file = DIRECTORY.resolve(safeLabel + "-" + Instant.now().toEpochMilli() + ".png");
            Files.write(file, png);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save screenshot to " + DIRECTORY, e);
        }
    }
}
