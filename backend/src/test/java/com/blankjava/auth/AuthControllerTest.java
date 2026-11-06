package com.blankjava.auth;

import static com.blankjava.contract.ContractValidation.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
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
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Status codes, headers and Problem bodies of {@code /api/auth/*}, the service mocked (#29, #30). */
@WebMvcTest(
        controllers = AuthController.class,
        properties = "blankjava.security.allowed-origins=https://localhost,https://frontend")
@ApiSecurity
@Import({ApiPathConfig.class, ProblemDetailsHandler.class, TraceIds.class, SecurityAuditLog.class, OriginCheck.class})
class AuthControllerTest {

    private static final String TRACE_ID = "[0-9a-f]{32}";
    private static final UUID USER_ID = UUID.fromString("7b2f4c1e-9a8d-4b6e-8f3a-2d1c0b9e8a7f");
    private static final String SET_COOKIE =
            "refresh_token=refresh-value; Path=/api/auth; Max-Age=604800; Expires=[^;]+; Secure; HttpOnly; SameSite=Strict";
    private static final String CLEAR_COOKIE =
            "refresh_token=; Path=/api/auth; Max-Age=0; Expires=[^;]+; Secure; HttpOnly; SameSite=Strict";
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
    void aLoginOpensASessionLikeARegistration() throws Exception {
        Session session = session();
        when(sessions.login("Jane@Example.com", "tangerine-river-boat")).thenReturn(session);

        try (CapturedLogs logs = new CapturedLogs(SecurityAuditLog.LOGGER_NAME)) {
            login("{\"email\":\"Jane@Example.com\",\"password\":\"tangerine-river-boat\"}")
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("Set-Cookie", matchesPattern(SET_COOKIE)))
                    .andExpect(jsonPath("$.accessToken").value("access-value"))
                    .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
                    .andExpect(matchesContract());

            assertThat(logs.events())
                    .singleElement()
                    .satisfies(event -> assertThat(CapturedLogs.keyValues(event))
                            .containsEntry("event.action", "auth.login.succeeded")
                            .containsEntry("user.id", USER_ID.toString())
                            .containsEntry("client.ip", "127.0.0.1"));
        }
    }

    @Test
    void aFailedLoginIsTheGeneric401AndAuditedWithTheMaskedEmail() throws Exception {
        when(sessions.login(anyString(), anyString())).thenThrow(new InvalidCredentialsException());

        try (CapturedLogs logs = new CapturedLogs(SecurityAuditLog.LOGGER_NAME)) {
            login("{\"email\":\"jane.doe@example.com\",\"password\":\"abc\"}")
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value("Invalid e-mail or password."))
                    .andExpect(jsonPath("$.traceId").value(matchesPattern(TRACE_ID)))
                    .andExpect(header().doesNotExist("Set-Cookie"))
                    .andExpect(matchesContract());

            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(CapturedLogs.keyValues(event))
                        .containsEntry("event.action", "auth.login.failed")
                        .containsEntry("event.outcome", "failure")
                        .containsEntry("user.email", "j***@example.com")
                        .doesNotContainKey("user.id");
                assertThat(event.getFormattedMessage() + CapturedLogs.keyValues(event))
                        .doesNotContain("jane.doe");
            });
        }
    }

    @Test
    void aStructurallyInvalidLoginIsA400() throws Exception {
        login("{\"email\":\"jane@example.com\"}")
                .andExpect(status().isBadRequest())
                .andExpect(matchesContract());
        verifyNoInteractions(sessions);
    }

    @Test
    void refreshRotatesTheCookieForAnAllowedOrigin() throws Exception {
        Session session = session();
        when(sessions.refresh("old-value")).thenReturn(session);

        for (String origin : List.of("https://localhost", "https://frontend", "HTTPS://LOCALHOST/")) {
            mvc.perform(post("/api/auth/refresh")
                            .header("Origin", origin)
                            .cookie(new Cookie("refresh_token", "old-value")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(header().string("Set-Cookie", matchesPattern(SET_COOKIE)))
                    .andExpect(matchesContract());
        }
    }

    @Test
    void aRejectedRefreshIsA401ThatClearsTheCookie() throws Exception {
        when(sessions.refresh(any())).thenThrow(new RefreshRejectedException());

        mvc.perform(post("/api/auth/refresh").header("Origin", "https://localhost"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", matchesPattern(CLEAR_COOKIE)))
                .andExpect(jsonPath("$.detail").value("The session has expired."))
                .andExpect(jsonPath("$.traceId").value(matchesPattern(TRACE_ID)))
                .andExpect(matchesContract());
    }

    @Test
    void refreshAndLogoutRefuseAForeignOrMissingOrigin() throws Exception {
        for (String path : List.of("/api/auth/refresh", "/api/auth/logout")) {
            for (String origin : new String[] {"https://evil.example", "null", "http://localhost", null}) {
                var request = post(path).cookie(new Cookie("refresh_token", "old-value"));
                if (origin != null) {
                    request.header("Origin", origin);
                }
                mvc.perform(request)
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.detail").value("Origin not allowed."))
                        .andExpect(jsonPath("$.traceId").value(matchesPattern(TRACE_ID)))
                        .andExpect(header().doesNotExist("Set-Cookie"))
                        .andExpect(matchesContract());
            }
        }
        verifyNoInteractions(sessions);
    }

    @Test
    void logoutRevokesClearsTheCookieAndIsAudited() throws Exception {
        when(sessions.logout("old-value")).thenReturn(Optional.of(USER_ID));

        try (CapturedLogs logs = new CapturedLogs(SecurityAuditLog.LOGGER_NAME)) {
            mvc.perform(post("/api/auth/logout")
                            .header("Origin", "https://localhost")
                            .cookie(new Cookie("refresh_token", "old-value")))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Set-Cookie", matchesPattern(CLEAR_COOKIE)))
                    .andExpect(matchesContract());

            assertThat(logs.events())
                    .singleElement()
                    .satisfies(event -> assertThat(CapturedLogs.keyValues(event))
                            .containsEntry("event.action", "auth.logout")
                            .containsEntry("user.id", USER_ID.toString()));
        }
    }

    @Test
    void logoutWithoutAnActiveSessionIsStillA204AndNotAudited() throws Exception {
        when(sessions.logout(null)).thenReturn(Optional.empty());

        try (CapturedLogs logs = new CapturedLogs(SecurityAuditLog.LOGGER_NAME)) {
            mvc.perform(post("/api/auth/logout").header("Origin", "https://frontend"))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string("Set-Cookie", matchesPattern(CLEAR_COOKIE)))
                    .andExpect(matchesContract());

            assertThat(logs.events()).isEmpty();
        }
    }

    private ResultActions login(String body) throws Exception {
        return mvc.perform(
                post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
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
