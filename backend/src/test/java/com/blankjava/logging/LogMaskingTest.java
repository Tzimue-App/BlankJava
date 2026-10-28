package com.blankjava.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class LogMaskingTest {

    @Test
    void masksEveryEmailAddressInAText() {
        assertThat(LogMasking.maskEmails("Login failed for jane.doe+test@example.com and bob@mail.example.org"))
                .isEqualTo("Login failed for j***@example.com and b***@mail.example.org");
    }

    @Test
    void leavesTextWithoutEmailUnchanged() {
        assertThat(LogMasking.maskEmails("Cannot read properties of undefined (reading 'name')"))
                .isEqualTo("Cannot read properties of undefined (reading 'name')");
        assertThat(LogMasking.maskEmails("@angular/core at user@")).isEqualTo("@angular/core at user@");
    }

    @Test
    void acceptsNull() {
        assertThat(LogMasking.maskEmails(null)).isNull();
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void staysFastOnTextsWithoutAtSign() {
        assertThat(LogMasking.maskEmails("a.".repeat(5_000))).isEqualTo("a.".repeat(5_000));
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void staysFastOnPathologicalInput() {
        String input = "a".repeat(50_000) + "@" + "b.".repeat(20_000);

        assertThat(LogMasking.maskEmails(input)).endsWith(".");
    }
}
