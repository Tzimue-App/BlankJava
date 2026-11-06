package com.blankjava.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.blankjava.web.TraceIds;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

class ProblemSecurityHandlersTest {

    private final ProblemSecurityHandlers handlers = new ProblemSecurityHandlers(new TraceIds());

    @Test
    void accessDeniedIsA403Problem() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.handle(new MockHttpServletRequest("GET", "/api/users/me"), response, new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"title\":\"Forbidden\"", "\"status\":403", "\"instance\":\"/api/users/me\"")
                .containsPattern("\"traceId\":\"[0-9a-f]{32}\"");
    }

    @Test
    void noTokenIsA401WithAPlainBearerChallenge() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.commence(
                new MockHttpServletRequest("GET", "/api/users/me"),
                response,
                new InsufficientAuthenticationException("none"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    void theRequestPathIsEscapedInTheJsonBody() {
        assertThat(ProblemSecurityHandlers.json("/a\"b\\c\u0001")).isEqualTo("/a\\\"b\\\\c\\u0001");
    }
}
