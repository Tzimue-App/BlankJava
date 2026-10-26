package com.blankjava.web;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@Import({TraceIds.class, ProblemDetailsHandlerTest.FailingController.class})
class ProblemDetailsHandlerTest {

    private static final String TRACE_ID = "[0-9a-f]{32}";

    @Autowired
    private MockMvc mvc;

    @Test
    void unknownPathIsA404ProblemWithTraceId() throws Exception {
        mvc.perform(get("/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.traceId").value(matchesPattern(TRACE_ID)));
    }

    @Test
    void unexpectedErrorIsA500ProblemThatHidesItsCause() throws Exception {
        mvc.perform(get("/test/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Unexpected error. Quote the trace id when reporting it."))
                .andExpect(jsonPath("$.traceId").value(matchesPattern(TRACE_ID)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @RestController
    static class FailingController {

        @GetMapping("/test/failure")
        String fail() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
