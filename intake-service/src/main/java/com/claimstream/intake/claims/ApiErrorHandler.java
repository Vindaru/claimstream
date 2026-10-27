package com.claimstream.intake.claims;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns intake failures into RFC 9457 problem responses with a machine-readable {@code code}. */
@RestControllerAdvice
public class ApiErrorHandler {

    @ExceptionHandler({InvalidClaimException.class, MissingRequestHeaderException.class,
        HttpMessageNotReadableException.class})
    ProblemDetail badRequest(Exception e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    @ExceptionHandler(RequestInProgressException.class)
    ProblemDetail inProgress(RequestInProgressException e) {
        return problem(HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS", e.getMessage());
    }

    @ExceptionHandler(DuplicateClaimException.class)
    ProblemDetail duplicate(DuplicateClaimException e) {
        return problem(HttpStatus.CONFLICT, "DUPLICATE_CLAIM", e.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail keyReused(IdempotencyKeyReusedException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
