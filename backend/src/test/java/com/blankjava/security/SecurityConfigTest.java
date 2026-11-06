package com.blankjava.security;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blankjava.web.ApiPathConfig;
import com.blankjava.web.ProblemDetailsHandler;
import com.blankjava.web.TraceIds;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@ApiSecurity
@Import({ApiPathConfig.class, ProblemDetailsHandler.class, TraceIds.class, SecurityConfigTest.ProtectedController.class
})
class SecurityConfigTest {

    private static final UUID USER = UUID.fromString("7b2f4c1e-9a8d-4b6e-8f3a-2d1c0b9e8a7f");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AccessTokenService tokens;

    @Test
    void aProtectedRouteWithoutTokenIsA401Problem() throws Exception {
        mvc.perform(get("/api/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.instance").value("/api/test/protected"))
                .andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")));
    }

    @Test
    void anInvalidTokenIsA401WithTheInvalidTokenChallenge() throws Exception {
        mvc.perform(get("/api/test/protected").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")));
    }

    @Test
    void aValidTokenReachesTheControllerAsItsUser() throws Exception {
        String token = tokens.issue(USER).value();

        mvc.perform(get("/api/test/protected").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(USER.toString()));
    }

    @Test
    void anUnknownPathNeedsATokenBeforeItIsA404() throws Exception {
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/does-not-exist")
                        .header("Authorization", "Bearer " + tokens.issue(USER).value()))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerRoutesStayProtectedWhenSwaggerUiIsOff() throws Exception {
        mvc.perform(get("/openapi.yaml")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }

    @Test
    void responsesAreNeverStoredInSessionsNorGetHsts() throws Exception {
        mvc.perform(get("/api/test/protected")
                        .secure(true)
                        .header("Authorization", "Bearer " + tokens.issue(USER).value()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @RestController
    static class ProtectedController {

        @GetMapping("/test/protected") // served as /api/test/protected (ApiPathConfig)
        String whoAmI(Principal principal) {
            return principal.getName();
        }
    }
}
