package com.blankjava.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Login refused (contract response {@code InvalidCredentials}): one {@code 401} body for an unknown
 * e-mail, a wrong password or a password that could never be valid, so the response never tells
 * which part was wrong nor whether the e-mail is registered.
 */
public class InvalidCredentialsException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    static final String DETAIL = "Invalid e-mail or password.";

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, DETAIL), null);
    }
}
