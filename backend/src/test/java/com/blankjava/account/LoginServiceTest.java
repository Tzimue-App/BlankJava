package com.blankjava.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class LoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final String PASSWORD = "tangerine-river-boat";

    private final PasswordEncoder encoder = encoder(1024, 1);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final UserAccount jane =
            new UserAccount("jane@example.com", "Jane", encoder.encode(PASSWORD), NOW.minusSeconds(3600));

    {
        when(accounts.findByEmail(anyString())).thenReturn(Optional.empty());
        when(accounts.findByEmail("jane@example.com")).thenReturn(Optional.of(jane));
    }

    private final LoginService service = new LoginService(accounts, encoder, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void theRightPasswordMatchesWhateverTheCaseAndSpacesOfTheEmail() {
        assertThat(service.authenticate(" Jane@Example.COM ", PASSWORD)).contains(jane);
    }

    @Test
    void thePasswordIsNormalisedLikeAtRegistration() {
        // Full-width forms typed on another keyboard: NFKC gives the registered password back
        String fullWidth = PASSWORD.chars()
                .map(c -> c == '-' ? '－' : c - 'a' + 'ａ')
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();

        assertThat(service.authenticate("jane@example.com", fullWidth)).contains(jane);
    }

    @Test
    void aWrongShortOrUnknownCredentialIsJustAMismatch() {
        assertThat(service.authenticate("jane@example.com", "tangerine-river-boaT"))
                .isEmpty();
        assertThat(service.authenticate("jane@example.com", "abc")).isEmpty();
        assertThat(service.authenticate("nobody@example.com", PASSWORD)).isEmpty();
        assertThat(service.authenticate("not an e-mail", PASSWORD)).isEmpty();
    }

    @Test
    void aHashOfALowerCostIsUpgradedAtLogin() {
        String oldHash = jane.getPasswordHash();
        LoginService stronger =
                new LoginService(accounts, encoder(2048, 2), Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));

        assertThat(stronger.authenticate("jane@example.com", PASSWORD)).contains(jane);

        assertThat(jane.getPasswordHash()).isNotEqualTo(oldHash).contains("m=2048,t=2,p=1");
        assertThat(jane.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(encoder.matches(PASSWORD, jane.getPasswordHash())).isTrue();
    }

    @Test
    void aHashOfTheCurrentCostIsLeftAlone() {
        String hash = jane.getPasswordHash();

        service.authenticate("jane@example.com", PASSWORD);

        assertThat(jane.getPasswordHash()).isEqualTo(hash);
    }

    private static PasswordEncoder encoder(int memoryKib, int iterations) {
        return new DelegatingPasswordEncoder(
                "argon2", Map.of("argon2", new Argon2PasswordEncoder(16, 32, 1, memoryKib, iterations)));
    }
}
