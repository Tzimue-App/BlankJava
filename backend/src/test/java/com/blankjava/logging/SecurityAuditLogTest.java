package com.blankjava.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.blankjava.logging.SecurityAuditLog.Outcome;
import org.junit.jupiter.api.Test;

class SecurityAuditLogTest {

    private final SecurityAuditLog audit = new SecurityAuditLog();

    @Test
    void successIsAnInfoEventWithStructuredFields() {
        try (CapturedLogs logs = new CapturedLogs(SecurityAuditLog.LOGGER_NAME)) {
            audit.record("user.registered", Outcome.SUCCESS, "8f14e45f-ceea-467f-a0e6-1b2c3d4e5f60", "172.30.0.1");

            ILoggingEvent event = logs.events().getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).isEqualTo("Security event user.registered (success)");
            assertThat(CapturedLogs.keyValues(event))
                    .containsEntry("event.category", "authentication")
                    .containsEntry("event.action", "user.registered")
                    .containsEntry("event.outcome", "success")
                    .containsEntry("user.id", "8f14e45f-ceea-467f-a0e6-1b2c3d4e5f60")
                    .containsEntry("client.ip", "172.30.0.1");
        }
    }

    @Test
    void failureIsAWarningAndAnUnknownUserIsOmitted() {
        try (CapturedLogs logs = new CapturedLogs(SecurityAuditLog.LOGGER_NAME)) {
            audit.record("auth.login.failed", Outcome.FAILURE, null, "172.30.0.1");

            ILoggingEvent event = logs.events().getFirst();
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(CapturedLogs.keyValues(event))
                    .containsEntry("event.outcome", "failure")
                    .doesNotContainKey("user.id");
        }
    }
}
