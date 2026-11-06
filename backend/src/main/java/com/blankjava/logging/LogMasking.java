package com.blankjava.logging;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Masks personal data before it reaches the logs (docs/technical_analysis.md §6.1): an e-mail
 * address keeps its first character and its domain, {@code jane@example.com} becomes {@code
 * j***@example.com}. Users are identified in logs by their id, never by their e-mail.
 */
public final class LogMasking {

    // Single character classes only: no repeated group, so no recursion in java.util.regex (a long
    // dotted domain overflowed the stack with "(?:\.label)+"). Callers pass bounded texts (contract:
    // message <= 1000, url <= 500 characters).
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9])[A-Za-z0-9._%+-]*@([A-Za-z0-9][A-Za-z0-9.-]*)");

    private LogMasking() {}

    /** Masks every e-mail address found in a free text (e.g. an error message). */
    public static @Nullable String maskEmails(@Nullable String text) {
        return text == null ? null : EMAIL.matcher(text).replaceAll("$1***@$2");
    }

    /**
     * Masks one value expected to be an e-mail address, keeping its first character and its domain
     * ({@code j***@example.com}). Anything else, which may be a password typed in the wrong field,
     * is fully masked ({@code ***}).
     */
    public static @Nullable String maskEmail(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        int at = trimmed.lastIndexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) {
            return "***";
        }
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, 1)) + "***" + trimmed.substring(at);
    }
}
