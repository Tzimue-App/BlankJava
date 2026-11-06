package com.blankjava.account;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks credentials (docs/technical_analysis.md §5.1, §5.3). No password rule is applied here: a
 * password that could never be valid is just a mismatch, so the answer never reveals the policy.
 * An unknown e-mail is checked against a dummy hash of the same cost, so it takes about as long as
 * a wrong password and the response time does not tell whether the address is registered.
 */
@Service
public class LoginService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String dummyHash;

    public LoginService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, Clock clock) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        // Same encoder, so same algorithm and cost as a real hash; the password is thrown away
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * The account matching these credentials, or empty for any mismatch. The e-mail is normalised
     * as at registration, the password with NFKC. A hash made with a lower cost than the current
     * configuration is upgraded on the way (§5.1).
     */
    @Transactional
    public Optional<UserAccount> authenticate(String email, String password) {
        String normalizedPassword = PasswordPolicy.normalize(password);
        Optional<UserAccount> account = accounts.findByEmail(AccountRules.normalizeEmail(email));
        if (account.isEmpty()) {
            passwordEncoder.matches(normalizedPassword, dummyHash);
            return Optional.empty();
        }
        UserAccount user = account.get();
        if (!passwordEncoder.matches(normalizedPassword, user.getPasswordHash())) {
            return Optional.empty();
        }
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.changePasswordHash(
                    passwordEncoder.encode(normalizedPassword), clock.instant().truncatedTo(ChronoUnit.MICROS));
        }
        return account;
    }
}
