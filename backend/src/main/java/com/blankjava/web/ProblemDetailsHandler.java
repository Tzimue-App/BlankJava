package com.blankjava.web;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error leaves the API as RFC 9457 Problem Details ({@code application/problem+json}, schema
 * {@code Problem} of api/openapi.yaml), with the request's {@code traceId}.
 *
 * <p>Spring MVC exceptions (unknown path, unsupported method, invalid body…) are mapped by {@link
 * ResponseEntityExceptionHandler}; anything else becomes a 500 whose details stay in the logs.
 */
@RestControllerAdvice
public class ProblemDetailsHandler extends ResponseEntityExceptionHandler {

    static final String TRACE_ID = "traceId";

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

    private final TraceIds traceIds;

    public ProblemDetailsHandler(TraceIds traceIds) {
        this.traceIds = traceIds;
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            problem.setProperty(TRACE_ID, traceIds.current());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    /** Unexpected error: logged once here with its stack trace, never exposed to the client. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        String traceId = traceIds.current();
        log.error("Unexpected error (traceId={})", traceId, exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error. Quote the trace id when reporting it.");
        problem.setProperty(TRACE_ID, traceId);
        return ResponseEntity.internalServerError()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
