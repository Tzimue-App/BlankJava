package com.blankjava.account;

import static com.blankjava.contract.ContractValidation.matchesContract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blankjava.logging.UserIdFilter;
import com.blankjava.security.AccessTokenService;
import com.blankjava.security.ApiSecurity;
import com.blankjava.security.JwtKeySet;
import com.blankjava.security.SecurityProperties;
import com.blankjava.web.ApiPathConfig;
import com.blankjava.web.ProblemDetailsHandler;
import com.blankjava.web.TraceIds;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** {@code GET /api/users/me} behind the real access rules, the repository mocked (#31). */
@WebMvcTest(UserController.class)
@ApiSecurity
@Import({ApiPathConfig.class, ProblemDetailsHandler.class, TraceIds.class})
class UserControllerTest {

    private static final String TRACE_ID = "[0-9a-f]{32}";
    private static final Instant CREATED = Instant.parse("2026-11-05T10:15:30Z");
    private static final String INVALID_TOKEN = "Bearer error=\"invalid_token\"";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccessTokenService tokens;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtKeySet keys;

    @Autowired
    private SecurityProperties properties;

    @MockitoBean
    private UserAccountRepository accounts;

    private final UserAccount jane = new UserAccount("jane@example.com", "Jane", "{argon2}secret-hash", CREATED);
    private final UUID janeId = UUID.randomUUID();

    @Test
    void aValidTokenReadsItsOwnersProfileWithoutAnyPasswordField() throws Exception {
        setId(jane, janeId);
        when(accounts.findById(janeId)).thenReturn(Optional.of(jane));

        me("Bearer " + tokens.issue(janeId).value())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(janeId.toString()))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.displayName").value("Jane"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-05T10:15:30Z"))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(content().string(not(containsString("hash"))))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(matchesContract());
    }

    @Test
    void theUserIsTheTokensSubjectOnly() throws Exception {
        UUID other = UUID.randomUUID();
        when(accounts.findById(any())).thenReturn(Optional.empty());
        when(accounts.findById(janeId)).thenReturn(Optional.of(jane));

        // A user id in the query is ignored: the token's subject decides
        mvc.perform(get("/api/users/me")
                        .queryParam("id", janeId.toString())
                        .header("Authorization", "Bearer " + tokens.issue(other).value()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noTokenIsA401ProblemWithTheBearerChallenge() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.traceId").value(matchesPattern(TRACE_ID)))
                .andExpect(matchesContract());
        verifyNoInteractions(accounts);
    }

    @Test
    void anExpiredTokenIsA401() throws Exception {
        Clock lastHour = Clock.fixed(Instant.now().minusSeconds(3600), ZoneOffset.UTC);
        String expired = new AccessTokenService(encoder, keys, properties, lastHour)
                .issue(janeId)
                .value();

        rejected("Bearer " + expired);
    }

    @Test
    void aTokenSignedByAnotherKeyIsA401() throws Exception {
        // Same key id as the active key, so only the signature tells them apart
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(JwtKeySet.MIN_KEY_BITS);
        KeyPair other = generator.generateKeyPair();
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) other.getPublic())
                .privateKey((RSAPrivateKey) other.getPrivate())
                .keyID(keys.activeKeyId())
                .build();
        JwtEncoder foreign = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        String forged = new AccessTokenService(foreign, keys, properties, Clock.systemUTC())
                .issue(janeId)
                .value();

        rejected("Bearer " + forged);
    }

    @Test
    void aTokenWithAModifiedPayloadIsA401() throws Exception {
        String[] parts = tokens.issue(UUID.randomUUID()).value().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String changed = payload.replaceFirst("\"sub\":\"[^\"]+\"", "\"sub\":\"" + janeId + "\"");
        assertThat(changed).isNotEqualTo(payload);
        String tampered = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(changed.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        rejected("Bearer " + tampered);
    }

    @Test
    void aValidTokenOfADeletedAccountIsA401LikeAnInvalidToken() throws Exception {
        when(accounts.findById(janeId)).thenReturn(Optional.empty());

        me("Bearer " + tokens.issue(janeId).value())
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", INVALID_TOKEN))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.detail").value("The access token is missing, expired or invalid."))
                .andExpect(jsonPath("$.instance").value("/api/users/me"))
                .andExpect(jsonPath("$.traceId").value(matchesPattern(TRACE_ID)))
                .andExpect(matchesContract());
    }

    @Test
    void theRequestLogsCarryTheUserIdOnlyWhileItRuns() throws Exception {
        setId(jane, janeId);
        List<String> seen = new ArrayList<>();
        when(accounts.findById(janeId)).thenAnswer(call -> {
            seen.add(MDC.get(UserIdFilter.MDC_KEY));
            return Optional.of(jane);
        });

        me("Bearer " + tokens.issue(janeId).value()).andExpect(status().isOk());

        assertThat(seen).containsExactly(janeId.toString());
        assertThat(MDC.get(UserIdFilter.MDC_KEY)).isNull();
    }

    private void rejected(String authorization) throws Exception {
        me(authorization)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", INVALID_TOKEN))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(matchesContract());
        verifyNoInteractions(accounts);
    }

    private ResultActions me(String authorization) throws Exception {
        return mvc.perform(get("/api/users/me").header("Authorization", authorization));
    }

    private static void setId(UserAccount account, UUID id) {
        ReflectionTestUtils.setField(account, "id", id);
    }
}
