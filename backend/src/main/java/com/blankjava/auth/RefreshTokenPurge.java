package com.blankjava.auth;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly purge of the refresh tokens expired or revoked for more than {@code
 * blankjava.security.refresh-token.purge-after} (7 days), so the table does not grow without end
 * (docs/technical_analysis.md §4.3). ShedLock makes it run once when several backend instances run.
 */
@Component
public class RefreshTokenPurge {

    static final String LOCK = "refresh-token-purge";

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenPurge.class);

    private final RefreshTokenService refreshTokens;

    public RefreshTokenPurge(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Scheduled(cron = "${blankjava.security.refresh-token.purge-cron:0 30 3 * * *}")
    @SchedulerLock(name = LOCK, lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void purge() {
        int deleted = refreshTokens.purge();
        log.atInfo()
                .addKeyValue("event.action", "refresh_token.purged")
                .addKeyValue("refresh_token.deleted", deleted)
                .log("Purged {} obsolete refresh tokens", deleted);
    }
}
