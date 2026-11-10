package com.blankjava.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signing and verification of the access tokens (docs/technical_analysis.md §5.2): RS256 only,
 * keys from {@link JwtKeySet}, and every token checked for its signature, {@code iss}, {@code aud},
 * {@code sub} and expiry.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtKeySet jwtKeySet(SecurityProperties properties) {
        SecurityProperties.Jwt jwt = properties.jwt();
        if (jwt.ephemeralKey()) {
            log.warn("JWT signing key generated at startup (blankjava.security.jwt.ephemeral-key):"
                    + " sessions end when the backend restarts. Never use this outside tests and dev.");
            return JwtKeySet.generate();
        }
        String privateKeyPath = jwt.privateKeyPath();
        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            throw new InvalidJwtKeyException("No JWT signing key configured (JWT_PRIVATE_KEY_PATH is empty).");
        }
        JwtKeySet keys = JwtKeySet.load(
                Path.of(privateKeyPath),
                jwt.previousPublicKeyPaths().stream().map(Path::of).toList());
        log.info("JWT keys loaded: active kid={}, accepted kids={}", keys.activeKeyId(), keys.verificationKeyIds());
        return keys;
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeySet keys) {
        return new NimbusJwtEncoder(keys.signingSource());
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeySet keys, SecurityProperties properties, Clock clock) {
        return decoder(keys, properties.jwt(), clock);
    }

    /** The decoder of {@link #jwtDecoder}, without Spring (unit tests). */
    static NimbusJwtDecoder decoder(JwtKeySet keys, SecurityProperties.Jwt jwt, Clock clock) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        // Only RS256 with one of our keys: "none", HS256 and other algorithms are rejected here
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys.verificationSource()));
        // Claims are checked by the Spring validators below, not by Nimbus
        processor.setJWTClaimsSetVerifier((claims, context) -> {});

        JwtTimestampValidator timestamps = new JwtTimestampValidator(jwt.clockSkew());
        timestamps.setClock(clock);
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                // Spring's default validator checks the timestamps only, not iss nor aud
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
                timestamps,
                new JwtIssuerValidator(jwt.issuer()),
                new JwtClaimValidator<List<String>>(
                        JwtClaimNames.AUD, audience -> audience != null && audience.contains(jwt.audience())),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, subject -> subject != null && !subject.isBlank())));
        return decoder;
    }
}
