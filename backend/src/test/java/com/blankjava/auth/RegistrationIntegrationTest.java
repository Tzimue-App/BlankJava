package com.blankjava.auth;

import static com.blankjava.contract.ContractValidation.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blankjava.account.EmailTakenException;
import com.blankjava.account.PasswordPolicy;
import com.blankjava.account.RegistrationService;
import com.blankjava.account.UserAccountRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** {@code POST /api/auth/register} on the whole application and a real PostgreSQL (#29). */
@Tag("integration")
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationIntegrationTest {

    private static final String PASSWORD = "Quiet-Lantern-7-Orchard";

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
    private UserAccountRepository accounts;

    @Autowired
    private PasswordPolicy passwordPolicy;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Value("${local.server.port}")
    private int port;

    @Test
    void theMigrationCreatedBothTablesWithTheirIndexes() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where version = '2' and success", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "select indexname from pg_indexes where tablename in ('app_user', 'refresh_token')",
                        String.class))
                .contains(
                        "app_user_email_uk",
                        "refresh_token_token_hash_uk",
                        "refresh_token_user_id_idx",
                        "refresh_token_family_id_idx",
                        "refresh_token_expires_at_idx");
    }

    @Test
    void registeringStoresTheAccountAndOpensASession() throws Exception {
        String local = unique();
        MvcResult result = register(local + "@Example.com ", " Jane ", PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.user.email").value(local.toLowerCase(Locale.ROOT) + "@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("Jane"))
                .andExpect(matchesContract())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie)
                .startsWith("refresh_token=")
                .contains("; Path=/api/auth; Max-Age=604800;", "; Secure; HttpOnly; SameSite=Strict");
        String cookieValue = setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));

        Map<String, Object> user = jdbc.queryForMap(
                "select id, email, display_name, password_hash, created_at, updated_at from app_user where email = ?",
                local.toLowerCase(Locale.ROOT) + "@example.com");
        assertThat(user.get("display_name")).isEqualTo("Jane");
        assertThat((String) user.get("password_hash")).startsWith("{argon2}$argon2id$");
        assertThat(user.get("created_at")).isEqualTo(user.get("updated_at"));
        String id = user.get("id").toString();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .contains("\"id\":\"" + id + "\"")
                .doesNotContain((String) user.get("password_hash"))
                .doesNotContain(PASSWORD)
                .doesNotContain(cookieValue);
        assertThat(OffsetDateTime.parse(com.jayway.jsonpath.JsonPath.read(body, "$.user.createdAt"))
                        .toInstant())
                .isEqualTo(((java.sql.Timestamp) user.get("created_at")).toInstant());

        // The refresh token: only its SHA-256 is stored, for this user, valid 7 days
        Map<String, Object> token =
                jdbc.queryForMap("select * from refresh_token where token_hash = ?", sha256(cookieValue));
        assertThat(token.get("user_id").toString()).isEqualTo(id);
        assertThat(token.get("family_id")).isNotNull();
        assertThat(token.get("revoked_at")).isNull();
        assertThat(Duration.between(
                        ((java.sql.Timestamp) token.get("created_at")).toInstant(),
                        ((java.sql.Timestamp) token.get("expires_at")).toInstant()))
                .isEqualTo(Duration.ofDays(7));

        // The access token is valid for this backend, names the user by id, never by e-mail
        Jwt jwt = jwtDecoder.decode(com.jayway.jsonpath.JsonPath.read(body, "$.accessToken"));
        assertThat(jwt.getSubject()).isEqualTo(id);
        assertThat(jwt.getClaims().values())
                .noneSatisfy(value -> assertThat(value.toString()).containsIgnoringCase(local));
    }

    @Test
    void theSameEmailWithAnotherCaseAndSpacesIsTaken() throws Exception {
        String local = unique();
        register(local + "@Example.com ", "Jane", PASSWORD).andExpect(status().isCreated());

        register(local.toLowerCase(Locale.ROOT) + "@example.com", "Other Jane", PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_taken"))
                .andExpect(matchesContract());
    }

    @Test
    void twoConcurrentRegistrationsOfTheSameEmailGiveOne201AndOne409() throws Exception {
        String body = json(unique() + "@example.com", "Jane", PASSWORD);
        HttpClient http = HttpClient.newHttpClient();
        int attempts = 6;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            for (int i = 0; i < attempts; i++) {
                statuses.add(pool.submit(() -> {
                    start.await();
                    return http.send(
                                    HttpRequest.newBuilder(
                                                    URI.create("http://localhost:" + port + "/api/auth/register"))
                                            .header("Content-Type", "application/json")
                                            .POST(HttpRequest.BodyPublishers.ofString(body))
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
            assertThat(codes).containsOnly(201, 409).containsOnlyOnce(201);
        }
    }

    @Test
    void aRaceLostOnTheUniqueIndexIsStillA409() {
        // The pre-check sees no account, as when another registration commits in between
        UserAccountRepository racing = mock(UserAccountRepository.class, AdditionalAnswers.delegatesTo(accounts));
        doReturn(false).when(racing).existsByEmail(anyString());
        RegistrationService service = new RegistrationService(racing, passwordPolicy, passwordEncoder, clock);
        String email = unique().toLowerCase(Locale.ROOT) + "@example.com";

        service.register(email, "Jane", PASSWORD);

        assertThatThrownBy(() -> service.register(email, "Jane", PASSWORD))
                .isInstanceOf(EmailTakenException.class)
                .hasMessageNotContaining(email);
        assertThat(jdbc.queryForObject("select count(*) from app_user where email = ?", Integer.class, email))
                .isEqualTo(1);
    }

    @Test
    void passwordRulesAreCountedInCodePointsAfterNfkc() throws Exception {
        expectPasswordError("fourteen-chars", "too_short");
        expectPasswordError("x".repeat(65), "too_long");
        expectPasswordError("demon1q2w3e4r5t", "compromised_password");

        // 64 four-byte characters (256 bytes in UTF-8) are accepted
        register(unique() + "@example.com", "Jane", "😀".repeat(64))
                .andExpect(status().isCreated())
                .andExpect(matchesContract());
        // 14 code points typed, 15 after NFKC (U+FB00 "ff" ligature)
        register(unique() + "@example.com", "Jane", "ﬀ" + "abcdefghijklm").andExpect(status().isCreated());
    }

    @Test
    void everyBrokenRuleIsReportedAtOnce() throws Exception {
        register("not-an-address", " a ", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("displayName"))
                .andExpect(jsonPath("$.errors[0].code").value("too_short"))
                .andExpect(jsonPath("$.errors[1].field").value("email"))
                .andExpect(jsonPath("$.errors[1].code").value("invalid_format"))
                .andExpect(jsonPath("$.errors[2].field").value("password"))
                .andExpect(jsonPath("$.errors[2].code").value("too_short"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(matchesContract());
    }

    @Test
    void neitherThePasswordNorItsHashNorTheEmailReachTheLogs(CapturedOutput output) throws Exception {
        String local = unique();
        register(local + "@example.com", "Jane", PASSWORD).andExpect(status().isCreated());
        register(local + "@example.com", "Jane", PASSWORD).andExpect(status().isConflict());
        register(unique() + "@example.com", "Jane", PASSWORD.substring(0, 10)).andExpect(status().isBadRequest());
        String hash = jdbc.queryForObject(
                "select password_hash from app_user where email = ?",
                String.class,
                local.toLowerCase(Locale.ROOT) + "@example.com");

        assertThat(output.getAll())
                .contains("user.registered")
                .doesNotContain(PASSWORD.substring(0, 10))
                .doesNotContain(hash.substring(hash.lastIndexOf('$') + 1))
                .doesNotContainIgnoringCase(local);
    }

    private void expectPasswordError(String password, String code) throws Exception {
        register(unique() + "@example.com", "Jane", password)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].code").value(code))
                .andExpect(matchesContract());
    }

    private ResultActions register(String email, String displayName, String password) throws Exception {
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(email, displayName, password)));
    }

    private static String json(String email, String displayName, String password) {
        return "{\"email\":\"" + email + "\",\"displayName\":\"" + displayName + "\",\"password\":\"" + password
                + "\"}";
    }

    /** A local part no other test uses, with upper-case letters to check the lower-casing. */
    private static String unique() {
        return "Jane." + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
    }
}
