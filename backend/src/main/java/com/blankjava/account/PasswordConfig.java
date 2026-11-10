package com.blankjava.account;

import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Password hashing and the compromised-password check (docs/technical_analysis.md §5.1). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PasswordProperties.class)
public class PasswordConfig {

    /** Encoding id of new hashes: they are stored as {@code {argon2}$argon2id$v=19$m=…}. */
    static final String ARGON2 = "argon2";

    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    /**
     * Argon2id behind a {@link DelegatingPasswordEncoder}: the {@code {argon2}} prefix names the
     * algorithm of each stored hash, and the hash itself carries its cost, so both can be raised
     * later without invalidating existing passwords. No BCrypt: its 72-byte input limit is below
     * the 64 code points (up to 256 bytes) the policy accepts (§2.2).
     */
    @Bean
    PasswordEncoder passwordEncoder(PasswordProperties properties) {
        PasswordProperties.Argon2 cost = properties.argon2();
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(
                SALT_BYTES, HASH_BYTES, cost.parallelism(), cost.memoryKib(), cost.iterations());
        return new DelegatingPasswordEncoder(ARGON2, Map.of(ARGON2, argon2));
    }

    @Bean
    CompromisedPasswordChecker compromisedPasswordChecker() {
        return BlocklistPasswordChecker.fromClasspath();
    }
}
