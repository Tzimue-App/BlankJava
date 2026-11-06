package com.blankjava.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * The refresh cookie is missing, unknown, expired or revoked (contract response {@code
 * RefreshRejected}): {@code 401} and the cookie is cleared, so the browser stops sending a dead
 * token and the client treats the user as anonymous.
 */
public class RefreshRejectedException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    static final String DETAIL = "The session has expired.";

    public RefreshRejectedException() {
        super(HttpStatus.UNAUTHORIZED, ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, DETAIL), null);
        getHeaders()
                .add(HttpHeaders.SET_COOKIE, RefreshTokenService.clearCookie().toString());
    }
}
