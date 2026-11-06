package com.blankjava.security;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Prints "APPLICATION FAILED TO START" with the key problem and how to fix it, instead of a stack
 * trace (registered in META-INF/spring.factories).
 */
public class InvalidJwtKeyFailureAnalyzer extends AbstractFailureAnalyzer<InvalidJwtKeyException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, InvalidJwtKeyException cause) {
        return new FailureAnalysis(
                cause.getMessage(),
                "Provide an RSA private key of at least " + JwtKeySet.MIN_KEY_BITS
                        + " bits in PKCS#8 PEM format and point JWT_PRIVATE_KEY_PATH to it"
                        + " (see infra/README.md, section 'JWT signing key').",
                cause);
    }
}
