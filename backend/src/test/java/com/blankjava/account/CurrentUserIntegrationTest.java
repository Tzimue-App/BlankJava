package com.blankjava.account;

import static com.blankjava.contract.ContractValidation.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.blankjava.logging.CapturedLogs;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** {@code GET /api/users/me} on the whole application and a real PostgreSQL (#31). */
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = "blankjava.access-log.enabled=true")
class CurrentUserIntegrationTest {

    private static final String PASSWORD = "Quiet-Lantern-7-Orchard";
    private static final String ACCESS_LOGGER = "com.blankjava.access";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theTokenOfARegistrationReadsTheSameProfile() throws Exception {
        MvcResult registration = register();
        String user = registration.getResponse().getContentAsString();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken(registration)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(JsonPath.<String>read(user, "$.user.id")))
                .andExpect(jsonPath("$.email").value(JsonPath.<String>read(user, "$.user.email")))
                .andExpect(jsonPath("$.displayName").value("Jane"))
                .andExpect(jsonPath("$.createdAt").value(JsonPath.<String>read(user, "$.user.createdAt")))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(matchesContract());
    }

    @Test
    void aTokenOutlivingItsAccountIsA401() throws Exception {
        MvcResult registration = register();
        String id = JsonPath.read(registration.getResponse().getContentAsString(), "$.user.id");
        jdbc.update("delete from app_user where id = ?", UUID.fromString(id));

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken(registration)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(matchesContract());
    }

    @Test
    void withoutATokenItIsA401() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(matchesContract());
    }

    @Test
    void theAccessLogLineOfAnAuthenticatedRequestCarriesTheUserIdAndAnAnonymousOneNone() throws Exception {
        try (CapturedLogs logs = new CapturedLogs(ACCESS_LOGGER)) {
            MvcResult registration = register();
            String id = JsonPath.read(registration.getResponse().getContentAsString(), "$.user.id");
            mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken(registration)))
                    .andExpect(status().isOk());

            assertThat(line(logs, "POST /api/auth/register")).doesNotContainKey("user.id");
            assertThat(line(logs, "GET /api/users/me")).containsEntry("user.id", id);
        }
    }

    private static Map<String, Object> line(CapturedLogs logs, String request) {
        ILoggingEvent event = logs.events().stream()
                .filter(e -> e.getFormattedMessage().startsWith(request + " "))
                .findFirst()
                .orElseThrow();
        return CapturedLogs.keyValues(event);
    }

    private MvcResult register() throws Exception {
        String email = "jane." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        return mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"displayName\":\"Jane\",\"password\":\"" + PASSWORD
                                + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }
}
