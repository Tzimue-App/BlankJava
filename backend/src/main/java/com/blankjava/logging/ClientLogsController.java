package com.blankjava.logging;

import com.blankjava.api.SystemApi;
import com.blankjava.api.model.ClientLogEntry;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/client-logs}: writes the browser's log events into the backend logs, with {@code
 * event.dataset=frontend} and the trace id of the page's last request as {@code frontend.trace.id}
 * (docs/technical_analysis.md §6.3). The 20-entry limit and the field rules come from the contract;
 * the 8 KB body limit from {@link ClientLogsBodyLimitFilter}.
 */
@RestController
public class ClientLogsController implements SystemApi {

    static final String LOGGER_NAME = "com.blankjava.frontend";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    @Override
    public ResponseEntity<Void> sendClientLogs(List<ClientLogEntry> entries) {
        entries.forEach(ClientLogsController::write);
        return ResponseEntity.accepted().build();
    }

    private static void write(ClientLogEntry entry) {
        LoggingEventBuilder event = log.atLevel(level(entry.getLevel())).addKeyValue("event.dataset", "frontend");
        // Optional fields are only written when the browser sent them (no "null" in the logs)
        event = addIfPresent(event, "frontend.trace.id", entry.getTraceId());
        event = addIfPresent(event, "frontend.url", LogMasking.maskEmails(entry.getUrl()));
        event = addIfPresent(event, "frontend.timestamp", entry.getTimestamp());
        event.log(LogMasking.maskEmails(entry.getMessage()));
    }

    private static LoggingEventBuilder addIfPresent(LoggingEventBuilder event, String key, @Nullable Object value) {
        return value == null ? event : event.addKeyValue(key, value);
    }

    private static Level level(ClientLogEntry.LevelEnum level) {
        return switch (level) {
            case ERROR -> Level.ERROR;
            case WARN -> Level.WARN;
            case INFO -> Level.INFO;
        };
    }
}
