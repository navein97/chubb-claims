package com.chubb.claims.api;

import com.chubb.claims.app.NotFoundException;
import com.chubb.claims.domain.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/** Maps failures to RFC 7807 problem responses with stable status codes the frontend can branch on. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException e) {
        HttpStatus status = switch (e.kind()) {
            case CONFLICT -> HttpStatus.CONFLICT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case INVALID -> HttpStatus.BAD_REQUEST;
        };
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** Lost a race (e.g. two handlers picked up the same claim): tell the client to refresh. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This claim was just changed by someone else. Refresh and try again.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
