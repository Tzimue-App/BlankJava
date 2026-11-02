package com.blankjava.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One structured log line per request ({@code event.dataset=backend.access}), written through the
 * normal logging so it carries the request's {@code trace.id} and {@code request.id} like every other
 * line (docs/technical_analysis.md §6.2). Runs inside the tracing observation filter; the client IP
 * is the one resolved from Nginx's {@code X-Forwarded-For}. Switched on by compose.yaml; Docker
 * health checks are not logged.
 */
@Component
@ConditionalOnBooleanProperty("blankjava.access-log.enabled")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AccessLogFilter extends OncePerRequestFilter {

    static final String LOGGER_NAME = "com.blankjava.access";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationNanos = System.nanoTime() - start;
            log.atInfo()
                    .addKeyValue("event.dataset", "backend.access")
                    .addKeyValue("http.request.method", request.getMethod())
                    .addKeyValue("url.path", request.getRequestURI())
                    .addKeyValue("http.response.status_code", response.getStatus())
                    .addKeyValue("event.duration", durationNanos)
                    .addKeyValue("client.ip", request.getRemoteAddr())
                    .log(
                            "{} {} {} in {} ms",
                            request.getMethod(),
                            request.getRequestURI(),
                            response.getStatus(),
                            durationNanos / 1_000_000);
        }
    }
}
