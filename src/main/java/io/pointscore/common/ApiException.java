package io.pointscore.common;

import org.springframework.http.HttpStatus;

/**
 * Base for failures that are the caller's business rather than ours.
 *
 * <p>Carrying the status and a stable machine-readable code on the exception
 * keeps HTTP concerns out of the services: a service throws
 * {@code InsufficientPointsException} because that is the domain truth, and one
 * handler decides it renders as 422.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Stable identifier clients can branch on, unlike the human-readable message. */
    public String getCode() {
        return code;
    }
}
