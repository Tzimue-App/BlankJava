package com.blankjava.logging;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

/**
 * Security events (registration, login, logout…) as structured log events of the {@code
 * SECURITY_AUDIT} logger (docs/technical_analysis.md §6.2), so they can be searched and counted:
 * {@code event.action}, {@code event.outcome}, {@code user.id}, {@code client.ip}. Never pass an
 * e-mail or a credential: users are identified by their id.
 */
@Component
public class SecurityAuditLog {

    static final String LOGGER_NAME = "SECURITY_AUDIT";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    public enum Outcome {
        SUCCESS("success"),
        FAILURE("failure");

        private final String value;

        Outcome(String value) {
            this.value = value;
        }
    }

    /**
     * Records one security event.
     *
     * @param action what happened, dotted and lower-case, e.g. {@code auth.login.failed}
     * @param outcome success or failure (failures are logged as warnings)
     * @param userId the user's id, or {@code null} when unknown (e.g. failed login)
     * @param clientIp the client address as seen by the backend behind the proxy
     */
    public void record(String action, Outcome outcome, @Nullable String userId, @Nullable String clientIp) {
        log.atLevel(outcome == Outcome.FAILURE ? Level.WARN : Level.INFO)
                .addKeyValue("event.category", "authentication")
                .addKeyValue("event.action", action)
                .addKeyValue("event.outcome", outcome.value)
                .addKeyValue("user.id", userId)
                .addKeyValue("client.ip", clientIp)
                .log("Security event {} ({})", action, outcome.value);
    }
}
