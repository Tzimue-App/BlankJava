package com.blankjava;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real log output (ECS JSON on the console, default profile) for requests coming through Nginx:
 * trace propagation from {@code traceparent}, request id, frontend events, and no credentials.
 */
@Tag("integration")
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "blankjava.access-log.enabled=true")
class LoggingIntegrationTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
    private static final String REQUEST_ID = "ff79fe9ac3fa821317ec2de2fb4b0cef";
    private static final String FRONTEND_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Test
    void theTraceIdOfTraceparentIsUsedForTheRequestAndReturnedInProblems() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/unknown"))
                .header("traceparent", TRACEPARENT)
                .GET());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"traceId\":\"" + TRACE_ID + "\"");
    }

    @Test
    void everyRequestHasOneAccessLineWithItsTraceId(CapturedOutput output) throws Exception {
        send(HttpRequest.newBuilder(uri("/actuator/info"))
                .header("traceparent", TRACEPARENT)
                .GET());

        assertThat(output.getOut().lines().filter(line -> line.contains("\"backend.access\"")))
                .anySatisfy(line -> assertThat(line)
                        .contains("\"trace.id\":\"" + TRACE_ID + "\"")
                        .contains("\"path\":\"/actuator/info\"")
                        .contains("\"status_code\":200"));
    }

    @Test
    void aFrontendEventIsOneJsonLineCorrelatedToBothRequestsWithoutCredentials(CapturedOutput output) throws Exception {
        String body = "[{\"level\":\"error\",\"message\":\"Save failed for jane.doe@example.com\",\"traceId\":\""
                + FRONTEND_TRACE_ID + "\",\"url\":\"/me\"}]";

        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/client-logs"))
                .header("Content-Type", "application/json")
                .header("traceparent", TRACEPARENT)
                .header("X-Request-Id", REQUEST_ID)
                .header("Authorization", "Bearer secret-access-token-123")
                .header("Cookie", "refresh_token=secret-refresh-cookie-456")
                .POST(HttpRequest.BodyPublishers.ofString(body)));

        assertThat(response.statusCode()).isEqualTo(202);
        // ECS JSON from Spring Boot: dotted keys are nested ("event":{"dataset":...}); the trace ids,
        // renamed from Micrometer's MDC keys, stay flat ("trace.id"), like in the Nginx access log
        List<String> frontendLines = output.getOut()
                .lines()
                .filter(line -> line.contains("\"event\":{\"dataset\":\"frontend\"}"))
                .toList();
        assertThat(frontendLines).hasSize(1);
        assertThat(frontendLines.getFirst())
                .startsWith("{")
                .endsWith("}")
                .contains("\"level\":\"ERROR\"")
                .contains("\"message\":\"Save failed for j***@example.com\"")
                .contains("\"trace.id\":\"" + TRACE_ID + "\"")
                .contains("\"request\":{\"id\":\"" + REQUEST_ID + "\"}")
                .contains("\"frontend\":{\"trace\":{\"id\":\"" + FRONTEND_TRACE_ID + "\"}")
                .doesNotContain("jane.doe")
                .doesNotContain("null");
        assertThat(output.getAll())
                .doesNotContain("secret-access-token-123")
                .doesNotContain("secret-refresh-cookie-456");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
