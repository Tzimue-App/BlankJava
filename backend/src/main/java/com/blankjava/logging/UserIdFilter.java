package com.blankjava.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the access token's {@code sub} into the logging context as {@code user.id} for the rest of
 * an authenticated request (docs/technical_analysis.md §6.2). Anonymous requests get none.
 *
 * <p>Runs in the security filter chain, right after the token is validated (see {@code
 * SecurityConfig}); it is not a servlet filter of its own. The id is also left as a request
 * attribute for {@link AccessLogFilter}, whose line is written after this filter has returned.
 */
public class UserIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "user.id";

    /** Request attribute holding the authenticated user id, read by the access log. */
    public static final String REQUEST_ATTRIBUTE = UserIdFilter.class.getName() + ".userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            chain.doFilter(request, response);
            return;
        }
        String userId = token.getName();
        request.setAttribute(REQUEST_ATTRIBUTE, userId);
        MDC.put(MDC_KEY, userId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
