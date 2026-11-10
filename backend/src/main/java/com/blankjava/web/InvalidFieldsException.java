package com.blankjava.web;

import com.blankjava.api.model.FieldError;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Fields breaking a rule checked by the backend itself (not by the generated Bean Validation
 * constraints): answered as the contract's {@code ValidationProblem}, a {@code 400} whose {@code
 * errors} lists every invalid field with its {@code code}.
 */
public class InvalidFieldsException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    public InvalidFieldsException(List<FieldError> errors) {
        super(HttpStatus.BAD_REQUEST, FieldErrors.problem(errors), null);
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("At least one invalid field is required");
        }
    }
}
