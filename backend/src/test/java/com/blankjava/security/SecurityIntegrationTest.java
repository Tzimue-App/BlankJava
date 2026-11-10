package com.blankjava.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The access rules on the running application, over HTTP (#37). */
@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest {

    /**
     * The only routes that answer without an access token. A new route is protected by default;
     * making one public means adding it to {@code SecurityConfig.PUBLIC_ROUTES} <b>and</b> here.
     */
    private static final Set<String> PUBLIC = Set.of(
            "POST /api/auth/login",
            "POST /api/auth/logout",
            "POST /api/auth/refresh",
            "POST /api/auth/register",
            "POST /api/client-logs",
            "GET /actuator/health",
            "GET /actuator/health/**",
            "GET /actuator/info",
            "GET /error");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AccessTokenService tokens;

    @Test
    void onlyTheListedRoutesAnswerWithoutAnAccessToken() throws Exception {
        Set<String> routes = mappedRoutes();
        TreeMap<String, Integer> statuses = new TreeMap<>();
        for (String route : routes) {
            statuses.put(route, send(route, null).statusCode());
        }

        assertThat(routes).as("public routes still mapped").containsAll(PUBLIC);
        assertThat(statuses).allSatisfy((route, status) -> {
            if (PUBLIC.contains(route)) {
                assertThat(status).as(route + " is public").isNotEqualTo(401);
            } else {
                assertThat(status).as(route + " needs a token").isEqualTo(401);
            }
        });
    }

    @Test
    void aForgedTokenIsA401ProblemWithTheInvalidTokenChallenge() throws Exception {
        HttpResponse<String> response = send("GET /api/anything", "Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0.");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Bearer error=\"invalid_token\"");
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).containsPattern("\"traceId\":\"[0-9a-f]{32}\"");
    }

    @Test
    void aValidTokenGetsPastTheAccessRules() throws Exception {
        String bearer = "Bearer " + tokens.issue(UUID.randomUUID()).value();

        assertThat(send("GET /api/unknown", bearer).statusCode()).isEqualTo(404);
    }

    @Test
    void noSessionIsEverCreated() throws Exception {
        HttpResponse<String> response = send("GET /actuator/info", null);

        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    /** "METHOD /path" of every Spring MVC and actuator mapping; a mapping without method is a GET. */
    private Set<String> mappedRoutes() {
        Set<String> routes = new TreeSet<>();
        for (RequestMappingInfoHandlerMapping mapping :
                context.getBeansOfType(RequestMappingInfoHandlerMapping.class).values()) {
            for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                for (String pattern : info.getPatternValues()) {
                    if (methods.isEmpty()) {
                        routes.add("GET " + pattern);
                    }
                    methods.forEach(method -> routes.add(method.name() + " " + pattern));
                }
            }
        }
        return routes;
    }

    private HttpResponse<String> send(String route, String authorization) throws Exception {
        String method = route.substring(0, route.indexOf(' '));
        String path = route.substring(route.indexOf(' ') + 1)
                .replaceAll("\\{[^}]*}", "x")
                .replace("**", "x");
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
