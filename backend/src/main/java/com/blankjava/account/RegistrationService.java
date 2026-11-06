package com.blankjava.account;

import com.blankjava.api.model.FieldError;
import com.blankjava.web.InvalidFieldsException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates accounts (docs/technical_analysis.md §2.2, §5.1): normalises the e-mail, display name and
 * password, checks every rule (all broken rules are reported at once), then stores the account
 * with the Argon2id hash of the normalised password.
 */
@Service
public class RegistrationService {

    /** Unique constraint on {@code app_user.email} (V2__accounts.sql). */
    static final String EMAIL_CONSTRAINT = "app_user_email_uk";

    private final UserAccountRepository accounts;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RegistrationService(
            UserAccountRepository accounts,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.accounts = accounts;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Creates the account.
     *
     * @throws InvalidFieldsException a field breaks a rule ({@code 400})
     * @throws EmailTakenException the normalised e-mail is already registered ({@code 409}), also
     *     when a concurrent registration of the same address wins the race to the unique index
     */
    @Transactional
    public UserAccount register(String email, String displayName, String password) {
        String normalizedEmail = AccountRules.normalizeEmail(email);
        String normalizedName = AccountRules.normalizeDisplayName(displayName);
        String normalizedPassword = PasswordPolicy.normalize(password);

        List<FieldError> errors = Stream.of(
                        AccountRules.checkEmail(normalizedEmail),
                        AccountRules.checkDisplayName(normalizedName),
                        passwordPolicy.check(normalizedPassword))
                .flatMap(Optional::stream)
                .toList();
        if (!errors.isEmpty()) {
            throw new InvalidFieldsException(errors);
        }
        // Spares the cost of a hash for the common case; the unique index settles the races
        if (accounts.existsByEmail(normalizedEmail)) {
            throw new EmailTakenException();
        }

        UserAccount account = new UserAccount(
                normalizedEmail,
                normalizedName,
                passwordEncoder.encode(normalizedPassword),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        try {
            return accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            if (isEmailConstraint(e)) {
                // Not chained: the database message quotes the e-mail
                throw new EmailTakenException();
            }
            throw e;
        }
    }

    private static boolean isEmailConstraint(Throwable exception) {
        for (@Nullable Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return EMAIL_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
