package com.blankjava.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.blankjava.web.TraceIds;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ProblemSecurityHandlersTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private final ProblemSecurityHandlers handlers = new ProblemSecurityHandlers(new TraceIds(), mapper);

    @Test
    void accessDeniedIsA403Problem() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.handle(new MockHttpServletRequest("GET", "/api/users/me"), response, new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.propertyNames())
                .containsExactlyInAnyOrder("type", "title", "status", "detail", "instance", "traceId");
        assertThat(body.get("type").asString()).isEqualTo("about:blank");
        assertThat(body.get("title").asString()).isEqualTo("Forbidden");
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("detail").asString()).isEqualTo("Access is denied.");
        assertThat(body.get("instance").asString()).isEqualTo("/api/users/me");
        assertThat(body.get("traceId").asString()).matches("[0-9a-f]{32}");
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
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("title").asString()).isEqualTo("Unauthorized");
        assertThat(body.get("detail").asString()).isEqualTo(ProblemSecurityHandlers.UNAUTHORIZED_DETAIL);
    }

    @Test
    void anEncodedPathIsReturnedAsReceived() throws Exception {
        String path = "/api/users/me/%22%3Cscript%3Ealert(1)%3C/script%3E%5C%01";

        assertThat(instanceOf(path)).isEqualTo(path);
    }

    @Test
    void aHostilePathStaysAJsonStringAndIsNeverMarkup() throws Exception {
        String hostile = "/api/\"<script>alert(1)</script>\\u0001";
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.commence(
                new MockHttpServletRequest("GET", hostile), response, new InsufficientAuthenticationException("none"));

        // Not a valid URI reference: left out rather than echoed, and the body is still valid JSON
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.has("instance")).isFalse();
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(response.getContentAsString()).doesNotContain("<script>");
    }

    private String instanceOf(String path) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handlers.commence(
                new MockHttpServletRequest("GET", path), response, new InsufficientAuthenticationException("none"));
        return mapper.readTree(response.getContentAsString()).get("instance").asString();
    }
}
