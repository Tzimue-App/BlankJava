package com.blankjava.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class UserIdFilterTest {

    private static final String USER_ID = "7b2f4c1e-9a8d-4b6e-8f3a-2d1c0b9e8a7f";

    private final UserIdFilter filter = new UserIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
    private final List<String> seen = new ArrayList<>();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anAuthenticatedRequestLogsItsUserIdUntilItEnds() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(USER_ID)
                .issuedAt(Instant.now())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        run();

        assertThat(seen).containsExactly(USER_ID);
        assertThat(request.getAttribute(UserIdFilter.REQUEST_ATTRIBUTE)).isEqualTo(USER_ID);
        assertThat(MDC.get(UserIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void anAnonymousRequestHasNoUserId() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        run();

        assertThat(seen).containsExactly((String) null);
        assertThat(request.getAttribute(UserIdFilter.REQUEST_ATTRIBUTE)).isNull();
    }

    @Test
    void aRequestWithoutAuthenticationHasNoUserId() throws Exception {
        run();

        assertThat(seen).containsExactly((String) null);
    }

    private void run() throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                seen.add(MDC.get(UserIdFilter.MDC_KEY));
            }
        });
    }
}
