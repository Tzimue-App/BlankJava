package com.blankjava.account;

import com.blankjava.api.model.FieldError;
import com.blankjava.api.model.FieldError.CodeEnum;
import com.blankjava.web.FieldErrors;
import java.text.Normalizer;
import java.util.Optional;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.stereotype.Component;

/**
 * Password rules (docs/technical_analysis.md §2.2, NIST SP 800-63B rev. 4): 15 to 64 Unicode code
 * points after NFKC normalisation, any character allowed, no composition rule, and not in the
 * compromised-password list. The contract only bounds the raw value (1–256 UTF-16 units); these
 * rules are enforced here.
 *
 * <p>The normalised form is also what gets hashed, so the same password typed on another keyboard
 * (full-width digits, ligatures…) matches. Login (#30) must normalise the same way.
 */
@Component
public class PasswordPolicy {

    static final int MIN_CODE_POINTS = 15;
    static final int MAX_CODE_POINTS = 64;
    static final String FIELD = "password";

    private final CompromisedPasswordChecker compromised;

    public PasswordPolicy(CompromisedPasswordChecker compromised) {
        this.compromised = compromised;
    }

    /** NFKC normal form: what is measured, checked and hashed. */
    public static String normalize(String password) {
        return Normalizer.normalize(password, Normalizer.Form.NFKC);
    }

    /**
     * The rule a normalised password breaks, if any: length first (the blocklist only matters for
     * passwords long enough to be accepted otherwise). Error messages never contain the password.
     */
    public Optional<FieldError> check(String normalized) {
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN_CODE_POINTS) {
            return Optional.of(FieldErrors.of(
                    FIELD, CodeEnum.TOO_SHORT, "The password must have at least " + MIN_CODE_POINTS + " characters."));
        }
        if (length > MAX_CODE_POINTS) {
            return Optional.of(FieldErrors.of(
                    FIELD, CodeEnum.TOO_LONG, "The password must have at most " + MAX_CODE_POINTS + " characters."));
        }
        if (compromised.check(normalized).isCompromised()) {
            return Optional.of(FieldErrors.of(
                    FIELD, CodeEnum.COMPROMISED_PASSWORD, "The password appears in known data breaches."));
        }
        return Optional.empty();
    }
}
