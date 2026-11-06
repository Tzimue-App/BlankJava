package com.blankjava.account;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;

/**
 * Offline compromised-password check (docs/technical_analysis.md §5.1): the password, normalised
 * like {@link PasswordPolicy} does and lower-cased, is looked up in a bundled list of common
 * breached passwords ({@value #RESOURCE}, source and licence in the README next to it). Works in CI
 * and without network. Case-insensitive on purpose: {@code Iloveyou1234567} is as weak as {@code
 * iloveyou1234567}.
 */
public final class BlocklistPasswordChecker implements CompromisedPasswordChecker {

    static final String RESOURCE = "passwords/compromised-passwords.txt";

    private final Set<String> blocklist;

    BlocklistPasswordChecker(Set<String> passwords) {
        Set<String> keys = new HashSet<>();
        passwords.forEach(password -> keys.add(key(password)));
        this.blocklist = Set.copyOf(keys);
    }

    /** The checker over the bundled list; fails at startup if the resource is missing. */
    static BlocklistPasswordChecker fromClasspath() {
        try (InputStream input = BlocklistPasswordChecker.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Compromised-password list not found on the classpath: " + RESOURCE);
            }
            Set<String> passwords = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                reader.lines().filter(line -> !line.isEmpty()).forEach(passwords::add);
            }
            return new BlocklistPasswordChecker(passwords);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }

    int size() {
        return blocklist.size();
    }

    @Override
    public CompromisedPasswordDecision check(@Nullable String password) {
        return new CompromisedPasswordDecision(password != null && blocklist.contains(key(password)));
    }

    private static String key(String password) {
        return PasswordPolicy.normalize(password).toLowerCase(Locale.ROOT);
    }
}
