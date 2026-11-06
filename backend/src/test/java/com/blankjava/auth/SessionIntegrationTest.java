package com.blankjava.auth;

import static com.blankjava.contract.ContractValidation.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.blankjava.logging.CapturedLogs;
import com.blankjava.logging.SecurityAuditLog;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Login, refresh and logout on the whole application and a real PostgreSQL (#30). */
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "blankjava.security.allowed-origins=https://localhost,https://frontend")
class SessionIntegrationTest {

    private static final String PASSWORD = "Quiet-Lantern-7-Orchard";
    private static final String ORIGIN = "https://localhost";
    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private RefreshTokenPurge purge;

    @Value("${local.server.port}")
    private int port;

    @Test
    void aWholeSessionLoginRefreshLogout() throws Exception {
        String email = registered();

        // Login: a new session, in a new token family
        MvcResult login = login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(matchesContract())
                .andReturn();
        String first = cookie(login);
        String userId = JsonPath.read(login.getResponse().getContentAsString(), "$.user.id");
        assertThat(jwtDecoder.decode(accessToken(login)).getSubject()).isEqualTo(userId);

        // Refresh: new access token, new cookie in the same family; the old one is revoked
        MvcResult refreshed = refresh(first)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.user.id").value(userId))
                .andExpect(matchesContract())
                .andReturn();
        String second = cookie(refreshed);
        assertThat(second).isNotEqualTo(first);
        assertThat(jwtDecoder.decode(accessToken(refreshed)).getSubject()).isEqualTo(userId);
        assertThat(jdbc.queryForObject(
                        "select count(distinct family_id) from refresh_token where token_hash in (?, ?)",
                        Integer.class,
                        RefreshTokenService.hash(first),
                        RefreshTokenService.hash(second)))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select replaced_by = (select id from refresh_token where token_hash = ?)"
                                + " from refresh_token where token_hash = ?",
                        Boolean.class,
                        RefreshTokenService.hash(second),
                        RefreshTokenService.hash(first)))
                .isTrue();

        // The rotated token is refused, and its cookie cleared
        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("refresh_token=; ")))
                .andExpect(matchesContract());

        // Logout: 204, cookie cleared, the token is refused afterwards
        logout(second)
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")))
                .andExpect(matchesContract());
        assertThat(jdbc.queryForObject(
                        "select revoked_at is not null and replaced_by is null from refresh_token where token_hash = ?",
                        Boolean.class,
                        RefreshTokenService.hash(second)))
                .isTrue();
        refresh(second).andExpect(status().isUnauthorized()).andExpect(matchesContract());

        // Logout again, or without cookie: still 204
        logout(second).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").header("Origin", ORIGIN)).andExpect(status().isNoContent());
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailGiveByteIdentical401s() throws Exception {
        String email = registered();

        String wrongPassword = login(email, PASSWORD + "x", TRACEPARENT)
                .andExpect(status().isUnauthorized())
                .andExpect(matchesContract())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String unknownEmail = login("nobody." + UUID.randomUUID() + "@example.com", PASSWORD, TRACEPARENT)
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();
        // A 3-character password: the generic 401, never a 400 revealing the policy
        String tooShort = login(email, "abc", TRACEPARENT)
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownEmail).isEqualTo(tooShort).contains("Invalid e-mail or password.");
    }

    @Test
    void theEmailOfALoginIsNormalisedLikeAtRegistration() throws Exception {
        String email = registered();

        login("  " + email.toUpperCase(Locale.ROOT) + " ", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void aCookiePresentedTwiceInARowIsRefusedTheSecondTime() throws Exception {
        String cookie = cookie(login(registered(), PASSWORD).andReturn());

        refresh(cookie).andExpect(status().isOk());
        refresh(cookie)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.startsWith("refresh_token=; ")));
    }

    @Test
    void twoConcurrentRefreshesWithTheSameCookieGiveOne200() throws Exception {
        String cookie = cookie(login(registered(), PASSWORD).andReturn());
        HttpClient http = HttpClient.newHttpClient();
        int attempts = 4;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            for (int i = 0; i < attempts; i++) {
                statuses.add(pool.submit(() -> {
                    start.await();
                    return http.send(
                                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/refresh"))
                                            .header("Origin", ORIGIN)
                                            .header("Cookie", "refresh_token=" + cookie)
                                            .POST(HttpRequest.BodyPublishers.noBody())
                                            .build(),
                                    HttpResponse.BodyHandlers.discarding())
                            .statusCode();
                }));
            }
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> status : statuses) {
                codes.add(status.get());
            }
            assertThat(codes).containsOnly(200, 401).containsOnlyOnce(200);
        }
    }

    @Test
    void refreshAndLogoutNeedAnAllowedOrigin() throws Exception {
        String cookie = cookie(login(registered(), PASSWORD).andReturn());

        for (String path : List.of("/api/auth/refresh", "/api/auth/logout")) {
            mvc.perform(post(path).cookie(new Cookie("refresh_token", cookie)))
                    .andExpect(status().isForbidden())
                    .andExpect(matchesContract());
            mvc.perform(post(path).header("Origin", "https://evil.example").cookie(new Cookie("refresh_token", cookie)))
                    .andExpect(status().isForbidden());
        }
        // Refused requests touched nothing: the cookie still works, from each allowed origin
        String next = cookie(mvc.perform(post("/api/auth/refresh")
                        .header("Origin", "https://frontend")
                        .cookie(new Cookie("refresh_token", cookie)))
                .andExpect(status().isOk())
                .andReturn());
        refresh(next).andExpect(status().isOk());
    }

    @Test
    void eachAuditEventIsEmittedOnceWithoutTheFullEmail() throws Exception {
        String email = registered();
        String local = email.substring(0, email.indexOf('@'));

        try (CapturedLogs logs = new CapturedLogs(SecurityAuditLog.LOGGER_NAME)) {
            login(email, PASSWORD + "x").andExpect(status().isUnauthorized());
            String cookie = cookie(login(email, PASSWORD).andReturn());
            logout(cookie).andExpect(status().isNoContent());
            logout(cookie).andExpect(status().isNoContent());

            assertThat(logs.events())
                    .extracting(event -> CapturedLogs.keyValues(event).get("event.action"))
                    .containsExactly("auth.login.failed", "auth.login.succeeded", "auth.logout");
            ILoggingEvent failed = logs.events().getFirst();
            assertThat(CapturedLogs.keyValues(failed))
                    .containsEntry("user.email", local.charAt(0) + "***@example.com")
                    .containsKey("client.ip");
            assertThat(CapturedLogs.keyValues(logs.events().get(1))).containsKeys("user.id", "client.ip");
            assertThat(logs.events())
                    .allSatisfy(event -> assertThat(event.getFormattedMessage() + CapturedLogs.keyValues(event))
                            .doesNotContain(local)
                            .doesNotContain(PASSWORD));
        }
    }

    @Test
    void thePurgeDeletesOnlyTokensExpiredOrRevokedForMoreThanSevenDays() {
        UUID user = UUID.randomUUID();
        jdbc.update(
                "insert into app_user (id, email, display_name, password_hash, created_at, updated_at)"
                        + " values (?, ?, 'Purge', '{argon2}x', now(), now())",
                user,
                "purge." + user + "@example.com");
        Instant now = Instant.now();
        String expiredLongAgo = seed(user, now.minus(Duration.ofDays(8)), null);
        String expiredRecently = seed(user, now.minus(Duration.ofDays(6)), null);
        String revokedLongAgo = seed(user, now.plus(Duration.ofDays(1)), now.minus(Duration.ofDays(8)));
        String revokedRecently = seed(user, now.plus(Duration.ofDays(1)), now.minus(Duration.ofDays(6)));
        String active = seed(user, now.plus(Duration.ofDays(3)), null);

        purge.purge();

        assertThat(jdbc.queryForList("select token_hash from refresh_token where user_id = ?", String.class, user))
                .containsExactlyInAnyOrder(expiredRecently, revokedRecently, active)
                .doesNotContain(expiredLongAgo, revokedLongAgo);
    }

    @Test
    void theShedLockTableExists() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where version = '3' and success", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from shedlock", Integer.class))
                .isNotNull();
    }

    private String seed(UUID user, Instant expiresAt, Instant revokedAt) {
        String hash = RefreshTokenService.hash(UUID.randomUUID().toString());
        jdbc.update(
                "insert into refresh_token (id, user_id, family_id, token_hash, created_at, expires_at, revoked_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                user,
                UUID.randomUUID(),
                hash,
                Timestamp.from(expiresAt.minus(Duration.ofDays(7))),
                Timestamp.from(expiresAt),
                revokedAt == null ? null : Timestamp.from(revokedAt));
        return hash;
    }

    /** Registers a new account; returns its normalised e-mail. */
    private String registered() throws Exception {
        String email = "jane." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"displayName\":\"Jane\",\"password\":\"" + PASSWORD
                                + "\"}"))
                .andExpect(status().isCreated());
        return email;
    }

    private ResultActions login(String email, String password) throws Exception {
        return login(email, password, null);
    }

    private ResultActions login(String email, String password, String traceparent) throws Exception {
        var request = post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
        if (traceparent != null) {
            request.header("traceparent", traceparent);
        }
        return mvc.perform(request);
    }

    private ResultActions refresh(String cookie) throws Exception {
        return mvc.perform(
                post("/api/auth/refresh").header("Origin", ORIGIN).cookie(new Cookie("refresh_token", cookie)));
    }

    private ResultActions logout(String cookie) throws Exception {
        return mvc.perform(
                post("/api/auth/logout").header("Origin", ORIGIN).cookie(new Cookie("refresh_token", cookie)));
    }

    private static String cookie(MvcResult result) {
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).startsWith("refresh_token=").contains("Max-Age=604800");
        return setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
