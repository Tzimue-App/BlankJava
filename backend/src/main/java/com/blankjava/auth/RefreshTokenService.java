package com.blankjava.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues refresh tokens (docs/technical_analysis.md §5.2): a random 256-bit opaque value sent only
 * in the {@code refresh_token} cookie; the database keeps its SHA-256 hash, the owner, a family id
 * (the chain of rotated tokens of one login) and the expiry. Rotation and revocation come with
 * {@code refresh} and {@code logout} (#30).
 */
@Service
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenService {

    /** Cookie name, also the {@code refreshCookie} security scheme of api/openapi.yaml. */
    public static final String COOKIE_NAME = "refresh_token";

    /** The cookie is only sent to the session endpoints, never to the rest of the API. */
    static final String COOKIE_PATH = "/api/auth";

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final RefreshTokenRepository repository;
    private final Duration lifetime;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenProperties properties, Clock clock) {
        this.repository = repository;
        this.lifetime = properties.lifetime();
        this.clock = clock;
    }

    /** A cookie value and how long it is valid. {@link #toString()} hides the value. */
    public record IssuedRefreshToken(String value, Duration lifetime) {

        @Override
        public String toString() {
            return "IssuedRefreshToken[lifetime=" + lifetime + "]";
        }
    }

    /** Issues the first token of a new family: a new session (registration, login). */
    @Transactional
    public IssuedRefreshToken issueNewSession(UUID userId) {
        return issue(userId, UUID.randomUUID());
    }

    private IssuedRefreshToken issue(UUID userId, UUID familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        repository.save(new RefreshToken(userId, familyId, hash(value), now, now.plus(lifetime)));
        return new IssuedRefreshToken(value, lifetime);
    }

    /**
     * The {@code Set-Cookie} for a token: {@code HttpOnly} (no script can read it), {@code Secure},
     * {@code SameSite=Strict} (never sent by a cross-site request) and scoped to {@code /api/auth}.
     */
    public static ResponseCookie cookie(IssuedRefreshToken token) {
        return ResponseCookie.from(COOKIE_NAME, token.value())
                .maxAge(token.lifetime())
                .path(COOKIE_PATH)
                .secure(true)
                .httpOnly(true)
                .sameSite("Strict")
                .build();
    }

    /** Lower-case hex SHA-256 of a cookie value, as stored in {@code refresh_token.token_hash}. */
    static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }
}
