package com.blankjava.account;

import com.blankjava.api.model.FieldError;
import com.blankjava.api.model.FieldError.CodeEnum;
import com.blankjava.web.FieldErrors;
import java.util.Locale;
import java.util.Optional;

/**
 * Normalisation and rules of the e-mail and the display name (docs/technical_analysis.md §2.2).
 * Both are checked on the normalised value, after the contract's bounds on the raw value; lengths
 * are counted in Unicode code points, as PostgreSQL's {@code varchar(n)} does.
 */
final class AccountRules {

    static final String EMAIL = "email";
    static final String DISPLAY_NAME = "displayName";

    static final int EMAIL_MAX = 254;
    static final int DISPLAY_NAME_MIN = 2;
    static final int DISPLAY_NAME_MAX = 50;

    private static final int LOCAL_PART_MAX = 64;
    private static final int LABEL_MAX = 63;
    private static final String LOCAL_PART_SYMBOLS = "!#$%&'*+/=?^_`{|}~.-";

    private AccountRules() {}

    /** Trimmed (any Unicode white space) and lower-cased independently of the server's locale. */
    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /** Trimmed (any Unicode white space). */
    static String normalizeDisplayName(String displayName) {
        return displayName.strip();
    }

    static Optional<FieldError> checkEmail(String normalized) {
        if (normalized.isEmpty()) {
            return error(EMAIL, CodeEnum.REQUIRED, "The e-mail address is required.");
        }
        if (codePoints(normalized) > EMAIL_MAX) {
            return error(
                    EMAIL, CodeEnum.TOO_LONG, "The e-mail address must have at most " + EMAIL_MAX + " characters.");
        }
        if (!isEmailAddress(normalized)) {
            return error(EMAIL, CodeEnum.INVALID_FORMAT, "The e-mail address is not valid.");
        }
        return Optional.empty();
    }

    static Optional<FieldError> checkDisplayName(String normalized) {
        int length = codePoints(normalized);
        if (length == 0) {
            return error(DISPLAY_NAME, CodeEnum.REQUIRED, "The display name is required.");
        }
        if (length < DISPLAY_NAME_MIN || length > DISPLAY_NAME_MAX) {
            return error(
                    DISPLAY_NAME,
                    length < DISPLAY_NAME_MIN ? CodeEnum.TOO_SHORT : CodeEnum.TOO_LONG,
                    "The display name must have " + DISPLAY_NAME_MIN + " to " + DISPLAY_NAME_MAX + " characters.");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            return error(DISPLAY_NAME, CodeEnum.INVALID_FORMAT, "The display name contains a control character.");
        }
        return Optional.empty();
    }

    /**
     * {@code local@domain} without quoting or comments: a dot-atom local part of at most 64
     * characters, and a domain of at least two labels (letters, digits, hyphens; Unicode letters
     * allowed) whose last label is not numeric. Written with plain loops, no backtracking regex.
     */
    static boolean isEmailAddress(String address) {
        int at = address.indexOf('@');
        if (at <= 0 || at != address.lastIndexOf('@') || at == address.length() - 1) {
            return false;
        }
        return isLocalPart(address.substring(0, at)) && isDomain(address.substring(at + 1));
    }

    private static boolean isLocalPart(String local) {
        if (codePoints(local) > LOCAL_PART_MAX
                || local.startsWith(".")
                || local.endsWith(".")
                || local.contains("..")) {
            return false;
        }
        return local.codePoints().allMatch(c -> Character.isLetterOrDigit(c) || LOCAL_PART_SYMBOLS.indexOf(c) >= 0);
    }

    private static boolean isDomain(String domain) {
        String[] labels = domain.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (String label : labels) {
            if (label.isEmpty()
                    || codePoints(label) > LABEL_MAX
                    || label.startsWith("-")
                    || label.endsWith("-")
                    || !label.codePoints().allMatch(c -> Character.isLetterOrDigit(c) || c == '-')) {
                return false;
            }
        }
        return !labels[labels.length - 1].codePoints().allMatch(Character::isDigit);
    }

    private static int codePoints(String text) {
        return text.codePointCount(0, text.length());
    }

    private static Optional<FieldError> error(String field, CodeEnum code, String message) {
        return Optional.of(FieldErrors.of(field, code, message));
    }
}
