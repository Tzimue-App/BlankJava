package com.blankjava.web;

import com.blankjava.api.model.FieldError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    /** RFC 9457 default problem type, always written: the contract makes {@code type} required. */
    static final URI ABOUT_BLANK = URI.create("about:blank");

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsHandler.class);

    private final TraceIds traceIds;

    public ProblemDetailsHandler(TraceIds traceIds) {
        this.traceIds = traceIds;
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            complete(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    /** Adds what every Problem of the contract carries: an explicit {@code type} and the trace id. */
    private void complete(ProblemDetail problem) {
        // Spring leaves "type" out of the JSON when it is not set, although RFC 9457 defaults it
        if (problem.getType() == null || ABOUT_BLANK.equals(problem.getType())) {
            problem.setType(ABOUT_BLANK);
        }
        problem.setProperty(TRACE_ID, traceIds.current());
    }

    /**
     * A request breaking the contract's constraints on a parameter (validation of the generated
     * interfaces runs in Spring's method-validation proxy, which throws this instead of an MVC
     * exception): a {@code 400} listing every violation with its {@code code}.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception) {
        ProblemDetail problem = FieldErrors.problem(FieldErrors.of(exception.getConstraintViolations()));
        complete(problem);
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /**
     * A request body breaking the constraints of its generated model ({@code @Valid @RequestBody}):
     * the contract's {@code ValidationProblem}, one entry per invalid field named as in the JSON.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(ProblemDetailsHandler::fieldError)
                .toList();
        ProblemDetail problem = errors.isEmpty()
                ? ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, "The request does not match the API contract.")
                : FieldErrors.problem(errors);
        return createResponseEntity(problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static FieldError fieldError(org.springframework.validation.FieldError error) {
        if (error.contains(ConstraintViolation.class)) {
            return FieldErrors.of(error.getField(), error.unwrap(ConstraintViolation.class));
        }
        // Not a Bean Validation error (e.g. a value that cannot be converted to the field's type)
        return FieldErrors.of(error.getField(), FieldError.CodeEnum.INVALID_FORMAT, "The value has an invalid format.");
    }

    /** Unexpected error: logged once here with its stack trace, never exposed to the client. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        String traceId = traceIds.current();
        log.error("Unexpected error (traceId={})", traceId, exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error. Quote the trace id when reporting it.");
        problem.setType(ABOUT_BLANK);
        problem.setProperty(TRACE_ID, traceId);
        return ResponseEntity.internalServerError()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
