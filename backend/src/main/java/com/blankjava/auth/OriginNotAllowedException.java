package com.blankjava.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** {@code Origin} missing or not an allowed origin on a cookie endpoint: {@code 403}. */
public class OriginNotAllowedException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    static final String DETAIL = "Origin not allowed.";

    public OriginNotAllowedException() {
        super(HttpStatus.FORBIDDEN, ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, DETAIL), null);
    }
}
