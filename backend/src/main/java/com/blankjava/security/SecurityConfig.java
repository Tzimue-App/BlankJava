package com.blankjava.security;

import com.blankjava.logging.UserIdFilter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Access rules of the API (docs/technical_analysis.md §5.2, §5.3): a short list of public routes,
 * everything else needs a valid access token. A new endpoint is therefore protected unless it is
 * added to {@link #PUBLIC_ROUTES} on purpose (checked by {@code SecurityIntegrationTest}).
 *
 * <p><b>CSRF protection is disabled on purpose.</b> Bearer-token endpoints are not exposed to CSRF
 * (a browser never attaches the token by itself). The only cookie endpoints, {@code refresh} and
 * {@code logout}, rely on {@code SameSite=Strict} plus a check of the {@code Origin} header (#30),
 * as §5.3 decides.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Routes reachable without an access token. */
    static final List<RequestMatcher> PUBLIC_ROUTES = List.of(
            post("/api/auth/register"),
            post("/api/auth/login"),
            post("/api/auth/refresh"),
            post("/api/auth/logout"),
            // Browser error reports, also sent by anonymous visitors (contract: no authentication)
            post("/api/client-logs"),
            get("/actuator/health"),
            get("/actuator/health/**"),
            get("/actuator/info"),
            // Error pages rendered by Spring Boot for errors outside Spring MVC
            PathPatternRequestMatcher.withDefaults().matcher("/error"));

    /** Swagger UI on the contract, when springdoc is switched on (dev profile only). */
    static final List<RequestMatcher> SWAGGER_ROUTES =
            List.of(get("/swagger-ui.html"), get("/swagger-ui/**"), get("/v3/api-docs/**"), get("/openapi.yaml"));

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            ProblemSecurityHandlers problems,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerUi)
            throws Exception {
        RequestMatcher publicRoutes = publicRoutes(swaggerUi);
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // HSTS belongs to the production Nginx configuration (M2), not to every environment
                .headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts.disable()))
                .authorizeHttpRequests(rules -> rules.requestMatchers(publicRoutes)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(server -> server.bearerTokenResolver(bearerTokenResolver(publicRoutes))
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(
                        errors -> errors.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                // user.id in the logging context once the token is validated
                .addFilterAfter(new UserIdFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    static RequestMatcher publicRoutes(boolean swaggerUi) {
        List<RequestMatcher> routes = new ArrayList<>(PUBLIC_ROUTES);
        if (swaggerUi) {
            routes.addAll(SWAGGER_ROUTES);
        }
        return new OrRequestMatcher(routes);
    }

    /**
     * Public routes ignore the {@code Authorization} header: the frontend sends its token on every
     * API call, and an expired one must not turn a public request (an error report, a login) into
     * a {@code 401}.
     */
    static BearerTokenResolver bearerTokenResolver(RequestMatcher publicRoutes) {
        DefaultBearerTokenResolver standard = new DefaultBearerTokenResolver();
        return request -> publicRoutes.matches(request) ? null : standard.resolve(request);
    }

    private static RequestMatcher post(String pattern) {
        return PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, pattern);
    }

    private static RequestMatcher get(String pattern) {
        return PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, pattern);
    }
}
