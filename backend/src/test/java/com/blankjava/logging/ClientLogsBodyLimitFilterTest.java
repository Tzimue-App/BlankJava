package com.blankjava.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.blankjava.web.TraceIds;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Requests without Content-Length (chunked), which MockMvc cannot produce. */
class ClientLogsBodyLimitFilterTest {

    private final ClientLogsBodyLimitFilter filter = new ClientLogsBodyLimitFilter(new TraceIds());

    @Test
    void aChunkedBodyOverTheLimitIsRejectedAfterReadingAtMostLimitPlusOneBytes() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> reachedController = new AtomicReference<>(false);

        filter.doFilter(chunked("x".repeat(20_000)), response, chain(reachedController, null));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString())
                .contains("\"status\":413")
                .containsPattern("\"traceId\":\"[0-9a-f]{32}\"");
        assertThat(reachedController.get()).isFalse();
    }

    @Test
    void anAcceptedChunkedBodyIsReplayedToTheController() throws Exception {
        AtomicReference<String> replayed = new AtomicReference<>();

        filter.doFilter(
                chunked("[{\"level\":\"info\",\"message\":\"ok\"}]"),
                new MockHttpServletResponse(),
                chain(null, replayed));

        assertThat(replayed.get()).isEqualTo("[{\"level\":\"info\",\"message\":\"ok\"}]");
    }

    @Test
    void otherPathsAndMethodsAreNotFiltered() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", ClientLogsBodyLimitFilter.PATH)))
                .isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", "/api/other")))
                .isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("POST", ClientLogsBodyLimitFilter.PATH)))
                .isFalse();
    }

    private static MockHttpServletRequest chunked(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ClientLogsBodyLimitFilter.PATH) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static MockFilterChain chain(AtomicReference<Boolean> reached, AtomicReference<String> body) {
        return new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                try {
                    if (reached != null) {
                        reached.set(true);
                    }
                    if (body != null) {
                        body.set(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                        assertThat(request.getContentLengthLong())
                                .isEqualTo(body.get().length());
                        assertThat(request.getReader()).isNotNull();
                    }
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }
}
