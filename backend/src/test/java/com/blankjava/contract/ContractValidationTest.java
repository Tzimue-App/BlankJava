package com.blankjava.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import org.junit.jupiter.api.Test;

/** The response validator is active: it catches a response that breaks the contract. */
class ContractValidationTest {

    private static final String PROBLEM = "application/problem+json";

    @Test
    void aProblemMatchingTheContractPasses() {
        ValidationReport report = ContractValidation.validator()
                .validateResponse(
                        "/api/client-logs",
                        Request.Method.POST,
                        SimpleResponse.Builder.status(413)
                                .withContentType(PROBLEM)
                                .withBody("{\"type\":\"about:blank\",\"title\":\"Content Too Large\",\"status\":413,"
                                        + "\"traceId\":\"4bf92f3577b34da6a3ce929d0e0e4736\"}")
                                .build());

        assertThat(report.getMessages()).isEmpty();
    }

    @Test
    void aProblemWithoutTraceIdIsRejected() {
        ValidationReport report = ContractValidation.validator()
                .validateResponse(
                        "/api/client-logs",
                        Request.Method.POST,
                        SimpleResponse.Builder.status(400)
                                .withContentType(PROBLEM)
                                .withBody("{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400}")
                                .build());

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.getMessages())
                .anySatisfy(message -> assertThat(message.getMessage()).contains("traceId"));
    }

    @Test
    void anUndocumentedStatusIsRejected() {
        ValidationReport report = ContractValidation.validator()
                .validateResponse(
                        "/api/client-logs",
                        Request.Method.POST,
                        SimpleResponse.Builder.status(200)
                                .withContentType("application/json")
                                .withBody("{}")
                                .build());

        assertThat(report.hasErrors()).isTrue();
    }

    @Test
    void aValidationProblemWithItsErrorsPasses() {
        assertThat(register400(",\"errors\":[{\"field\":\"password\",\"code\":\"too_short\"}]")
                        .getMessages())
                .isEmpty();
    }

    @Test
    void aValidationProblemWithoutErrorsOrWithAnUnknownCodeIsRejected() {
        assertThat(register400("").hasErrors()).isTrue();
        assertThat(register400(",\"errors\":[{\"field\":\"password\",\"code\":\"too_weak\"}]")
                        .hasErrors())
                .isTrue();
    }

    private static ValidationReport register400(String errors) {
        return ContractValidation.validator()
                .validateResponse(
                        "/api/auth/register",
                        Request.Method.POST,
                        SimpleResponse.Builder.status(400)
                                .withContentType(PROBLEM)
                                .withBody("{\"type\":\"about:blank\",\"title\":\"Bad Request\",\"status\":400,"
                                        + "\"traceId\":\"4bf92f3577b34da6a3ce929d0e0e4736\"" + errors + "}")
                                .build());
    }
}
