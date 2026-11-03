package com.blankjava.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void putsAValidRequestIdInTheLoggingContextDuringTheRequestOnly() throws Exception {
        assertThat(seenDuringRequest("ff79fe9ac3fa821317ec2de2fb4b0cef")).isEqualTo("ff79fe9ac3fa821317ec2de2fb4b0cef");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void ignoresAMissingOrMalformedRequestId() throws Exception {
        assertThat(seenDuringRequest(null)).isNull();
        assertThat(seenDuringRequest("abc\n{\"fake\":\"entry\"}")).isNull();
        assertThat(seenDuringRequest("not-hexadecimal-at-all-0000000000")).isNull();
    }

    private String seenDuringRequest(String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anything");
        if (header != null) {
            request.addHeader(RequestIdFilter.HEADER, header);
        }
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get(RequestIdFilter.MDC_KEY));
            }
        });
        return seen.get();
    }
}
