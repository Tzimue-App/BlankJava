package com.blankjava.security;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code blankjava.security.*}: allowed origins and access-token settings (application.yaml,
 * docs/technical_analysis.md §5.2).
 *
 * @param allowedOrigins origins accepted on the cookie endpoints ({@code refresh}, {@code logout})
 * @param jwt access-token settings
 */
@ConfigurationProperties("blankjava.security")
public record SecurityProperties(List<String> allowedOrigins, Jwt jwt) {

    public SecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    /**
     * Access-token settings ({@code blankjava.security.jwt.*}).
     *
     * @param issuer {@code iss} claim, checked on every token
     * @param audience {@code aud} claim, checked on every token
     * @param accessTokenLifetime lifetime of an access token
     * @param clockSkew tolerance on {@code exp} and {@code nbf}
     * @param privateKeyPath PKCS#8 PEM file of the active signing key
     * @param previousPublicKeyPaths PEM files of public keys still accepted during a rotation
     * @param ephemeralKey generate a key at startup instead of reading one (tests, dev profile)
     */
    public record Jwt(
            String issuer,
            String audience,
            Duration accessTokenLifetime,
            Duration clockSkew,
            @Nullable String privateKeyPath,
            List<String> previousPublicKeyPaths,
            boolean ephemeralKey) {

        public Jwt {
            previousPublicKeyPaths = previousPublicKeyPaths == null
                    ? List.of()
                    : previousPublicKeyPaths.stream()
                            .filter(path -> !path.isBlank())
                            .toList();
        }
    }
}
