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
        // Quadratic before #59: this length took far longer than the timeout with unbounded "*"
        String input = "a.".repeat(50_000);

        assertThat(LogMasking.maskEmails(input)).isEqualTo(input);
    }

    @Test
    void masksAddressesAtTheRfcLengthLimits() {
        String local = "j" + "x".repeat(63);
        String domain = "d" + "o".repeat(240) + ".example.com";

        assertThat(LogMasking.maskEmails("from " + local + "@" + domain + " end"))
                .isEqualTo("from j***@" + domain + " end");
    }

    @Test
    void stillMasksTheEndOfAnOverlongLocalPart() {
        String local = "abcdef" + "x".repeat(64);

        assertThat(LogMasking.maskEmails(local + "@example.com")).isEqualTo("abcdefx***@example.com");
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void staysFastOnPathologicalInput() {
        String input = "a".repeat(50_000) + "@" + "b.".repeat(20_000);

        assertThat(LogMasking.maskEmails(input)).endsWith(".");
    }

    @Test
    void masksOneEmailValueAndHidesAnythingElse() {
        assertThat(LogMasking.maskEmail(" Jane.Doe@Example.com ")).isEqualTo("J***@Example.com");
        // Not an address: could be a password typed in the e-mail field
        assertThat(LogMasking.maskEmail("hunter2-hunter2")).isEqualTo("***");
        assertThat(LogMasking.maskEmail("jane@")).isEqualTo("***");
        assertThat(LogMasking.maskEmail("@example.com")).isEqualTo("***");
        // The first code point is kept whole, even outside the BMP
        assertThat(LogMasking.maskEmail("😀x@example.com")).isEqualTo("😀***@example.com");
        assertThat(LogMasking.maskEmail(null)).isNull();
    }
}
