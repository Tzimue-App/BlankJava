package com.blankjava.account;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code blankjava.security.password.*}: cost of the Argon2id hash (docs/technical_analysis.md
 * §5.1). The defaults are OWASP's minimum for Argon2id; raise them so one hash takes 100–300 ms on
 * the production host (backend/README.md, section <i>Passwords</i>). Tests use lighter values.
 *
 * @param argon2 Argon2id cost parameters
 */
@ConfigurationProperties("blankjava.security.password")
public record PasswordProperties(@DefaultValue Argon2 argon2) {

    /**
     * Argon2id cost.
     *
     * @param memoryKib memory per hash, in KiB (19 MiB)
     * @param iterations passes over the memory
     * @param parallelism lanes (threads) per hash
     */
    public record Argon2(
            @DefaultValue("19456") int memoryKib,
            @DefaultValue("2") int iterations,
            @DefaultValue("1") int parallelism) {}
}
