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
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh tokens (docs/technical_analysis.md §5.2): a random 256-bit opaque value sent only in the
 * {@code refresh_token} cookie; the database keeps its SHA-256 hash, the owner, a family id (the
 * chain of rotated tokens of one login) and the expiry.
 *
 * <p>Rotation is strict: a token is revoked as soon as it is used, and a second use is refused.
 * The frontend serialises refreshes across tabs with the Web Locks API (#32), so a browser never
 * presents the same cookie twice. Revoking the whole family on reuse comes with M2.
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
    private final Duration purgeAfter;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, RefreshTokenProperties properties, Clock clock) {
        this.repository = repository;
        this.lifetime = properties.lifetime();
        this.purgeAfter = properties.purgeAfter();
        this.clock = clock;
    }

    /** A cookie value and how long it is valid. {@link #toString()} hides the value. */
    public record IssuedRefreshToken(String value, Duration lifetime) {

        @Override
        public String toString() {
            return "IssuedRefreshToken[lifetime=" + lifetime + "]";
        }
    }

    /** The owner of a rotated token and the token that replaces it. */
    public record Rotation(UUID userId, IssuedRefreshToken token) {}

    /** Issues the first token of a new family: a new session (registration, login). */
    @Transactional
    public IssuedRefreshToken issueNewSession(UUID userId) {
        return issue(userId, UUID.randomUUID(), now()).issued();
    }

    /**
     * Exchanges an active token for a new one of the same family: the presented token is revoked
     * and points to its replacement. Empty when the token is unknown, expired or already revoked.
     * The row is locked, so two concurrent uses of one token cannot both succeed.
     */
    @Transactional
    public Optional<Rotation> rotate(String value) {
        Instant now = now();
        Optional<RefreshToken> found = repository.findByTokenHashForUpdate(hash(value));
        if (found.isEmpty() || !found.get().isActive(now)) {
            return Optional.empty();
        }
        RefreshToken current = found.get();
        Issued next = issue(current.getUserId(), current.getFamilyId(), now);
        current.revoke(now, next.stored().getId());
        return Optional.of(new Rotation(current.getUserId(), next.issued()));
    }

    /**
     * Revokes a token (logout). Returns its owner when it was active; empty when it was unknown,
     * expired or already revoked, which is not an error: logout is idempotent.
     */
    @Transactional
    public Optional<UUID> revoke(String value) {
        Instant now = now();
        return repository
                .findByTokenHashForUpdate(hash(value))
                .filter(token -> token.isActive(now))
                .map(token -> {
                    token.revoke(now, null);
                    return token.getUserId();
                });
    }

    /** Deletes the tokens expired or revoked for longer than the retention; returns how many. */
    @Transactional
    public int purge() {
        return repository.deleteObsolete(now().minus(purgeAfter));
    }

    private record Issued(RefreshToken stored, IssuedRefreshToken issued) {}

    private Issued issue(UUID userId, UUID familyId, Instant now) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken stored = repository.save(new RefreshToken(userId, familyId, hash(value), now, now.plus(lifetime)));
        return new Issued(stored, new IssuedRefreshToken(value, lifetime));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * The {@code Set-Cookie} for a token: {@code HttpOnly} (no script can read it), {@code Secure},
     * {@code SameSite=Strict} (never sent by a cross-site request) and scoped to {@code /api/auth}.
     */
    public static ResponseCookie cookie(IssuedRefreshToken token) {
        return base(token.value()).maxAge(token.lifetime()).build();
    }

    /** The {@code Set-Cookie} that deletes the cookie: same name, path and attributes, {@code Max-Age=0}. */
    public static ResponseCookie clearCookie() {
        return base("").maxAge(0).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .path(COOKIE_PATH)
                .secure(true)
                .httpOnly(true)
                .sameSite("Strict");
    }

    /** Lower-case hex SHA-256 of a cookie value, as stored in {@code refresh_token.token_hash}. */
    static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }
}
