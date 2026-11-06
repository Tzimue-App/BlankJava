package com.blankjava.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blankjava.api.model.FieldError;
import com.blankjava.api.model.FieldError.CodeEnum;
import com.blankjava.api.model.RegisterRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** Constraint → contract {@code code} mapping, on the generated request models. */
class FieldErrorsTest {

    private static final jakarta.validation.ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @Test
    void aMissingFieldIsRequired() {
        assertThat(codes(new RegisterRequest(null, "Jane", "tangerine-river-boat")))
                .containsExactly(error("email", CodeEnum.REQUIRED));
    }

    @Test
    void anEmptyStringIsRequiredNotTooShort() {
        assertThat(codes(new RegisterRequest("jane@example.com", "", "tangerine-river-boat")))
                .containsExactly(error("displayName", CodeEnum.REQUIRED));
    }

    @Test
    void aValueAboveTheHardBoundIsTooLong() {
        assertThat(codes(new RegisterRequest("jane@example.com", "Jane", "x".repeat(257))))
                .containsExactly(error("password", CodeEnum.TOO_LONG));
        assertThat(codes(new RegisterRequest("j".repeat(255), "Jane", "tangerine-river-boat")))
                .containsExactly(error("email", CodeEnum.TOO_LONG));
    }

    @Test
    void messagesNeverRepeatTheRejectedValue() {
        String password = "secret-".repeat(40);

        assertThat(FieldErrors.of(VALIDATOR.validate(new RegisterRequest("jane@example.com", "Jane", password))))
                .singleElement()
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("secret"));
    }

    @Test
    void theProblemListsErrorsSortedByField() {
        var problem = FieldErrors.problem(List.of(
                FieldErrors.of("password", CodeEnum.TOO_SHORT, "short"),
                FieldErrors.of("displayName", CodeEnum.TOO_SHORT, "short")));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo(FieldErrors.DETAIL);
        assertThat(problem.getProperties())
                .extractingByKey(FieldErrors.ERRORS)
                .asList()
                .extracting("field")
                .containsExactly("displayName", "password");
    }

    @Test
    void anInvalidFieldsExceptionNeedsAtLeastOneError() {
        assertThatThrownBy(() -> new InvalidFieldsException(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<FieldError> codes(RegisterRequest request) {
        return FieldErrors.of(VALIDATOR.validate(request)).stream()
                .map(error -> new FieldError(error.getField(), error.getCode()))
                .toList();
    }

    private static FieldError error(String field, CodeEnum code) {
        return new FieldError(field, code);
    }
}
