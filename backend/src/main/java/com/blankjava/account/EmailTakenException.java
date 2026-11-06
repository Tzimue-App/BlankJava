package com.blankjava.account;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * The e-mail is already registered: {@code 409} with {@code code: email_taken} (contract response
 * {@code EmailTaken}). The message never contains the address.
 */
public class EmailTakenException extends ErrorResponseException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "email_taken";

    public EmailTakenException() {
        super(HttpStatus.CONFLICT, problem(), null);
    }

    private static ProblemDetail problem() {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This e-mail address is already registered.");
        problem.setProperty("code", CODE);
        return problem;
    }
}
