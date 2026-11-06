package com.blankjava.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues access tokens (docs/technical_analysis.md §5.2): RS256 JWT signed with the active key,
 * {@code kid} in the header, claims {@code iss}, {@code aud}, {@code sub} (the user id, never the
 * e-mail), {@code iat}, {@code exp}, {@code jti}.
 */
@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final JwtKeySet keys;
    private final SecurityProperties.Jwt settings;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, JwtKeySet keys, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.keys = keys;
        this.settings = properties.jwt();
        this.clock = clock;
    }

    public AccessToken issue(UUID userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .audience(List.of(settings.audience()))
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(settings.accessTokenLifetime()))
                .id(UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keys.activeKeyId())
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, settings.accessTokenLifetime());
    }

    /**
     * A signed access token and its lifetime.
     *
     * @param value the compact JWT, sent as {@code Authorization: Bearer <value>}
     * @param lifetime how long it is valid from now
     */
    public record AccessToken(String value, Duration lifetime) {

        /** {@code expiresIn} of the contract's {@code SessionResponse}. */
        public int expiresInSeconds() {
            return Math.toIntExact(lifetime.toSeconds());
        }

        @Override
        public String toString() {
            // Never print the token itself (logs, assertion messages)
            return "AccessToken[lifetime=" + lifetime + "]";
        }
    }
}
