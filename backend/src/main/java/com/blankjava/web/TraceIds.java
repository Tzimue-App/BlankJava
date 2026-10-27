package com.blankjava.web;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Trace id of the current request, returned in every Problem Details response so that a reported
 * error can be found in the logs.
 *
 * <p>Reads the {@code traceId} put in the logging context (MDC) by tracing. Until tracing is wired
 * (structured logging issue, #6), no trace id is in the MDC and a random W3C-format id (32 hex
 * characters) is generated instead.
 */
@Component
public class TraceIds {

    static final String MDC_KEY = "traceId";

    public String current() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null || traceId.isBlank() ? newTraceId() : traceId;
    }

    static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
