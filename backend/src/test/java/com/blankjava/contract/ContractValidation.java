package com.blankjava.contract;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Checks MockMvc responses against api/openapi.yaml (docs/technical_analysis.md §7.1, contract
 * compliance): status, headers and body must be what the contract documents for that operation.
 *
 * <p>Only responses are checked. Requests are not, because tests send invalid requests on
 * purpose to check the {@code 400} answers.
 *
 * <pre>{@code
 * mvc.perform(post("/api/client-logs")...)
 *         .andExpect(status().isAccepted())
 *         .andExpect(ContractValidation.matchesContract());
 * }</pre>
 */
public final class ContractValidation {

    /** The contract, read from the repository (tests run from {@code backend/}). */
    static final Path CONTRACT = Path.of("..", "api", "openapi.yaml");

    private static final OpenApiInteractionValidator VALIDATOR =
            OpenApiInteractionValidator.createForInlineApiSpecification(readContract())
                    .withBasePathOverride("/api")
                    .withLevelResolver(LevelResolver.create()
                            .withLevel("validation.request", ValidationReport.Level.IGNORE)
                            // By default the validator adds `additionalProperties: false` to every
                            // schema that does not declare it. That breaks `allOf` (each branch of
                            // ValidationProblem would reject the members of the other) and is not
                            // what the contract says. Off: only an explicit `additionalProperties`
                            // of the contract is checked.
                            .withLevel("validation.schema.additionalProperties", ValidationReport.Level.IGNORE)
                            // It also rejects members that the explicit `additionalProperties: true`
                            // of Problem allows (e.g. `errors`), a bug of its OpenAPI 3.1 support.
                            // No response schema of the contract is closed, so nothing is lost.
                            .withLevel(
                                    "validation.response.body.schema.additionalProperties",
                                    ValidationReport.Level.IGNORE)
                            .build())
                    .build();

    private ContractValidation() {}

    private static String readContract() {
        try {
            return Files.readString(CONTRACT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the contract " + CONTRACT.toAbsolutePath(), e);
        }
    }

    /** Fails when the response does not match the contract. */
    public static ResultMatcher matchesContract() {
        return OpenApiValidationMatchers.openApi().isValid(VALIDATOR);
    }

    /** The shared validator, to check responses built by hand. */
    public static OpenApiInteractionValidator validator() {
        return VALIDATOR;
    }
}
