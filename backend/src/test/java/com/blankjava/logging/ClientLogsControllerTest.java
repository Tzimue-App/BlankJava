package com.blankjava.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.blankjava.web.ApiPathConfig;
import com.blankjava.web.ProblemDetailsHandler;
import com.blankjava.web.TraceIds;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ClientLogsController.class)
@Import({ApiPathConfig.class, ProblemDetailsHandler.class, TraceIds.class, ClientLogsBodyLimitFilter.class})
class ClientLogsControllerTest {

    private static final String ENTRY = """
            {"level":"error","message":"Save failed for jane.doe@example.com","traceId":"4bf92f3577b34da6a3ce929d0e0e4736","url":"/me","timestamp":"2026-10-28T10:15:30Z"}""";

    @Autowired
    private MockMvc mvc;

    @Test
    void writesEachEntryAsAFrontendLogEventWithMaskedEmail() throws Exception {
        try (CapturedLogs logs = new CapturedLogs(ClientLogsController.LOGGER_NAME)) {
            mvc.perform(post("/api/client-logs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[" + ENTRY + ",{\"level\":\"warn\",\"message\":\"Slow page\"}]"))
                    .andExpect(status().isAccepted());

            assertThat(logs.events()).hasSize(2);
            ILoggingEvent error = logs.events().getFirst();
            assertThat(error.getLevel()).isEqualTo(Level.ERROR);
            assertThat(error.getFormattedMessage()).isEqualTo("Save failed for j***@example.com");
            assertThat(CapturedLogs.keyValues(error))
                    .containsEntry("event.dataset", "frontend")
                    .containsEntry("frontend.trace.id", "4bf92f3577b34da6a3ce929d0e0e4736")
                    .containsEntry("frontend.url", "/me");
            assertThat(logs.events().get(1).getLevel()).isEqualTo(Level.WARN);
        }
    }

    @Test
    void infoEntriesAreLoggedAtInfo() throws Exception {
        try (CapturedLogs logs = new CapturedLogs(ClientLogsController.LOGGER_NAME)) {
            mvc.perform(post("/api/client-logs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"level\":\"info\",\"message\":\"Started\"}]"))
                    .andExpect(status().isAccepted());

            assertThat(logs.events().getFirst().getLevel()).isEqualTo(Level.INFO);
        }
    }

    @Test
    void moreThanTwentyEntriesIsA400() throws Exception {
        String body = "[" + String.join(",", Collections.nCopies(21, "{\"level\":\"info\",\"message\":\"x\"}")) + "]";

        mvc.perform(post("/api/client-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")));
    }

    @Test
    void anEntryBreakingTheContractIsA400() throws Exception {
        mvc.perform(post("/api/client-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"level\":\"fatal\",\"message\":\"x\"}]"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/client-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"level\":\"error\",\"message\":\"x\",\"traceId\":\"not-a-trace-id\"}]"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/client-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aBodyOverEightKilobytesIsA413BeforeParsing() throws Exception {
        String message = "x".repeat(ClientLogsBodyLimitFilter.MAX_BYTES);

        mvc.perform(post("/api/client-logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"level\":\"error\",\"message\":\"" + message + "\"}]"))
                .andExpect(status().is(413))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{32}")));
    }
}
