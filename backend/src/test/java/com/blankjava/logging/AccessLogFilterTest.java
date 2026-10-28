package com.blankjava.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AccessLogFilterTest {

    private final AccessLogFilter filter = new AccessLogFilter();

    @Test
    void writesOneStructuredLinePerRequestWithStatusDurationAndClientIp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/client-logs");
        request.setRemoteAddr("172.30.0.1");

        try (CapturedLogs logs = new CapturedLogs(AccessLogFilter.LOGGER_NAME)) {
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
                @Override
                public void doFilter(ServletRequest req, ServletResponse res) {
                    ((HttpServletResponse) res).setStatus(202);
                }
            });

            ILoggingEvent event = logs.events().getFirst();
            assertThat(event.getFormattedMessage()).startsWith("POST /api/client-logs 202 in ");
            assertThat(CapturedLogs.keyValues(event))
                    .containsEntry("event.dataset", "backend.access")
                    .containsEntry("http.request.method", "POST")
                    .containsEntry("url.path", "/api/client-logs")
                    .containsEntry("http.response.status_code", 202)
                    .containsEntry("client.ip", "172.30.0.1")
                    .containsKey("event.duration");
        }
    }

    @Test
    void logsTheRequestEvenWhenTheChainFails() {
        try (CapturedLogs logs = new CapturedLogs(AccessLogFilter.LOGGER_NAME)) {
            try {
                filter.doFilter(
                        new MockHttpServletRequest("GET", "/api/x"),
                        new MockHttpServletResponse(),
                        new MockFilterChain() {
                            @Override
                            public void doFilter(ServletRequest req, ServletResponse res) {
                                throw new IllegalStateException("boom");
                            }
                        });
            } catch (Exception expected) {
                // the failure itself is handled elsewhere
            }

            assertThat(logs.events()).hasSize(1);
        }
    }

    @Test
    void dockerHealthChecksAreNotLogged() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health")))
                .isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/info")))
                .isFalse();
    }
}
