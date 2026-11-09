package com.blankjava.security;

import com.blankjava.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * {@code 401} and {@code 403} raised by Spring Security, as Problem Details with the request's
 * {@code traceId} — the same shape as the errors of {@code ProblemDetailsHandler}, which cannot
 * see them (they happen in the filter chain, before Spring MVC).
 */
@Component
public class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** Detail of every {@code 401} about the access token. */
    public static final String UNAUTHORIZED_DETAIL = "The access token is missing, expired or invalid.";

    /** RFC 6750 challenge when a token was sent but cannot be used. */
    public static final String INVALID_TOKEN_CHALLENGE = "Bearer error=\"invalid_token\"";

    private final TraceIds traceIds;

    public ProblemSecurityHandlers(TraceIds traceIds) {
        this.traceIds = traceIds;
    }

    /** No or invalid access token. RFC 6750: {@code error="invalid_token"} when one was sent. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String challenge = exception instanceof OAuth2AuthenticationException ? INVALID_TOKEN_CHALLENGE : "Bearer";
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
        write(response, request, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_DETAIL);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        write(response, request, HttpStatus.FORBIDDEN, "Access is denied.");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":"
                        + status.value() + ",\"detail\":\"" + detail + "\",\"instance\":\""
                        + json(request.getRequestURI()) + "\",\"traceId\":\"" + traceIds.current() + "\"}");
    }

    /** Escapes a value for a JSON string (the request path is client-controlled). */
    static String json(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
