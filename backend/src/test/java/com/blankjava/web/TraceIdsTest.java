package com.blankjava.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TraceIdsTest {

    private final TraceIds traceIds = new TraceIds();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void usesTheTraceIdOfTheLoggingContext() {
        MDC.put(TraceIds.MDC_KEY, "4bf92f3577b34da6a3ce929d0e0e4736");

        assertThat(traceIds.current()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void generatesAW3cFormatIdWhenTheContextHasNone() {
        String first = traceIds.current();
        String second = traceIds.current();

        assertThat(first).matches("[0-9a-f]{32}");
        assertThat(second).matches("[0-9a-f]{32}").isNotEqualTo(first);
    }

    @Test
    void ignoresABlankTraceId() {
        MDC.put(TraceIds.MDC_KEY, " ");

        assertThat(traceIds.current()).matches("[0-9a-f]{32}");
    }
}
