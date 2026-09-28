package io.pointscore.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into RFC 9457 problem responses, so every failure in the API
 * has the same shape and clients never have to parse a stack trace or guess
 * whether a 500 was really their fault.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        // Expected outcomes, not incidents -- logged at DEBUG so a member
        // mistyping an id does not page anybody.
        log.debug("{} on {}: {}", ex.getCode(), request.getRequestURI(), ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getCode());
        problem.setType(URI.create("https://pointscore.io/errors/" + ex.getCode().toLowerCase()));
        problem.setProperty("code", ex.getCode());
        problem.setProperty("timestamp", Instant.now());

        if (ex instanceof InsufficientPointsException insufficient) {
            problem.setProperty("required", insufficient.getRequired());
            problem.setProperty("available", insufficient.getAvailable());
        }
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more fields are invalid");
        problem.setTitle("VALIDATION_FAILED");
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("fieldErrors", fieldErrors);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Last resort. The detail is deliberately vague -- an unexpected failure
     * must not leak SQL or class names to a caller -- while the log keeps the
     * full trace for us.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.");
        problem.setTitle("INTERNAL_ERROR");
        problem.setProperty("code", "INTERNAL_ERROR");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
