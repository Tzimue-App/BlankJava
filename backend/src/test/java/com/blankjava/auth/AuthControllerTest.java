package com.blankjava.auth;

import static com.blankjava.contract.ContractValidation.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blankjava.account.EmailTakenException;
import com.blankjava.account.UserAccount;
import com.blankjava.api.model.FieldError.CodeEnum;
import com.blankjava.auth.RefreshTokenService.IssuedRefreshToken;
import com.blankjava.auth.SessionService.Session;
import com.blankjava.logging.CapturedLogs;
import com.blankjava.logging.SecurityAuditLog;
import com.blankjava.security.AccessTokenService.AccessToken;
import com.blankjava.security.ApiSecurity;
import com.blankjava.web.ApiPathConfig;
import com.blankjava.web.FieldErrors;
import com.blankjava.web.InvalidFieldsException;
import com.blankjava.web.ProblemDetailsHandler;
import com.blankjava.web.TraceIds;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Status codes, headers and Problem bodies of {@code /api/auth/*}, the service mocked (#29). */
@WebMvcTest(AuthController.class)
@ApiSecurity
@Import({ApiPathConfig.class, ProblemDetailsHandler.class, TraceIds.class, SecurityAuditLog.class})
class AuthControllerTest {

    private static final String TRACE_ID = "[0-9a-f]{32}";
    private static final UUID USER_ID = UUID.fromString("7b2f4c1e-9a8d-4b6e-8f3a-2d1c0b9e8a7f");
    private static final String VALID = """
            {"email":"Jane@Example.com ","displayName":"Jane","password":"tangerine-river-boat"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessionService sessions;

    @Test
    void registeringAnswers201WithTheSessionTheCookieAndNoStore() throws Exception {
        Session session = session();
        when(sessions.register("Jane@Example.com ", "Jane", "tangerine-river-boat"))
                .thenReturn(session);

        register(VALID)
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(
                        header().string(
                                        "Set-Cookie",
                                        matchesPattern(
                                                "refresh_token=refresh-value; Path=/api/auth; Max-Age=604800; Expires=[^;]+; Secure; HttpOnly; SameSite=Strict")))
                .andExpect(jsonPath("$.accessToken").value("access-value"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.user.email").value("jane@example.com"))
                .andExpect(jsonPath("$.user.displayName").value("Jane"))
                .andExpect(jsonPath("$.user.createdAt").value("2026-11-06T10:15:30.123456Z"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(matchesContract());
    }

    @Test
    void aRegistrationIsAuditedWithTheUserIdAndClientIpButNoEmail() throws Exception {
        Session session = session();
        when(sessions.register(anyString(), anyString(), anyString())).thenReturn(session);

        try (CapturedLogs logs = new CapturedLogs(SecurityAuditLog.LOGGER_NAME)) {
            register(VALID).andExpect(status().isCreated());

            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(CapturedLogs.keyValues(event))
                        .containsEntry("event.action", "user.registered")
                        .containsEntry("event.outcome", "success")
                        .containsEntry("user.id", USER_ID.toString())
                        .containsEntry("client.ip", "127.0.0.1");
                assertThat(event.getFormattedMessage() + CapturedLogs.keyValues(event))
                        .doesNotContainIgnoringCase("jane@example.com");
            });
        }
    }

    @Test
    void brokenRulesAreAValidationProblem() throws Exception {
        when(sessions.register(anyString(), anyString(), anyString()))
                .thenThrow(new InvalidFieldsException(List.of(
                        FieldErrors.of("password", CodeEnum.COMPROMISED_PASSWORD, "Breached."),
                        FieldErrors.of("displayName", CodeEnum.TOO_SHORT, "Too short."))));

        register(VALID)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.traceId").value(matchesPattern(TRACE_ID)))
                .andExpect(jsonPath("$.errors[0].field").value("displayName"))
                .andExpect(jsonPath("$.errors[0].code").value("too_short"))
                .andExpect(jsonPath("$.errors[1].field").value("password"))
                .andExpect(jsonPath("$.errors[1].code").value("compromised_password"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(matchesContract());
    }

    @Test
    void aMissingFieldIsAValidationProblemBeforeTheServiceIsCalled() throws Exception {
        register("{\"displayName\":\"Jane\",\"password\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].code").value("required"))
                .andExpect(jsonPath("$.errors[1].field").value("password"))
                .andExpect(jsonPath("$.errors[1].code").value("required"))
                .andExpect(matchesContract());
        verifyNoInteractions(sessions);
    }

    @Test
    void aValueAboveTheContractBoundIsTooLongAndNeverEchoed() throws Exception {
        String password = "secret-".repeat(40);

        register("{\"email\":\"jane@example.com\",\"displayName\":\"Jane\",\"password\":\"" + password + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].code").value("too_long"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
                .andExpect(matchesContract());
    }

    @Test
    void aTakenEmailIsA409WithItsCode() throws Exception {
        when(sessions.register(anyString(), anyString(), anyString())).thenThrow(new EmailTakenException());

        register(VALID)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("email_taken"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.traceId").value(matchesPattern(TRACE_ID)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jane"))))
                .andExpect(matchesContract());
    }

    @Test
    void registeringNeedsNoAccessTokenAndIgnoresAnExpiredOne() throws Exception {
        Session session = session();
        when(sessions.register(anyString(), anyString(), anyString())).thenReturn(session);

        mvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer expired.or.forged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID))
                .andExpect(status().isCreated());
    }

    @Test
    void loginRefreshAndLogoutAreNotImplementedYet() throws Exception {
        for (String path : List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout")) {
            mvc.perform(post(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"jane@example.com\",\"password\":\"x\"}"))
                    .andExpect(status().isNotImplemented())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.traceId").value(matchesPattern(TRACE_ID)))
                    .andExpect(matchesContract());
        }
    }

    private ResultActions register(String body) throws Exception {
        return mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static Session session() {
        UserAccount user = mock(UserAccount.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getEmail()).thenReturn("jane@example.com");
        when(user.getDisplayName()).thenReturn("Jane");
        when(user.getCreatedAt()).thenReturn(Instant.parse("2026-11-06T10:15:30.123456Z"));
        return new Session(
                user,
                new AccessToken("access-value", Duration.ofMinutes(10)),
                new IssuedRefreshToken("refresh-value", Duration.ofDays(7)));
    }
}
