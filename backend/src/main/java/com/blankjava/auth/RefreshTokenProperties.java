package com.blankjava.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code blankjava.security.refresh-token.*} (docs/technical_analysis.md §5.2).
 *
 * @param lifetime how long a refresh token, and its cookie, stays valid
 */
@ConfigurationProperties("blankjava.security.refresh-token")
public record RefreshTokenProperties(@DefaultValue("7d") Duration lifetime) {}
