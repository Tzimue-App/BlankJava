package com.blankjava.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code blankjava.security.refresh-token.*} (docs/technical_analysis.md §4.3, §5.2).
 *
 * @param lifetime how long a refresh token, and its cookie, stays valid
 * @param purgeAfter how long an expired or revoked token is kept before the purge deletes it
 * @param purgeCron when the purge runs (Spring cron, server time zone)
 */
@ConfigurationProperties("blankjava.security.refresh-token")
public record RefreshTokenProperties(
        @DefaultValue("7d") Duration lifetime,
        @DefaultValue("7d") Duration purgeAfter,
        @DefaultValue("0 30 3 * * *") String purgeCron) {}
