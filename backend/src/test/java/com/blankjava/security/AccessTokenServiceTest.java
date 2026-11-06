package com.blankjava.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Issued tokens, and every kind of token the decoder must refuse (#37 acceptance criteria). */
class AccessTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");
    private static final SecurityProperties.Jwt SETTINGS = new SecurityProperties.Jwt(
            "blankjava-backend",
            "blankjava-web",
            Duration.ofMinutes(10),
            Duration.ofSeconds(30),
            null,
            List.of(),
            false);
    private static final UUID USER = UUID.fromString("7b2f4c1e-9a8d-4b6e-8f3a-2d1c0b9e8a7f");

    private final JwtKeySet keys = JwtKeySet.of(TestKeys.privateKey(TestKeys.ACTIVE), List.of());

    @Test
    void anIssuedTokenCarriesTheClaimsOfTheSpecification() {
        AccessTokenService.AccessToken token = service(keys, at(NOW)).issue(USER);

        Jwt jwt = decoder(keys, at(NOW.plusSeconds(60))).decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo(USER.toString());
        // iss is a plain string (StringOrURI), not a URL: read it as a string
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("blankjava-backend");
        assertThat(jwt.getAudience()).containsExactly("blankjava-web");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", keys.activeKeyId());
        assertThat(token.expiresInSeconds()).isEqualTo(600);
    }

    @Test
    void theTokenNeverCarriesAnEmailAndItsToStringHidesIt() {
        AccessTokenService.AccessToken token = service(keys, at(NOW)).issue(USER);

        assertThat(token.toString()).doesNotContain(token.value()).contains("PT10M");
        assertThat(decoder(keys, at(NOW)).decode(token.value()).getClaims())
                .containsOnlyKeys("iss", "aud", "sub", "iat", "exp", "jti");
    }

    @Test
    void anExpiredTokenIsRefusedOnceTheClockSkewIsOver() {
        String token = service(keys, at(NOW)).issue(USER).value();

        assertThat(decoder(keys, at(NOW.plus(Duration.ofMinutes(10)).plusSeconds(29)))
                        .decode(token))
                .isNotNull();
        assertThatThrownBy(() -> decoder(
                                keys, at(NOW.plus(Duration.ofMinutes(10)).plusSeconds(31)))
                        .decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenSignedByAnotherKeyIsRefused() {
        JwtKeySet other = JwtKeySet.of(TestKeys.privateKey(TestKeys.OTHER), List.of());
        String token = service(other, at(NOW)).issue(USER).value();

        assertThatThrownBy(() -> decoder(keys, at(NOW)).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void anUnsignedTokenIsRefused() {
        String header = Base64URL.encode("{\"alg\":\"none\"}").toString();
        String payload = Base64URL.encode(
                        claims("blankjava-backend", "blankjava-web").toString())
                .toString();

        assertThatThrownBy(() -> decoder(keys, at(NOW)).decode(header + "." + payload + "."))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void anHs256TokenUsingThePublicKeyAsSecretIsRefused() throws Exception {
        // Algorithm confusion: an attacker knows the public key and signs with it as an HMAC secret
        byte[] secret = new String(keys.activePublicKey().getEncoded(), StandardCharsets.ISO_8859_1)
                .getBytes(StandardCharsets.ISO_8859_1);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS256)
                        .keyID(keys.activeKeyId())
                        .build(),
                claims("blankjava-backend", "blankjava-web"));
        jwt.sign(new MACSigner(secret));

        assertThatThrownBy(() -> decoder(keys, at(NOW)).decode(jwt.serialize())).isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenOfAnotherIssuerIsRefused() {
        String token = signed(keys, "someone-else", "blankjava-web");

        assertThatThrownBy(() -> decoder(keys, at(NOW)).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenForAnotherAudienceIsRefused() {
        String token = signed(keys, "blankjava-backend", "another-service");

        assertThatThrownBy(() -> decoder(keys, at(NOW)).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenWithAModifiedPayloadIsRefused() {
        String[] parts = service(keys, at(NOW)).issue(USER).value().split("\\.", -1);
        String forged = Base64URL.encode(
                        claims("blankjava-backend", "blankjava-web").toString())
                .toString();

        assertThatThrownBy(() -> decoder(keys, at(NOW)).decode(parts[0] + "." + forged + "." + parts[2]))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenOfThePreviousKeyIsAcceptedWhileItsPublicKeyIsInTheSetAndRefusedAfter() {
        JwtKeySet previous = JwtKeySet.of(TestKeys.privateKey(TestKeys.PREVIOUS), List.of());
        String token = service(previous, at(NOW)).issue(USER).value();
        JwtKeySet duringRotation =
                JwtKeySet.of(TestKeys.privateKey(TestKeys.ACTIVE), List.of(TestKeys.publicKey(TestKeys.PREVIOUS)));

        assertThat(decoder(duringRotation, at(NOW)).decode(token).getSubject()).isEqualTo(USER.toString());
        assertThatThrownBy(() -> decoder(keys, at(NOW)).decode(token)).isInstanceOf(JwtException.class);
    }

    private static AccessTokenService service(JwtKeySet keys, Clock clock) {
        return new AccessTokenService(
                new NimbusJwtEncoder(keys.signingSource()), keys, new SecurityProperties(List.of(), SETTINGS), clock);
    }

    private static JwtDecoder decoder(JwtKeySet keys, Clock clock) {
        return JwtConfig.decoder(keys, SETTINGS, clock);
    }

    /** A token signed with a real key but with chosen {@code iss} and {@code aud}. */
    private static String signed(JwtKeySet keys, String issuer, String audience) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(USER.toString())
                .issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(600))
                .build();
        return new NimbusJwtEncoder(keys.signingSource())
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.RS256)
                                .keyId(keys.activeKeyId())
                                .build(),
                        claims))
                .getTokenValue();
    }

    private static JWTClaimsSet claims(String issuer, String audience) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(600)))
                .build();
    }

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
