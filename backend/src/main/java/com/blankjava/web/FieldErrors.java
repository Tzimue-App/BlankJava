package com.blankjava.web;

import com.blankjava.api.model.FieldError;
import com.blankjava.api.model.FieldError.CodeEnum;
import jakarta.validation.ConstraintViolation;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds the {@code errors} of a {@code ValidationProblem} (api/openapi.yaml): one {@code {field,
 * code, message}} per invalid field. The {@code code} is what clients rely on (they show their own
 * translated text); the {@code message} is for developers and never repeats the rejected value, so
 * a password cannot end up in a response.
 */
public final class FieldErrors {

    static final String ERRORS = "errors";
    static final String DETAIL = "Some fields are invalid.";

    private static final Set<String> REQUIRED = Set.of("NotNull", "NotBlank", "NotEmpty");
    private static final Set<String> LENGTH = Set.of("Size", "Length");

    private FieldErrors() {}

    /** One invalid field. */
    public static FieldError of(String field, CodeEnum code, String message) {
        return new FieldError(field, code).message(message);
    }

    /** The {@code 400} Problem listing {@code errors}, sorted by field for a stable output. */
    static ProblemDetail problem(Collection<FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, DETAIL);
        problem.setProperty(
                ERRORS,
                errors.stream()
                        .sorted(Comparator.comparing(FieldError::getField)
                                .thenComparing(error -> error.getCode().getValue()))
                        .toList());
        return problem;
    }

    /** A Bean Validation violation of the generated request models, as a field error. */
    static FieldError of(String field, ConstraintViolation<?> violation) {
        CodeEnum code = code(violation);
        return of(field, code, message(code));
    }

    /**
     * The contract's code for a violated constraint: missing → {@code required}; length outside the
     * bounds → {@code too_short} / {@code too_long} (an empty string counts as missing); anything
     * else (e-mail format, pattern, enum…) → {@code invalid_format}.
     */
    static CodeEnum code(ConstraintViolation<?> violation) {
        Annotation constraint = violation.getConstraintDescriptor().getAnnotation();
        String name = constraint.annotationType().getSimpleName();
        if (REQUIRED.contains(name)) {
            return CodeEnum.REQUIRED;
        }
        if (LENGTH.contains(name)) {
            int length = length(violation.getInvalidValue());
            if (length == 0) {
                return CodeEnum.REQUIRED;
            }
            Object min = violation.getConstraintDescriptor().getAttributes().get("min");
            return min instanceof Integer bound && length < bound ? CodeEnum.TOO_SHORT : CodeEnum.TOO_LONG;
        }
        return CodeEnum.INVALID_FORMAT;
    }

    private static int length(@Nullable Object value) {
        return switch (value) {
            case CharSequence text -> text.length();
            case Collection<?> collection -> collection.size();
            case null, default -> 0;
        };
    }

    private static String message(CodeEnum code) {
        return switch (code) {
            case REQUIRED -> "The field is required.";
            case INVALID_FORMAT -> "The value has an invalid format.";
            case TOO_SHORT -> "The value is too short.";
            case TOO_LONG -> "The value is too long.";
            case COMPROMISED_PASSWORD -> "The password appears in known data breaches.";
        };
    }

    /** Field errors of a list of violations, named by {@code field}. */
    static List<FieldError> of(Collection<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(violation -> of(violation.getPropertyPath().toString(), violation))
                .toList();
    }
}
