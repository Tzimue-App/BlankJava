package com.blankjava.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * The access token is valid but its {@code sub} is no longer an account (deleted since the token
 * was issued). Answered exactly like an invalid token (contract response {@code Unauthorized}): the
 * client cannot tell the two apart and drops its session either way.
 */
public class TokenOwnerNotFoundException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    public TokenOwnerNotFoundException() {
        super(
                HttpStatus.UNAUTHORIZED,
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ProblemSecurityHandlers.UNAUTHORIZED_DETAIL),
                null);
        getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, ProblemSecurityHandlers.INVALID_TOKEN_CHALLENGE);
    }
}
