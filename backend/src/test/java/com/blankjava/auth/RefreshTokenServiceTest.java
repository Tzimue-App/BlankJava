package com.blankjava.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blankjava.auth.RefreshTokenService.IssuedRefreshToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final UUID USER = UUID.fromString("7b2f4c1e-9a8d-4b6e-8f3a-2d1c0b9e8a7f");

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final RefreshTokenService service = new RefreshTokenService(
            repository,
            new RefreshTokenProperties(Duration.ofDays(7), Duration.ofDays(7), "0 30 3 * * *"),
            Clock.fixed(NOW, ZoneOffset.UTC));

    {
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aTokenIsA256BitRandomValueStoredOnlyAsItsSha256() throws Exception {
        IssuedRefreshToken token = service.issueNewSession(USER);

        assertThat(Base64.getUrlDecoder().decode(token.value())).hasSize(32);
        RefreshToken stored = saved().getFirst();
        String sha256 = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(token.value().getBytes(StandardCharsets.US_ASCII)));
        assertThat(stored.getTokenHash()).isEqualTo(sha256).hasSize(64).doesNotContain(token.value());
        assertThat(stored.getUserId()).isEqualTo(USER);
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(stored.getRevokedAt()).isNull();
        assertThat(stored.getReplacedBy()).isNull();
    }

    @Test
    void everyNewSessionHasItsOwnValueAndFamily() {
        IssuedRefreshToken first = service.issueNewSession(USER);
        IssuedRefreshToken second = service.issueNewSession(USER);

        assertThat(first.value()).isNotEqualTo(second.value());
        List<RefreshToken> stored = saved(2);
        assertThat(stored.get(0).getFamilyId()).isNotEqualTo(stored.get(1).getFamilyId());
    }

    @Test
    void theCookieHasTheContractAttributes() {
        IssuedRefreshToken token = new IssuedRefreshToken("abc", Duration.ofDays(7));

        assertThat(RefreshTokenService.cookie(token).toString())
                .startsWith("refresh_token=abc; Path=/api/auth; Max-Age=604800; Expires=")
                .endsWith("; Secure; HttpOnly; SameSite=Strict");
    }

    @Test
    void toStringHidesTheValue() {
        IssuedRefreshToken token = service.issueNewSession(USER);

        assertThat(token.toString()).doesNotContain(token.value());
        assertThat(saved().getFirst().toString())
                .doesNotContain(saved().getFirst().getTokenHash());
    }

    private List<RefreshToken> saved() {
        return saved(1);
    }

    private List<RefreshToken> saved(int times) {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository, times(times)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void rotationRevokesTheTokenPointsToItsReplacementAndKeepsTheFamily() {
        RefreshToken current = stored(NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("old")))
                .thenReturn(Optional.of(current));

        RefreshTokenService.Rotation rotation = service.rotate("old").orElseThrow();

        assertThat(rotation.userId()).isEqualTo(USER);
        RefreshToken next = saved().getFirst();
        assertThat(next.getFamilyId()).isEqualTo(current.getFamilyId());
        assertThat(next.getTokenHash())
                .isEqualTo(RefreshTokenService.hash(rotation.token().value()));
        assertThat(next.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        assertThat(current.getReplacedBy()).isEqualTo(next.getId());
    }

    @Test
    void anUnknownExpiredOrRevokedTokenIsNotRotated() {
        RefreshToken expired = stored(NOW);
        RefreshToken revoked = stored(NOW.plus(Duration.ofDays(1)));
        revoked.revoke(NOW.minusSeconds(1), null);
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("expired")))
                .thenReturn(Optional.of(expired));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("revoked")))
                .thenReturn(Optional.of(revoked));

        assertThat(service.rotate("unknown")).isEmpty();
        assertThat(service.rotate("expired")).isEmpty();
        assertThat(service.rotate("revoked")).isEmpty();
        verify(repository, never()).save(any(RefreshToken.class));
        assertThat(expired.getRevokedAt()).isNull();
    }

    @Test
    void revokingReturnsTheOwnerOnceThenNothing() {
        RefreshToken current = stored(NOW.plus(Duration.ofDays(1)));
        when(repository.findByTokenHashForUpdate(RefreshTokenService.hash("value")))
                .thenReturn(Optional.of(current));

        assertThat(service.revoke("value")).contains(USER);
        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        assertThat(current.getReplacedBy()).isNull();
        assertThat(service.revoke("value")).isEmpty();
    }

    @Test
    void thePurgeCutoffIsTheRetentionBeforeNow() {
        when(repository.deleteObsolete(any())).thenReturn(3);

        assertThat(service.purge()).isEqualTo(3);
        verify(repository).deleteObsolete(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void theClearingCookieHasTheSameAttributesAndMaxAgeZero() {
        assertThat(RefreshTokenService.clearCookie().toString())
                .startsWith("refresh_token=; Path=/api/auth; Max-Age=0; Expires=")
                .endsWith("; Secure; HttpOnly; SameSite=Strict");
    }

    private static RefreshToken stored(java.time.Instant expiresAt) {
        return new RefreshToken(USER, UUID.randomUUID(), "hash", NOW.minus(Duration.ofDays(1)), expiresAt);
    }
}
