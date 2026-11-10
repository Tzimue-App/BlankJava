package com.blankjava.security;

import com.blankjava.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code 401} and {@code 403} raised by Spring Security, as Problem Details with the request's
 * {@code traceId} — the same shape as the errors of {@code ProblemDetailsHandler}, which cannot
 * see them (they happen in the filter chain, before Spring MVC).
 *
 * <p>The body is a {@link ProblemDetail} serialized by the application's Jackson mapper, never a
 * string template: the request path in {@code instance} is client-controlled (#52).
 */
@Component
public class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** Detail of every {@code 401} about the access token. */
    public static final String UNAUTHORIZED_DETAIL = "The access token is missing, expired or invalid.";

    /** RFC 6750 challenge when a token was sent but cannot be used. */
    public static final String INVALID_TOKEN_CHALLENGE = "Bearer error=\"invalid_token\"";

    private static final URI ABOUT_BLANK = URI.create("about:blank");

    private final TraceIds traceIds;

    private final JsonMapper json;

    public ProblemSecurityHandlers(TraceIds traceIds, JsonMapper jsonMapper) {
        this.traceIds = traceIds;
        // The mixin flattens the properties (traceId) and drops empty members, as Spring MVC does
        this.json = jsonMapper
                .rebuild()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .build();
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
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(ABOUT_BLANK);
        problem.setInstance(instance(request));
        problem.setProperty("traceId", traceIds.current());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json.writeValueAsString(problem));
    }

    /**
     * The request path as received (still percent-encoded), or none when it is not a valid URI
     * reference: the contract types {@code instance} as {@code uri-reference}.
     */
    static @Nullable URI instance(HttpServletRequest request) {
        try {
            return new URI(request.getRequestURI());
        } catch (URISyntaxException notAUri) {
            return null;
        }
    }
}
